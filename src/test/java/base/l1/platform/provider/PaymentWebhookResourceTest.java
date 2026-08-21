package base.l1.platform.provider;

import base.l1.platform.provider.inbox.ProviderEventRecord;
import base.l1.platform.provider.inbox.RecordProviderEventUC;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inbound webhook path, driven over HTTP the way a provider drives it.
 *
 * <p>Bodies here are built as literal strings with localstripe's own formatting — two-space
 * indentation, keys in sorted order — rather than serialised from an object. That is the point: the
 * signature covers bytes, and a test that lets Jackson produce both the signed payload and the
 * request body would agree with itself no matter what the endpoint did.
 *
 * <p>No {@code @TestSecurity} anywhere in this class, also deliberately. The endpoint is
 * unauthenticated by design and the signature is the credential; if any of these started needing a
 * token, the provider integration would be broken in production and green here.
 */
@QuarkusTest
class PaymentWebhookResourceTest {

    private static final String SECRET = "whsec_test";

    @Inject
    RecordProviderEventUC inbox;

    @Test
    void aSignedPayoutConfirmationIsAcceptedAndStored() {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String reference = "P0786ABCDEF1";
        String body = payoutEvent(eventId, "payout.paid", reference, 250_000, "eur");

        post(body, signature(body, Instant.now().getEpochSecond()))
                .statusCode(200)
                .body("content.signal", org.hamcrest.Matchers.equalTo("PAYOUT_CONFIRMED"))
                .body("content.processed", org.hamcrest.Matchers.equalTo(false));

        ProviderEventRecord stored = stored(eventId);
        assertEquals("PAYOUT_CONFIRMED", stored.getSignal());
        assertEquals(reference, stored.getProviderRef(),
                "the reference we minted must survive the round trip, not the provider's own id");
        assertEquals(250_000L, stored.getAmountMinor());
        assertEquals("EUR", stored.getCurrency(), "Stripe sends currency lower-case; Money will not");
        assertEquals(body, stored.getRawPayload(), "the bytes that were signed are what we keep");
        assertNull(stored.getTenantCode(),
                "a webhook carries no tenant; guessing one is worse than recording that we do not know");
        assertNull(stored.getProcessedAt(), "nothing dispatches these yet");
    }

    @Test
    void aBodyAlteredAfterSigningIsRefused() {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String body = payoutEvent(eventId, "payout.paid", "P0786ABCDEF1", 250_000, "eur");
        String header = signature(body, Instant.now().getEpochSecond());

        // One digit. This is the whole reason the signature covers the body rather than an id.
        String tampered = body.replace("250000", "950000");

        post(tampered, header).statusCode(400).body("error.code", org.hamcrest.Matchers.equalTo("WEBHOOK_INVALID"));
        assertTrue(inbox.find("localstripe", eventId).isEmpty(), "nothing may be stored from an unverified body");
    }

    @Test
    void reformattingTheBodyBreaksTheSignature() {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String body = payoutEvent(eventId, "payout.paid", "P0786ABCDEF1", 250_000, "eur");
        String header = signature(body, Instant.now().getEpochSecond());

        // Same JSON, same values, whitespace removed — what an endpoint that parsed and
        // re-serialised would end up hashing. It must not verify.
        String compacted = body.replaceAll("\\s+", "");

        post(compacted, header).statusCode(400);
        assertTrue(inbox.find("localstripe", eventId).isEmpty());
    }

    @Test
    void aCapturedWebhookCannotBeReplayedLater() {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String body = payoutEvent(eventId, "payout.paid", "P0786ABCDEF1", 1_000, "eur");
        long anHourAgo = Instant.now().minusSeconds(3_600).getEpochSecond();

        // Correctly signed, for a timestamp outside the configured skew.
        post(body, signature(body, anHourAgo)).statusCode(400);
        assertTrue(inbox.find("localstripe", eventId).isEmpty());
    }

    @Test
    void aRedeliveredEventIsAcceptedAgainAndStoredOnce() {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String body = payoutEvent(eventId, "payout.paid", "P0786ABCDEF1", 4_200, "eur");
        String header = signature(body, Instant.now().getEpochSecond());

        post(body, header).statusCode(200);
        // A provider retries until it gets a 2xx, so the second delivery must not be a conflict.
        post(body, header).statusCode(200);

        assertEquals(1L, ProviderEventRecord.count("providerEventId = ?1", eventId));
    }

    @Test
    void anUnknownProviderIsRefusedBeforeTheBodyMatters() {
        String body = payoutEvent("evt_whatever", "payout.paid", "P0786ABCDEF1", 100, "eur");

        given().contentType(ContentType.JSON)
                .header("Stripe-Signature", "t=1,v1=deadbeef")
                .body(body)
                .when().post("/webhooks/definitely-not-configured")
                .then().statusCode(400)
                .body("error.code", org.hamcrest.Matchers.equalTo("PROVIDER_UNKNOWN"));
    }

    @Test
    void aMissingSignatureHeaderIsRefused() {
        String body = payoutEvent("evt_nosig", "payout.paid", "P0786ABCDEF1", 100, "eur");

        given().contentType(ContentType.JSON)
                .body(body)
                .when().post("/webhooks/localstripe")
                .then().statusCode(400);
    }

    @Test
    void anEventTypeWeDoNotActOnIsStillRecorded() {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String body = payoutEvent(eventId, "customer.created", "P0786ABCDEF1", 0, "eur");

        post(body, signature(body, Instant.now().getEpochSecond())).statusCode(200);

        // UNKNOWN rather than rejected: an event we cannot classify is still evidence, and
        // 4xx-ing it would make the provider retry something we will never understand.
        assertEquals("UNKNOWN", stored(eventId).getSignal());
    }

    // ---- helpers ----------------------------------------------------------------------

    private static io.restassured.response.ValidatableResponse post(String body, String signatureHeader) {
        return given().contentType(ContentType.JSON)
                .header("Stripe-Signature", signatureHeader)
                .body(body)
                .when().post("/webhooks/localstripe")
                .then();
    }

    private ProviderEventRecord stored(String eventId) {
        Optional<ProviderEventRecord> found = inbox.find("localstripe", eventId);
        assertTrue(found.isPresent(), "event " + eventId + " should have been stored");
        ProviderEventRecord record = found.get();
        assertNotNull(record.getReceivedAt());
        return record;
    }

    /** Exactly what localstripe sends: {@code t=<created>,v1=<hmac of "created.body">}. */
    private static String signature(String body, long created) {
        return "t=" + created + ",v1=" + hmac(created + "." + body);
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A Stripe event as localstripe writes it — {@code json.dumps(..., indent=2, sort_keys=True)}.
     * Written out literally so the formatting is part of the test rather than a side effect of
     * whatever serialiser happens to be on the classpath.
     */
    private static String payoutEvent(String eventId, String type, String reference,
                                      long amount, String currency) {
        long created = Instant.now().getEpochSecond();
        return """
                {
                  "api_version": "2017-08-15",
                  "created": %d,
                  "data": {
                    "object": {
                      "amount": %d,
                      "currency": "%s",
                      "id": "po_%s",
                      "metadata": {
                        "reference": "%s"
                      },
                      "object": "payout",
                      "status": "paid"
                    }
                  },
                  "id": "%s",
                  "object": "event",
                  "type": "%s"
                }""".formatted(created, amount, currency, eventId.substring(4), reference, eventId, type);
    }
}

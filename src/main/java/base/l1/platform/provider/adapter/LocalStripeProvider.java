package base.l1.platform.provider.adapter;

import base.l0.system.money.Money;
import base.l1.platform.provider.PaymentHandle;
import base.l1.platform.provider.PaymentProvider;
import base.l1.platform.provider.PaymentRequest;
import base.l1.platform.provider.ProviderCallFailedException;
import base.l1.platform.provider.ProviderCapabilities;
import base.l1.platform.provider.ProviderEvent;
import base.l1.platform.provider.RefundHandle;
import base.l1.platform.provider.RefundRequest;
import base.l1.platform.provider.WebhookSigner;
import base.l1.platform.provider.WebhookVerificationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An adapter for <a href="https://github.com/adrienverge/localstripe">localstripe</a>, a stateful
 * fake Stripe server that runs in a container next to the app.
 *
 * <p>Named for what it is. It speaks the Stripe API and verifies real Stripe webhook signatures,
 * but it is a development adapter: it is registered only when
 * {@code payment.provider.localstripe.enabled=true}, which no production profile sets. Calling it
 * {@code StripePaymentProvider} would have implied a Stripe integration that does not exist —
 * there is no 3-D Secure, no confirmation flow, no Connect — and the point of this platform is
 * that a provider id never resolves to something other than what it claims to be.
 *
 * <p>What it is genuinely for is the webhook path, which no in-process mock can test. localstripe
 * signs {@code HMAC-SHA256(secret, created + "." + body)} and sends it as
 * {@code Stripe-Signature: t=…,v1=…} — byte for byte the construction {@link WebhookSigner}
 * implements. It also serialises its payloads with {@code indent=2, sort_keys=True}, so any
 * verification that re-encodes parsed JSON instead of hashing the received bytes fails immediately
 * and loudly. That is the bug this exists to catch.
 */
@ApplicationScoped
@IfBuildProperty(name = "payment.provider.localstripe.enabled", stringValue = "true")
public class LocalStripeProvider implements PaymentProvider {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The key our own reference travels in, on anything we initiate. */
    static final String REFERENCE_KEY = "reference";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Inject
    WebhookSigner signer;

    @ConfigProperty(name = "payment.provider.localstripe.api-base", defaultValue = "http://localhost:8420")
    String apiBase;

    @ConfigProperty(name = "payment.provider.localstripe.api-key", defaultValue = "sk_test_localstripe")
    String apiKey;

    @ConfigProperty(name = "payment.provider.localstripe.webhook-secret", defaultValue = "whsec_localstripe_dev")
    String webhookSecret;

    @Override
    public String id() {
        return "localstripe";
    }

    @Override
    public ProviderCapabilities capabilities() {
        // Refunds and payouts are real API calls here; localstripe emits payout.paid and
        // payout.failed, which is what payoutReturnNotification claims. There is no settlement
        // file, and saying otherwise is how a flag stops meaning anything.
        return new ProviderCapabilities(true, false, true, true, false);
    }

    @Override
    public PaymentHandle initiatePayment(PaymentRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("amount", Long.toString(request.amount().amountMinor()));
        form.put("currency", request.amount().currency().toLowerCase(Locale.ROOT));
        putMetadata(form, request.metadata());

        JsonNode intent = post("/v1/payment_intents", form, request.idempotencyKey());
        return new PaymentHandle(
                id(),
                intent.path("id").asText(null),
                null,
                intent.path("client_secret").asText(null),
                Instant.now().plus(Duration.ofMinutes(30)),
                request.metadata() == null ? Map.of() : request.metadata());
    }

    @Override
    public RefundHandle refund(RefundRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("payment_intent", request.originalProviderRef());
        form.put("amount", Long.toString(request.amount().amountMinor()));
        if (request.reason() != null) {
            form.put("metadata[reason]", request.reason());
        }

        JsonNode refund = post("/v1/refunds", form, request.idempotencyKey());
        return new RefundHandle(id(), refund.path("id").asText(null),
                "succeeded".equals(refund.path("status").asText()));
    }

    @Override
    public ProviderEvent parseWebhook(String rawBody, Map<String, String> headers) {
        StripeSignatureHeader header = StripeSignatureHeader.parse(headers.get("stripe-signature"));
        signer.verify(webhookSecret, header.signature(), header.timestamp(), rawBody);

        JsonNode event;
        try {
            event = JSON.readTree(rawBody);
        } catch (Exception e) {
            throw new WebhookVerificationException("Webhook body is not valid JSON: " + e.getMessage());
        }

        JsonNode object = event.path("data").path("object");
        return new ProviderEvent(
                id(),
                event.path("id").asText(null),
                referenceOf(object),
                signalOf(event.path("type").asText("")),
                amountOf(object),
                event.hasNonNull("created")
                        ? Instant.ofEpochSecond(event.get("created").asLong())
                        : Instant.now(),
                rawBody);
    }

    /**
     * The reference the event is about.
     *
     * <p>Prefers the reference <em>we</em> minted and attached when initiating, because that is the
     * one that resolves to a document on our side; the provider's own id is only a fallback for
     * events we did not originate.
     */
    private static String referenceOf(JsonNode object) {
        String ours = object.path("metadata").path(REFERENCE_KEY).asText(null);
        return ours != null && !ours.isBlank() ? ours : object.path("id").asText(null);
    }

    private static Money amountOf(JsonNode object) {
        String currency = object.path("currency").asText(null);
        if (currency == null || currency.isBlank()) {
            return null;
        }
        for (String field : new String[]{"amount", "amount_due", "total"}) {
            if (object.hasNonNull(field)) {
                // Stripe's minor units are ours: zero-decimal currencies like VND carry whole
                // units in this field, which is what Money means by minor units for JPY and VND.
                return Money.of(object.get(field).asLong(), currency.toUpperCase(Locale.ROOT));
            }
        }
        return null;
    }

    /**
     * Provider event type to normalised signal.
     *
     * <p>Only the types localstripe actually emits are mapped. It sends nothing for refunds or
     * disputes, so {@code REFUND_SETTLED} and {@code CHARGEBACK_OPENED} have no local source and
     * are not listed — an entry here for an event that can never arrive would read as coverage we
     * do not have.
     */
    private static ProviderEvent.Signal signalOf(String type) {
        return switch (type) {
            case "payment_intent.succeeded", "invoice.payment_succeeded" -> ProviderEvent.Signal.PAYMENT_SUCCEEDED;
            case "payment_intent.payment_failed", "invoice.payment_failed" -> ProviderEvent.Signal.PAYMENT_FAILED;
            case "payout.paid"     -> ProviderEvent.Signal.PAYOUT_CONFIRMED;
            case "payout.failed"   -> ProviderEvent.Signal.PAYOUT_RETURNED;
            default                -> ProviderEvent.Signal.UNKNOWN;
        };
    }

    private static void putMetadata(Map<String, String> form, Map<String, String> metadata) {
        if (metadata == null) {
            return;
        }
        metadata.forEach((key, value) -> form.put("metadata[" + key + "]", value));
    }

    private JsonNode post(String path, Map<String, String> form, String idempotencyKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                // Forwarded unchanged, per the SPI contract: a retry must not create a second
                // charge or a second refund.
                .header("Idempotency-Key", idempotencyKey)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ProviderCallFailedException(
                        id(), path, response.statusCode(), response.body());
            }
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new ProviderCallFailedException(id(), path, 0, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallFailedException(id(), path, 0, "interrupted");
        }
    }

    private static String encode(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        form.forEach((key, value) -> {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return body.toString();
    }
}


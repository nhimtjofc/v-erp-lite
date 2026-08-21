package base.l1.platform.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the four specific weaknesses the review found in the scheme this replaces.
 */
class WebhookSignerTest {

    private WebhookSigner signer;

    @BeforeEach
    void setUp() {
        signer = new WebhookSigner();
        signer.maxSkew = Duration.ofMinutes(5);
    }

    @Test
    void aValidSignatureVerifies() {
        long now = Instant.now().getEpochSecond();
        String body = "{\"status\":\"SUCCESS\",\"amountMinor\":100000}";
        String signature = signer.sign("secret", now, body);
        assertDoesNotThrow(() -> signer.verify("secret", signature, String.valueOf(now), body));
    }

    @Test
    void aSignatureForSuccessDoesNotAlsoAuthenticateFailure() {
        // The old scheme signed only intentId:txnId:amount, so one captured signature was valid for
        // both outcomes. The whole body is covered now.
        long now = Instant.now().getEpochSecond();
        String success = "{\"status\":\"SUCCESS\",\"amountMinor\":100000}";
        String failed = "{\"status\":\"FAILED\",\"amountMinor\":100000}";
        String signature = signer.sign("secret", now, success);
        assertThrows(WebhookVerificationException.class,
                () -> signer.verify("secret", signature, String.valueOf(now), failed));
    }

    @Test
    void aCapturedWebhookCannotBeReplayedLater() {
        long longAgo = Instant.now().minus(Duration.ofHours(2)).getEpochSecond();
        String body = "{\"status\":\"SUCCESS\"}";
        String signature = signer.sign("secret", longAgo, body);
        WebhookVerificationException e = assertThrows(WebhookVerificationException.class,
                () -> signer.verify("secret", signature, String.valueOf(longAgo), body));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("outside the accepted window"));
    }

    @Test
    void eachProviderSecretIsIndependent() {
        long now = Instant.now().getEpochSecond();
        String body = "{\"status\":\"SUCCESS\"}";
        String signedByVnpay = signer.sign("vnpay-secret", now, body);
        assertThrows(WebhookVerificationException.class,
                () -> signer.verify("stripe-secret", signedByVnpay, String.valueOf(now), body));
    }

    @Test
    void missingSignatureOrTimestampIsRejectedNotIgnored() {
        long now = Instant.now().getEpochSecond();
        assertThrows(WebhookVerificationException.class,
                () -> signer.verify("secret", null, String.valueOf(now), "{}"));
        assertThrows(WebhookVerificationException.class,
                () -> signer.verify("secret", "deadbeef", null, "{}"));
        assertThrows(WebhookVerificationException.class,
                () -> signer.verify("", "deadbeef", String.valueOf(now), "{}"));
    }
}


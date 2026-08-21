package base.l1.platform.provider;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * HMAC signing and verification for webhooks, per provider.
 *
 * <p>Four specific weaknesses in the scheme this replaces, all fixed here:
 * <ul>
 *   <li>The secret was a compile-time constant in source, shared by every provider. Secrets are now
 *       per-provider config, so one can be rotated or leaked without touching the others.</li>
 *   <li>Only {@code intentId:txnId:amount} was signed — not the status, so a single valid signature
 *       authenticated both a SUCCESS and a FAILED webhook. The signed payload now covers the whole
 *       body.</li>
 *   <li>No timestamp, so a captured webhook replayed forever. A timestamp is signed alongside the
 *       body and rejected outside a configured skew.</li>
 *   <li>Comparison used {@code String.equals}, which returns early. {@link MessageDigest#isEqual}
 *       is constant-time.</li>
 * </ul>
 *
 * <p>Signing covers the raw request body, not a payload rebuilt from parsed fields. Re-serialising
 * JSON does not reproduce the bytes the provider signed.
 */
@ApplicationScoped
public class WebhookSigner {

    @ConfigProperty(name = "payment.provider.webhook.max-skew", defaultValue = "PT5M")
    Duration maxSkew;

    /** Canonical signed payload: the timestamp, a dot, then the exact body received. */
    public String sign(String secret, long timestampEpochSeconds, String rawBody) {
        return hmacHex(secret, timestampEpochSeconds + "." + rawBody);
    }

    public void verify(String secret, String signatureHeader, String timestampHeader, String rawBody) {
        if (secret == null || secret.isBlank()) {
            throw new WebhookVerificationException("No webhook secret is configured for this provider");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookVerificationException("Webhook is missing its signature header");
        }

        long timestamp = parseTimestamp(timestampHeader);
        Instant sent = Instant.ofEpochSecond(timestamp);
        Duration drift = Duration.between(sent, Instant.now()).abs();
        if (drift.compareTo(maxSkew) > 0) {
            throw new WebhookVerificationException(
                    "Webhook timestamp is outside the accepted window (drift " + drift + ", max " + maxSkew + ")");
        }

        String expected = sign(secret, timestamp, rawBody);
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new WebhookVerificationException("Webhook signature does not verify");
        }
    }

    private long parseTimestamp(String header) {
        return Optional.ofNullable(header)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new WebhookVerificationException("Webhook timestamp is not an epoch-second value");
                    }
                })
                .orElseThrow(() -> new WebhookVerificationException("Webhook is missing its timestamp header"));
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}


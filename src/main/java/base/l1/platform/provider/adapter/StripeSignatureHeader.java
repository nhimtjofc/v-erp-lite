package base.l1.platform.provider.adapter;

import base.l1.platform.provider.WebhookVerificationException;

/**
 * The {@code Stripe-Signature} header, split into the two parts our verifier needs.
 *
 * <p>Stripe sends one header carrying comma-separated key/value pairs — {@code t=<epoch>} and one
 * or more {@code v1=<hex>} — where the signature is HMAC-SHA256 over {@code t + "." + rawBody}.
 * That is the same construction {@code WebhookSigner} implements, so the only adaptation needed is
 * unpacking the header; the crypto is shared with every other provider.
 *
 * <p>Multiple {@code v1} values appear during a secret rotation, when Stripe signs with both the
 * old and the new secret. Only the first is read here, which is correct while a single secret is
 * configured and is the thing to revisit if rotation is ever done without downtime.
 */
record StripeSignatureHeader(String timestamp, String signature) {

    static StripeSignatureHeader parse(String header) {
        if (header == null || header.isBlank()) {
            throw new WebhookVerificationException("Webhook is missing its Stripe-Signature header");
        }

        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if ("t".equals(key) && timestamp == null) {
                timestamp = value;
            } else if ("v1".equals(key) && signature == null) {
                signature = value;
            }
        }

        if (timestamp == null || signature == null) {
            throw new WebhookVerificationException(
                    "Stripe-Signature header carries no t= and v1= pair");
        }
        return new StripeSignatureHeader(timestamp, signature);
    }
}


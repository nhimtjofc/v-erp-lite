package base.l1.platform.provider;

/**
 * One payment provider — a card processor, a bank rail, an e-wallet.
 *
 * <p>This exists because the predecessor selected its provider with a string switch that defaulted
 * anything it did not recognise to a mock. A typo'd or unconfigured provider name silently became a
 * fake success, in any environment including production. Here an unknown id has no implementation
 * and the registry refuses it; there is no default case to fall through to.
 */
public interface PaymentProvider {

    /** Stable lower-case id, e.g. {@code "vnpay"}. Matches the {@code payment.provider.<id>.*} config prefix. */
    String id();

    ProviderCapabilities capabilities();

    PaymentHandle initiatePayment(PaymentRequest request);

    /**
     * @param request carries the idempotency key; a production adapter MUST forward it to the
     *                provider unchanged so a retry cannot create a second refund.
     */
    RefundHandle refund(RefundRequest request);

    /**
     * Verifies and parses an inbound webhook.
     *
     * @param rawBody the exact bytes received — not a re-serialisation of parsed fields, because a
     *                signature covers what was sent, and re-encoding will not reproduce it
     * @param headers the request headers, which is where most providers put the signature
     * @return the normalised event
     * @throws WebhookVerificationException if the signature, timestamp or payload does not verify
     */
    ProviderEvent parseWebhook(String rawBody, java.util.Map<String, String> headers);
}


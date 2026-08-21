package base.l1.platform.provider;

/**
 * What a provider can actually do. Declared rather than assumed, so a flow that needs partial
 * refunds fails at configuration time against a provider that cannot do them, not at runtime
 * against a customer who is waiting.
 */
public record ProviderCapabilities(
        boolean partialRefund,
        /**
         * Declared, but no method on {@link PaymentProvider} captures yet: {@code initiatePayment}
         * covers initiation and the provider captures on its own. Splitting authorisation from
         * capture is what this flag is for, and it is not wired.
         */
        boolean multiCapture,
        boolean payout,
        boolean payoutReturnNotification,
        boolean settlementFile
) {
    public static ProviderCapabilities minimal() {
        return new ProviderCapabilities(false, false, false, false, false);
    }
}


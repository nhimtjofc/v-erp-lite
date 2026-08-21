package base.l1.platform.provider;

import base.l0.system.money.Money;

import java.time.Instant;

/**
 * A normalised inbound provider notification. Each adapter is responsible for turning its
 * provider's wire format into this; nothing downstream should know what VNPay's field names are.
 */
public record ProviderEvent(
        String provider,
        String providerEventId,
        String providerRef,
        Signal signal,
        Money reportedAmount,
        Instant occurredAt,
        String rawPayload
) {
    public enum Signal {
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        PAYMENT_EXPIRED,
        REFUND_SETTLED,
        CHARGEBACK_OPENED,
        PAYOUT_CONFIRMED,
        PAYOUT_RETURNED,
        UNKNOWN
    }
}


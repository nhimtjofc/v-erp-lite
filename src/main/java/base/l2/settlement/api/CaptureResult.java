package base.l2.settlement.api;

import base.l0.system.money.Money;

import java.util.UUID;

public record CaptureResult(
        UUID entryUuid,
        Money grossCharged,
        Money providerFee,
        Money commissionTotal,
        int sellerCount
) {
    /** What the provider actually owes us for this capture: gross less its own fee. */
    public Money netReceivable() {
        return grossCharged.subtract(providerFee);
    }
}


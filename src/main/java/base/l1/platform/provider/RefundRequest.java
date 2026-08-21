package base.l1.platform.provider;

import base.l0.system.money.Money;

public record RefundRequest(
        String originalProviderRef,
        Money amount,
        String reason,
        String idempotencyKey
) {
}


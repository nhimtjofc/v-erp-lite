package base.l2.recon.bank.interfaces.dto;

import base.l0.system.money.Money;

/** Names what a suspense item turned out to be. {@code amount} is signed as the original was. */
public record ReclassifyRequest(
        String bankRef,
        String counterAccountCode,
        Money amount,
        String memo
) {
}


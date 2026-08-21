package base.l2.recon.bank.interfaces.dto;

import base.l0.system.money.Money;

import java.time.LocalDate;

/** What the bank states it held at the close of {@code statementDate}. */
public record AttestBalanceRequest(
        String bankAccountCode,
        LocalDate statementDate,
        Money closingBalance,
        String statementRef,
        String note
) {
}


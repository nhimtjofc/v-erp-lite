package base.l2.recon.bank.interfaces.dto;

import base.l0.system.money.Money;

import java.time.LocalDate;

/**
 * A statement line to book.
 *
 * <p>{@code amount} is signed as the statement reads it — positive in, negative out. Leave
 * {@code counterAccountCode} null when nobody knows yet what the movement was; it lands in suspense
 * and can be named later.
 */
public record RecordBankTransactionRequest(
        String bankAccountCode,
        Money amount,
        String counterAccountCode,
        String bankRef,
        LocalDate valueDate,
        String memo
) {
}


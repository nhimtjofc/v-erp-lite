package base.l2.recon.bank.domain;

import base.l0.system.money.Money;

import java.time.LocalDate;

/**
 * One line off a bank statement, as a human read it.
 *
 * <p>{@code amount} is <em>signed the way the statement reads</em> — positive money in, negative
 * money out — rather than carrying a direction. That is a departure from {@link
 * base.l2.ledger.journal.domain.PostingLine}, where amounts are positive and the direction holds
 * the sign, and it is deliberate: this record is filled in by someone with a statement in front of
 * them, and asking that person to translate "-12.50" into a credit to an asset account is asking
 * them to make the one mistake that produces a plausible, balanced, wrong entry.
 *
 * <p>{@code bankRef} is the bank's own reference for the transaction and is not optional. It is the
 * idempotency key, so it is what stops the same statement line being booked twice by two people
 * working the same reconciliation — a real risk in a manual process and the reason the key is not
 * generated here.
 *
 * <p>{@code counterAccountCode} may be null, meaning "seen, not yet understood". The transaction is
 * still booked immediately, against suspense. Waiting for the classification before recording the
 * movement would leave the bank balance wrong for exactly as long as the question takes to answer.
 */
public record BankTransaction(
        String bankAccountCode,
        Money amount,
        String counterAccountCode,
        String bankRef,
        LocalDate valueDate,
        String memo
) {
    public boolean isIntoBank() {
        return amount.amountMinor() > 0;
    }
}


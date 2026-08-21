package base.l2.ledger.journal.domain;

import base.l0.system.money.Money;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A line as requested, before it is posted. {@code lineUuid} is assigned by the caller so a
 * settlement can reference the exact line it created — an invoice line, say, that a later payment
 * will be allocated against.
 */
public record PostingLine(
        UUID lineUuid,
        UUID accountUuid,
        Direction direction,
        Money amount,
        LocalDate dueDate,
        String memo
) {
    public static PostingLine debit(UUID lineUuid, UUID accountUuid, Money amount) {
        return new PostingLine(lineUuid, accountUuid, Direction.DR, amount, null, null);
    }

    public static PostingLine credit(UUID lineUuid, UUID accountUuid, Money amount) {
        return new PostingLine(lineUuid, accountUuid, Direction.CR, amount, null, null);
    }

    public PostingLine dueOn(LocalDate due) {
        return new PostingLine(lineUuid, accountUuid, direction, amount, due, memo);
    }

    public PostingLine describedAs(String text) {
        return new PostingLine(lineUuid, accountUuid, direction, amount, dueDate, text);
    }

    public long signedMinor() {
        return direction.signed(amount.amountMinor());
    }
}


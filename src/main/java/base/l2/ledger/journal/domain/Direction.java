package base.l2.ledger.journal.domain;

/**
 * A line's side. The amount is always positive; this carries the sign, which is why a line can
 * never be accidentally negated by arithmetic.
 */
public enum Direction {
    DR,
    CR;

    public long signed(long amountMinor) {
        return this == DR ? amountMinor : -amountMinor;
    }

    public Direction opposite() {
        return this == DR ? CR : DR;
    }
}


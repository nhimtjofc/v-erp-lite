package base.l2.settlement.payout.domain;

/**
 * A payout's lifecycle.
 *
 * <p>The state that matters here is {@link #INSTRUCTED}. The system this replaces went straight
 * from "we called the bank API" to PAID in one transaction — the two facts were the same state, so
 * a transfer rejected or returned days later had nowhere to move to and no way to restore the
 * balance, and the books permanently showed money as delivered that had come back.
 */
public enum PayoutState {
    REQUESTED,
    APPROVED,
    REJECTED,
    /** Sent to the bank, not yet confirmed. Funds sit in a control account, not the partner's. */
    INSTRUCTED,
    CONFIRMED,
    RETURNED,
    FAILED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == REJECTED || this == FAILED;
    }
}


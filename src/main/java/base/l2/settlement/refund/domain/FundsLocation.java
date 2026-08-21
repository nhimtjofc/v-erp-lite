package base.l2.settlement.refund.domain;

/**
 * Where a seller's share of an order currently sits. A refund has to take the money back from
 * wherever it actually is, and the three cases are genuinely different postings.
 */
public enum FundsLocation {
    /** Still held pending delivery. Reverse the hold. */
    IN_ESCROW,
    /** Released to the seller's available balance but not yet paid out. Debit the balance. */
    RELEASED,
    /** Already wired to the seller's bank. The seller now owes us; the account goes debit. */
    PAID_OUT
}


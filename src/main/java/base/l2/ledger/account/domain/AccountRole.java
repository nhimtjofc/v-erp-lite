package base.l2.ledger.account.domain;

/**
 * What an account is structurally, in the sense accounting uses the words.
 *
 * <p>Orthogonal to {@link AccountClass}, which says what statement the account lands on. A control
 * account can be an asset or a liability; so can a general one.
 */
public enum AccountRole {

    /**
     * Detail for one counterparty, inside a subsidiary ledger, rolling up to a {@link #CONTROL}.
     *
     * <p>Strictly the subsidiary ledger is the whole set and this is one account within it. The
     * shorter name is the industry's own metonymy and is not ambiguous — nothing else here could be
     * meant by it.
     */
    SUBLEDGER,

    /**
     * Summarises a subsidiary ledger. Never posted to directly.
     *
     * <p>The accounting meaning: accounts payable control carries the total of every counterparty
     * account beneath it, and stays equal to their sum because postings go to the subsidiary
     * accounts and the control follows. {@code LedgerInvariantChecker} reports a direct posting
     * here, and reports the control drifting from the total of its children.
     */
    CONTROL,

    /**
     * An ordinary general-ledger account: no subsidiary ledger, posted to directly.
     *
     * <p>Bank positions, provider positions, clearing accounts, revenue, expense and equity all sit
     * here. What distinguishes them is recorded where it belongs rather than in this enum —
     * {@link AccountClass} says what kind of account it is, {@code external_ref} says whose position
     * it mirrors, and {@code is_clearing} says whether it is expected to return to zero.
     */
    GENERAL
}


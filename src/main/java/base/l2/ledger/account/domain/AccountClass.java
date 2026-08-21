package base.l2.ledger.account.domain;

/**
 * Where an account sits in the accounting equation: {@code assets = liabilities + equity}, with
 * revenue and expense as the temporary accounts that close into equity.
 *
 * <p>These five are not a taste. They are the standard classification every double-entry system
 * shares, and they are what decides which statement an account appears on — assets and liabilities
 * on the balance sheet, revenue and expense on the income statement. Without this the book can
 * produce a trial balance and nothing else, because a trial balance only proves the entries sum to
 * zero; it says nothing about what the balances mean.
 *
 * <p>This is deliberately separate from {@link AccountRole}. Classification and structure are
 * orthogonal: a control account may be an asset (a bank position) or a liability (funds in flight),
 * and knowing it is a control tells you nothing about which.
 *
 * <p>Two-way accounts are classified by their <em>normal</em> side, which is the standard
 * treatment. A partner sub-ledger is a LIABILITY because we normally owe partners; when one carries
 * a debit balance they owe us, and it is reclassified to receivables for presentation rather than
 * being a different account.
 */
public enum AccountClass {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE;

    /** True for the balance-sheet classes, which carry their balance across a period close. */
    public boolean isPermanent() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }
}


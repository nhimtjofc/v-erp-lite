package base.l2.ledger.account.domain;

import java.util.Locale;
import java.util.UUID;

/**
 * The account naming scheme. Codes are hierarchical and colon-separated so related accounts sort
 * and prefix-match together — {@code subledger:<type>:*} is every account of one counterparty
 * kind. The type is whatever the caller calls it; nothing in this package knows the list.
 *
 * <p>The well-known control accounts below are the ones the settlement flows post against. Each
 * exists because some real position needs a name: what a provider owes us, what the bank holds,
 * what has been instructed but not confirmed, what arrived that we cannot yet explain.
 */
public final class AccountCode {

    /** Money held at a payment provider, pending settlement to the bank. */
    public static final String CONTROL_PSP = "asset:psp:";
    /** The operating bank position. */
    public static final String CONTROL_BANK = "asset:bank:operating";
    /**
     * Instructed to the bank but not yet confirmed. The state a payout sits in between "we called
     * the API" and "the partner has the money" — two facts the predecessor recorded as one.
     */
    public static final String CONTROL_PAYOUT_IN_FLIGHT = "clearing:payout:in_flight";
    /** Statement lines that match nothing yet. Should trend to zero; a standing balance is money we cannot explain. */
    public static final String CONTROL_SUSPENSE = "clearing:suspense:unmatched";

    /**
     * Money a payer has charged back and we are contesting.
     *
     * <p>An <em>asset</em>-side clearing account, unlike the other two. Those hold something we
     * owe — funds in transit to a bank, a receipt nobody has explained. This holds a claim: the
     * acquirer has already taken the money off us and we may or may not get it back, so the
     * balance is a debit and it stands for "value we are still arguing about". It must reach zero
     * when the dispute ends, whichever way it goes.
     */
    public static final String CONTROL_DISPUTE_HELD = "clearing:dispute:held";
    /**
     * Money received that is not yet ours to release — held pending whatever condition the calling
     * system attaches to it. A real account, so several kinds of hold can coexist here.
     */
    public static final String CONTROL_FUNDS_HELD = "liability:funds_held";
    /** Rollup over the partner sub-ledgers. Never posted to directly. */
    public static final String CONTROL_COUNTERPARTIES = "control:counterparties";

    /** What we keep from a payment we processed. What the caller calls it is the caller's business. */
    public static final String REVENUE_FEE = "revenue:fee";
    /** Interest the bank pays on a balance we hold there. Arrives without us asking for it. */
    public static final String REVENUE_INTEREST = "revenue:interest";
    public static final String EXPENSE_PSP_FEES = "expense:psp_fees";
    /**
     * What the bank charges for holding the account — maintenance, transfer and FX fees. A
     * dedicated account rather than suspense, because these are predictable: an off-book item
     * that recurs monthly is a missing account code, not a mystery.
     */
    public static final String EXPENSE_BANK_FEES = "expense:bank_fees";
    public static final String EXPENSE_WRITE_OFF = "expense:write_off";
    public static final String EXPENSE_ROUNDING = "expense:rounding";
    public static final String EQUITY_OPENING_BALANCE = "equity:opening_balance";

    private AccountCode() {
    }

    public static String subledger(String ownerType, UUID ownerId) {
        return "subledger:" + ownerType.toLowerCase(Locale.ROOT) + ":" + ownerId;
    }

    public static String psp(String provider) {
        return CONTROL_PSP + provider.toLowerCase(Locale.ROOT);
    }

    /**
     * One code per real bank account. Two accounts sharing a code cannot be reconciled against
     * their statements, because neither balance is a claim about anything a bank would confirm.
     */
    public static String bank(String name) {
        return "asset:bank:" + name.toLowerCase(Locale.ROOT);
    }

    /** Per-currency liquidity account, the pivot a currency conversion turns on. */
    public static String liquidity(String currency) {
        return "control:liquidity:" + currency.toLowerCase(Locale.ROOT);
    }
}


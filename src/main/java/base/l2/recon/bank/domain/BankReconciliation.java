package base.l2.recon.bank.domain;

/**
 * The naming that ties a bank item's postings together.
 *
 * <p>An item booked to suspense and later reclassified is two entries, and they only read as one
 * story if both carry the same group. The group is the bank's reference, which is the only
 * identifier both entries can agree on without either of them having to know the other exists.
 */
public final class BankReconciliation {

    private static final String GROUP_PREFIX = "bank-item:";

    private BankReconciliation() {
    }

    public static String groupOf(String bankRef) {
        return GROUP_PREFIX + bankRef;
    }
}


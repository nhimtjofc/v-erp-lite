package base.l2.ledger;

import base.l0.system.util.UuidUtil;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.UUID;

/** Account setup shared by the ledger tests. */
@ApplicationScoped
public class LedgerTestFixture {

    @Inject
    OpenAccountUC openAccount;

    @Inject
    EntityManager em;

    public LedgerAccount psp(String currency) {
        return openAccount.open(OpenAccountUC.Spec.asset(AccountCode.psp("mock"), currency, "mock"));
    }

    public LedgerAccount bank(String currency) {
        return openAccount.open(OpenAccountUC.Spec.asset(AccountCode.CONTROL_BANK, currency, "bank"));
    }

    /** A bank account of its own, so a test's balances cannot be disturbed by another's. */
    public LedgerAccount bank(String name, String currency) {
        return openAccount.open(
                OpenAccountUC.Spec.asset(AccountCode.bank(name), currency, "bank:" + name));
    }

    public LedgerAccount inFlight(String currency) {
        return openAccount.open(OpenAccountUC.Spec.clearingLiability(
                AccountCode.CONTROL_PAYOUT_IN_FLIGHT, currency, null, (short) 2));
    }

    public LedgerAccount suspense(String currency) {
        return openAccount.open(
                OpenAccountUC.Spec.clearingLiability(AccountCode.CONTROL_SUSPENSE, currency, null, (short) 2));
    }

    /** The asset-side clearing account a contested chargeback sits in. */
    public LedgerAccount disputeHeld(String currency) {
        return openAccount.open(OpenAccountUC.Spec.clearingAsset(
                AccountCode.CONTROL_DISPUTE_HELD, currency, null, (short) 75));
    }

    public LedgerAccount expense(String code, String currency) {
        return openAccount.open(OpenAccountUC.Spec.expense(code, currency));
    }

    public LedgerAccount revenue(String code, String currency) {
        return openAccount.open(OpenAccountUC.Spec.revenue(code, currency));
    }

    public LedgerAccount commission(String currency) {
        return openAccount.open(OpenAccountUC.Spec.revenue(AccountCode.REVENUE_FEE, currency));
    }

    public LedgerAccount arControl(String currency) {
        return openAccount.open(OpenAccountUC.Spec.control(AccountCode.CONTROL_COUNTERPARTIES, currency));
    }

    public LedgerAccount seller(UUID sellerId, String currency, long creditLimitMinor) {
        return openAccount.open(OpenAccountUC.Spec.partner(
                AccountCode.subledger("seller", sellerId), "SELLER", sellerId, currency,
                arControl(currency).getUuid(), creditLimitMinor, null));
    }

    /** A seller whose escrow carries payment terms, so its lines get a due date. */
    public LedgerAccount sellerOnTerms(UUID sellerId, String currency, short termsDays) {
        return openAccount.open(OpenAccountUC.Spec.partner(
                AccountCode.subledger("seller", sellerId), "SELLER", sellerId, currency,
                arControl(currency).getUuid(), 0L, termsDays));
    }

    public LedgerAccount buyer(UUID buyerId, String currency) {
        return openAccount.open(OpenAccountUC.Spec.custodial(
                AccountCode.subledger("buyer", buyerId), "BUYER", buyerId, currency,
                arControl(currency).getUuid()));
    }

    public UUID newId() {
        return UuidUtil.v7();
    }

    /** Reads the balance straight from the row, bypassing any first-level cache. */
    @Transactional
    public long balanceOf(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery("SELECT balance_minor FROM ledger_accounts WHERE uuid = ?1")
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    public long trialBalanceNet(String tenantCode, String currency) {
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(net_minor,0) FROM v_trial_balance WHERE tenant_code = ?1 AND currency = ?2")
                .setParameter(1, tenantCode)
                .setParameter(2, currency)
                .getResultStream().findFirst().orElse(0L);
        return ((Number) value).longValue();
    }

    /**
     * Lines of an entry whose stamped effective_at disagrees with the entry's own.
     *
     * <p>Compared in SQL rather than pulled into Java and checked there: the column is a
     * TIMESTAMP, the driver hands it back in the session time zone, and a test that reconstructs
     * an Instant to compare against is really asserting the JVM and the database agree about
     * offsets. The invariant is that the two columns hold the same value, which SQL can say
     * directly.
     */
    @Transactional
    public long linesDisagreeingWithTheirEntry(UUID entryUuid) {
        em.clear();
        Object value = em.createNativeQuery("""
                SELECT COUNT(*)
                  FROM journal_lines l
                  JOIN journal_entries e ON e.uuid = l.entry_uuid
                 WHERE l.entry_uuid = ?1 AND NOT (l.effective_at <=> e.effective_at)
                """)
                .setParameter(1, entryUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    public long accountDrift(UUID accountUuid) {
        Object value = em.createNativeQuery(
                        "SELECT drift_minor FROM v_account_balance_audit WHERE account_uuid = ?1")
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }
}


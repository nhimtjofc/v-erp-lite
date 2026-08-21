package base.l2.recon.application;

import base.l2.recon.domain.InvariantBreach;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the invariants that must hold continuously, across every tenant.
 *
 * <p>The predecessor's reconciliation compared each account against the sum of its own rows — a
 * relationship maintained by the same code that wrote both, so it could not fail on the errors that
 * matter. It also never ran: no scheduler, no endpoint, no test. Its own comment said "In
 * production, trigger an alert or lock the wallet." This does that.
 *
 * <p>Native queries throughout, because these deliberately read across tenants and so must bypass
 * the tenant discriminator that guards every other read in the service.
 */
@ApplicationScoped
public class LedgerInvariantChecker {

    private static final Logger log = Logger.getLogger(LedgerInvariantChecker.class);

    @Inject
    EntityManager em;

    @Transactional
    public List<InvariantBreach> runAll() {
        List<InvariantBreach> breaches = new ArrayList<>();
        breaches.addAll(trialBalanceIsZero());
        breaches.addAll(accountBalancesMatchTheirLines());
        breaches.addAll(everyEntryHasItsLines());
        breaches.addAll(controlsAreNotPostedTo());
        breaches.addAll(clearingLifecyclesSettle());
        breaches.addAll(everyClearingItemNamesItself());
        breaches.addAll(debitBalancesRespectLimits());
        breaches.addAll(theBankAgreesWithUs());
        breaches.addAll(feeScheduleWindowsDoNotOverlap());

        for (InvariantBreach breach : breaches) {
            if (breach.severity() == InvariantBreach.Severity.CRITICAL) {
                log.errorf("LEDGER INVARIANT BREACH [%s] tenant=%s subject=%s: %s",
                        breach.invariant(), breach.tenantCode(), breach.subject(), breach.detail());
            } else {
                log.warnf("Ledger invariant warning [%s] tenant=%s subject=%s: %s",
                        breach.invariant(), breach.tenantCode(), breach.subject(), breach.detail());
            }
        }
        return breaches;
    }

    /**
     * Everything the full sweep checks, plus the two folds it deliberately does not repeat.
     *
     * <p>{@link #runAll()} verifies balances by folding forward from the last closed period, which
     * is bounded by the close cadence. This re-derives them from the first line ever written. It is
     * the same question asked without the shortcut, and it exists so that a bug in the incremental
     * path cannot hide behind its own optimisation — the closing balances are trusted by the fast
     * path, so something has to keep checking the thing that produced them.
     *
     * <p>Costly by design and meant to run rarely: a full fold of every line of every account.
     */
    @Transactional
    public List<InvariantBreach> runFullAudit() {
        List<InvariantBreach> breaches = new ArrayList<>();
        breaches.addAll(rows("SELECT tenant_code, currency, net_minor FROM v_trial_balance "
                + "WHERE net_minor <> 0")
                .stream()
                .map(r -> new InvariantBreach("TRIAL_BALANCE_ZERO_FULL", InvariantBreach.Severity.CRITICAL,
                        str(r[0]), str(r[1]),
                        "Book does not balance over its whole history; debits minus credits = " + r[2],
                        num(r[2])))
                .toList());
        breaches.addAll(rows("SELECT tenant_code, code, drift_minor, stored_balance_minor, "
                + "derived_balance_minor FROM v_account_balance_audit WHERE drift_minor <> 0")
                .stream()
                .map(r -> new InvariantBreach("ACCOUNT_BALANCE_MATCHES_LINES_FULL",
                        InvariantBreach.Severity.CRITICAL, str(r[0]), str(r[1]),
                        "Over full history, stored balance " + r[3] + " but lines sum to " + r[4],
                        num(r[2])))
                .toList());

        for (InvariantBreach breach : breaches) {
            log.errorf("LEDGER FULL-AUDIT BREACH [%s] tenant=%s subject=%s: %s",
                    breach.invariant(), breach.tenantCode(), breach.subject(), breach.detail());
        }
        return breaches;
    }

    /**
     * The trial balance. Non-zero means something wrote outside the posting procedure.
     *
     * <p>Reads the incremental view, which folds forward from the newest closed period. With no
     * close it is identical to the full one.
     */
    private List<InvariantBreach> trialBalanceIsZero() {
        return rows("SELECT tenant_code, currency, net_minor FROM v_trial_balance_incremental WHERE net_minor <> 0")
                .stream()
                .map(r -> new InvariantBreach("TRIAL_BALANCE_ZERO", InvariantBreach.Severity.CRITICAL,
                        str(r[0]), str(r[1]),
                        "Book does not balance; debits minus credits = " + r[2], num(r[2])))
                .toList();
    }

    /**
     * Stored balance against the fold of the account's own immutable lines.
     *
     * <p>Folded forward from the last close rather than from the beginning: a closed period cannot
     * change, so re-deriving it every quarter hour verifies nothing. {@link #runFullAudit()} is what
     * still checks the part this one takes on trust.
     */
    private List<InvariantBreach> accountBalancesMatchTheirLines() {
        return rows("SELECT tenant_code, code, drift_minor, stored_balance_minor, derived_balance_minor "
                + "FROM v_account_balance_audit_incremental WHERE drift_minor <> 0")
                .stream()
                .map(r -> new InvariantBreach("ACCOUNT_BALANCE_MATCHES_LINES", InvariantBreach.Severity.CRITICAL,
                        str(r[0]), str(r[1]),
                        "Stored balance " + r[3] + " but lines sum to " + r[4], num(r[2])))
                .toList();
    }

    /** A header with fewer than two lines means the transaction guard was bypassed. */
    private List<InvariantBreach> everyEntryHasItsLines() {
        return rows("SELECT tenant_code, idempotency_key, line_count FROM v_incomplete_entries")
                .stream()
                .map(r -> new InvariantBreach("ENTRY_HAS_LINES", InvariantBreach.Severity.CRITICAL,
                        str(r[0]), str(r[1]),
                        "Entry has only " + r[2] + " line(s)", num(r[2])))
                .toList();
    }

    /**
     * A control account must carry no postings of its own.
     *
     * <p>It equals the total of the subsidiary ledger beneath it because every posting goes to a
     * subsidiary account and the control follows. A line posted straight to the control breaks that
     * relationship without breaking anything the trial balance can see.
     */
    private List<InvariantBreach> controlsAreNotPostedTo() {
        return rows("SELECT tenant_code, control_code, direct_line_count, subledger_count "
                + "FROM v_control_rollup WHERE subledger_count > 0 AND direct_line_count > 0")
                .stream()
                .map(r -> new InvariantBreach("CONTROL_NOT_POSTED_TO", InvariantBreach.Severity.WARNING,
                        str(r[0]), str(r[1]),
                        "Control account has " + r[2] + " direct line(s) over " + r[3] + " sub-ledger(s)", num(r[2])))
                .toList();
    }

    /**
     * Money a lifecycle parked in a clearing account and never came back for.
     *
     * <p>This replaces a check that could not fail. Its predecessor summed <em>every</em> line of
     * every entry in a reconcile group, and every entry already balances per currency by the posting
     * procedure's own rule — so the total was structurally zero and the invariant had never once been
     * able to fire. It was reaching for this and could not express it.
     *
     * <p>Netting over only the <b>clearing</b> accounts a group touches asks the question that
     * actually has an answer: a payout instructed and never confirmed leaves {@code in_flight}
     * non-zero for its group; a bank item recorded and never classified leaves suspense non-zero for
     * its. This is the query half of the model Stripe's Ledger describes — the intermediate accounts
     * were already here, and nothing was reading them.
     */
    private List<InvariantBreach> clearingLifecyclesSettle() {
        return rows("SELECT tenant_code, reconcile_group, currency, clearing_net_minor, "
                + "TIMESTAMPDIFF(DAY, last_effective_at, NOW()) AS days_open, tolerance_days "
                + "FROM v_clearing_group_balance WHERE clearing_net_minor <> 0 "
                + "AND TIMESTAMPDIFF(DAY, last_effective_at, NOW()) > tolerance_days")
                .stream()
                .map(r -> new InvariantBreach("CLEARING_LIFECYCLE_SETTLES", InvariantBreach.Severity.WARNING,
                        str(r[0]), str(r[1]),
                        "Still holding " + r[3] + " " + r[2] + " in a clearing account after "
                                + r[4] + " days (tolerance " + r[5] + ")", num(r[3])))
                .toList();
    }

    /**
     * Clearing activity carrying no reconcile group.
     *
     * <p>{@link #clearingLifecyclesSettle()} can only see lifecycles that named themselves, so
     * something posted into a clearing account without a group is invisible to it — and invisible is
     * the worst state for money nobody has explained. Reported only when it does not net out.
     */
    private List<InvariantBreach> everyClearingItemNamesItself() {
        return rows("SELECT tenant_code, code, currency, net_minor, "
                + "TIMESTAMPDIFF(DAY, oldest_effective_at, NOW()) AS days_open, tolerance_days "
                + "FROM v_untracked_clearing_items "
                + "WHERE TIMESTAMPDIFF(DAY, oldest_effective_at, NOW()) > tolerance_days")
                .stream()
                .map(r -> new InvariantBreach("CLEARING_ITEM_IS_TRACKED", InvariantBreach.Severity.WARNING,
                        str(r[0]), str(r[1]),
                        "Untracked " + r[3] + " " + r[2] + " sitting in a clearing account for "
                                + r[4] + " days with no reconcile group", num(r[3])))
                .toList();
    }

    /** A partner owing us more than policy allows is an exposure to act on, not a number to store. */
    private List<InvariantBreach> debitBalancesRespectLimits() {
        return rows("SELECT tenant_code, code, balance_minor, credit_limit_minor FROM ledger_accounts "
                + "WHERE balance_minor > credit_limit_minor")
                .stream()
                .map(r -> new InvariantBreach("DEBIT_WITHIN_CREDIT_LIMIT", InvariantBreach.Severity.CRITICAL,
                        str(r[0]), str(r[1]),
                        "Debit balance " + r[2] + " exceeds limit " + r[3], num(r[2])))
                .toList();
    }

    /**
     * The only check here that consults something outside the book.
     *
     * <p>Every other invariant grades the ledger against itself, which is exactly why none of them
     * can see the failure this one is for: a transaction that happened at the bank and was never
     * booked is absent from both sides of an entry at once, so the trial balance still nets zero and
     * no account drifts. Double-entry catches entries that are wrong, not entries that are missing.
     * An attestation is the external witness that closes that gap.
     *
     * <p>WARNING rather than CRITICAL. The books are not wrong, they are incomplete, and a
     * five-euro account fee nobody has booked yet should not read as a stop-work condition.
     */
    private List<InvariantBreach> theBankAgreesWithUs() {
        return rows("SELECT tenant_code, account_code, currency, delta_minor, closing_balance_minor, "
                + "book_balance_minor, statement_date FROM v_bank_attestation_delta WHERE delta_minor <> 0")
                .stream()
                .map(r -> new InvariantBreach("BANK_AGREES_WITH_BOOK", InvariantBreach.Severity.WARNING,
                        str(r[0]), str(r[1]),
                        "Bank states " + r[4] + " " + r[2] + " at " + r[6] + " but our books say "
                                + r[5] + "; " + r[3] + " of activity is unbooked", num(r[3])))
                .toList();
    }

    /**
     * Two versions of one fee arrangement claiming the same instant.
     *
     * <p>"No two windows for this code overlap" is not expressible as a CHECK in MariaDB, and a
     * trigger enforcing it would scan the table on every insert. So it is checked here instead —
     * which is the right trade for a table written by hand a few times a year and read on every
     * capture.
     *
     * <p>CRITICAL despite nothing being wrong with the ledger yet. An overlap means "the rate at
     * time T" has two answers, so two captures of the same order can be priced differently and both
     * pass verification. The damage is in the postings that have not happened.
     */
    private List<InvariantBreach> feeScheduleWindowsDoNotOverlap() {
        return rows("SELECT tenant_code, code, version_a, version_b FROM v_fee_schedule_overlaps")
                .stream()
                .map(r -> new InvariantBreach("FEE_SCHEDULE_WINDOWS_DISJOINT", InvariantBreach.Severity.CRITICAL,
                        str(r[0]), str(r[1]),
                        "Versions " + r[2] + " and " + r[3] + " are both in force at the same time",
                        num(r[2])))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql) {
        return em.createNativeQuery(sql).getResultList();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}


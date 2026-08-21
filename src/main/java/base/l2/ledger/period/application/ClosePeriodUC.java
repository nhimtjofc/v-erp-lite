package base.l2.ledger.period.application;

import base.l0.system.security.CallerContext;
import base.l2.ledger.period.domain.ClosedPeriod;
import base.l2.ledger.period.domain.PeriodRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Closes a period, and reopens one when there is a reason worth recording.
 *
 * <p>Closing refuses while the trial balance is non-zero for any currency. That ordering is the
 * whole value of a close: a period sealed over a book that does not balance preserves the error
 * instead of the figure, and the moment to find out is before anyone quotes the number, not when a
 * correcting entry is refused six weeks later.
 *
 * <p>It does not refuse over an unreconciled bank attestation. A delta there means the books are
 * incomplete rather than wrong — an unbooked account fee, say — and blocking every close until a
 * five-euro charge is explained would make closing something people route around.
 *
 * <p>Closing is deliberately the expensive operation. It folds every line of every account in the
 * tenant twice — once to prove nothing drifts, once to record where each account stood — and both
 * scans grow with total history. That is the trade the design is making: pay for a complete
 * verification once per period, instead of ninety-six times a day for a period that cannot change.
 * {@code period_closing_balances} is what the recurring check folds forward from afterwards.
 */
@ApplicationScoped
public class ClosePeriodUC {

    @Inject
    EntityManager em;

    @Inject
    CallerContext caller;

    @Transactional
    public ClosedPeriod close(Instant closedThrough, String note) {
        if (closedThrough == null) {
            throw new PeriodRejectedException("A close needs the instant it closes through");
        }
        if (closedThrough.isAfter(Instant.now())) {
            throw new PeriodRejectedException(
                    "Cannot close through " + closedThrough + "; it has not happened yet");
        }

        List<?> unbalanced = em.createNativeQuery(
                        "SELECT currency FROM v_trial_balance WHERE tenant_code = ?1 AND net_minor <> 0")
                .setParameter(1, caller.tenantCode())
                .getResultList();
        if (!unbalanced.isEmpty()) {
            throw new PeriodRejectedException(
                    "The book does not balance in " + unbalanced + "; fix that before sealing the period");
        }

        // Not merely "the entries sum to zero" but "every stored balance still equals the fold of
        // its own lines". The closing balance recorded below inherits whatever authority this check
        // has, so it is the one that has to be made here rather than assumed.
        Number drifting = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM v_account_balance_audit "
                                + "WHERE tenant_code = ?1 AND drift_minor <> 0")
                .setParameter(1, caller.tenantCode())
                .getSingleResult();
        if (drifting.longValue() > 0) {
            throw new PeriodRejectedException(drifting + " account(s) do not match the fold of their "
                    + "own lines; a period sealed over that would preserve the error, not the figure");
        }

        ClosedPeriod existing = ClosedPeriod.<ClosedPeriod>find("closedThrough = ?1 and tenantCode = ?2", closedThrough, caller.tenantCode()).firstResult();
        if (existing != null && existing.getReopenedAt() == null) {
            return existing;
        }

        ClosedPeriod period = existing != null ? existing : new ClosedPeriod();
        period.setTenantCode(caller.tenantCode());
        period.setClosedThrough(closedThrough);
        period.setNote(note);
        period.setClosedAt(Instant.now());
        period.setClosedBy(caller.actorId());
        // A reopened period being closed again is the normal end of a correction, so the reopening
        // is cleared rather than the row being replaced — the audit columns stay legible.
        period.setReopenedAt(null);
        period.setReopenedBy(null);
        period.persist();
        // The row has to exist before its balances can reference it.
        ClosedPeriod.flush();

        recordClosingBalances(period);
        return period;
    }

    /**
     * Records what every account stood at, as of the instant the period closed.
     *
     * <p>Every account, including ones with no activity at all: an account that did not move still
     * has a balance, and leaving it out would make the incremental audit treat it as starting from
     * zero. Hence the LEFT JOIN from {@code ledger_accounts} rather than a scan of lines.
     *
     * <p>Upsert rather than insert, because re-closing a period that was reopened and corrected has
     * to overwrite what the first close recorded. That is also why the previous numbers are not
     * kept: the reopening is on the period row with its reason, and two conflicting answers to
     * "what did this account stand at" would be worse than one.
     */
    private void recordClosingBalances(ClosedPeriod period) {
        // closed_through is copied from the period row rather than passed as a parameter. The two
        // are the same instant, but they do not survive the trip as the same VALUE: Hibernate and a
        // hand-bound java.sql.Timestamp disagree about the session time zone, so a parameter-bound
        // copy lands hours away from what the watermark view reads and every join silently misses.
        em.createNativeQuery(
                        "INSERT INTO period_closing_balances "
                                + "    (uuid, tenant_code, period_uuid, closed_through, account_uuid, "
                                + "     currency, balance_minor, line_count) "
                                + "SELECT UUID(), a.tenant_code, p.uuid, p.closed_through, a.uuid, "
                                + "       a.currency, COALESCE(SUM(l.signed_minor), 0), COUNT(l.uuid) "
                                + "  FROM closed_periods p "
                                + "  JOIN ledger_accounts a ON a.tenant_code = p.tenant_code "
                                + "  LEFT JOIN journal_lines l "
                                + "         ON l.account_uuid = a.uuid AND l.effective_at <= p.closed_through "
                                + " WHERE p.uuid = ?1 "
                                + " GROUP BY p.uuid, p.closed_through, a.uuid, a.tenant_code, a.currency "
                                + "ON DUPLICATE KEY UPDATE "
                                + "     balance_minor = VALUES(balance_minor), "
                                + "     line_count    = VALUES(line_count)")
                .setParameter(1, period.getUuid())
                .executeUpdate();
    }

    /**
     * Reopens a period so a correction can be dated into it.
     *
     * <p>Requires a reason, and leaves the row behind. That is the difference between reopening and
     * a bypass: both let the entry through, only one of them is still visible afterwards.
     */
    @Transactional
    public ClosedPeriod reopen(Instant closedThrough, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new PeriodRejectedException("Reopening a closed period needs a reason on the record");
        }
        ClosedPeriod period = ClosedPeriod.<ClosedPeriod>find("closedThrough = ?1 and tenantCode = ?2", closedThrough, caller.tenantCode()).firstResult();
        if (period == null) {
            throw new PeriodRejectedException("No period is closed through " + closedThrough);
        }
        period.setReopenedAt(Instant.now());
        period.setReopenedBy(caller.actorId());
        period.setReopenReason(reason);
        period.persist();
        return period;
    }

    /** The latest instant everything is sealed through, if anything is. */
    public Optional<Instant> closedThrough() {
        return ClosedPeriod.<ClosedPeriod>find("reopenedAt is null and tenantCode = ?1 order by closedThrough desc", caller.tenantCode())
                .firstResultOptional()
                .map(ClosedPeriod::getClosedThrough);
    }
}

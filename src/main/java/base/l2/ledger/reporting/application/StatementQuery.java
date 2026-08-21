package base.l2.ledger.reporting.application;

import base.l0.system.persistance.JdbcTypes;
import base.l0.system.security.CallerContext;
import base.l2.ledger.reporting.interfaces.dto.AgingDto;
import base.l2.ledger.reporting.interfaces.dto.StatementLineDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Statement of account and aging.
 *
 * <p>The statement reads {@code account_new_balance_minor} straight off each line rather than
 * folding history, because every line already records the balance it produced. Handed to a partner
 * disputing a figure, each row shows the movement and the balance it left behind.
 */
@ApplicationScoped
public class StatementQuery {

    @Inject
    EntityManager em;

    @Inject
    CallerContext caller;

    @SuppressWarnings("unchecked")
    public List<StatementLineDto> statement(UUID accountUuid) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT l.uuid, l.entry_uuid, e.event_type, e.document_type, e.document_id,
                       l.direction, l.amount_minor, l.currency, l.due_date,
                       l.account_new_balance_minor, l.account_entry_seq, e.effective_at, l.memo
                  FROM journal_lines l
                  JOIN journal_entries e ON e.uuid = l.entry_uuid
                 WHERE l.account_uuid = ?1 AND l.tenant_code = ?2
                 ORDER BY l.account_entry_seq
                """)
                .setParameter(1, accountUuid)
                .setParameter(2, caller.tenantCode())
                .getResultList();

        return rows.stream().map(r -> new StatementLineDto(
                (UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], (UUID) r[4],
                (String) r[5], num(r[6]), (String) r[7],
                JdbcTypes.toLocalDate(r[8]),
                num(r[9]), num(r[10]),
                JdbcTypes.toInstant(r[11]),
                (String) r[12])).toList();
    }

    @SuppressWarnings("unchecked")
    public List<AgingDto> aging(String ownerType) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT account_uuid, account_code, owner_type, owner_id, currency,
                       current_minor, overdue_1_30_minor, overdue_31_60_minor,
                       overdue_61_90_minor, overdue_90_plus_minor, total_minor
                  FROM v_aging
                 WHERE tenant_code = ?1 AND (?2 IS NULL OR owner_type = ?2)
                 ORDER BY overdue_90_plus_minor DESC, total_minor DESC
                """)
                .setParameter(1, caller.tenantCode())
                .setParameter(2, ownerType)
                .getResultList();

        return rows.stream().map(r -> new AgingDto(
                (UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4],
                num(r[5]), num(r[6]), num(r[7]), num(r[8]), num(r[9]), num(r[10]))).toList();
    }

    /**
     * The balance an account stood at on a given date.
     *
     * <p>A fold, not a snapshot lookup, and deliberately. The per-line snapshots chain in posting
     * order, so the newest line dated at or before a cutoff carries the balance after every line
     * with a lower sequence — including ones dated after the cutoff, if anything was backdated.
     * Backdating is routine here: a bank movement discovered late is booked with the value date
     * the bank gave it, which is the whole mechanism by which
     * {@code v_bank_attestation_delta} closes. Reading a snapshot would have returned the balance
     * as of "whenever that line was posted", silently, and only for accounts that had been
     * corrected — which is to say only for the accounts anyone was looking at.
     *
     * <p>The fold is served entirely by {@code idx_journal_lines_account_effective}
     * {@code (account_uuid, effective_at, signed_minor, …)}: seek to the account, scan the dated
     * range, sum a column already in the index. No join, no row lookups.
     */
    public long balanceAsOf(UUID accountUuid, LocalDate asOf) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(l.signed_minor), 0)
                  FROM journal_lines l
                 WHERE l.account_uuid = ?1 AND l.tenant_code = ?2 AND l.effective_at < ?3
                """)
                .setParameter(1, accountUuid)
                .setParameter(2, caller.tenantCode())
                .setParameter(3, Timestamp.valueOf(asOf.plusDays(1).atStartOfDay()))
                .getSingleResult();
        return num(result);
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}


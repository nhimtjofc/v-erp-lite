package base.l2.recon.bank.application;

import base.l0.system.persistance.JdbcTypes;
import base.l0.system.security.CallerContext;
import base.l2.recon.bank.interfaces.dto.BankAttestationDto;
import base.l2.recon.bank.interfaces.dto.SuspenseItemDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The read side of bank reconciliation: what the bank said, what we say, and what is unexplained.
 *
 * <p>Every figure here is computed at read time. Nothing stores a delta or an age, because a stored
 * difference is a copy of a number that stops agreeing with its sources the moment either moves —
 * and the whole value of these two reports is that booking the missing transactions makes them
 * correct themselves.
 */
@ApplicationScoped
public class BankReconciliationQuery {

    @Inject
    EntityManager em;

    @Inject
    CallerContext caller;

    /** Every attestation held for an account, newest statement first. */
    public List<BankAttestationDto> attestations(String accountCode) {
        return attestationRows(accountCode, false);
    }

    /**
     * Only the attestations that do not agree with the book.
     *
     * <p>The working list. A reconciled statement is not interesting once it has reconciled, and a
     * report that shows every closed month alongside the open one buries the thing needing action.
     */
    public List<BankAttestationDto> unreconciled(String accountCode) {
        return attestationRows(accountCode, true);
    }

    @SuppressWarnings("unchecked")
    private List<BankAttestationDto> attestationRows(String accountCode, boolean unreconciledOnly) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT attestation_uuid, account_uuid, account_code, currency, statement_date,
                       statement_ref, closing_balance_minor, book_balance_minor, delta_minor
                  FROM v_bank_attestation_delta
                 WHERE tenant_code = ?1
                   AND (?2 IS NULL OR account_code = ?2)
                   AND (?3 = FALSE OR delta_minor <> 0)
                 ORDER BY statement_date DESC, account_code
                """)
                .setParameter(1, caller.tenantCode())
                .setParameter(2, accountCode)
                .setParameter(3, unreconciledOnly)
                .getResultList();

        return rows.stream().map(r -> new BankAttestationDto(
                (UUID) r[0],
                (UUID) r[1],
                (String) r[2],
                (String) r[3],
                JdbcTypes.toLocalDate(r[4]),
                (String) r[5],
                num(r[6]),
                num(r[7]),
                num(r[8]),
                num(r[8]) == 0L)).toList();
    }

    /**
     * What is sitting in suspense, oldest first.
     *
     * <p>Ordered by age rather than by amount, because a suspense balance is not a problem for
     * being large — it is a problem for being old. A small amount nobody has explained in six weeks
     * is a worse signal than a large one that arrived this morning.
     */
    @SuppressWarnings("unchecked")
    public List<SuspenseItemDto> suspense() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT account_uuid, code, currency, balance_minor, open_since
                  FROM v_clearing_aging
                 WHERE tenant_code = ?1
                   AND code LIKE 'clearing:suspense:%'
                 ORDER BY open_since IS NULL, open_since
                """)
                .setParameter(1, caller.tenantCode())
                .getResultList();

        Instant now = Instant.now();
        return rows.stream().map(r -> {
            Instant openSince = JdbcTypes.toInstant(r[4]);
            long daysOpen = openSince == null ? 0L : ChronoUnit.DAYS.between(openSince, now);
            return new SuspenseItemDto(
                    (UUID) r[0],
                    (String) r[1],
                    (String) r[2],
                    num(r[3]),
                    openSince,
                    daysOpen);
        }).toList();
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}


package base.l2.ledger.reporting.application;

import base.l2.ledger.account.domain.AccountClass;
import base.l2.ledger.account.domain.AccountRole;

import java.util.UUID;

/**
 * What to narrow an account search by. Every field optional; null means "don't care".
 *
 * <p>Each of these maps onto an index that already exists, which is why search here needs no new
 * ones: {@code codePrefix} rides {@code uq_ledger_accounts_code (tenant_code, code, currency)} as a
 * range scan, owner fields ride {@code idx_ledger_accounts_owner}, and {@code role} rides
 * {@code idx_ledger_accounts_role}. A filter that cannot be served by one of them does not belong
 * here — it belongs behind a new index, added deliberately.
 *
 * @param codePrefix matched as a prefix, never as a contains. {@code LIKE '%x%'} cannot use an
 *                   index and would turn every search into a full scan, which is the thing this
 *                   whole change exists to stop.
 */
public record AccountFilter(
        String codePrefix,
        String ownerType,
        UUID ownerId,
        AccountRole role,
        AccountClass accountClass,
        String currency
) {
    public static AccountFilter none() {
        return new AccountFilter(null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return codePrefix == null && ownerType == null && ownerId == null
                && role == null && accountClass == null && currency == null;
    }
}


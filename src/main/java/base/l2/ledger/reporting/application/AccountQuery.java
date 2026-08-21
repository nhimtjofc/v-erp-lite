package base.l2.ledger.reporting.application;

import base.l0.system.api.Page;
import base.l0.system.money.Money;
import base.l2.ledger.account.domain.AccountRole;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.account.domain.NormalSide;
import base.l2.ledger.reporting.interfaces.dto.AccountBalanceDto;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reading accounts, a page at a time.
 *
 * <p>Paging is keyset, not offset. {@code OFFSET n} has to walk and discard n rows to reach the
 * page, so deep pages get linearly slower — but the disqualifying problem for a ledger is
 * correctness rather than speed: accounts are opened while somebody is paging, every insert shifts
 * the rows after it, and a caller walking pages with an offset silently sees some accounts twice
 * and misses others entirely. Keying off the last row seen instead makes each page a primary-key
 * range scan whose cost does not depend on how deep it is, and concurrent inserts can only ever
 * append.
 *
 * <p>The ordering is by {@code uuid}, which is the primary key, so the scan is the index. What
 * matters for correctness is only that the ordering is total and stable — not that it is
 * chronological — because the cursor comparison and the sort use the same column.
 */
@ApplicationScoped
public class AccountQuery {

    /** What a caller gets when it asks for no particular size. */
    public static final int DEFAULT_LIMIT = 100;

    /**
     * The ceiling, applied regardless of what was asked for.
     *
     * <p>A caller-supplied limit with no maximum is the same unbounded query wearing a query
     * parameter: {@code ?limit=1000000} would reintroduce exactly the problem paging removes.
     * Clamped rather than rejected, because the page reports the limit it actually used, so a
     * caller asking for too much gets a smaller page and a cursor rather than an error.
     */
    public static final int MAX_LIMIT = 500;

    /** A page of accounts matching {@code filter}, starting after {@code cursor}. */
    public Page<AccountBalanceDto> search(AccountFilter filter, String cursor, Integer requestedLimit) {
        int limit = clamp(requestedLimit);
        AccountFilter effective = filter == null ? AccountFilter.none() : filter;

        List<String> clauses = new ArrayList<>();
        Parameters params = new Parameters();

        UUID after = parseCursor(cursor);
        if (after != null) {
            clauses.add("uuid > :after");
            params.and("after", after);
        }
        if (isSet(effective.codePrefix())) {
            clauses.add("code like :codePrefix");
            params.and("codePrefix", effective.codePrefix() + "%");
        }
        if (isSet(effective.ownerType())) {
            clauses.add("ownerType = :ownerType");
            params.and("ownerType", effective.ownerType());
        }
        if (effective.ownerId() != null) {
            clauses.add("ownerId = :ownerId");
            params.and("ownerId", effective.ownerId());
        }
        if (effective.role() != null) {
            clauses.add("accountRole = :role");
            params.and("role", effective.role());
        }
        if (effective.accountClass() != null) {
            clauses.add("accountClass = :accountClass");
            params.and("accountClass", effective.accountClass());
        }
        if (isSet(effective.currency())) {
            clauses.add("currency = :currency");
            params.and("currency", effective.currency());
        }

        return page(String.join(" and ", clauses), params, limit);
    }

    /**
     * Counterparties currently carrying a debit balance — the ones who owe us.
     *
     * <p>{@code minimumOwedMinor} exists because the useful answer is "who owes us enough to act
     * on", not "who is a cent in debit". It is also what makes the query indexable:
     * {@code (tenant_code, account_role, balance_minor)} turns the threshold into a range scan,
     * where an unqualified {@code balance_minor > 0} was a full table scan at any size.
     */
    public Page<AccountBalanceDto> listDebtors(long minimumOwedMinor, String cursor, Integer requestedLimit) {
        int limit = clamp(requestedLimit);
        List<String> clauses = new ArrayList<>();
        Parameters params = new Parameters();

        clauses.add("accountRole = :role");
        params.and("role", AccountRole.SUBLEDGER);
        clauses.add("balanceMinor > :owed");
        params.and("owed", Math.max(0L, minimumOwedMinor));

        UUID after = parseCursor(cursor);
        if (after != null) {
            clauses.add("uuid > :after");
            params.and("after", after);
        }
        return page(String.join(" and ", clauses), params, limit);
    }

    private Page<AccountBalanceDto> page(String where, Parameters params, int limit) {
        // One row beyond the page, so hasMore is observed rather than guessed from a full page.
        List<LedgerAccount> rows = (where.isEmpty()
                ? LedgerAccount.<LedgerAccount>findAll(Sort.by("uuid"))
                : LedgerAccount.<LedgerAccount>find(where, Sort.by("uuid"), params))
                .range(0, limit)
                .list();

        boolean hasMore = rows.size() > limit;
        List<LedgerAccount> visible = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = visible.isEmpty() ? null : visible.getLast().getUuid().toString();

        return Page.of(visible.stream().map(AccountQuery::toDto).toList(), nextCursor, hasMore, limit);
    }

    private static int clamp(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    /**
     * A cursor that is not a key we could have issued is refused rather than ignored.
     *
     * <p>Silently treating it as "start from the beginning" would hand a caller page one while it
     * believed it was on page nine, which is the same class of quiet wrongness as truncating a list.
     */
    private static UUID parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(cursor.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException(cursor);
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    static AccountBalanceDto toDto(LedgerAccount account) {
        long signed = account.getBalanceMinor();
        // A CR-natural account (something we owe) reads more naturally with the sign flipped.
        long natural = account.getNormalSide() == NormalSide.CR ? -signed : signed;
        return new AccountBalanceDto(
                account.getUuid(),
                account.getCode(),
                account.getAccountClass().name(),
                account.getAccountRole().name(),
                account.getOwnerType(),
                account.getOwnerId(),
                account.getCurrency(),
                signed,
                Money.of(signed, account.getCurrency()).toDecimal(),
                account.getNormalSide().name(),
                natural,
                signed > 0,
                account.getCreditLimitMinor(),
                account.getEntrySeq());
    }
}


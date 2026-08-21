package base.l2.ledger.reporting.interfaces.rest;

import base.l0.system.api.ApiResp;
import base.l0.system.api.Page;
import base.l2.ledger.account.domain.AccountClass;
import base.l2.ledger.account.domain.AccountRole;
import base.l2.ledger.reporting.application.AccountFilter;
import base.l2.ledger.reporting.application.AccountQuery;
import base.l2.ledger.reporting.application.StatementQuery;
import base.l2.ledger.reporting.interfaces.dto.AccountBalanceDto;
import base.l2.ledger.reporting.interfaces.dto.AgingDto;
import base.l2.ledger.reporting.interfaces.dto.StatementLineDto;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/ledger")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class LedgerResource {

    @Inject
    AccountQuery accounts;

    @Inject
    StatementQuery statements;

    /**
     * A page of accounts, optionally narrowed.
     *
     * <p>Paged rather than complete, and the response shape says so. Returning a bare array while
     * quietly capping it would leave a caller believing it had every account when it had the first
     * hundred — the same silent-wrongness this service refuses everywhere else. Walk the pages by
     * passing {@code nextCursor} back as {@code after} until {@code hasMore} is false.
     *
     * @param code narrows by code prefix. A prefix, not a substring: {@code LIKE '%x%'} cannot use
     *             an index and would make every search a full scan.
     */
    @GET
    @Path("/accounts")
    public ApiResp<Page<AccountBalanceDto>> listAccounts(@QueryParam("ownerType") String ownerType,
                                                         @QueryParam("ownerId") UUID ownerId,
                                                         @QueryParam("code") String code,
                                                         @QueryParam("role") AccountRole role,
                                                         @QueryParam("class") AccountClass accountClass,
                                                         @QueryParam("currency") String currency,
                                                         @QueryParam("after") String after,
                                                         @QueryParam("limit") Integer limit) {
        AccountFilter filter = new AccountFilter(code, ownerType, ownerId, role, accountClass, currency);
        return ApiResp.fromData(accounts.search(filter, after, limit));
    }

    /**
     * Counterparties currently carrying a debit balance — which counterparty owes us money.
     *
     * @param minOwed only those owing at least this much, in minor units. Defaults to any debit
     *                balance at all, which is the honest default but rarely the useful question:
     *                an exposure report wants the ones worth acting on.
     */
    @GET
    @Path("/accounts/debtors")
    public ApiResp<Page<AccountBalanceDto>> listDebtors(@QueryParam("minOwed") Long minOwed,
                                                        @QueryParam("after") String after,
                                                        @QueryParam("limit") Integer limit) {
        return ApiResp.fromData(accounts.listDebtors(minOwed == null ? 0L : minOwed, after, limit));
    }

    @GET
    @Path("/accounts/{accountUuid}/statement")
    public ApiResp<List<StatementLineDto>> statement(@PathParam("accountUuid") UUID accountUuid) {
        return ApiResp.fromList(statements.statement(accountUuid));
    }

    @GET
    @Path("/accounts/{accountUuid}/balance-as-of")
    public ApiResp<Map<String, Object>> balanceAsOf(@PathParam("accountUuid") UUID accountUuid,
                                                    @QueryParam("date") String date) {
        LocalDate asOf = date == null ? LocalDate.now() : LocalDate.parse(date);
        return ApiResp.fromData(Map.of(
                "accountUuid", accountUuid,
                "asOf", asOf.toString(),
                "balanceMinor", statements.balanceAsOf(accountUuid, asOf)));
    }

    @GET
    @Path("/aging")
    public ApiResp<List<AgingDto>> aging(@QueryParam("ownerType") String ownerType) {
        return ApiResp.fromList(statements.aging(ownerType));
    }
}


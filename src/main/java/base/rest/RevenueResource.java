package base.rest;

import base.entity.Business;
import base.security.BusinessAccess;
import base.security.CallerContext;
import base.service.RevenueService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Produces;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Path("/revenue")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "businessOwner", "operator"})
public class RevenueResource {

    @Inject
    CallerContext caller;

    @Inject
    BusinessAccess businessAccess;

    @Inject
    RevenueService revenueService;

    @GET
    @Path("/daily")
    public RevenueService.DailyReport getDailyRevenue(@QueryParam("from") LocalDate from,
                                                      @QueryParam("to") LocalDate to,
                                                      @QueryParam("currency") String currency,
                                                      @QueryParam("businessId") UUID requestedBusinessId) {
        LocalDate today = LocalDate.now(RevenueService.REPORTING_ZONE);
        LocalDate resolvedFrom = from == null ? YearMonth.from(today).atDay(1) : from;
        LocalDate resolvedTo = to == null ? today : to;
        UUID businessId = resolveBusinessScope(requestedBusinessId);

        return revenueService.dailyReport(
                caller.tenantCode(),
                businessId,
                resolvedFrom,
                resolvedTo,
                currency
        );
    }

    @GET
    @Path("/month-to-date")
    public RevenueService.DailyReport getMonthToDateRevenue(@QueryParam("month") String month,
                                                            @QueryParam("currency") String currency,
                                                            @QueryParam("businessId") UUID requestedBusinessId) {
        LocalDate today = LocalDate.now(RevenueService.REPORTING_ZONE);
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth selectedMonth = currentMonth;
        if (month != null && !month.isBlank()) {
            try {
                selectedMonth = YearMonth.parse(month.trim());
            } catch (RuntimeException exception) {
                throw new BadRequestException("Month must use YYYY-MM format");
            }
        }
        if (selectedMonth.isAfter(currentMonth)) {
            throw new BadRequestException("Month cannot be in the future");
        }

        LocalDate to = selectedMonth.equals(currentMonth) ? today : selectedMonth.atEndOfMonth();
        return revenueService.dailyReport(
                caller.tenantCode(),
                resolveBusinessScope(requestedBusinessId),
                selectedMonth.atDay(1),
                to,
                currency
        );
    }

    private UUID resolveBusinessScope(UUID requestedBusinessId) {
        if (businessAccess.isScopedStaff()) {
            return businessAccess.requireCurrentBusinessId();
        }
        if (requestedBusinessId == null) {
            return null;
        }
        Business business = Business.find(
                "id = ?1 and tenantId = ?2",
                requestedBusinessId,
                caller.tenantCode()
        ).firstResult();
        if (business == null) {
            throw new BadRequestException("Business does not exist in the current tenant");
        }
        return business.getId();
    }
}

package base.rest;

import base.entity.Business;
import base.security.BusinessAccess;
import base.security.CallerContext;
import base.service.RevenueService;
import base.service.EntitlementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Path("/revenue")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "businessOwner", "operator"})
public class RevenueResource {

    public static class RevenueAdjustmentRequest {
        public UUID businessId;
        public LocalDate entryDate;
        public String category;
        public String direction;
        public long amountMinor;
        public String currency;
        public String description;
    }

    @Inject
    CallerContext caller;

    @Inject
    BusinessAccess businessAccess;

    @Inject
    RevenueService revenueService;

    @Inject
    EntitlementService entitlementService;

    @GET
    @Path("/daily")
    public RevenueService.DailyReport getDailyRevenue(@QueryParam("from") LocalDate from,
                                                      @QueryParam("to") LocalDate to,
                                                      @QueryParam("currency") String currency,
                                                      @QueryParam("businessId") UUID requestedBusinessId) {
        entitlementService.requireFeature(caller.tenantCode(), EntitlementService.FEATURE_REPORTS);
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
        entitlementService.requireFeature(caller.tenantCode(), EntitlementService.FEATURE_REPORTS);
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

    @GET
    @Path("/monthly")
    public RevenueService.MonthlyReport getMonthlyRevenue(@QueryParam("month") String month,
                                                          @QueryParam("currency") String currency,
                                                          @QueryParam("businessId") UUID requestedBusinessId) {
        entitlementService.requireFeature(caller.tenantCode(), EntitlementService.FEATURE_REPORTS);
        YearMonth selectedMonth;
        try {
            selectedMonth = month == null || month.isBlank()
                    ? YearMonth.now(RevenueService.REPORTING_ZONE)
                    : YearMonth.parse(month.trim());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Month must use YYYY-MM format");
        }
        return revenueService.monthlyReport(
                caller.tenantCode(),
                resolveBusinessScope(requestedBusinessId),
                selectedMonth,
                currency
        );
    }

    @POST
    @Path("/adjustments")
    @RolesAllowed({"admin", "businessOwner"})
    public Response createRevenueAdjustment(RevenueAdjustmentRequest request) {
        entitlementService.requireFeature(caller.tenantCode(), EntitlementService.FEATURE_REPORTS);
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        Business business = resolveBusiness(request.businessId);
        RevenueService.AdjustmentEntry adjustment = revenueService.createAdjustment(
                caller.tenantCode(),
                business,
                request.entryDate,
                request.category,
                request.direction,
                request.amountMinor,
                request.currency,
                request.description,
                caller.userId()
        );
        return Response.created(URI.create("/api/revenue/adjustments/" + adjustment.id()))
                .entity(adjustment)
                .build();
    }

    @DELETE
    @Path("/adjustments/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    public Response deleteRevenueAdjustment(@PathParam("id") UUID adjustmentId) {
        entitlementService.requireFeature(caller.tenantCode(), EntitlementService.FEATURE_REPORTS);
        UUID businessId = businessAccess.isScopedStaff()
                ? businessAccess.requireCurrentBusinessId()
                : null;
        revenueService.deleteAdjustment(caller.tenantCode(), businessId, adjustmentId);
        return Response.noContent().build();
    }

    private UUID resolveBusinessScope(UUID requestedBusinessId) {
        Business business = resolveBusiness(requestedBusinessId);
        return business == null ? null : business.getId();
    }

    private Business resolveBusiness(UUID requestedBusinessId) {
        if (businessAccess.isScopedStaff()) {
            return businessAccess.requireCurrentBusiness();
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
        return business;
    }
}

package base.rest;

import base.entity.TenantSubscription;
import base.security.CallerContext;
import base.service.EntitlementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/admin/tenants/{tenantCode}/subscription")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminSubscriptionResource {

    public record ChangeSubscriptionRequest(String planCode, String billingPeriod, String status) {}

    public record MockSubscriptionTimeRequest(String scenario) {}

    @Inject
    EntitlementService entitlementService;

    @Inject
    CallerContext caller;

    @GET
    public EntitlementService.Entitlements getSubscription(@PathParam("tenantCode") String tenantCode) {
        return entitlementService.getEntitlements(tenantCode);
    }

    @PUT
    public TenantSubscription changeSubscription(
            @PathParam("tenantCode") String tenantCode,
            ChangeSubscriptionRequest request
    ) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("Subscription request is required");
        }
        return entitlementService.changeSubscription(
                tenantCode,
                request.planCode(),
                request.billingPeriod(),
                request.status(),
                caller.userId()
        );
    }

    @POST
    @Path("/mock-time")
    public EntitlementService.Entitlements mockSubscriptionTime(
            @PathParam("tenantCode") String tenantCode,
            MockSubscriptionTimeRequest request
    ) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("Mock subscription request is required");
        }
        return entitlementService.mockSubscriptionTime(tenantCode, request.scenario(), caller.userId());
    }
}

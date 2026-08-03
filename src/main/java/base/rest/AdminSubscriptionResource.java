package base.rest;

import base.entity.TenantSubscription;
import base.security.CallerContext;
import base.security.CallerContext;
import base.service.EntitlementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
}

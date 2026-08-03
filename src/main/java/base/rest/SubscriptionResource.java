package base.rest;

import base.entity.SubscriptionPlan;
import base.security.CallerContext;
import base.service.EntitlementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
public class SubscriptionResource {

    @Inject
    CallerContext caller;

    @Inject
    EntitlementService entitlementService;

    @GET
    @Path("/plans")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public List<SubscriptionPlan> listPlans() {
        return entitlementService.listActivePlans();
    }

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public EntitlementService.Entitlements myEntitlements() {
        return entitlementService.getEntitlements(caller.tenantCode());
    }
}

package base.rest;

import base.security.CallerContext;
import base.service.EcommerceIntegrationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/integrations/ecommerce/v1")
@Produces(MediaType.APPLICATION_JSON)
public class EcommerceIntegrationResource {

    @Inject
    CallerContext caller;

    @Inject
    EcommerceIntegrationService service;

    @GET
    @Path("/health")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public EcommerceIntegrationService.HealthSnapshot getEcommerceHealth(
            @QueryParam("businessId") UUID businessId
    ) {
        return service.checkHealth(caller.tenantCode(), businessId);
    }
}

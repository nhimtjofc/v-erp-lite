package base.rest;

import base.security.CallerContext;
import base.service.TenantConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/tenant-settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TenantConfigResource {

    @Inject
    TenantConfigService service;

    @Inject
    CallerContext caller;

    // ── Tenant-level config (admin / businessOwner) ──

    /**
     * Get the tenant-level settings (admin / businessOwner).
     */
    @GET
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public Response getTenantSettings() {
        return Response.ok(Map.of("settings", service.getTenantSettings(caller.tenantCode()))).build();
    }

    /**
     * Update the tenant-level settings (admin / businessOwner, full replace).
     */
    @PUT
    @RolesAllowed({"admin", "businessOwner"})
    public Response putTenantSettings(ConfigRequest request) {
        service.putTenantSettings(
                caller.tenantCode(),
                request.settings,
                caller.userId()
        );
        return Response.ok(Map.of("message", "Tenant settings updated")).build();
    }

    // ── Effective (merged) settings for current user ──

    /**
     * Get the effective settings for the current user, computed by deep-merging
     * hardcoded defaults &larr; tenant config &larr; this user's overrides.
     */
    @GET
    @Path("/effective")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response getEffectiveSettings() {
        return Response.ok(
                service.getEffectiveSettings(caller.tenantCode(), caller.userId())
        ).build();
    }

    // ── User-level preference overrides ──

    /**
     * Get the current user's personal override settings.
     */
    @GET
    @Path("/user")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response getUserSettings() {
        return Response.ok(Map.of("settings", service.getUserSettings(caller.userId()))).build();
    }

    /**
     * Update the current user's personal override settings (full replace).
     */
    @PUT
    @Path("/user")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response putUserSettings(ConfigRequest request) {
        service.putUserSettings(
                caller.userId(),
                caller.tenantCode(),
                request.settings
        );
        return Response.ok(Map.of("message", "User settings updated")).build();
    }

    // ── request DTO ──

    public record ConfigRequest(Map<String, Object> settings) {}
}

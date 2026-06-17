package base.rest;

import base.service.KeycloakSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Manual trigger for the Keycloak sync process.
 * <p>
 * The sync also runs automatically on {@code @Startup}, but this endpoint
 * allows re-triggering when Keycloak was not yet available during app boot
 * (e.g. containers still starting).
 */
@Path("/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SyncResource {

    @Inject
    KeycloakSyncService syncService;

    /**
     * Full sync: migrates the protocol mapper (once) and syncs all users.
     */
    @POST
    @Path("/keycloak")
    public Response syncAll() {
        KeycloakSyncService.SyncResult result = syncService.syncAll();
        return Response.ok(Map.of(
                "created", result.created,
                "updated", result.updated,
                "failed", result.failed,
                "mapperMigrated", result.mapperMigrated,
                "errors", result.errors
        )).build();
    }

    /**
     * Sync only users belonging to a specific tenant.
     */
    @POST
    @Path("/keycloak/tenants/{code}")
    @Transactional
    public Response syncTenant(@PathParam("code") String tenantCode) {
        KeycloakSyncService.SyncResult result = syncService.syncTenant(tenantCode);
        return Response.ok(Map.of(
                "created", result.created,
                "updated", result.updated,
                "failed", result.failed,
                "mapperMigrated", result.mapperMigrated,
                "errors", result.errors
        )).build();
    }
}

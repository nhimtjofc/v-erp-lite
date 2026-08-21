package base.rest;

import java.time.Instant;

import java.util.UUID;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import base.service.EcommerceCatalogService;
import base.service.EcommerceCatalogService.CatalogChangesResult;
import base.service.EcommerceCatalogService.CatalogSyncSummary;
import base.service.EcommerceCatalogService.CatalogMappingSyncRequest;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/integrations/ecommerce/v1/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "businessOwner"})
@Tag(name = "Ecommerce Catalog", description = "Endpoints for Ecommerce plugin catalog synchronization")
public class EcommerceCatalogResource {

    @Inject
    EcommerceCatalogService catalogService;

    @GET
    @Path("/changes")
    @Operation(summary = "Get Catalog Changes", description = "Returns active products modified after the 'since' timestamp, with their external Ecommerce IDs.")
    public CatalogChangesResult getCatalogChanges(
            @QueryParam("businessId") UUID businessId,
            @QueryParam("since") Instant since,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        
        return catalogService.getChanges(businessId, since, limit, offset);
    }

    @GET
    @Path("/summary")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    @Operation(summary = "Get Catalog Sync Summary", description = "Returns summary statistics of the Ecommerce catalog synchronization.")
    public CatalogSyncSummary getCatalogSyncSummary(
            @QueryParam("businessId") UUID businessId) {
        return catalogService.getSummary(businessId);
    }

    @POST
    @Path("/mappings")
    @Operation(summary = "Upsert Catalog Mappings", description = "Upserts Ecommerce mapping IDs and sync statuses for products and variants.")
    public void upsertCatalogMappings(
            @QueryParam("businessId") UUID businessId,
            CatalogMappingSyncRequest request) {
        catalogService.upsertMappings(businessId, request.items);
    }
}

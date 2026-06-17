package base.rest;

import base.entity.Tenant;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class TenantResource {

    @GET
    public List<Tenant> list() {
        return Tenant.listAll();
    }

    @GET
    @Path("/{id}")
    public Tenant get(@PathParam("id") UUID id) {
        Tenant tenant = Tenant.findById(id);
        if (tenant == null) {
            throw new NotFoundException("Tenant not found");
        }
        return tenant;
    }

    @POST
    @Transactional
    public Response create(Tenant tenant) {
        tenant.setId(UUID.randomUUID());
        tenant.persist();
        return Response.created(URI.create("/api/tenants/" + tenant.getId())).entity(tenant).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Tenant update(@PathParam("id") UUID id, Tenant updated) {
        Tenant tenant = Tenant.findById(id);
        if (tenant == null) {
            throw new NotFoundException("Tenant not found");
        }
        tenant.setCode(updated.getCode());
        tenant.setName(updated.getName());
        tenant.setOwnerId(updated.getOwnerId());
        tenant.setActive(updated.isActive());
        return tenant;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        boolean deleted = Tenant.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Tenant not found");
        }
        return Response.noContent().build();
    }
}

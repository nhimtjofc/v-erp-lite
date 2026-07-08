package base.rest;

import base.entity.Business;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/businesses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class BusinessResource {

    @Inject
    CallerContext caller;

    @GET
    public List<Business> listBusinesses() {
        return Business.list("tenantId", caller.tenantCode());
    }

    @GET
    @Path("/{id}")
    public Business getBusiness(@PathParam("id") UUID id) {
        Business business = Business.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (business == null) {
            throw new NotFoundException("Business not found");
        }
        return business;
    }

    @POST
    @Transactional
    public Response createBusiness(Business business) {
        business.setId(UUID.randomUUID());
        business.setTenantId(caller.tenantCode());
        business.setCreatedBy(caller.userId());
        business.persist();
        return Response.created(URI.create("/api/businesses/" + business.getId())).entity(business).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Business updateBusiness(@PathParam("id") UUID id, Business updated) {
        Business business = Business.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (business == null) {
            throw new NotFoundException("Business not found");
        }
        business.setName(updated.getName());
        business.setAddress(updated.getAddress());
        business.setPhone(updated.getPhone());
        business.setEmail(updated.getEmail());
        business.setTaxId(updated.getTaxId());
        business.setLogoUrl(updated.getLogoUrl());
        business.setActive(updated.isActive());
        business.setUpdatedBy(caller.userId());
        return business;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteBusiness(@PathParam("id") UUID id) {
        long deleted = Business.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("Business not found");
        }
        return Response.noContent().build();
    }
}

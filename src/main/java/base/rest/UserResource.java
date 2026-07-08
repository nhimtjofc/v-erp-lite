package base.rest;

import base.entity.Role;
import base.entity.User;
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

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    CallerContext caller;

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public User me() {
        return resolveMe();
    }

    @PUT
    @Path("/me")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    @Transactional
    public User updateMe(User updated) {
        User user = resolveMe();
        if (updated.getFirstName() != null) user.setFirstName(updated.getFirstName());
        if (updated.getLastName() != null) user.setLastName(updated.getLastName());
        if (updated.getPhone() != null) user.setPhone(updated.getPhone());
        if (updated.getJobTitle() != null) user.setJobTitle(updated.getJobTitle());
        if (updated.getBio() != null) user.setBio(updated.getBio());
        if (updated.getTimezone() != null) user.setTimezone(updated.getTimezone());
        if (updated.getWebsiteUrl() != null) user.setWebsiteUrl(updated.getWebsiteUrl());
        if (updated.getAvatarUrl() != null) user.setAvatarUrl(updated.getAvatarUrl());
        return user;
    }

    private User resolveMe() {
        return caller.resolveUser();
    }

    @GET
    @RolesAllowed({"admin", "businessOwner"})
    public List<User> list() {
        return User.list("tenantId", caller.tenantCode());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    public User get(@PathParam("id") UUID id) {
        User user = User.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    @POST
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public Response create(User user) {
        user.setId(UUID.randomUUID());
        user.setTenantId(caller.tenantCode());
        if (user.getRole() != null && user.getRole().getId() != null) {
            user.setRole(Role.findById(user.getRole().getId()));
        }
        user.persist();
        return Response.created(URI.create("/api/users/" + user.getId())).entity(user).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public User update(@PathParam("id") UUID id, User updated) {
        User user = User.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (updated.getFirstName() != null) user.setFirstName(updated.getFirstName());
        if (updated.getLastName() != null) user.setLastName(updated.getLastName());
        if (updated.getPhone() != null) user.setPhone(updated.getPhone());
        if (updated.getJobTitle() != null) user.setJobTitle(updated.getJobTitle());
        if (updated.getBio() != null) user.setBio(updated.getBio());
        if (updated.getTimezone() != null) user.setTimezone(updated.getTimezone());
        if (updated.getWebsiteUrl() != null) user.setWebsiteUrl(updated.getWebsiteUrl());
        if (updated.getAvatarUrl() != null) user.setAvatarUrl(updated.getAvatarUrl());
        if (updated.getRole() != null && updated.getRole().getId() != null) {
            user.setRole(Role.findById(updated.getRole().getId()));
        }
        user.setUpdatedBy(caller.userId());
        return user;
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        long deleted = User.delete("id = ?1 and tenantId = ?2", id, caller.tenantCode());
        if (deleted == 0) {
            throw new NotFoundException("User not found");
        }
        return Response.noContent().build();
    }
}

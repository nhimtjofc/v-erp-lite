package base.rest;

import base.entity.Role;
import base.entity.User;
import base.security.BusinessAccess;
import base.security.CallerContext;
import base.service.EntitlementService;
import base.service.KeycloakSyncService;
import base.service.WalletService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    @Inject
    CallerContext caller;

    @Inject
    WalletService walletService;

    @Inject
    BusinessAccess businessAccess;

    @Inject
    EntitlementService entitlementService;

    @Inject
    KeycloakSyncService keycloakSync;

    @GET
    @Path("/test-error")
    @jakarta.annotation.security.PermitAll
    public jakarta.ws.rs.core.Response testError() {
        base.entity.User.find("email = ?1 and tenantId = ?2", "a", "b").firstResult();
        return jakarta.ws.rs.core.Response.ok("ok").build();
    }

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
    public List<User> listUsers() {
        return User.list("tenantId", caller.tenantCode());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    public User getUser(@PathParam("id") UUID id) {
        User user = User.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    @POST
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public Response createUser(User user) {
        entitlementService.requireUserCapacity(caller.tenantCode());
        user.setId(UUID.randomUUID());
        user.setTenantId(caller.tenantCode());
        user.setRole(resolveAssignableRole(user.getRole()));
        businessAccess.synchronizeAssignment(user, caller.tenantCode());
        user.persist();
        walletService.getOrCreateUserWallet(
                caller.tenantCode(), user.getId(), "VND", caller.userId());
        try {
            String keycloakId = keycloakSync.createUserInKeycloak(user);
            if (keycloakId != null) {
                user.setKeycloakId(keycloakId);
            }
        } catch (Exception exception) {
            log.warn("Failed to create Keycloak account for {}: {}", user.getEmail(), exception.getMessage());
        }
        return Response.created(URI.create("/api/users/" + user.getId())).entity(user).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public User updateUser(@PathParam("id") UUID id, User updated) {
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
        if (updated.getRole() != null) {
            Role oldRole = user.getRole();
            Role newRole = resolveAssignableRole(updated.getRole());
            user.setRole(newRole);
            if (user.getKeycloakId() != null && (oldRole == null || !newRole.getName().equals(oldRole.getName()))) {
                try {
                    keycloakSync.updateKeycloakUserRole(user.getKeycloakId(), newRole.getName());
                } catch (Exception exception) {
                    log.warn("Failed to update Keycloak role for {}: {}", user.getEmail(), exception.getMessage());
                }
            }
        }
        businessAccess.synchronizeAssignment(user, caller.tenantCode());
        user.setUpdatedBy(caller.userId());
        return user;
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public Response deleteUser(@PathParam("id") UUID id) {
        User user = User.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        if (user.getId().equals(caller.resolveUser().getId())) {
            throw new BadRequestException("You cannot remove your own account");
        }
        if (caller.hasRole("businessOwner") && !caller.hasRole("admin")
                && (user.getRole() == null || !"operator".equals(user.getRole().getName()))) {
            throw new ForbiddenException("Owners can manage operator accounts only");
        }
        if (user.getKeycloakId() != null) {
            try {
                keycloakSync.disableKeycloakUser(user.getKeycloakId());
            } catch (Exception exception) {
                log.warn("Failed to disable Keycloak account for {}: {}", user.getEmail(), exception.getMessage());
            }
        }
        user.delete();
        return Response.noContent().build();
    }

    private Role resolveAssignableRole(Role requestedRole) {
        if (requestedRole == null) {
            if (caller.hasRole("businessOwner") && !caller.hasRole("admin")) {
                throw new BadRequestException("An operator role is required");
            }
            return null;
        }
        Role role = requestedRole.getId() != null
                ? Role.findById(requestedRole.getId())
                : Role.find("name", requestedRole.getName()).firstResult();
        if (role == null) {
            throw new BadRequestException("Role not found");
        }
        if (caller.hasRole("businessOwner") && !caller.hasRole("admin") && !"operator".equals(role.getName())) {
            throw new ForbiddenException("Owners can assign the operator role only");
        }
        return role;
    }
}

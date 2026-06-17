package base.rest;

import base.entity.Role;
import base.entity.Tenant;
import base.entity.User;
import base.service.KeycloakSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * System admin endpoints for managing tenants and tenant users.
 * All endpoints require the {@code admin} role.
 * <p>
 * User mutations (create/update/delete) sync bidirectionally between
 * the app DB and Keycloak — changes flow app → Keycloak so the admin
 * UI is the single source of truth.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminResource {

    private static final Logger log = LoggerFactory.getLogger(AdminResource.class);

    @Inject
    KeycloakSyncService keycloakSync;

    // ──────────────────────────────────────────────
    // Tenant management
    // ──────────────────────────────────────────────
    // Full tenant CRUD lives in TenantResource (/api/tenants),
    // also @RolesAllowed("admin"). This resource adds only
    // tenant-scoped user management below.

    // ──────────────────────────────────────────────
    // Tenant user management
    // ──────────────────────────────────────────────

    @GET
    @Path("/tenants/{tenantId}/users")
    public List<User> listTenantUsers(@PathParam("tenantId") String tenantId) {
        Tenant tenant = Tenant.find("code", tenantId).firstResult();
        if (tenant == null) {
            throw new NotFoundException("Tenant not found: " + tenantId);
        }
        return User.list("tenantId", tenantId);
    }

    @POST
    @Path("/tenants/{tenantId}/users")
    @Transactional
    public Response createTenantUser(@PathParam("tenantId") String tenantId, User user) {
        Tenant tenant = Tenant.find("code", tenantId).firstResult();
        if (tenant == null) {
            throw new NotFoundException("Tenant not found: " + tenantId);
        }

        // Persist locally first
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setRole(resolveRole(user.getRole()));
        user.persist();

        // Sync to Keycloak (create KC user, ensure org, add member)
        try {
            String kcId = keycloakSync.createUserInKeycloak(user);
            if (kcId != null) {
                user.setKeycloakId(kcId);
                user.persist();
                log.info("Created Keycloak user {} for {}", kcId, user.getEmail());
            } else {
                log.warn("Keycloak user creation returned null for {} — DB user created without KC", user.getEmail());
            }
        } catch (Exception e) {
            log.warn("Failed to sync user {} to Keycloak: {}", user.getEmail(), e.getMessage());
            // User is still created in DB even if KC sync fails
        }

        return Response.created(URI.create("/api/admin/tenants/" + tenantId + "/users/" + user.getId()))
                .entity(user)
                .build();
    }

    @PUT
    @Path("/tenants/{tenantId}/users/{id}")
    @Transactional
    public User updateTenantUser(
            @PathParam("tenantId") String tenantId,
            @PathParam("id") UUID id,
            User updated
    ) {
        Tenant tenant = Tenant.find("code", tenantId).firstResult();
        if (tenant == null) {
            throw new NotFoundException("Tenant not found: " + tenantId);
        }
        User user = User.findById(id);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        // Track role change for KC sync
        Role oldRole = user.getRole();

        user.setFirstName(updated.getFirstName());
        user.setLastName(updated.getLastName());
        user.setActive(updated.isActive());
        user.setRole(resolveRole(updated.getRole()));
        if (updated.getEmail() != null) {
            user.setEmail(updated.getEmail());
        }
        if (updated.getPhone() != null) {
            user.setPhone(updated.getPhone());
        }
        if (updated.getJobTitle() != null) {
            user.setJobTitle(updated.getJobTitle());
        }
        if (updated.getTimezone() != null) {
            user.setTimezone(updated.getTimezone());
        }

        // Sync role change to Keycloak
        Role newRole = user.getRole();
        if (user.getKeycloakId() != null && newRole != null
                && (oldRole == null || !newRole.getName().equals(oldRole.getName()))) {
            try {
                keycloakSync.updateKeycloakUserRole(user.getKeycloakId(), newRole.getName());
                log.info("Updated Keycloak role for {} to {}", user.getEmail(), newRole.getName());
            } catch (Exception e) {
                log.warn("Failed to update Keycloak role for {}: {}", user.getEmail(), e.getMessage());
            }
        }

        return user;
    }

    @DELETE
    @Path("/tenants/{tenantId}/users/{id}")
    @Transactional
    public Response deleteTenantUser(
            @PathParam("tenantId") String tenantId,
            @PathParam("id") UUID id
    ) {
        Tenant tenant = Tenant.find("code", tenantId).firstResult();
        if (tenant == null) {
            throw new NotFoundException("Tenant not found: " + tenantId);
        }
        User user = User.findById(id);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        // Disable in Keycloak first (then delete from app DB)
        if (user.getKeycloakId() != null) {
            try {
                keycloakSync.disableKeycloakUser(user.getKeycloakId());
                log.info("Disabled Keycloak user {} for {}", user.getKeycloakId(), user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to disable Keycloak user {}: {}", user.getKeycloakId(), e.getMessage());
            }
        }

        user.delete();
        return Response.noContent().build();
    }

    /**
     * Resolves a Role reference: first tries the UUID primary key,
     * then falls back to looking up by name. This lets the frontend
     * send either {@code {id: "89b5..."}} or {@code {name: "operator"}}.
     */
    private static Role resolveRole(Role input) {
        if (input == null) return null;
        // Try UUID primary key lookup first — only if the id looks like a UUID
        if (input.getId() != null && isValidUuid(input.getId().toString())) {
            Role byId = Role.findById(input.getId());
            if (byId != null) return byId;
        }
        // Fall back to name-based lookup (supports {name: "operator"} payloads)
        if (input.getName() != null) {
            Role byName = Role.find("name", input.getName()).firstResult();
            if (byName != null) return byName;
        }
        throw new BadRequestException("Unknown role: " + (input.getName() != null ? input.getName() : input.getId()));
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

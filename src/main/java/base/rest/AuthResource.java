package base.rest;

import base.entity.Customer;
import base.entity.Role;
import base.entity.User;
import base.security.CallerContext;
import base.security.TotpUtil;
import base.service.KeycloakSyncService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;

import java.util.Map;
import java.util.UUID;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    CallerContext caller;

    @Inject
    Keycloak keycloak;

    @Inject
    KeycloakSyncService keycloakSync;

    private static final String KEYCLOAK_REALM = "v-erp-lite";

    // ── Password ──

    /**
     * Change password for the currently authenticated user.
     * Uses Keycloak Admin API to reset the password directly.
     * The user is already authenticated via OIDC, so we don't need to re-verify the old password.
     */
    @PUT
    @Path("/password")
    @Transactional
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response changePassword(PasswordChangeRequest request) {
        if (request.newPassword == null || request.newPassword.length() < 8) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Password must be at least 8 characters"))
                    .build();
        }

        User user = caller.resolveUser();

        // Use Keycloak Admin API to reset password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.newPassword);
        credential.setTemporary(false);

        keycloak.realm(KEYCLOAK_REALM)
                .users()
                .get(user.getKeycloakId())
                .resetPassword(credential);

        return Response.ok(Map.of("message", "Password updated successfully")).build();
    }

    // ── MFA ──

    /**
     * Generate a TOTP secret and return the provisioning URI for the authenticator app.
     */
    @GET
    @Path("/mfa/setup")
    @Transactional
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response setupMfa() {
        User user = caller.resolveUser();

        // Generate new secret
        String secret = TotpUtil.generateSecret();
        String email = caller.userEmail();
        String provisioningUri = TotpUtil.buildProvisioningUri(secret, email, "V-ERP Lite");

        // Store secret (not yet enabled — user must verify first)
        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        user.persist();

        return Response.ok(Map.of(
                "secret", secret,
                "provisioningUri", provisioningUri
        )).build();
    }

    /**
     * Verify a TOTP code and enable MFA.
     */
    @POST
    @Path("/mfa/verify")
    @Transactional
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response verifyMfa(MfaVerifyRequest request) {
        if (request.code == null || request.code.length() != 6) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid verification code"))
                    .build();
        }

        User user = caller.resolveUser();
        if (user.getMfaSecret() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "MFA not initialized. Call POST /auth/mfa/setup first"))
                    .build();
        }

        if (!TotpUtil.verify(request.code, user.getMfaSecret())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid verification code"))
                    .build();
        }

        user.setMfaEnabled(true);
        user.persist();

        return Response.ok(Map.of("message", "MFA enabled successfully")).build();
    }

    /**
     * Disable MFA for the current user.
     */
    @POST
    @Path("/mfa/disable")
    @Transactional
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response disableMfa() {
        User user = caller.resolveUser();
        user.setMfaSecret(null);
        user.setMfaEnabled(false);
        user.persist();
        return Response.ok(Map.of("message", "MFA disabled")).build();
    }

    /**
     * Get MFA status for the current user.
     */
    @GET
    @Path("/mfa/status")
    @RolesAllowed({"admin", "operator", "businessOwner", "customer"})
    public Response mfaStatus() {
        User user = caller.resolveUser();
        return Response.ok(Map.of(
                "mfaEnabled", user.isMfaEnabled(),
                "hasSecret", user.getMfaSecret() != null
        )).build();
    }

    // ── request DTOs ──

    public record PasswordChangeRequest(String newPassword) {}

    public record MfaVerifyRequest(String code) {}

    public static class RegisterRequest {
        public String email;
        public String password;
        public String firstName;
        public String lastName;
        public String phone;
    }

    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(RegisterRequest request) {
        if (request.email == null || request.email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (request.password == null || request.password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        if (request.firstName == null || request.firstName.isBlank()) {
            throw new BadRequestException("First name is required");
        }
        if (request.lastName == null || request.lastName.isBlank()) {
            throw new BadRequestException("Last name is required");
        }

        String tenantCode = "v-erp-app"; // Default tenant

        // Check if user already exists
        long exists = User.count("email = ?1 and tenantId = ?2", request.email, tenantCode);
        if (exists > 0) {
            throw new BadRequestException("User with email " + request.email + " already exists");
        }

        // Resolve customer role
        Role role = Role.find("name", "customer").firstResult();
        if (role == null) {
            throw new BadRequestException("Customer role not found in system");
        }

        // 1. Create and persist User locally
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantCode);
        user.setEmail(request.email);
        user.setFirstName(request.firstName);
        user.setLastName(request.lastName);
        user.setPhone(request.phone);
        user.setRole(role);
        user.setActive(true);
        user.persist();

        // 2. Create customer profile locally immediately
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantCode);
        customer.setEmail(request.email);
        customer.setFirstName(request.firstName);
        customer.setLastName(request.lastName);
        customer.setPhone(request.phone);
        customer.setActive(true);
        customer.persist();

        // 3. Sync to Keycloak
        try {
            String kcId = keycloakSync.createUserInKeycloak(user);
            if (kcId != null) {
                user.setKeycloakId(kcId);

                // Override password with user's password
                keycloakSync.setPassword(kcId, request.password);
            } else {
                throw new BadRequestException("Failed to register user in Keycloak");
            }
        } catch (Exception e) {
            throw new BadRequestException("Failed to sync user to authentication service: " + e.getMessage());
        }

        return Response.ok(Map.of("message", "User registered successfully")).build();
    }
}

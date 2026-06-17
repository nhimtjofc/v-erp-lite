package base.rest;

import base.entity.User;
import base.security.CallerContext;
import base.security.TotpUtil;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;

import java.util.Map;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    CallerContext caller;

    @Inject
    Keycloak keycloak;

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
    @RolesAllowed({"admin", "operator", "businessOwner"})
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
    @RolesAllowed({"admin", "operator", "businessOwner"})
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
    @RolesAllowed({"admin", "operator", "businessOwner"})
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
    @RolesAllowed({"admin", "operator", "businessOwner"})
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
    @RolesAllowed({"admin", "operator", "businessOwner"})
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
}

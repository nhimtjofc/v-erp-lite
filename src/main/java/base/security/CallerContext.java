package base.security;

import base.entity.User;
import base.entity.Business;
import base.service.UserProvisioningService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Request-scoped caller context — the single bean business code injects to learn
 * <em>who</em> is calling and <em>where</em> (which tenant).
 * <p>
 * Uses direct {@link JsonWebToken} injection (made available by quarkus-oidc +
 * quarkus-smallrye-jwt) rather than looking up the principal via
 * {@link SecurityIdentity}, because the principal type varies across Quarkus versions.
 */
@RequestScoped
public class CallerContext {

    private static final Logger log = LoggerFactory.getLogger(CallerContext.class);

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    UserProvisioningService userProvisioningService;

    // ── where ──

    /**
     * Returns the tenant code from the authenticated OIDC token.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Keycloak Organizations {@code organization} claim (array of aliases,
     *       e.g. {@code ["v-erp-app"]}) — the member's org alias is the tenant code.</li>
     *   <li>Custom {@code tenant_code} claim injected by the protocol mapper (backward compat).</li>
     *   <li>{@code azp} (authorized party) as a last-resort fallback.</li>
     *   <li>{@code "default"} if nothing else resolves.</li>
     * </ol>
     */
    public String tenantCode() {
        // 1. Keycloak Organizations claim (organization scope emits array of aliases)
        try {
            JsonValue orgClaim = jwt.getClaim("organization");
            if (orgClaim instanceof JsonArray arr && !arr.isEmpty()) {
                return arr.getString(0);
            }
        } catch (Exception e) {
            // Not an array or not present — fall through
        }

        // 2. Custom tenant_code attribute (protocol mapper, backward compat)
        String code = jwt.getClaim("tenant_code");
        if (code == null || code.isBlank()) {
            code = jwt.getClaim("azp");
        }
        if (code != null && !code.isBlank()) return code;
        log.warn("No tenant_code claim found in JWT — falling back to 'default'");
        return "default";
    }

    // ── who ──

    /**
     * Returns the authenticated user's ID (Keycloak subject UUID).
     */
    public UUID userId() {
        return parseUuid(jwt.getSubject());
    }

    /**
     * Returns the user's email from the token.
     */
    public String userEmail() {
        return jwt.getClaim("email");
    }

    /**
     * Returns the user's preferred username.
     */
    public String preferredUsername() {
        return jwt.getClaim("preferred_username");
    }

    /**
     * Returns true if the caller has the given role.
     */
    public boolean hasRole(String role) {
        return securityIdentity.hasRole(role);
    }

    /**
     * Returns true if the caller is authenticated (not anonymous).
     */
    public boolean isAuthenticated() {
        return !securityIdentity.isAnonymous();
    }

    /**
     * Resolves the app-DB User entity for the currently authenticated caller.
     * Throws 404 if no matching user exists in the database.
     */
    public User resolveUser() {
        String email = userEmail();
        String tenantCode = tenantCode();
        User user = User.find("email = ?1 and tenantId = ?2", email, tenantCode).firstResult();
        if (user == null) {
            // Check if we have an authenticated user and email in the JWT, then auto-provision
            if (isAuthenticated() && email != null && !email.isBlank()) {
                String kcId = jwt.getSubject();
                String firstName = jwt.getClaim("given_name");
                String lastName = jwt.getClaim("family_name");
                if (firstName == null) {
                    firstName = jwt.getClaim("name"); // fallback
                }
                String avatarUrl = jwt.getClaim("picture");
                user = userProvisioningService.provisionUser(email, tenantCode, kcId, firstName, lastName, avatarUrl);
            } else {
                throw new NotFoundException("User not found for authenticated caller");
            }
        }
        return user;
    }

    public Business resolveBusiness() {
        User user = resolveUser();
        String tenantCode = tenantCode();
        Business business = Business.find("createdBy = ?1 and tenantId = ?2", user.getId(), tenantCode).firstResult();
        if (business == null && (hasRole("operator") || hasRole("businessOwner"))) {
            User owner = User.find("email = ?1 and tenantId = ?2", "owner@v-erp.com", tenantCode).firstResult();
            if (owner != null) {
                business = Business.find("createdBy = ?1 and tenantId = ?2", owner.getId(), tenantCode).firstResult();
            }
            if (business == null) {
                business = Business.find("tenantId = ?1", tenantCode).firstResult();
            }
        }
        return business;
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

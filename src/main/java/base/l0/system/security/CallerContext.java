package base.l0.system.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;
import java.util.UUID;

/**
 * The calling tenant and actor, read from the bearer token issued by the ERP that fronts this
 * service. {@link #tenantCode()} feeds Hibernate's tenant resolver, so every query this request
 * makes is confined to that tenant without any query having to say so.
 */
@RequestScoped
public class CallerContext {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "payment.tenant.claim", defaultValue = "tenant_code")
    String tenantClaim;

    public String tenantCode() {
        // PostEntryUC passes this straight to the posting procedure, so an elevation has to reach
        // here too — otherwise system work would post into one tenant while querying another.
        Optional<String> acting = SystemTenantContext.acting();
        if (acting.isPresent()) {
            return acting.get();
        }
        Object claim = jwt == null ? null : jwt.getClaim(tenantClaim);
        if (claim == null || claim.toString().isBlank()) {
            throw new MissingTenantException("Request carries no " + tenantClaim + " claim");
        }
        return claim.toString();
    }

    public UUID actorId() {
        return Optional.ofNullable(jwt)
                .map(token -> token.<Object>getClaim("sub"))
                .map(Object::toString)
                .flatMap(CallerContext::parseUuid)
                .orElse(null);
    }

    public boolean hasRole(String role) {
        return identity != null && identity.hasRole(role);
    }

    private static Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}


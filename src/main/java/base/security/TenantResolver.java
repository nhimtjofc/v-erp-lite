package base.security;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolves the OIDC tenant based on the request.
 * For now, uses the default tenant; can be extended for multi-realm setups.
 */
@ApplicationScoped
public class TenantResolver implements io.quarkus.oidc.TenantResolver {

    @Override
    public String resolve(RoutingContext context) {
        // Default single-tenant OIDC — always resolve to the configured tenant.
        // Override to support multi-realm Keycloak by inspecting the request (host, header, etc.).
        return null; // null = use the default OIDC tenant
    }
}

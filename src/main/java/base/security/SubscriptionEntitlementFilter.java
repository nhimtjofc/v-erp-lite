package base.security;

import base.service.EntitlementService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Applies plan access at the HTTP boundary. The frontend may hide unavailable
 * navigation, but only this server-side check is authoritative.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class SubscriptionEntitlementFilter implements ContainerRequestFilter {

    @Inject
    CallerContext caller;

    @Inject
    EntitlementService entitlementService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!caller.isAuthenticated() || caller.hasRole("admin")) {
            return;
        }

        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (isPlatformAdministration(path) || isSubscriptionRecoveryPath(path) || path.startsWith("auth/")) {
            return;
        }

        String tenantCode = caller.tenantCode();
        if (path.startsWith("integrations/ecommerce/")) {
            entitlementService.requireFeature(tenantCode, EntitlementService.FEATURE_ECOMMERCE);
           return;
        }
        if (path.startsWith("payment-intents")) {
            entitlementService.requireFeature(tenantCode, EntitlementService.FEATURE_ONLINE_PAYMENTS);
           return;
        }
        if (path.startsWith("wallets") || path.startsWith("payouts")) {
            entitlementService.requireFeature(tenantCode, EntitlementService.FEATURE_WALLET_PAYOUTS);
           return;
        }

    }

    static boolean isPlatformAdministration(String path) {
        return path.startsWith("admin/")
                || path.startsWith("tenants")
                || path.startsWith("sync/")
               || path.startsWith("i18n/")
                || path.startsWith("health");
    }

    static boolean isSubscriptionRecoveryPath(String path) {
        return path.equals("users/me") || path.startsWith("subscriptions/");
    }

    static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replaceFirst("^/+", "");
        return normalized.startsWith("api/") ? normalized.substring(4) : normalized;
    }

}

package base.rest;

import base.entity.Business;
import base.entity.Order;
import base.entity.User;
import base.security.BusinessAccess;
import base.security.CallerContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.HashMap;
import java.util.Map;

@Path("/debug-db")
@Produces(MediaType.APPLICATION_JSON)
public class DebugDbResource {

    @Inject
    CallerContext caller;

    @Inject
    BusinessAccess businessAccess;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/caller")
    public Response debugCaller() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("token_subject", jwt.getSubject());
            result.put("token_email", jwt.getClaim("email"));
            result.put("token_tenant_code", jwt.getClaim("tenant_code"));
            result.put("token_azp", jwt.getClaim("azp"));
            result.put("token_organization", jwt.getClaim("organization"));
            
            result.put("caller_email", caller.userEmail());
            result.put("caller_tenant", caller.tenantCode());
            result.put("caller_id", caller.userId());
            result.put("caller_is_authenticated", caller.isAuthenticated());

            try {
                User u = caller.resolveUser();
                result.put("resolved_user_id", u.getId());
                result.put("resolved_user_email", u.getEmail());
                result.put("resolved_user_tenant", u.getTenantId());
                result.put("resolved_user_role", u.getRole() != null ? u.getRole().getName() : null);
            } catch (Exception e) {
                result.put("resolve_error", e.getMessage());
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("/orders")
    public Response debugOrders() {
        Map<String, Object> result = new HashMap<>();
        try {
            User u = caller.resolveUser();
            String tenant = caller.tenantCode();
            result.put("tenant", tenant);
            result.put("user_id", u.getId());
            result.put("user_email", u.getEmail());
            result.put("user_role", u.getRole() != null ? u.getRole().getName() : null);
            result.put("user_business_id", u.getBusiness() != null ? u.getBusiness().getId() : null);
            result.put("user_business_name", u.getBusiness() != null ? u.getBusiness().getName() : null);

            Business found = businessAccess.findCurrentBusiness();
            result.put("findCurrentBusiness", found != null ? found.getId() + "/" + found.getName() : "null");

            long totalOrders = Order.count("tenantId = ?1 AND deletedAt IS NULL", tenant);
            result.put("total_orders_in_tenant", totalOrders);

            if (found != null) {
                long businessOrders = Order.count("tenantId = ?1 AND business.id = ?2 AND deletedAt IS NULL", tenant, found.getId());
                result.put("orders_for_business", businessOrders);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("error_type", e.getClass().getName());
        }
        return Response.ok(result).build();
    }
}

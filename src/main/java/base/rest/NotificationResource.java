package base.rest;

import base.entity.Customer;
import base.entity.Notification;
import base.entity.User;
import base.security.CallerContext;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    CallerContext caller;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public List<Notification> listNotifications(@QueryParam("unreadOnly") Boolean unreadOnly) {
        String tenantCode = caller.tenantCode();
        Customer customer = resolveCustomer();

        if (customer == null) {
            // Staff/admin: return all notifications for the tenant
            if (unreadOnly != null && unreadOnly) {
                return Notification.find("tenantId = ?1 AND readAt IS NULL ORDER BY createdAt DESC", tenantCode).list();
            }
            return Notification.find("tenantId = ?1 ORDER BY createdAt DESC", tenantCode).list();
        }

        if (unreadOnly != null && unreadOnly) {
            return Notification.find("tenantId = ?1 AND customer.id = ?2 AND readAt IS NULL ORDER BY createdAt DESC",
                    tenantCode, customer.getId()).list();
        }
        return Notification.find("tenantId = ?1 AND customer.id = ?2 ORDER BY createdAt DESC",
                tenantCode, customer.getId()).list();
    }

    @GET
    @Path("/count")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public Map<String, Object> getNotificationCount() {
        String tenantCode = caller.tenantCode();
        Customer customer = resolveCustomer();

        long total;
        long unread;

        if (customer == null) {
            total = Notification.find("tenantId = ?1", tenantCode).count();
            unread = Notification.find("tenantId = ?1 AND readAt IS NULL", tenantCode).count();
        } else {
            total = Notification.find("tenantId = ?1 AND customer.id = ?2", tenantCode, customer.getId()).count();
            unread = Notification.find("tenantId = ?1 AND customer.id = ?2 AND readAt IS NULL",
                    tenantCode, customer.getId()).count();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("unread", unread);
        return result;
    }

    @PUT
    @Path("/{id}/read")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    @Transactional
    public Notification markNotificationRead(@PathParam("id") UUID id) {
        String tenantCode = caller.tenantCode();
        Customer customer = resolveCustomer();
        Notification notification = customer == null
                ? Notification.find("id = ?1 AND tenantId = ?2", id, tenantCode).firstResult()
                : Notification.find("id = ?1 AND tenantId = ?2 AND customer.id = ?3", id, tenantCode, customer.getId())
                        .firstResult();
        if (notification == null) {
            throw new NotFoundException("Notification not found");
        }
        notification.setReadAt(Instant.now());
        return notification;
    }

    @PUT
    @Path("/read-all")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    @Transactional
    public Map<String, Object> markAllNotificationsRead() {
        String tenantCode = caller.tenantCode();
        Customer customer = resolveCustomer();
        Instant now = Instant.now();

        long updated;
        if (customer == null) {
            updated = Notification.update("readAt = ?1 WHERE tenantId = ?2 AND readAt IS NULL",
                    now, tenantCode);
        } else {
            updated = Notification.update("readAt = ?1 WHERE tenantId = ?2 AND customer.id = ?3 AND readAt IS NULL",
                    now, tenantCode, customer.getId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updated", updated);
        return result;
    }

    private Customer resolveCustomer() {
        if (securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator")) {
            Customer customer = Customer.find("email = ?1 AND tenantId = ?2",
                    caller.userEmail(), caller.tenantCode()).firstResult();
            if (customer == null) {
                throw new ForbiddenException("Customer profile not found for authenticated caller");
            }
            return customer;
        }
        return null;
    }
}

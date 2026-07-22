package base.service;

import base.entity.Customer;
import base.entity.Notification;
import base.entity.Order;
import base.entity.User;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class NotificationService {

    public Notification notifyBuyer(Order order, String type, String message, UUID actorId) {
        if (order == null) {
            return null;
        }
        Customer customer = order.getCustomer();
        if (customer == null) {
            return null;
        }
        return createNotification(order, customer, null, type, message, actorId);
    }

    public List<Notification> notifyBusinessStaff(Order order, String type, String message, UUID actorId) {
        if (order == null || order.getBusiness() == null) {
            return List.of();
        }

        Map<UUID, User> recipients = new LinkedHashMap<>();
        List<User> assignedStaff = User.find(
                "tenantId = ?1 and active = true and business.id = ?2 "
                        + "and (role.name = ?3 or role.name = ?4)",
                order.getTenantId(),
                order.getBusiness().getId(),
                "businessOwner",
                "operator"
        ).list();
        for (User user : assignedStaff) {
            recipients.put(user.getId(), user);
        }

        UUID ownerId = order.getBusiness().getCreatedBy();
        if (ownerId != null && !recipients.containsKey(ownerId)) {
            User owner = User.find(
                    "id = ?1 and tenantId = ?2 and active = true",
                    ownerId,
                    order.getTenantId()
            ).firstResult();
            if (owner != null) {
                recipients.put(owner.getId(), owner);
            }
        }

        List<Notification> notifications = new ArrayList<>();
        for (User recipient : recipients.values()) {
            notifications.add(createNotification(order, null, recipient, type, message, actorId));
        }
        return notifications;
    }

    private Notification createNotification(Order order, Customer customer, User recipientUser,
                                            String type, String message, UUID actorId) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTenantId(order.getTenantId());
        notification.setCustomer(customer);
        notification.setRecipientUser(recipientUser);
        notification.setOrderId(order.getId());
        notification.setType(type);
        notification.setMessage(message);
        notification.setCreatedBy(actorId);
        notification.persist();
        return notification;
    }
}

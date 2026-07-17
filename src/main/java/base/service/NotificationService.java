package base.service;

import base.entity.Customer;
import base.entity.Notification;
import base.entity.Order;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class NotificationService {

    public Notification notifyBuyer(Order order, String type, String message, UUID actorId) {
        Customer customer = order.getCustomer();
        if (customer == null) {
            return null;
        }
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTenantId(order.getTenantId());
        notification.setCustomer(customer);
        notification.setOrderId(order.getId());
        notification.setType(type);
        notification.setMessage(message);
        notification.setCreatedBy(actorId);
        notification.persist();
        return notification;
    }
}

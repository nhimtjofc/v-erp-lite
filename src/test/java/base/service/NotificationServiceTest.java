package base.service;

import base.entity.Business;
import base.entity.Notification;
import base.entity.Order;
import base.entity.Role;
import base.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class NotificationServiceTest {

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    NotificationService notificationService;

    @Test
    void createsNotificationForTheOrderCustomer() {
        String tenantId = "buyer-notification-" + UUID.randomUUID();
        PaymentTestDataFactory.SeedOrder seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(50_000));

        Notification created = QuarkusTransaction.requiringNew().call(() -> {
            Order order = Order.findById(seed.order().getId());
            return notificationService.notifyBuyer(
                    order,
                    "ORDER_STATUS_UPDATED",
                    "The order status changed.",
                    UUID.randomUUID());
        });

        assertNull(created.getRecipientUser());
        assertEquals(1, Notification.count(
                "tenantId = ?1 and orderId = ?2 and customer.id = ?3",
                tenantId, seed.order().getId(), seed.customer().getId()));
        assertEquals(0, Notification.count(
                "tenantId = ?1 and orderId = ?2 and recipientUser is not null",
                tenantId, seed.order().getId()));
    }

    @Test
    void createsSeparateNotificationsForTheAssignedOwnerAndOperator() {
        String tenantId = "notification-" + UUID.randomUUID();
        PaymentTestDataFactory.SeedOrder seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(50_000));
        UUID ownerId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            Business business = Business.findById(seed.seller().getId());
            User owner = createStaffUser(tenantId, ownerId, "businessOwner", business);
            createStaffUser(tenantId, operatorId, "operator", business);
            business.setCreatedBy(owner.getId());
        });

        List<Notification> created = QuarkusTransaction.requiringNew().call(() -> {
            Order order = Order.findById(seed.order().getId());
            return notificationService.notifyBusinessStaff(
                    order,
                    "ORDER_PLACED",
                    "A new order was placed.",
                    UUID.randomUUID());
        });

        assertEquals(2, created.size());
        assertEquals(1, Notification.count(
                "tenantId = ?1 and orderId = ?2 and recipientUser.id = ?3",
                tenantId, seed.order().getId(), ownerId));
        assertEquals(1, Notification.count(
                "tenantId = ?1 and orderId = ?2 and recipientUser.id = ?3",
                tenantId, seed.order().getId(), operatorId));
        assertEquals(0, Notification.count(
                "tenantId = ?1 and orderId = ?2 and customer is not null",
                tenantId, seed.order().getId()));
    }

    private User createStaffUser(String tenantId, UUID id, String roleName, Business business) {
        Role role = Role.find("name", roleName).firstResult();
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setEmail(roleName + "-" + id + "@example.com");
        user.setFirstName(roleName);
        user.setLastName("Notification Test");
        user.setRole(role);
        user.setBusiness(business);
        user.setActive(true);
        user.persist();
        return user;
    }
}

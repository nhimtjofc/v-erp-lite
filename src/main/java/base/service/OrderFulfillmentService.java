package base.service;

import base.entity.Business;
import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.OrderItem;
import base.entity.Product;
import base.entity.ProductVariant;
import base.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class OrderFulfillmentService {

    public static final String NO_RESPONSE_REASON = "no response within SLA";

    @Inject
    PaymentRefundService paymentRefundService;

    @Inject
    NotificationService notificationService;

    @Transactional
    public Order decide(String tenantId, UUID orderId, User owner, String decision, String reason, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        requireOwner(order, owner);
        String normalizedDecision = normalizeDecision(decision);
        if (!"PAID_ESCROW".equals(order.getStatus())) {
            throw new BadRequestException("Only an order in PAID_ESCROW can be accepted or declined");
        }
        if ("DECLINE".equals(normalizedDecision) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when declining an order");
        }

        if ("ACCEPT".equals(normalizedDecision)) {
            transition(order, "OWNER_ACCEPTED", "Owner accepted the fulfillment commitment", actorId);
            notificationService.notifyBuyer(
                    order,
                    "ORDER_OWNER_ACCEPTED",
                    "The vendor accepted order " + order.getOrderNumber() + " and will begin fulfillment.",
                    actorId);
        } else {
            declineAndRefund(order, reason.trim(), actorId);
        }
        return order;
    }

    @Transactional
    public boolean autoDecline(String tenantId, UUID orderId, Instant now) {
        Order order = orderForUpdate(tenantId, orderId);
        if (!"PAID_ESCROW".equals(order.getStatus())
                || order.getOwnerDecisionDeadline() == null
                || order.getOwnerDecisionDeadline().isAfter(now)) {
            return false;
        }
        declineAndRefund(order, NO_RESPONSE_REASON, null);
        return true;
    }

    private void declineAndRefund(Order order, String reason, UUID actorId) {
        paymentRefundService.refundOrder(order.getTenantId(), order.getId(), reason, actorId);
        releaseReservedInventory(order, actorId);
        transition(order, "OWNER_DECLINED", reason, actorId);
        notificationService.notifyBuyer(
                order,
                "ORDER_OWNER_DECLINED",
                "The vendor declined order " + order.getOrderNumber() + ". Refund initiated. Reason: " + reason,
                actorId);
    }

    private void releaseReservedInventory(Order order, UUID actorId) {
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getProductVariant();
            if (variant != null) {
                variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - item.getQuantity()));
                variant.setUpdatedBy(actorId);
            } else {
                Product product = item.getProduct();
                product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - item.getQuantity()));
                product.setUpdatedBy(actorId);
            }
        }
    }

    private void transition(Order order, String status, String note, UUID actorId) {
        String oldStatus = order.getStatus();
        order.setStatus(status);
        order.setUpdatedBy(actorId);

        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setOrder(order);
        history.setTenantId(order.getTenantId());
        history.setOldStatus(oldStatus);
        history.setNewStatus(status);
        history.setNote(note);
        history.setCreatedBy(actorId);
        history.persist();
    }

    private void requireOwner(Order order, User owner) {
        Business business = order.getBusiness();
        if (business == null || owner == null || business.getCreatedBy() == null
                || !business.getCreatedBy().equals(owner.getId())) {
            throw new ForbiddenException("Only the owner of this order may make the fulfillment decision");
        }
    }

    private String normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new BadRequestException("Decision is required");
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPT".equals(normalized) && !"DECLINE".equals(normalized)) {
            throw new BadRequestException("Decision must be ACCEPT or DECLINE");
        }
        return normalized;
    }

    private Order orderForUpdate(String tenantId, UUID orderId) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", orderId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        return order;
    }
}

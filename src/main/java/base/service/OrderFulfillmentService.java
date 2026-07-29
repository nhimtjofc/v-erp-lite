package base.service;

import base.entity.Business;
import base.entity.EscrowRecord;
import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.OrderItem;
import base.entity.Product;
import base.entity.ProductVariant;
import base.entity.User;
import base.entity.Wallet;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class OrderFulfillmentService {

    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final long FLAT_SHIPPING_FEE_MINOR = 250_000;
    private static final long FREE_SHIPPING_THRESHOLD_MINOR = 2_500_000;

    public static final String NO_RESPONSE_REASON = "no response within SLA";

    @Inject
    PaymentRefundService paymentRefundService;

    @Inject
    NotificationService notificationService;

    @Inject
    WalletService walletService;

    @Inject
    EscrowService escrowService;

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

    @Transactional
    public OrderItem cancelItem(String tenantId, UUID orderId, UUID itemId, String reason, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        String orderStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("PENDING_PAYMENT").contains(orderStatus)) {
            throw new BadRequestException("Cannot cancel items when order is in " + orderStatus + " status");
        }

        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelledAt() != null) {
            throw new BadRequestException("This item has already been cancelled");
        }

        boolean isPaid = List.of("PAID_ESCROW", "CONFIRMED").contains(orderStatus);
        long originalSubtotal = order.getItems().stream()
                .filter(oi -> oi.getCancelledAt() == null)
                .mapToLong(OrderItem::getSubtotalAmountMinor).sum();
        long originalFinalAmount = order.getFinalAmountMinor();

        // Cancel the item
        item.setCancelledAt(Instant.now());
        item.setCancelledBy(actorId);
        item.setCancellationReason(reason);
        item.setUpdatedBy(actorId);

        // Release inventory
        ProductVariant variant = item.getProductVariant();
        if (variant != null) {
            variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - item.getQuantity()));
            variant.setUpdatedBy(actorId);
        } else {
            Product product = item.getProduct();
            product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - item.getQuantity()));
            product.setUpdatedBy(actorId);
        }

        // Recalculate order totals
        long activeSubtotal = order.getItems().stream()
                .filter(oi -> oi.getCancelledAt() == null)
                .mapToLong(OrderItem::getSubtotalAmountMinor)
                .sum();
        boolean allCancelled = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null);
        order.setSubtotalAmountMinor(activeSubtotal);

        BigDecimal discountPct = order.getDiscountPercent() != null ? order.getDiscountPercent() : BigDecimal.ZERO;
        long discountAmount = BigDecimal.valueOf(activeSubtotal)
                .multiply(discountPct)
                .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                .longValue();
        order.setDiscountAmountMinor(discountAmount);

        long afterDiscount = Math.max(0, activeSubtotal - discountAmount);
        long shippingFee = order.getShippingFeeMinor();
        if (allCancelled) {
            shippingFee = 0;
        } else if (afterDiscount < FREE_SHIPPING_THRESHOLD_MINOR && shippingFee == 0) {
            shippingFee = FLAT_SHIPPING_FEE_MINOR;
        } else if (afterDiscount >= FREE_SHIPPING_THRESHOLD_MINOR) {
            shippingFee = 0;
        }
        order.setShippingFeeMinor(shippingFee);

        BigDecimal vatPct = order.getVatPercent() != null ? order.getVatPercent() : BigDecimal.ZERO;
        long vatAmount = BigDecimal.valueOf(afterDiscount)
                .multiply(vatPct)
                .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                .longValue();
        long taxAmount = BigDecimal.valueOf(afterDiscount)
                .multiply(BigDecimal.valueOf(0.10))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        order.setVatAmountMinor(vatAmount);
        order.setTaxAmountMinor(taxAmount);

        long newFinalAmount = allCancelled ? 0 : Math.max(0, afterDiscount + shippingFee + taxAmount);
        order.setFinalAmountMinor(newFinalAmount);

        // Handle refund
        long refundAmount = 0;
        if (isPaid) {
            refundAmount = calculateItemRefund(item.getSubtotalAmountMinor(), originalSubtotal, originalFinalAmount);
            item.setReturnRefundMinor(refundAmount);
            item.setReturnRefundCurrency(order.getCurrency() != null ? order.getCurrency() : "VND");

            if (refundAmount > 0) {
                List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, orderId);
                Wallet platformWallet = walletService.getOrCreateWallet(
                        tenantId, "PLATFORM", PLATFORM_OWNER_ID,
                        order.getCurrency() != null ? order.getCurrency() : "VND", actorId);
                for (EscrowRecord escrow : escrows) {
                    if (!"HELD".equals(escrow.getStatus())) continue;
                    long actualRefund = Math.min(refundAmount, escrow.getAmountMinor());
                    walletService.refundPending(
                            tenantId, platformWallet.getId(),
                            Money.of(actualRefund, order.getCurrency()),
                            "ORDER_ITEM", itemId, actorId,
                            "Refund for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));
                    escrow.setAmountMinor(escrow.getAmountMinor() - actualRefund);
                    if (escrow.getAmountMinor() <= 0) {
                        escrow.setStatus("REFUNDED");
                    }
                    escrow.setUpdatedBy(actorId);
                }

                boolean isWalletPayment = "BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod())
                        || "WALLET".equalsIgnoreCase(order.getPaymentMethod());
                if (isWalletPayment && order.getCustomer() != null) {
                    User customerUser = User.find("email = ?1 and tenantId = ?2",
                            order.getCustomer().getEmail(), tenantId).firstResult();
                    if (customerUser != null) {
                        Wallet customerWallet = walletService.getOrCreateWallet(
                                tenantId, "USER", customerUser.getId(), order.getCurrency(), actorId);
                        walletService.creditAvailable(
                                tenantId, customerWallet.getId(),
                                Money.of(refundAmount, order.getCurrency()),
                                "OWNER_CANCEL_REFUND", "ORDER_ITEM", itemId, actorId,
                                "Refund for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));
                    }
                }
            }
        }

        String newStatus = allCancelled ? "REFUNDED" : order.getStatus();
        order.setUpdatedBy(actorId);

        String noteText = "Item cancelled by owner: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString());
        if (reason != null && !reason.isEmpty()) {
            noteText += " - " + reason;
        }
        transition(order, newStatus, noteText, actorId);

        return item;
    }

    private long calculateItemRefund(long itemSubtotal, long orderSubtotal, long finalAmount) {
        if (orderSubtotal <= 0) {
            return itemSubtotal;
        }
        BigDecimal proportion = BigDecimal.valueOf(itemSubtotal)
                .divide(BigDecimal.valueOf(orderSubtotal), 10, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(finalAmount)
                .multiply(proportion)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
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
        if (business == null || owner == null || business.getCreatedBy() == null) {
            throw new ForbiddenException("Only the owner of this order may make the fulfillment decision");
        }
        boolean match = business.getCreatedBy().equals(owner.getId())
                || business.getCreatedBy().toString().equals(owner.getKeycloakId());
        if (!match) {
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

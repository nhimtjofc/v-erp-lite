package base.service;

import base.entity.Customer;
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
public class ReturnRefundService {

    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final long FLAT_SHIPPING_FEE_MINOR = 250_000;
    private static final long FREE_SHIPPING_THRESHOLD_MINOR = 2_500_000;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Inject
    WalletService walletService;

    @Inject
    EscrowService escrowService;

    @Inject
    PaymentRefundService paymentRefundService;

    @Inject
    NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel request flow (PAID_ESCROW → owner approval → refund from escrow)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public OrderItem requestCancelItem(String tenantId, UUID orderId, UUID itemId, String reason, UUID actorId, String actorEmail) {
        Order order = orderForUpdate(tenantId, orderId);
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!"PAID_ESCROW".equals(status)) {
            throw new BadRequestException("Cannot request cancellation when order is in " + status + " status");
        }

        Customer c = Customer.find("email = ?1 and tenantId = ?2", actorEmail, tenantId).firstResult();
        UUID customerId = c != null ? c.getId() : null;
        boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                || (actorId != null && actorId.equals(order.getCreatedBy()));
        if (!isOwner) {
            throw new ForbiddenException("You are not allowed to request cancellation for this order");
        }

        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelledAt() != null) {
            throw new BadRequestException("This item has already been cancelled");
        }
        if (item.getCancelRequestedAt() != null) {
            throw new BadRequestException("Cancel already requested for this item");
        }
        if (item.getCancelApprovedAt() != null) {
            throw new BadRequestException("Cancel already approved for this item");
        }

        item.setCancelRequestedAt(Instant.now());
        item.setCancelRequestedBy(actorId);
        item.setCancelStatus("REQUESTED");
        item.setCancellationReason(reason != null ? reason.trim() : null);
        item.setUpdatedBy(actorId);

        boolean allCancelled = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null || oi.getCancelRequestedAt() != null
                        || (oi.getId().equals(itemId)));
        if (allCancelled) {
            String oldStatus = order.getStatus();
            order.setStatus("CANCEL_REQUESTED");
            order.setUpdatedBy(actorId);
            persistHistory(order, oldStatus, "CANCEL_REQUESTED",
                    "All items requested cancellation: " + (reason != null ? reason : ""), actorId, tenantId);
        } else {
            persistHistory(order, order.getStatus(), order.getStatus(),
                    "Item cancel requested: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString())
                            + (reason != null ? " - " + reason : ""),
                    actorId, tenantId);
        }

        notificationService.notifyBusinessStaff(
                order,
                "CANCEL_REQUESTED",
                "Customer requested cancellation for item "
                        + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + "."
                        + (reason != null ? " Reason: " + reason : ""),
                actorId);

        return item;
    }

    @Transactional
    public OrderItem approveCancelItem(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelRequestedAt() == null) {
            throw new BadRequestException("No cancellation request for this item");
        }
        if (item.getCancelApprovedAt() != null) {
            throw new BadRequestException("Cancellation already approved for this item");
        }
        if (item.getCancelledAt() != null) {
            throw new BadRequestException("This item has already been cancelled");
        }

        // Mark item as cancelled
        item.setCancelledAt(Instant.now());
        item.setCancelledBy(actorId);
        item.setCancelApprovedAt(Instant.now());
        item.setCancelApprovedBy(actorId);
        item.setCancelStatus("APPROVED");
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
        long originalSubtotal = order.getItems().stream()
                .filter(oi -> oi.getCancelledAt() == null && !oi.getId().equals(itemId))
                .mapToLong(OrderItem::getSubtotalAmountMinor).sum();
        boolean allCancelled = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null);
        order.setSubtotalAmountMinor(originalSubtotal);

        BigDecimal discountPct = order.getDiscountPercent() != null ? order.getDiscountPercent() : BigDecimal.ZERO;
        long discountAmount = BigDecimal.valueOf(originalSubtotal)
                .multiply(discountPct)
                .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                .longValue();
        order.setDiscountAmountMinor(discountAmount);

        long afterDiscount = Math.max(0, originalSubtotal - discountAmount);
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

        // No refund here — money stays in escrow. Admin decides the final disposition.
        long refundAmount = calculateItemRefund(order, item);
        item.setReturnRefundMinor(refundAmount);
        item.setReturnRefundCurrency(order.getCurrency() != null ? order.getCurrency() : "VND");

        String newStatus = allCancelled ? "CANCEL_APPROVED" : order.getStatus();
        order.setUpdatedBy(actorId);

        String noteText = "Cancel request approved for item: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString())
                + ". Awaiting admin refund decision.";
        persistHistory(order, order.getStatus(), newStatus, noteText, actorId, tenantId);

        if (allCancelled) {
            order.setStatus("CANCEL_APPROVED");
        }

        notificationService.notifyBuyer(
                order,
                "CANCEL_APPROVED",
                "Cancellation for item " + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + " has been approved by the owner. "
                        + "Refund is pending admin review.",
                actorId);

        return item;
    }

    @Transactional
    public OrderItem processCancelRefund(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelApprovedAt() == null) {
            throw new BadRequestException("Cancellation has not been approved yet");
        }
        if (item.getCancelledAt() == null) {
            throw new BadRequestException("Item has not been cancelled");
        }
        if (item.getReturnRefundMinor() == null || item.getReturnRefundMinor() <= 0) {
            throw new BadRequestException("No refund amount calculated for this item");
        }
        // Prevent double-processing
        if (item.getCancelStatus() != null && "REFUNDED".equals(item.getCancelStatus())) {
            throw new BadRequestException("Refund already processed for this item");
        }

        long refundAmount = item.getReturnRefundMinor();

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
                    "Admin refund for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));
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
                        "CANCEL_REFUND", "ORDER_ITEM", itemId, actorId,
                        "Admin refund for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));
            }
        }

        item.setCancelStatus("REFUNDED");
        item.setUpdatedBy(actorId);

        boolean allHandled = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null);
        if (allHandled) {
            String oldStatus = order.getStatus();
            order.setStatus("REFUNDED");
            order.setUpdatedBy(actorId);
            persistHistory(order, oldStatus, "REFUNDED",
                    "Admin processed refund for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString()),
                    actorId, tenantId);
        } else {
            persistHistory(order, order.getStatus(), order.getStatus(),
                    "Admin processed refund for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString()),
                    actorId, tenantId);
        }

        notificationService.notifyBuyer(
                order,
                "CANCEL_REFUNDED",
                "Refund of " + refundAmount + " for cancelled item "
                        + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + " has been processed by admin.",
                actorId);

        return item;
    }

    @Transactional
    public OrderItem releaseCancelToSeller(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelApprovedAt() == null) {
            throw new BadRequestException("Cancellation has not been approved yet");
        }
        if (item.getCancelledAt() == null) {
            throw new BadRequestException("Item has not been cancelled");
        }
        if (item.getCancelStatus() != null && "RELEASED".equals(item.getCancelStatus())) {
            throw new BadRequestException("Funds already released to seller for this item");
        }

        long releaseAmount = item.getReturnRefundMinor() != null ? item.getReturnRefundMinor() : 0;

        if (releaseAmount > 0) {
            List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, orderId);
            for (EscrowRecord escrow : escrows) {
                if (!"HELD".equals(escrow.getStatus())) continue;
                long actualRelease = Math.min(releaseAmount, escrow.getAmountMinor());
                Wallet platformWallet = walletService.getOrCreateWallet(
                        tenantId, "PLATFORM", PLATFORM_OWNER_ID,
                        order.getCurrency() != null ? order.getCurrency() : "VND", actorId);

                walletService.releasePendingToAvailable(
                        tenantId, platformWallet.getId(),
                        Money.of(actualRelease, order.getCurrency()),
                        "ESCROW", escrow.getId(), actorId,
                        "Admin release to seller for cancelled item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));

                walletService.debitAvailable(
                        tenantId, platformWallet.getId(),
                        Money.of(actualRelease, order.getCurrency()),
                        "ESCROW_RELEASE_SELLER", "ESCROW", escrow.getId(), actorId,
                        "Transfer to seller for cancelled item");

                Wallet sellerWallet = escrow.getSellerWallet();
                if (sellerWallet != null) {
                    walletService.creditAvailable(
                            tenantId, sellerWallet.getId(),
                            Money.of(actualRelease, order.getCurrency()),
                            "ESCROW_SELLER_CREDIT", "ESCROW", escrow.getId(), actorId,
                            "Payment for cancelled item released by admin");
                }

                escrow.setStatus("RELEASED");
                escrow.setUpdatedBy(actorId);
            }
        }

        item.setCancelStatus("RELEASED");
        item.setUpdatedBy(actorId);

        persistHistory(order, order.getStatus(), order.getStatus(),
                "Admin released cancelled item funds to seller: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString()),
                actorId, tenantId);

        return item;
    }

    @Transactional
    public OrderItem processReturnRefund(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getReturnApprovedAt() == null) {
            throw new BadRequestException("Return has not been approved yet");
        }
        if (item.getReturnRefundMinor() == null || item.getReturnRefundMinor() <= 0) {
            throw new BadRequestException("No refund amount calculated for this item");
        }
        if (item.getReturnStatus() != null && "REFUNDED".equals(item.getReturnStatus())) {
            throw new BadRequestException("Refund already processed for this item");
        }

        long refundAmount = item.getReturnRefundMinor();

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
                    "Admin refund for returned item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));
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
                        "RETURN_REFUND", "ORDER_ITEM", itemId, actorId,
                        "Admin refund for returned item: " + (item.getProduct() != null ? item.getProduct().getName() : ""));
            }
        }

        item.setReturnStatus("REFUNDED");
        item.setUpdatedBy(actorId);

        boolean allHandled = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null || "REFUNDED".equals(oi.getReturnStatus()));
        if (allHandled) {
            String oldStatus = order.getStatus();
            order.setStatus("REFUNDED");
            order.setUpdatedBy(actorId);
            persistHistory(order, oldStatus, "REFUNDED",
                    "Admin processed refund for returned item: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString()),
                    actorId, tenantId);
        } else {
            persistHistory(order, order.getStatus(), order.getStatus(),
                    "Admin processed refund for returned item: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString()),
                    actorId, tenantId);
        }

        notificationService.notifyBuyer(
                order,
                "RETURN_REFUNDED",
                "Refund of " + refundAmount + " for returned item "
                        + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + " has been processed by admin.",
                actorId);

        return item;
    }

    @Transactional
    public OrderItem rejectCancelItem(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelRequestedAt() == null) {
            throw new BadRequestException("No cancellation request for this item");
        }
        if (item.getCancelApprovedAt() != null) {
            throw new BadRequestException("Cancellation already approved for this item");
        }

        item.setCancelStatus("REJECTED");
        item.setUpdatedBy(actorId);

        String noteText = "Cancel request rejected for item: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString());
        persistHistory(order, order.getStatus(), order.getStatus(), noteText, actorId, tenantId);

        notificationService.notifyBuyer(
                order,
                "CANCEL_REJECTED",
                "Cancellation for item " + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + " has been rejected.",
                actorId);

        return item;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Return flow
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public OrderItem requestItemReturn(String tenantId, UUID orderId, UUID itemId, String reason, UUID actorId, String actorEmail) {
        Order order = orderForUpdate(tenantId, orderId);
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("DELIVERED", "PENDING_COMPLETION").contains(status)) {
            throw new BadRequestException("Cannot request return when order is in " + status + " status");
        }

        Customer c = Customer.find("email = ?1 and tenantId = ?2", actorEmail, tenantId).firstResult();
        UUID customerId = c != null ? c.getId() : null;
        boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                || (actorId != null && actorId.equals(order.getCreatedBy()));
        if (!isOwner) {
            throw new ForbiddenException("You are not allowed to request return for this order");
        }

        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelledAt() != null) {
            throw new BadRequestException("Cannot request return for a cancelled item");
        }
        if (item.getReturnRequestedAt() != null) {
            throw new BadRequestException("Return already requested for this item");
        }
        if (item.getReturnApprovedAt() != null) {
            throw new BadRequestException("Return already approved for this item");
        }

        item.setReturnRequestedAt(Instant.now());
        item.setReturnRequestedBy(actorId);
        item.setReturnReason(reason != null ? reason.trim() : null);
        item.setReturnStatus("REQUESTED");
        item.setUpdatedBy(actorId);

        boolean allReturned = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null || oi.getReturnApprovedAt() != null
                        || (oi.getId().equals(itemId)));
        if (allReturned) {
            String oldStatus = order.getStatus();
            order.setStatus("RETURN_REQUESTED");
            order.setUpdatedBy(actorId);
            persistHistory(order, oldStatus, "RETURN_REQUESTED",
                    "All items requested return: " + (reason != null ? reason : ""), actorId, tenantId);
        } else {
            persistHistory(order, order.getStatus(), order.getStatus(),
                    "Item return requested: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString())
                            + (reason != null ? " - " + reason : ""),
                    actorId, tenantId);
        }

        notificationService.notifyBusinessStaff(
                order,
                "RETURN_REQUESTED",
                "Customer requested return for item " + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + "."
                        + (reason != null ? " Reason: " + reason : ""),
                actorId);

        return item;
    }

    @Transactional
    public OrderItem approveItemReturn(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getReturnRequestedAt() == null) {
            throw new BadRequestException("No return request for this item");
        }
        if (item.getReturnApprovedAt() != null) {
            throw new BadRequestException("Return already approved for this item");
        }

        item.setReturnApprovedAt(Instant.now());
        item.setReturnApprovedBy(actorId);
        item.setReturnStatus("APPROVED");
        item.setUpdatedBy(actorId);

        long refundAmount = calculateItemRefund(order, item);
        item.setReturnRefundMinor(refundAmount);
        item.setReturnRefundCurrency(order.getCurrency() != null ? order.getCurrency() : "VND");

        recalculateOrderAfterReturn(order, actorId, tenantId);

        boolean allItemsHandled = order.getItems().stream()
                .allMatch(oi -> oi.getCancelledAt() != null || oi.getReturnApprovedAt() != null);
        if (allItemsHandled) {
            String oldStatus = order.getStatus();
            order.setStatus("RETURN_APPROVED");
            order.setUpdatedBy(actorId);
            persistHistory(order, oldStatus, "RETURN_APPROVED",
                    "All items return approved, awaiting admin refund", actorId, tenantId);
        } else {
            String orderStatus = order.getStatus();
            persistHistory(order, orderStatus, orderStatus,
                    "Item return approved: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString())
                            + " - Refund pending admin review",
                    actorId, tenantId);
        }

        notificationService.notifyBuyer(
                order,
                "RETURN_APPROVED",
                "Return for item " + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + " has been approved. "
                        + "Refund is pending admin review.",
                actorId);

        return item;
    }

    @Transactional
    public OrderItem rejectItemReturn(String tenantId, UUID orderId, UUID itemId, UUID actorId) {
        Order order = orderForUpdate(tenantId, orderId);
        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantId).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getReturnRequestedAt() == null) {
            throw new BadRequestException("No return request for this item");
        }
        if (item.getReturnApprovedAt() != null) {
            throw new BadRequestException("Return already approved for this item");
        }

        item.setReturnStatus("REJECTED");
        item.setUpdatedBy(actorId);

        String noteText = "Item return rejected: " + (item.getProduct() != null ? item.getProduct().getName() : itemId.toString());
        persistHistory(order, order.getStatus(), order.getStatus(), noteText, actorId, tenantId);

        notificationService.notifyBuyer(
                order,
                "RETURN_REJECTED",
                "Return for item " + (item.getProduct() != null ? item.getProduct().getName() : "")
                        + " in order " + order.getOrderNumber() + " has been rejected.",
                actorId);

        return item;
    }

    private long calculateItemRefund(Order order, OrderItem item) {
        long itemSubtotal = item.getSubtotalAmountMinor();
        long orderSubtotal = order.getSubtotalAmountMinor();
        if (orderSubtotal <= 0) {
            return itemSubtotal;
        }
        long finalAmount = order.getFinalAmountMinor();
        BigDecimal proportion = BigDecimal.valueOf(itemSubtotal)
                .divide(BigDecimal.valueOf(orderSubtotal), 10, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(finalAmount)
                .multiply(proportion)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private void recalculateOrderAfterReturn(Order order, UUID actorId, String tenantId) {
        long activeSubtotal = 0;
        for (OrderItem oi : order.getItems()) {
            if (oi.getCancelledAt() == null && oi.getReturnApprovedAt() == null) {
                activeSubtotal += oi.getSubtotalAmountMinor();
            }
        }
        order.setSubtotalAmountMinor(activeSubtotal);

        BigDecimal discountPct = order.getDiscountPercent() != null ? order.getDiscountPercent() : BigDecimal.ZERO;
        long discountAmount = BigDecimal.valueOf(activeSubtotal)
                .multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
        order.setDiscountAmountMinor(discountAmount);

        long afterDiscount = Math.max(0, activeSubtotal - discountAmount);
        long shippingFee = afterDiscount >= FREE_SHIPPING_THRESHOLD_MINOR ? 0 : FLAT_SHIPPING_FEE_MINOR;
        order.setShippingFeeMinor(shippingFee);

        BigDecimal vatPct = order.getVatPercent() != null ? order.getVatPercent() : BigDecimal.ZERO;
        long vatAmount = BigDecimal.valueOf(afterDiscount)
                .multiply(vatPct)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
        long taxAmount = BigDecimal.valueOf(afterDiscount)
                .multiply(BigDecimal.valueOf(0.10))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        order.setVatAmountMinor(vatAmount);
        order.setTaxAmountMinor(taxAmount);

        long finalAmount = Math.max(0, afterDiscount + shippingFee + taxAmount);
        order.setFinalAmountMinor(finalAmount);
        order.setUpdatedBy(actorId);
    }

    private void persistHistory(Order order, String oldStatus, String newStatus, String note, UUID userId, String tenantCode) {
        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setOrder(order);
        history.setTenantId(tenantCode);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setNote(note);
        history.setCreatedBy(userId);
        history.persist();
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

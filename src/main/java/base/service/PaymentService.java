package base.service;

import base.entity.Business;
import base.entity.EscrowRecord;
import base.entity.Order;
import base.entity.OrderItem;
import base.entity.OrderHistory;
import base.entity.PaymentIntent;
import base.entity.Wallet;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PaymentService {

    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final int PLATFORM_COMMISSION_PERCENT = 5;
    @Inject
    WalletService walletService;

    @Inject
    MoneyService moneyService;

    @Inject
    EscrowService escrowService;

    @Inject
    NotificationService notificationService;

    @ConfigProperty(name = "orders.owner-decision-sla", defaultValue = "PT24H")
    Duration ownerDecisionSla;

    @ConfigProperty(name = "orders.escrow-hold-days", defaultValue = "7")
    int escrowHoldDays;

    @Transactional
    public List<EscrowRecord> settleSucceededIntent(PaymentIntent intent, UUID actorId) {
        if (intent == null) {
            throw new BadRequestException("Payment intent is required");
        }
        if (!"SUCCEEDED".equals(intent.getStatus())) {
            throw new BadRequestException("Only succeeded payment intents can be settled");
        }

        Order order = Order.find(
                "id = ?1 and tenantId = ?2 and deletedAt IS NULL",
                intent.getOrderId(),
                intent.getTenantId()
        ).withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(intent.getCustomerId())) {
            throw new BadRequestException("Payment customer does not match order customer");
        }
        if (!List.of("PENDING", "PENDING_PAYMENT", "PAID_ESCROW").contains(order.getStatus())) {
            throw new BadRequestException("Only an order awaiting payment can be settled");
        }

        List<EscrowRecord> existing = escrowService.listByOrder(intent.getTenantId(), intent.getOrderId());
        if (!existing.isEmpty()) {
            markPaidEscrow(order, actorId);
            return existing;
        }

        moneyService.requireSameCurrency(order.getCurrency(), intent.getCurrency());
        long expectedAmount = order.getFinalAmountMinor();

        if (expectedAmount != intent.getAmountMinor()) {
            throw new BadRequestException("Payment amount does not match order total");
        }

        List<SellerAllocation> allocations = allocateBySeller(order, intent.getAmountMinor());
        long platformCommissionTotal = 0;
        List<EscrowRecord> records = new ArrayList<>();
        Instant escrowHoldUntil = Instant.now().plus(Duration.ofDays(escrowHoldDays));

        for (SellerAllocation allocation : allocations) {
            long commission = commissionFor(allocation.amountMinor());
            long sellerNet = allocation.amountMinor() - commission;
            platformCommissionTotal += commission;
            if (sellerNet > 0) {
                records.add(escrowService.holdSellerEscrow(
                        intent.getTenantId(),
                        intent.getOrderId(),
                        allocation.sellerOwnerId(),
                        Money.of(sellerNet, intent.getCurrency()),
                        escrowHoldUntil,
                        actorId
                ));
            }
        }

        if (platformCommissionTotal > 0) {
            Wallet platformWallet = walletService.getOrCreateWallet(
                    intent.getTenantId(),
                    "PLATFORM",
                    PLATFORM_OWNER_ID,
                    intent.getCurrency(),
                    actorId
            );
            walletService.creditAvailable(
                    intent.getTenantId(),
                    platformWallet.getId(),
                    Money.of(platformCommissionTotal, intent.getCurrency()),
                    "COMMISSION",
                    "ORDER",
                    intent.getOrderId(),
                    actorId,
                    "Platform commission from order"
            );
        }

        markPaidEscrow(order, actorId);
        return records;
    }

    @Transactional
    public List<EscrowRecord> processWalletPayment(Order order, Wallet userWallet, UUID actorId) {
        if (!List.of("PENDING", "PENDING_PAYMENT", "PAID_ESCROW").contains(order.getStatus())) {
            throw new BadRequestException("Only an order awaiting payment can be settled");
        }

        List<EscrowRecord> existing = escrowService.listByOrder(order.getTenantId(), order.getId());
        if (!existing.isEmpty()) {
            markPaidEscrow(order, actorId);
            return existing;
        }

        moneyService.requireSameCurrency(order.getCurrency(), userWallet.getCurrency());
        long expectedAmount = order.getFinalAmountMinor();

        // Deduct from user wallet
        walletService.debitAvailable(
                userWallet.getTenantId(),
                userWallet.getId(),
                Money.of(expectedAmount, userWallet.getCurrency()),
                "PAYMENT",
                "ORDER",
                order.getId(),
                actorId,
                "Thanh toán đơn hàng " + order.getOrderNumber()
        );

        List<SellerAllocation> allocations = allocateBySeller(order, expectedAmount);
        long platformCommissionTotal = 0;
        List<EscrowRecord> records = new ArrayList<>();
        Instant escrowHoldUntil = Instant.now().plus(Duration.ofDays(escrowHoldDays));

        for (SellerAllocation allocation : allocations) {
            long commission = commissionFor(allocation.amountMinor());
            long sellerNet = allocation.amountMinor() - commission;
            platformCommissionTotal += commission;
            if (sellerNet > 0) {
                records.add(escrowService.holdSellerEscrow(
                        order.getTenantId(),
                        order.getId(),
                        allocation.sellerOwnerId(),
                        Money.of(sellerNet, userWallet.getCurrency()),
                        escrowHoldUntil,
                        actorId
                ));
            }
        }

        if (platformCommissionTotal > 0) {
            Wallet platformWallet = walletService.getOrCreateWallet(
                    order.getTenantId(),
                    "PLATFORM",
                    PLATFORM_OWNER_ID,
                    userWallet.getCurrency(),
                    actorId
            );
            walletService.creditAvailable(
                    order.getTenantId(),
                    platformWallet.getId(),
                    Money.of(platformCommissionTotal, userWallet.getCurrency()),
                    "COMMISSION",
                    "ORDER",
                    order.getId(),
                    actorId,
                    "Platform commission from order"
            );
        }

        markPaidEscrow(order, actorId);
        return records;
    }

    private void markPaidEscrow(Order order, UUID actorId) {
        if ("PAID_ESCROW".equals(order.getStatus())) {
            return;
        }
        String oldStatus = order.getStatus();
        order.setStatus("PAID_ESCROW");
        order.setOwnerDecisionDeadline(Instant.now().plus(ownerDecisionSla));
        order.setUpdatedBy(actorId);

        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setOrder(order);
        history.setTenantId(order.getTenantId());
        history.setOldStatus(oldStatus);
        history.setNewStatus("PAID_ESCROW");
        history.setNote("Payment received and held in escrow; waiting for owner decision");
        history.setCreatedBy(actorId);
        history.persist();

        notificationService.notifyBuyer(
                order,
                "ORDER_PAID",
                "Payment was confirmed for order " + order.getOrderNumber() + ".",
                actorId);
        notificationService.notifyBusinessStaff(
                order,
                "ORDER_PAID",
                "Payment was received for order " + order.getOrderNumber() + ".",
                actorId);
    }

    private List<SellerAllocation> allocateBySeller(Order order, long totalAmountMinor) {
        Map<UUID, Long> subtotalBySeller = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Business seller = item.getProduct() == null ? null : item.getProduct().getBusiness();
            if (seller == null) {
                seller = order.getBusiness();
            }
            if (seller == null) {
                throw new BadRequestException("Order is missing a seller for fund allocation");
            }
            subtotalBySeller.merge(seller.getId(), item.getSubtotalAmountMinor(), Long::sum);
        }
        if (subtotalBySeller.isEmpty()) {
            throw new BadRequestException("Order has no items for fund allocation");
        }

        // Avoid any boxed->primitive lambda nullability issues by using explicit loop
        long subtotal = 0;
        for (Long v : subtotalBySeller.values()) {
            if (v == null) {
                throw new BadRequestException("Invalid order subtotal while allocating by seller");
            }
            subtotal += v;
        }
        if (subtotal <= 0) {
            throw new BadRequestException("Order subtotal must be greater than zero");
        }

        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(subtotalBySeller.entrySet());
        List<SellerAllocation> allocations = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, Long> entry = entries.get(i);
            long amount;
            if (i == entries.size() - 1) {
                amount = totalAmountMinor - allocated;
            } else {
                amount = BigDecimal.valueOf(totalAmountMinor)
                        .multiply(BigDecimal.valueOf(entry.getValue()))
                        .divide(BigDecimal.valueOf(subtotal), 0, RoundingMode.HALF_UP)
                        .longValue();
                allocated += amount;
            }
            allocations.add(new SellerAllocation(entry.getKey(), amount));
        }
        return allocations;
    }

    private long commissionFor(long amountMinor) {
        return BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(PLATFORM_COMMISSION_PERCENT))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private record SellerAllocation(UUID sellerOwnerId, long amountMinor) {}
}

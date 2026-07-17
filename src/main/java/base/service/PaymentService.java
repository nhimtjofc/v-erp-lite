package base.service;

import base.entity.Business;
import base.entity.EscrowRecord;
import base.entity.Order;
import base.entity.OrderItem;
import base.entity.PaymentIntent;
import base.entity.Wallet;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PaymentService {

    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final int PLATFORM_COMMISSION_PERCENT = 5;
    private static final int DEFAULT_ESCROW_RELEASE_DAYS = 7;

    @Inject
    WalletService walletService;

    @Inject
    EscrowService escrowService;

    @Transactional
    public List<EscrowRecord> settleSucceededIntent(PaymentIntent intent, UUID actorId) {
        if (intent == null) {
            throw new BadRequestException("Payment intent is required");
        }
        if (!"SUCCEEDED".equals(intent.getStatus())) {
            throw new BadRequestException("Only succeeded payment intents can be settled");
        }

        List<EscrowRecord> existing = escrowService.listByOrder(intent.getTenantId(), intent.getOrderId());
        if (!existing.isEmpty()) {
            return existing;
        }

        Order order = Order.find(
                "id = ?1 and tenantId = ?2 and deletedAt IS NULL",
                intent.getOrderId(),
                intent.getTenantId()
        ).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(intent.getCustomerId())) {
            throw new BadRequestException("Payment customer does not match order customer");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BadRequestException("Only PENDING orders can be settled");
        }

        Money finalAmount = Money.of(order.getFinalAmountMinor(), order.getCurrency());
        if (finalAmount.getAmountMinor() != intent.getAmountMinor()) {
            throw new BadRequestException("Payment amount does not match order total");
        }

        List<SellerAllocation> allocations = allocateBySeller(order, intent.getAmountMinor());
        long platformCommissionTotal = 0;
        List<EscrowRecord> records = new ArrayList<>();
        Instant releaseDueAt = Instant.now().plus(DEFAULT_ESCROW_RELEASE_DAYS, ChronoUnit.DAYS);

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
                        releaseDueAt,
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

        // Payment and fulfillment are separate lifecycles. A successful payment
        // leaves the order PENDING until staff explicitly confirms it.
        return records;
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

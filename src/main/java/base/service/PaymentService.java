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
            throw new BadRequestException("Vui lòng nhập ý định thanh toán");
        }
        if (!"SUCCEEDED".equals(intent.getStatus())) {
            throw new BadRequestException("Chỉ xử lý chia tiền cho thanh toán đã thành công");
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
            throw new NotFoundException("Không tìm thấy đơn hàng");
        }
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(intent.getCustomerId())) {
            throw new BadRequestException("Khách hàng của thanh toán không khớp đơn hàng");
        }

        Money finalAmount = Money.fromDecimal(order.getFinalAmount(), intent.getCurrency());
        if (finalAmount.getAmountMinor() != intent.getAmountMinor()) {
            throw new BadRequestException("Số tiền thanh toán không khớp đơn hàng");
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
                    "Hoa hồng nền tảng từ đơn hàng"
            );
        }

        return records;
    }

    private List<SellerAllocation> allocateBySeller(Order order, long totalAmountMinor) {
        Map<UUID, BigDecimal> subtotalBySeller = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Business seller = item.getProduct() == null ? null : item.getProduct().getBusiness();
            if (seller == null) {
                seller = order.getBusiness();
            }
            if (seller == null) {
                throw new BadRequestException("Đơn hàng thiếu người bán để chia tiền");
            }
            subtotalBySeller.merge(seller.getId(), item.getSubtotalAmount(), BigDecimal::add);
        }
        if (subtotalBySeller.isEmpty()) {
            throw new BadRequestException("Đơn hàng chưa có sản phẩm để chia tiền");
        }

        BigDecimal subtotal = subtotalBySeller.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Tổng tiền sản phẩm phải lớn hơn 0");
        }

        List<Map.Entry<UUID, BigDecimal>> entries = new ArrayList<>(subtotalBySeller.entrySet());
        List<SellerAllocation> allocations = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, BigDecimal> entry = entries.get(i);
            long amount;
            if (i == entries.size() - 1) {
                amount = totalAmountMinor - allocated;
            } else {
                amount = BigDecimal.valueOf(totalAmountMinor)
                        .multiply(entry.getValue())
                        .divide(subtotal, 0, RoundingMode.HALF_UP)
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

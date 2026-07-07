package base.service;

import base.entity.EscrowRecord;
import base.entity.Order;
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
import java.util.List;
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

        long commission = BigDecimal.valueOf(intent.getAmountMinor())
                .multiply(BigDecimal.valueOf(PLATFORM_COMMISSION_PERCENT))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();

        long sellerNet = intent.getAmountMinor() - commission;
        Instant releaseDueAt = Instant.now().plus(DEFAULT_ESCROW_RELEASE_DAYS, ChronoUnit.DAYS);

        if (sellerNet > 0) {
            Wallet sellerWallet = walletService.getOrCreateWallet(
                    intent.getTenantId(), "PLATFORM",
                    PLATFORM_OWNER_ID, intent.getCurrency(), actorId
            );
            escrowService.holdSellerEscrow(
                    intent.getTenantId(),
                    intent.getOrderId(),
                    sellerWallet.getId(),
                    Money.of(sellerNet, intent.getCurrency()),
                    releaseDueAt,
                    actorId
            );
        }

        if (commission > 0) {
            walletService.creditAvailable(
                    intent.getTenantId(),
                    walletService.getOrCreateWallet(
                            intent.getTenantId(), "PLATFORM",
                            PLATFORM_OWNER_ID, intent.getCurrency(), actorId
                    ).getId(),
                    Money.of(commission, intent.getCurrency()),
                    "COMMISSION", "ORDER", intent.getOrderId(),
                    actorId, "Hoa hồng nền tảng từ đơn hàng"
            );
        }

        return escrowService.listByOrder(intent.getTenantId(), intent.getOrderId());
    }
}

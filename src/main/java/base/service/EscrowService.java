package base.service;

import base.entity.EscrowRecord;
import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.Wallet;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EscrowService {

    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final int PLATFORM_COMMISSION_PERCENT = 5;

    @Inject
    WalletService walletService;

    @Inject
    MoneyService moneyService;

    @Transactional
    public EscrowRecord holdSellerEscrow(String tenantId, UUID orderId, UUID sellerOwnerId, Money amount,
            Instant releaseDueAt, UUID actorId) {
        if (orderId == null) {
            throw new BadRequestException("Vui lòng nhập mã đơn hàng");
        }
        if (sellerOwnerId == null) {
            throw new BadRequestException("Vui lòng nhập mã người bán");
        }
        moneyService.requirePositive(amount);

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", sellerOwnerId, amount.getCurrency(),
                actorId);
        EscrowRecord existing = EscrowRecord.find(
                "tenantId = ?1 and orderId = ?2 and sellerWallet.id = ?3",
                tenantId,
                orderId,
                sellerWallet.getId()).firstResult();
        if (existing != null) {
            return existing;
        }

        Wallet platformWallet = walletService.getOrCreateWallet(
                tenantId, "PLATFORM", PLATFORM_OWNER_ID, amount.getCurrency(), actorId);
        UUID escrowId = UUID.randomUUID();
        walletService.creditPending(
                tenantId, platformWallet.getId(), amount,
                "ESCROW_HOLD", "ESCROW", escrowId,
                actorId, "Tạm giữ tiền đơn hàng (platform)");

        EscrowRecord escrow = new EscrowRecord();
        escrow.setId(escrowId);
        escrow.setTenantId(tenantId);
        escrow.setOrderId(orderId);
        escrow.setSellerWallet(sellerWallet);
        escrow.setAmountMinor(amount.getAmountMinor());
        escrow.setCurrency(amount.getCurrency());
        escrow.setStatus("HELD");
        escrow.setReleaseDueAt(releaseDueAt == null ? Instant.now() : releaseDueAt);
        escrow.setCreatedBy(actorId);
        escrow.persist();
        return escrow;
    }

    public List<EscrowRecord> listByOrder(String tenantId, UUID orderId) {
        return EscrowRecord.list("tenantId = ?1 and orderId = ?2 order by createdAt asc", tenantId, orderId);
    }

    public List<EscrowRecord> listByStatus(String tenantId, String status) {
        return EscrowRecord.list("tenantId = ?1 and status = ?2", tenantId, status.trim().toUpperCase());
    }

    public long sumHeldForSeller(String tenantId, UUID sellerWalletId) {
        Long total = EscrowRecord.getEntityManager()
                .createQuery("select COALESCE(SUM(e.amountMinor), 0) from EscrowRecord e where e.tenantId = :tenantId and e.sellerWallet.id = :sellerWalletId and e.status = 'HELD'", Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("sellerWalletId", sellerWalletId)
                .getSingleResult();
        return total;
    }

    @Transactional
    public List<EscrowRecord> releaseOrderEscrow(String tenantId, UUID orderId, UUID actorId) {
        List<EscrowRecord> records = heldRecords(tenantId, orderId);
        if (records.isEmpty()) return records;

        Wallet platformWallet = walletService.getOrCreateWallet(
                tenantId, "PLATFORM", PLATFORM_OWNER_ID, records.get(0).getCurrency(), actorId);

        for (EscrowRecord record : records) {
            Wallet sellerWallet = record.getSellerWallet();
            Money amount = Money.of(record.getAmountMinor(), record.getCurrency());

            walletService.releasePendingToAvailable(
                    tenantId, platformWallet.getId(), amount,
                    "ESCROW", record.getId(), actorId,
                    "Giải ngân tiền tạm giữ đơn hàng từ ví platform");

            long commission = commissionFor(record.getAmountMinor());
            long sellerNet = record.getAmountMinor() - commission;

            if (sellerNet > 0) {
                walletService.debitAvailable(
                        tenantId, platformWallet.getId(), Money.of(sellerNet, record.getCurrency()),
                        "ESCROW_RELEASE_SELLER", "ESCROW", record.getId(), actorId,
                        "Chuyển tiền bán hàng cho người bán");

                walletService.creditAvailable(
                        tenantId, sellerWallet.getId(), Money.of(sellerNet, record.getCurrency()),
                        "ESCROW_SELLER_CREDIT", "ESCROW", record.getId(), actorId,
                        "Nhận tiền từ đơn hàng đã giải ngân");
            }

            record.setStatus("RELEASED");
            record.setUpdatedBy(actorId);
        }

        // Auto-complete order when escrow is released (after 7-day hold period)
        if (!records.isEmpty()) {
            Order order = Order.find("id = ?1 and tenantId = ?2", orderId, tenantId).firstResult();
            if (order != null && "PENDING_COMPLETION".equals(order.getStatus())) {
                String oldStatus = order.getStatus();
                order.setStatus("COMPLETED");
                order.setUpdatedBy(actorId);
                order.persist();

                OrderHistory history = new OrderHistory();
                history.setId(UUID.randomUUID());
                history.setOrder(order);
                history.setTenantId(tenantId);
                history.setOldStatus(oldStatus);
                history.setNewStatus("COMPLETED");
                history.setNote("Auto-completed after escrow hold period");
                history.setCreatedBy(actorId);
                history.persist();
            }
        }

        return records;
    }

    @Transactional
    public List<EscrowRecord> refundOrderEscrow(String tenantId, UUID orderId, UUID actorId) {
        List<EscrowRecord> records = heldRecords(tenantId, orderId);
        if (records.isEmpty()) return records;

        Wallet platformWallet = walletService.getOrCreateWallet(
                tenantId, "PLATFORM", PLATFORM_OWNER_ID, records.get(0).getCurrency(), actorId);

        for (EscrowRecord record : records) {
            walletService.refundPending(
                    tenantId, platformWallet.getId(),
                    Money.of(record.getAmountMinor(), record.getCurrency()),
                    "ESCROW", record.getId(), actorId,
                    "Hoàn tiền tạm giữ đơn hàng từ ví platform");
            record.setStatus("REFUNDED");
            record.setUpdatedBy(actorId);
        }
        return records;
    }

    @Transactional
    public EscrowRecord refundEscrow(String tenantId, UUID escrowId, UUID actorId) {
        EscrowRecord escrow = EscrowRecord.find("id = ?1 and tenantId = ?2", escrowId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (escrow == null) {
            throw new NotFoundException("Không tìm thấy bản ghi ký quỹ");
        }
        if (!"HELD".equals(escrow.getStatus())) {
            throw new BadRequestException("Chỉ hoàn tiền ký quỹ đang giữ");
        }

        Wallet platformWallet = walletService.getOrCreateWallet(
                tenantId, "PLATFORM", PLATFORM_OWNER_ID, escrow.getCurrency(), actorId);
        Money amount = Money.of(escrow.getAmountMinor(), escrow.getCurrency());

        walletService.refundPending(
                tenantId, platformWallet.getId(), amount,
                "ESCROW", escrow.getId(), actorId,
                "Hoàn tiền ký quỹ từ ví platform"
        );

        escrow.setStatus("REFUNDED");
        escrow.setUpdatedBy(actorId);
        return escrow;
    }

    private List<EscrowRecord> heldRecords(String tenantId, UUID orderId) {
        if (orderId == null) {
            throw new BadRequestException("Vui lòng nhập mã đơn hàng");
        }
        return EscrowRecord.list(
                "tenantId = ?1 and orderId = ?2 and status = ?3 order by createdAt asc",
                tenantId,
                orderId,
                "HELD");
    }

    private long commissionFor(long amountMinor) {
        return java.math.BigDecimal.valueOf(amountMinor)
                .multiply(java.math.BigDecimal.valueOf(PLATFORM_COMMISSION_PERCENT))
                .divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
    }

    @Transactional
    public int forceReleaseAllHeldEscrows(UUID actorId) {
        List<Object[]> eligibleOrders = EscrowRecord.getEntityManager().createQuery(
                "select distinct e.orderId, e.tenantId from EscrowRecord e where e.status = 'HELD'",
                Object[].class)
                .getResultList();

        int successCount = 0;
        for (Object[] row : eligibleOrders) {
            UUID orderId = (UUID) row[0];
            String tenantId = (String) row[1];
            try {
                releaseOrderEscrow(tenantId, orderId, actorId);
                successCount++;
            } catch (Exception e) {
                // skip failures silently
            }
        }
        return successCount;
    }
}

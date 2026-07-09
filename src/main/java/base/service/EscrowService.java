package base.service;

import base.entity.EscrowRecord;
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

        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", sellerOwnerId, amount.getCurrency(),
                actorId);
        EscrowRecord existing = EscrowRecord.find(
                "tenantId = ?1 and orderId = ?2 and sellerWallet.id = ?3",
                tenantId,
                orderId,
                wallet.getId()).firstResult();
        if (existing != null) {
            return existing;
        }

        walletService.holdEscrowForSeller(
                tenantId,
                sellerOwnerId,
                amount,
                "ORDER",
                orderId,
                actorId,
                "Tạm giữ tiền đơn hàng");

        EscrowRecord escrow = new EscrowRecord();
        escrow.setId(UUID.randomUUID());
        escrow.setTenantId(tenantId);
        escrow.setOrderId(orderId);
        escrow.setSellerWallet(wallet);
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

    @Transactional
    public List<EscrowRecord> releaseOrderEscrow(String tenantId, UUID orderId, UUID actorId) {
        List<EscrowRecord> records = heldRecords(tenantId, orderId);
        for (EscrowRecord record : records) {
            walletService.releasePendingToAvailable(
                    tenantId,
                    record.getSellerWallet().getId(),
                    Money.of(record.getAmountMinor(), record.getCurrency()),
                    "ORDER",
                    orderId,
                    actorId,
                    "Giải ngân tiền đơn hàng");
            record.setStatus("RELEASED");
            record.setUpdatedBy(actorId);
        }
        return records;
    }

    @Transactional
    public List<EscrowRecord> refundOrderEscrow(String tenantId, UUID orderId, UUID actorId) {
        List<EscrowRecord> records = heldRecords(tenantId, orderId);
        for (EscrowRecord record : records) {
            walletService.refundPending(
                    tenantId,
                    record.getSellerWallet().getId(),
                    Money.of(record.getAmountMinor(), record.getCurrency()),
                    "ORDER",
                    orderId,
                    actorId,
                    "Hoàn tiền tạm giữ đơn hàng");
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

        Wallet sellerWallet = escrow.getSellerWallet();
        Money amount = Money.of(escrow.getAmountMinor(), escrow.getCurrency());

        walletService.refundPending(
                escrow.getTenantId(), sellerWallet.getId(), amount,
                "ORDER", escrow.getOrderId(),
                actorId, "Hoàn tiền ký quỹ"
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
}

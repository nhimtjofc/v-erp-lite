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

    public List<EscrowRecord> listByOrder(String tenantId, UUID orderId) {
        return EscrowRecord.list("tenantId = ?1 and orderId = ?2", tenantId, orderId);
    }

    public List<EscrowRecord> listByStatus(String tenantId, String status) {
        return EscrowRecord.list("tenantId = ?1 and status = ?2", tenantId, status.trim().toUpperCase());
    }

    @Transactional
    public EscrowRecord holdSellerEscrow(String tenantId, UUID orderId, UUID sellerWalletId,
                                          Money amount, Instant releaseDueAt, UUID actorId) {
        Wallet wallet = walletService.getWallet(tenantId, sellerWalletId);
        UUID escrowId = UUID.randomUUID();

        walletService.creditPending(
                tenantId, sellerWalletId, amount,
                "ESCROW_HOLD", "ORDER", orderId,
                actorId, "Giữ tiền ký quỹ từ đơn hàng"
        );

        EscrowRecord record = new EscrowRecord();
        record.setId(escrowId);
        record.setTenantId(tenantId);
        record.setOrderId(orderId);
        record.setSellerWallet(wallet);
        record.setAmountMinor(amount.getAmountMinor());
        record.setCurrency(amount.getCurrency());
        record.setStatus("HELD");
        record.setReleaseDueAt(releaseDueAt);
        record.setCreatedBy(actorId);
        record.persist();
        return record;
    }

    @Transactional
    public EscrowRecord releaseEscrow(UUID escrowId) {
        return releaseEscrowInternal(escrowId, null);
    }

    @Transactional
    public EscrowRecord releaseEscrow(UUID escrowId, UUID actorId) {
        return releaseEscrowInternal(escrowId, actorId);
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

        walletService.settlePendingToAvailable(
                escrow.getTenantId(), sellerWallet.getId(), amount, actorId
        );
        walletService.debitAvailable(
                escrow.getTenantId(), sellerWallet.getId(), amount,
                "ESCROW_REFUND", "ORDER", escrow.getOrderId(),
                actorId, "Hoàn tiền ký quỹ"
        );

        escrow.setStatus("REFUNDED");
        escrow.setUpdatedBy(actorId);
        return escrow;
    }

    private EscrowRecord releaseEscrowInternal(EscrowRecord escrow, UUID actorId) {
        if (!"HELD".equals(escrow.getStatus())) {
            return escrow;
        }
        Money amount = Money.of(escrow.getAmountMinor(), escrow.getCurrency());
        Wallet sellerWallet = escrow.getSellerWallet();

        walletService.settlePendingToAvailable(
                escrow.getTenantId(), sellerWallet.getId(), amount, actorId
        );

        escrow.setStatus("RELEASED");
        escrow.setUpdatedBy(actorId);
        return escrow;
    }

    private EscrowRecord releaseEscrowInternal(UUID escrowId, UUID actorId) {
        EscrowRecord escrow = EscrowRecord.findById(escrowId);
        if (escrow == null) {
            throw new NotFoundException("Không tìm thấy bản ghi ký quỹ");
        }
        return releaseEscrowInternal(escrow, actorId);
    }
}

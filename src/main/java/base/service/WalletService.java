package base.service;

import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WalletService {

    public Wallet getWallet(String tenantId, UUID walletId) {
        if (walletId == null) {
            throw new BadRequestException("Vui lòng nhập mã ví");
        }
        Wallet wallet = Wallet.find("id = ?1 and tenantId = ?2", walletId, tenantId).firstResult();
        if (wallet == null) {
            throw new NotFoundException("Không tìm thấy ví");
        }
        return wallet;
    }

    public List<Wallet> listWallets(String tenantId, String ownerType, UUID ownerId) {
        if (ownerType != null && ownerId != null) {
            return Wallet.list(
                    "tenantId = ?1 and ownerType = ?2 and ownerId = ?3",
                    tenantId,
                    ownerType.trim().toUpperCase(),
                    ownerId
            );
        }
        if (ownerType != null) {
            return Wallet.list(
                    "tenantId = ?1 and ownerType = ?2",
                    tenantId,
                    ownerType.trim().toUpperCase()
            );
        }
        return Wallet.list("tenantId = ?1", tenantId);
    }

    @Transactional
    public Wallet getOrCreateWallet(String tenantId, String ownerType, UUID ownerId, String currency, UUID actorId) {
        if (ownerType == null || ownerType.isBlank()) {
            throw new BadRequestException("Vui lòng nhập loại chủ ví");
        }
        if (ownerId == null) {
            throw new BadRequestException("Vui lòng nhập mã chủ ví");
        }
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Vui lòng nhập loại tiền tệ");
        }
        String normalOwnerType = ownerType.trim().toUpperCase();
        String normalCurrency = currency.trim().toUpperCase();

        Wallet existing = Wallet.find(
                "tenantId = ?1 and ownerType = ?2 and ownerId = ?3 and currency = ?4",
                tenantId, normalOwnerType, ownerId, normalCurrency
        ).firstResult();
        if (existing != null) {
            return existing;
        }

        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setTenantId(tenantId);
        wallet.setOwnerType(normalOwnerType);
        wallet.setOwnerId(ownerId);
        wallet.setCurrency(normalCurrency);
        wallet.setAvailableBalanceMinor(0);
        wallet.setPendingBalanceMinor(0);
        wallet.setCreatedBy(actorId);
        wallet.persist();
        return wallet;
    }

    @Transactional
    public Wallet creditAvailable(String tenantId, UUID walletId, Money amount, String transactionType,
                                  String referenceType, UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        if (amount.getAmountMinor() <= 0) {
            throw new BadRequestException("Số tiền ghi có phải lớn hơn 0");
        }
        wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() + amount.getAmountMinor());
        recordTransaction(wallet, amount.getAmountMinor(), "CREDIT", transactionType,
                referenceType, referenceId, null, actorId, description);
        return wallet;
    }

    @Transactional
    public Wallet debitAvailable(String tenantId, UUID walletId, Money amount, String transactionType,
                                 String referenceType, UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        if (amount.getAmountMinor() <= 0) {
            throw new BadRequestException("Số tiền ghi nợ phải lớn hơn 0");
        }
        if (wallet.getAvailableBalanceMinor() < amount.getAmountMinor()) {
            throw new BadRequestException("Số dư khả dụng không đủ");
        }
        wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() - amount.getAmountMinor());
        recordTransaction(wallet, amount.getAmountMinor(), "DEBIT", transactionType,
                referenceType, referenceId, null, actorId, description);
        return wallet;
    }

    @Transactional
    public Wallet creditPending(String tenantId, UUID walletId, Money amount, String transactionType,
                                String referenceType, UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        if (amount.getAmountMinor() <= 0) {
            throw new BadRequestException("Số tiền ghi có phải lớn hơn 0");
        }
        wallet.setPendingBalanceMinor(wallet.getPendingBalanceMinor() + amount.getAmountMinor());
        recordTransaction(wallet, amount.getAmountMinor(), "CREDIT", transactionType,
                referenceType, referenceId, null, actorId, description);
        return wallet;
    }

    @Transactional
    public Wallet settlePendingToAvailable(String tenantId, UUID walletId, Money amount, UUID actorId) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        if (amount.getAmountMinor() <= 0) {
            throw new BadRequestException("Số tiền phải lớn hơn 0");
        }
        if (wallet.getPendingBalanceMinor() < amount.getAmountMinor()) {
            throw new BadRequestException("Số dư chờ xử lý không đủ");
        }
        wallet.setPendingBalanceMinor(wallet.getPendingBalanceMinor() - amount.getAmountMinor());
        wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() + amount.getAmountMinor());
        return wallet;
    }

    public List<WalletTransaction> listTransactions(String tenantId, UUID walletId, int limit, int offset) {
        getWallet(tenantId, walletId);
        return WalletTransaction.find(
                "tenantId = ?1 and wallet.id = ?2 order by createdAt desc",
                tenantId, walletId
        ).page(offset / Math.max(limit, 1), Math.max(limit, 1)).list();
    }

    @Transactional
    public Wallet adjustBalance(String tenantId, UUID walletId, long adjustmentMinor, String reason, UUID actorId) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        if (adjustmentMinor > 0) {
            wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() + adjustmentMinor);
            recordTransaction(wallet, adjustmentMinor, "CREDIT", "ADJUSTMENT",
                    null, null, null, actorId, reason);
        } else if (adjustmentMinor < 0) {
            long absAmount = -adjustmentMinor;
            if (wallet.getAvailableBalanceMinor() < absAmount) {
                throw new BadRequestException("Số dư khả dụng không đủ để điều chỉnh");
            }
            wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() - absAmount);
            recordTransaction(wallet, absAmount, "DEBIT", "ADJUSTMENT",
                    null, null, null, actorId, reason);
        }
        return wallet;
    }

    private Wallet walletForUpdate(String tenantId, UUID walletId) {
        Wallet wallet = Wallet.find("id = ?1 and tenantId = ?2", walletId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (wallet == null) {
            throw new NotFoundException("Không tìm thấy ví");
        }
        return wallet;
    }

    private void recordTransaction(Wallet wallet, long amountMinor, String type, String transactionType,
                                   String referenceType, UUID referenceId, UUID correctionOfTransactionId,
                                   UUID actorId, String description) {
        WalletTransaction txn = new WalletTransaction();
        txn.setId(UUID.randomUUID());
        txn.setTenantId(wallet.getTenantId());
        txn.setWallet(wallet);
        txn.setAmountMinor(amountMinor);
        txn.setType(type);
        txn.setTransactionType(transactionType != null ? transactionType.trim().toUpperCase() : "OTHER");
        txn.setReferenceType(referenceType);
        txn.setReferenceId(referenceId);
        txn.setCorrectionOfTransactionId(correctionOfTransactionId);
        txn.setSequenceNo(System.currentTimeMillis());
        txn.setDescription(description);
        txn.setCreatedBy(actorId);
        txn.persist();
    }
}

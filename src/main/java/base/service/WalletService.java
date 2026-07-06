package base.service;

import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class WalletService {

    public record WalletMutation(Wallet wallet, WalletTransaction transaction) {}

    @Inject
    EntityManager entityManager;

    @Inject
    MoneyService moneyService;

    @Inject
    LedgerCheckpointService checkpointService;

    @Transactional
    public Wallet getOrCreateWallet(String tenantId, String ownerType, UUID ownerId, String currency, UUID actorId) {
        String normalizedOwnerType = normalizeOwnerType(ownerType);
        String normalizedCurrency = moneyService.normalizeCurrency(currency);
        if (ownerId == null) {
            throw new BadRequestException("Vui lòng nhập mã chủ ví");
        }

        Wallet wallet = Wallet.find(
                "tenantId = ?1 and ownerType = ?2 and ownerId = ?3 and currency = ?4",
                tenantId,
                normalizedOwnerType,
                ownerId,
                normalizedCurrency
        ).firstResult();
        if (wallet != null) {
            return wallet;
        }

        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setTenantId(tenantId);
        wallet.setOwnerType(normalizedOwnerType);
        wallet.setOwnerId(ownerId);
        wallet.setCurrency(normalizedCurrency);
        wallet.setCreatedBy(actorId);
        wallet.persist();
        return wallet;
    }

    public Wallet getWallet(String tenantId, UUID walletId) {
        Wallet wallet = Wallet.find("id = ?1 and tenantId = ?2", walletId, tenantId).firstResult();
        if (wallet == null) {
            throw new NotFoundException("Không tìm thấy ví");
        }
        return wallet;
    }

    public List<Wallet> listWallets(String tenantId, String ownerType, UUID ownerId, String currency) {
        if (ownerType != null && ownerId != null && currency != null) {
            return Wallet.list(
                    "tenantId = ?1 and ownerType = ?2 and ownerId = ?3 and currency = ?4",
                    tenantId,
                    normalizeOwnerType(ownerType),
                    ownerId,
                    moneyService.normalizeCurrency(currency)
            );
        }
        if (ownerType != null && ownerId != null) {
            return Wallet.list(
                    "tenantId = ?1 and ownerType = ?2 and ownerId = ?3",
                    tenantId,
                    normalizeOwnerType(ownerType),
                    ownerId
            );
        }
        return Wallet.list("tenantId", tenantId);
    }

    public List<WalletTransaction> listTransactions(String tenantId, UUID walletId) {
        getWallet(tenantId, walletId);
        return WalletTransaction.list(
                "tenantId = ?1 and wallet.id = ?2 order by sequenceNo desc",
                tenantId,
                walletId
        );
    }

    @Transactional
    public WalletMutation creditAvailable(String tenantId, UUID walletId, Money amount, String transactionType,
                                          String referenceType, UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        moneyService.requirePositive(amount);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        WalletTransaction existing = existingReferenceTransaction(tenantId, walletId, transactionType, referenceType, referenceId);
        if (existing != null) {
            return new WalletMutation(wallet, existing);
        }

        wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() + amount.getAmountMinor());
        wallet.setUpdatedBy(actorId);
        WalletTransaction transaction = appendTransaction(
                wallet,
                amount.getAmountMinor(),
                "CREDIT",
                transactionType,
                referenceType,
                referenceId,
                actorId,
                description
        );
        checkpointService.writeCheckpoint(wallet, transaction, actorId);
        return new WalletMutation(wallet, transaction);
    }

    @Transactional
    public WalletMutation debitAvailable(String tenantId, UUID walletId, Money amount, String transactionType,
                                         String referenceType, UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        moneyService.requirePositive(amount);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        WalletTransaction existing = existingReferenceTransaction(tenantId, walletId, transactionType, referenceType, referenceId);
        if (existing != null) {
            return new WalletMutation(wallet, existing);
        }
        if (wallet.getAvailableBalanceMinor() < amount.getAmountMinor()) {
            throw new BadRequestException("Số dư khả dụng không đủ");
        }

        wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() - amount.getAmountMinor());
        wallet.setUpdatedBy(actorId);
        WalletTransaction transaction = appendTransaction(
                wallet,
                amount.getAmountMinor(),
                "DEBIT",
                transactionType,
                referenceType,
                referenceId,
                actorId,
                description
        );
        checkpointService.writeCheckpoint(wallet, transaction, actorId);
        return new WalletMutation(wallet, transaction);
    }

    @Transactional
    public WalletMutation creditPending(String tenantId, UUID walletId, Money amount, String transactionType,
                                        String referenceType, UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        moneyService.requirePositive(amount);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        WalletTransaction existing = existingReferenceTransaction(tenantId, walletId, transactionType, referenceType, referenceId);
        if (existing != null) {
            return new WalletMutation(wallet, existing);
        }

        wallet.setPendingBalanceMinor(wallet.getPendingBalanceMinor() + amount.getAmountMinor());
        wallet.setUpdatedBy(actorId);
        WalletTransaction transaction = appendTransaction(
                wallet,
                amount.getAmountMinor(),
                "CREDIT",
                transactionType,
                referenceType,
                referenceId,
                actorId,
                description
        );
        checkpointService.writeCheckpoint(wallet, transaction, actorId);
        return new WalletMutation(wallet, transaction);
    }

    @Transactional
    public WalletMutation releasePendingToAvailable(String tenantId, UUID walletId, Money amount, String referenceType,
                                                    UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        moneyService.requirePositive(amount);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        WalletTransaction existing = existingReferenceTransaction(tenantId, walletId, "ESCROW_RELEASE", referenceType, referenceId);
        if (existing != null) {
            return new WalletMutation(wallet, existing);
        }
        if (wallet.getPendingBalanceMinor() < amount.getAmountMinor()) {
            throw new BadRequestException("Số dư tạm giữ không đủ");
        }

        wallet.setPendingBalanceMinor(wallet.getPendingBalanceMinor() - amount.getAmountMinor());
        wallet.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor() + amount.getAmountMinor());
        wallet.setUpdatedBy(actorId);
        WalletTransaction transaction = appendTransaction(
                wallet,
                amount.getAmountMinor(),
                "CREDIT",
                "ESCROW_RELEASE",
                referenceType,
                referenceId,
                actorId,
                description
        );
        checkpointService.writeCheckpoint(wallet, transaction, actorId);
        return new WalletMutation(wallet, transaction);
    }

    @Transactional
    public WalletMutation refundPending(String tenantId, UUID walletId, Money amount, String referenceType,
                                        UUID referenceId, UUID actorId, String description) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        moneyService.requirePositive(amount);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        WalletTransaction existing = existingReferenceTransaction(tenantId, walletId, "ESCROW_REFUND", referenceType, referenceId);
        if (existing != null) {
            return new WalletMutation(wallet, existing);
        }
        if (wallet.getPendingBalanceMinor() < amount.getAmountMinor()) {
            throw new BadRequestException("Số dư tạm giữ không đủ");
        }

        wallet.setPendingBalanceMinor(wallet.getPendingBalanceMinor() - amount.getAmountMinor());
        wallet.setUpdatedBy(actorId);
        WalletTransaction transaction = appendTransaction(
                wallet,
                amount.getAmountMinor(),
                "DEBIT",
                "ESCROW_REFUND",
                referenceType,
                referenceId,
                actorId,
                description
        );
        checkpointService.writeCheckpoint(wallet, transaction, actorId);
        return new WalletMutation(wallet, transaction);
    }

    @Transactional
    public WalletMutation holdEscrowForSeller(String tenantId, UUID sellerOwnerId, Money amount, String referenceType,
                                              UUID referenceId, UUID actorId, String description) {
        Wallet wallet = getOrCreateWallet(tenantId, "SELLER", sellerOwnerId, amount.getCurrency(), actorId);
        return creditPending(tenantId, wallet.getId(), amount, "ESCROW_HOLD", referenceType, referenceId, actorId, description);
    }

    @Transactional
    public WalletMutation releaseEscrowForSeller(String tenantId, UUID sellerOwnerId, Money amount, String referenceType,
                                                 UUID referenceId, UUID actorId, String description) {
        Wallet wallet = getOrCreateWallet(tenantId, "SELLER", sellerOwnerId, amount.getCurrency(), actorId);
        return releasePendingToAvailable(tenantId, wallet.getId(), amount, referenceType, referenceId, actorId, description);
    }

    @Transactional
    public WalletMutation refundEscrowForSeller(String tenantId, UUID sellerOwnerId, Money amount, String referenceType,
                                                UUID referenceId, UUID actorId, String description) {
        Wallet wallet = getOrCreateWallet(tenantId, "SELLER", sellerOwnerId, amount.getCurrency(), actorId);
        return refundPending(tenantId, wallet.getId(), amount, referenceType, referenceId, actorId, description);
    }

    private Wallet walletForUpdate(String tenantId, UUID walletId) {
        if (walletId == null) {
            throw new BadRequestException("Vui lòng nhập mã ví");
        }
        Wallet wallet = Wallet.find("id = ?1 and tenantId = ?2", walletId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (wallet == null) {
            throw new NotFoundException("Không tìm thấy ví");
        }
        return wallet;
    }

    private WalletTransaction appendTransaction(Wallet wallet, long amountMinor, String type, String transactionType,
                                                String referenceType, UUID referenceId, UUID actorId, String description) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setTenantId(wallet.getTenantId());
        transaction.setWallet(wallet);
        transaction.setAmountMinor(amountMinor);
        transaction.setType(type);
        transaction.setTransactionType(normalizeTransactionType(transactionType));
        transaction.setReferenceType(referenceType == null || referenceType.isBlank()
                ? null
                : referenceType.trim().toUpperCase(Locale.ROOT));
        transaction.setReferenceId(referenceId);
        transaction.setSequenceNo(nextSequenceNo(wallet));
        transaction.setDescription(description);
        transaction.setCreatedBy(actorId);
        transaction.persist();
        return transaction;
    }

    private long nextSequenceNo(Wallet wallet) {
        Long lastSequence = entityManager.createQuery(
                "select coalesce(max(t.sequenceNo), 0) from WalletTransaction t where t.tenantId = :tenantId and t.wallet.id = :walletId",
                Long.class
        ).setParameter("tenantId", wallet.getTenantId())
                .setParameter("walletId", wallet.getId())
                .getSingleResult();
        return lastSequence + 1;
    }

    private WalletTransaction existingReferenceTransaction(String tenantId, UUID walletId, String transactionType,
                                                           String referenceType, UUID referenceId) {
        if (referenceType == null || referenceType.isBlank() || referenceId == null) {
            return null;
        }
        return WalletTransaction.find(
                "tenantId = ?1 and wallet.id = ?2 and transactionType = ?3 and referenceType = ?4 and referenceId = ?5",
                tenantId,
                walletId,
                normalizeTransactionType(transactionType),
                referenceType.trim().toUpperCase(Locale.ROOT),
                referenceId
        ).firstResult();
    }

    private String normalizeOwnerType(String ownerType) {
        if (ownerType == null || ownerType.isBlank()) {
            throw new BadRequestException("Vui lòng nhập loại chủ ví");
        }
        return ownerType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTransactionType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            throw new BadRequestException("Vui lòng nhập loại giao dịch");
        }
        return transactionType.trim().toUpperCase(Locale.ROOT);
    }
}

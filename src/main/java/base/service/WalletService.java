package base.service;

import base.entity.Bank;
import base.entity.Business;
import base.entity.PayoutRequest;
import base.entity.User;
import base.entity.Wallet;
import base.entity.WalletTopUp;
import base.entity.WalletTransaction;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class WalletService {

    private static final int PLATFORM_COMMISSION_PERCENT = 5;

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

    @Transactional
    public Wallet getOrCreateUserWallet(String tenantId, UUID userId, String currency, UUID actorId) {
        User user = User.find("id = ?1 and tenantId = ?2", userId, tenantId).firstResult();
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        String normalizedCurrency = moneyService.normalizeCurrency(currency);
        Business business = resolveOwnerBusiness(user, tenantId);
        Wallet wallet;
        if (business != null) {
            wallet = getOrCreateWallet(tenantId, "SELLER", business.getId(), normalizedCurrency, actorId);
        } else {
            wallet = Wallet.find(
                    "tenantId = ?1 and user.id = ?2 and ownerType = 'USER' and currency = ?3 order by createdAt asc",
                    tenantId, userId, normalizedCurrency).firstResult();
            if (wallet == null) {
                wallet = getOrCreateWallet(tenantId, "USER", userId, normalizedCurrency, actorId);
            }
        }
        wallet.setUser(user);
        wallet.setUpdatedBy(actorId);
        return wallet;
    }

    public boolean isWalletOwnedByUser(String tenantId, Wallet wallet, User user) {
        if (wallet == null || user == null || !tenantId.equals(wallet.getTenantId())) {
            return false;
        }
        if (wallet.getUser() != null && user.getId().equals(wallet.getUser().getId())) {
            return true;
        }
        if ("USER".equals(wallet.getOwnerType())) {
            return user.getId().equals(wallet.getOwnerId());
        }
        if (!"SELLER".equals(wallet.getOwnerType()) || !isBusinessOwner(user)) {
            return false;
        }

        Business assignedBusiness = user.getBusiness();
        if (assignedBusiness != null
                && tenantId.equals(assignedBusiness.getTenantId())
                && assignedBusiness.getId().equals(wallet.getOwnerId())) {
            return true;
        }
        return Business.count(
                "id = ?1 and tenantId = ?2 and createdBy = ?3",
                wallet.getOwnerId(),
                tenantId,
                user.getId()
        ) > 0;
    }

    private Business resolveOwnerBusiness(User user, String tenantId) {
        if (!isBusinessOwner(user)) {
            return null;
        }
        Business assignedBusiness = user.getBusiness();
        if (assignedBusiness != null && tenantId.equals(assignedBusiness.getTenantId())) {
            return assignedBusiness;
        }
        return Business.find(
                "tenantId = ?1 and createdBy = ?2 order by createdAt asc",
                tenantId,
                user.getId()
        ).firstResult();
    }

    private boolean isBusinessOwner(User user) {
        return user.getRole() != null && "businessOwner".equals(user.getRole().getName());
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

    public List<WalletTransaction> listTransactions(String tenantId, UUID walletId, int limit, int offset) {
        getWallet(tenantId, walletId);
        return WalletTransaction.find("wallet.tenantId = ?1 and wallet.id = ?2 order by createdAt desc", tenantId, walletId)
                .page(offset / limit, limit)
                .list();
    }

    @Transactional
    public void completeTopUp(String tenantId, UUID topUpId, UUID actorId) {
        if (topUpId == null) {
            throw new BadRequestException("Top-up ID is required");
        }
        WalletTopUp topUp = WalletTopUp.find("id = ?1 and tenantId = ?2", topUpId, tenantId)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (topUp == null) {
            throw new NotFoundException("Top-up request not found");
        }
        if (!"PENDING".equals(topUp.getStatus())) {
            throw new BadRequestException("Top-up request is not pending");
        }

        topUp.setStatus("COMPLETED");
        topUp.setUpdatedBy(actorId);

        creditAvailable(
                topUp.getWallet().getTenantId(),
                topUp.getWallet().getId(),
                Money.of(topUp.getAmountMinor(), topUp.getCurrency()),
                "TOP_UP",
                "WALLET_TOP_UP",
                topUp.getId(),
                actorId,
                "Nạp tiền vào ví"
        );
    }

    @Transactional
    public Wallet updateBankDetails(String tenantId, UUID walletId, UUID bankId,
                                    String bankAccountNumber, String bankAccountHolder, UUID actorId) {
        if (bankId == null || bankAccountNumber == null || bankAccountNumber.isBlank()
                || bankAccountHolder == null || bankAccountHolder.isBlank()) {
            throw new BadRequestException("Bank, account number, and account holder are required");
        }
        Bank bank = Bank.find("id = ?1 and active = true", bankId).firstResult();
        if (bank == null) {
            throw new BadRequestException("Selected bank is not available");
        }
        Wallet wallet = walletForUpdate(tenantId, walletId);
        boolean changed = wallet.getBank() == null || !bank.getId().equals(wallet.getBank().getId())
                || !bankAccountNumber.trim().equals(wallet.getBankAccountNumber())
                || !bankAccountHolder.trim().equals(wallet.getBankAccountHolder());
        if (!changed) {
            return wallet;
        }

        wallet.setBank(bank);
        wallet.setBankName(bank.getName());
        wallet.setBankAccountNumber(bankAccountNumber.trim());
        wallet.setBankAccountHolder(bankAccountHolder.trim());
        wallet.setBankDetailsUpdatedAt(Instant.now());
        wallet.setUpdatedBy(actorId);

        List<PayoutRequest> staleFlagged = PayoutRequest.list(
                "tenantId = ?1 and wallet.id = ?2 and status = 'FLAGGED'", tenantId, walletId);
        for (PayoutRequest payout : staleFlagged) {
            payout.setStatus("REJECTED");
            payout.setFlagReason(payout.getFlagReason() + " | Bank details changed after screening");
            payout.setUpdatedBy(actorId);
        }
        return wallet;
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


    @Transactional
    public Wallet adjustBalance(String tenantId, UUID walletId, long adjustmentMinor, String reason, UUID actorId) {
        Wallet wallet = walletForUpdate(tenantId, walletId);
        Money amount = Money.of(Math.abs(adjustmentMinor), wallet.getCurrency());
        UUID refId = UUID.nameUUIDFromBytes((walletId + ":" + adjustmentMinor + ":" + reason).getBytes(StandardCharsets.UTF_8));
        if (adjustmentMinor > 0) {
            creditAvailable(tenantId, walletId, amount, "ADJUSTMENT",
                    "ADJUSTMENT", refId, actorId, reason);
        } else if (adjustmentMinor < 0) {
            debitAvailable(tenantId, walletId, amount, "ADJUSTMENT",
                    "ADJUSTMENT", refId, actorId, reason);
        }
        return wallet;
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
        entityManager.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
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

    public long getPendingEscrowForSeller(String tenantId, Wallet wallet) {
        if (!"SELLER".equals(wallet.getOwnerType()) || wallet.getOwnerId() == null) return 0;
        List<Long> amounts = entityManager.createQuery(
            "SELECT e.amountMinor FROM EscrowRecord e WHERE e.tenantId = :tenantId AND e.sellerWallet.id = :walletId AND e.status = 'HELD'", Long.class)
            .setParameter("tenantId", tenantId)
            .setParameter("walletId", wallet.getId())
            .getResultList();
        long totalNet = 0;
        for (Long amt : amounts) {
            if (amt != null) {
                totalNet += (amt - commissionFor(amt));
            }
        }
        return totalNet;
    }

    private long commissionFor(long amountMinor) {
        return java.math.BigDecimal.valueOf(amountMinor)
                .multiply(java.math.BigDecimal.valueOf(PLATFORM_COMMISSION_PERCENT))
                .divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
    }

    public Map<String, Long> getWalletStats(String tenantId) {
        Map<String, Long> stats = new HashMap<>();

        Long customerTotal = entityManager.createQuery(
            "SELECT COALESCE(SUM(w.availableBalanceMinor), 0) FROM Wallet w WHERE w.tenantId = :tenantId AND w.ownerType = 'USER'", Long.class)
            .setParameter("tenantId", tenantId)
            .getSingleResult();
        Long platformCommission = entityManager.createQuery(
            "SELECT COALESCE(SUM(w.availableBalanceMinor), 0) FROM Wallet w WHERE w.tenantId = :tenantId AND w.ownerType = 'PLATFORM'", Long.class)
            .setParameter("tenantId", tenantId)
            .getSingleResult();

        Long sellerAvailable = entityManager.createQuery(
            "SELECT COALESCE(SUM(w.availableBalanceMinor), 0) FROM Wallet w WHERE w.tenantId = :tenantId AND w.ownerType = 'SELLER'", Long.class)
            .setParameter("tenantId", tenantId)
            .getSingleResult();

        List<Long> escrowAmounts = entityManager.createQuery(
            "SELECT e.amountMinor FROM EscrowRecord e WHERE e.tenantId = :tenantId AND e.status = 'HELD'", Long.class)
            .setParameter("tenantId", tenantId)
            .getResultList();

        long escrowHeld = 0;
        long escrowNet = 0;
        for (Long amt : escrowAmounts) {
            if (amt != null) {
                escrowHeld += amt;
                escrowNet += (amt - commissionFor(amt));
            }
        }

        stats.put("customerBalance", customerTotal);
        stats.put("platformCommission", platformCommission);
        stats.put("escrowBalance", escrowHeld);
        stats.put("sellerBalance", sellerAvailable + escrowNet);
        return stats;
    }

    public List<WalletTransaction> listAllTransactions(String tenantId, int limit, int offset) {
        return WalletTransaction.find("tenantId = ?1 ORDER BY createdAt DESC", tenantId)
                .page(offset / limit, limit)
                .list();
    }
}

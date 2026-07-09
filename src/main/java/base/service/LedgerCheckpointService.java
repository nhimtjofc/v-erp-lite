package base.service;

import base.entity.LedgerCheckpoint;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LedgerCheckpointService {

    private static final Logger log = Logger.getLogger(LedgerCheckpointService.class);
    private final EntityManager em;

    public LedgerCheckpointService(EntityManager em) {
        this.em = em;
    }

    public LedgerCheckpoint writeCheckpoint(Wallet wallet, WalletTransaction transaction, UUID actorId) {
        LedgerCheckpoint checkpoint = new LedgerCheckpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setTenantId(wallet.getTenantId());
        checkpoint.setWallet(wallet);
        checkpoint.setLastTransactionId(transaction.getId());
        checkpoint.setLastSequenceNo(transaction.getSequenceNo());
        checkpoint.setAvailableBalanceMinor(wallet.getAvailableBalanceMinor());
        checkpoint.setPendingBalanceMinor(wallet.getPendingBalanceMinor());
        checkpoint.setCheckpointedAt(Instant.now());
        checkpoint.setCreatedBy(actorId);
        checkpoint.persist();
        return checkpoint;
    }

    public LedgerCheckpoint latestForWallet(String tenantId, UUID walletId) {
        return LedgerCheckpoint.find(
                "tenantId = ?1 and wallet.id = ?2 order by lastSequenceNo desc",
                tenantId,
                walletId
        ).firstResult();
    }

    @Transactional
    public void createCheckpoint(UUID walletId, String tenantCode) {
        Wallet wallet = Wallet.find("id = ?1 and tenantId = ?2", walletId, tenantCode).firstResult();
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found");
        }

        Long latestSequenceNo = em.createQuery(
                "SELECT MAX(wt.sequenceNo) FROM WalletTransaction wt WHERE wt.wallet.id = :walletId AND wt.tenantId = :tenantId", Long.class)
                .setParameter("walletId", walletId)
                .setParameter("tenantId", tenantCode)
                .getSingleResult();

        if (latestSequenceNo == null) {
            return;
        }

        LedgerCheckpoint lastCheckpoint = LedgerCheckpoint.find(
                "wallet.id = ?1 and tenantId = ?2 ORDER BY lastSequenceNo DESC", walletId, tenantCode)
                .firstResult();

        if (lastCheckpoint != null && lastCheckpoint.getLastSequenceNo() >= latestSequenceNo) {
            return;
        }

        Long startSeq = lastCheckpoint != null ? lastCheckpoint.getLastSequenceNo() : 0L;

        List<WalletTransaction> recentTxns = WalletTransaction.find(
                "wallet.id = ?1 and tenantId = ?2 and sequenceNo > ?3 and sequenceNo <= ?4",
                walletId, tenantCode, startSeq, latestSequenceNo).list();

        long availableDiff = 0;
        long pendingDiff = 0;

        for (WalletTransaction txn : recentTxns) {
            String txnType = txn.getTransactionType();
            if ("ESCROW_HOLD".equals(txnType)) {
                if ("CREDIT".equals(txn.getType())) pendingDiff += txn.getAmountMinor();
                else pendingDiff -= txn.getAmountMinor();
            } else if ("ESCROW_REFUND".equals(txnType)) {
                if ("DEBIT".equals(txn.getType())) pendingDiff -= txn.getAmountMinor();
                else pendingDiff += txn.getAmountMinor();
            } else if ("ESCROW_RELEASE".equals(txnType)) {
                if ("CREDIT".equals(txn.getType())) {
                    pendingDiff -= txn.getAmountMinor();
                    availableDiff += txn.getAmountMinor();
                }
            } else {
                if ("CREDIT".equals(txn.getType())) availableDiff += txn.getAmountMinor();
                else if ("DEBIT".equals(txn.getType())) availableDiff -= txn.getAmountMinor();
            }
        }

        long newAvailable = (lastCheckpoint != null ? lastCheckpoint.getAvailableBalanceMinor() : 0) + availableDiff;
        long newPending = (lastCheckpoint != null ? lastCheckpoint.getPendingBalanceMinor() : 0) + pendingDiff;

        LedgerCheckpoint checkpoint = new LedgerCheckpoint();
        checkpoint.setTenantId(tenantCode);
        checkpoint.setWallet(wallet);

        WalletTransaction lastTxn = WalletTransaction.find("wallet.id = ?1 and tenantId = ?2 and sequenceNo = ?3", walletId, tenantCode, latestSequenceNo).firstResult();
        if (lastTxn != null) {
            checkpoint.setLastTransactionId(lastTxn.getId());
        }

        checkpoint.setLastSequenceNo(latestSequenceNo);
        checkpoint.setAvailableBalanceMinor(newAvailable);
        checkpoint.setPendingBalanceMinor(newPending);
        checkpoint.setCheckpointedAt(Instant.now());

        checkpoint.persist();
        log.infof("Created LedgerCheckpoint for Wallet ID: %s at Sequence: %d", walletId, latestSequenceNo);
    }
}

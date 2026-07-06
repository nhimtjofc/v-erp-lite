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

    /**
     * Creates a new checkpoint for a given wallet, summarizing all transactions 
     * up to the latest sequence number.
     */
    @Transactional
    public void createCheckpoint(UUID walletId, String tenantCode) {
        Wallet wallet = Wallet.find("id = ?1 and tenantId = ?2", walletId, tenantCode).firstResult();
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found");
        }

        // Get the last sequence no from transactions
        Long latestSequenceNo = em.createQuery(
                "SELECT MAX(wt.sequenceNo) FROM WalletTransaction wt WHERE wt.wallet.id = :walletId AND wt.tenantId = :tenantId", Long.class)
                .setParameter("walletId", walletId)
                .setParameter("tenantId", tenantCode)
                .getSingleResult();

        if (latestSequenceNo == null) {
            return; // No transactions yet
        }

        // Check if there is an existing checkpoint
        LedgerCheckpoint lastCheckpoint = LedgerCheckpoint.find(
                "wallet.id = ?1 and tenantId = ?2 ORDER BY lastSequenceNo DESC", walletId, tenantCode)
                .firstResult();

        if (lastCheckpoint != null && lastCheckpoint.getLastSequenceNo() >= latestSequenceNo) {
            return; // Already up to date
        }

        Long startSeq = lastCheckpoint != null ? lastCheckpoint.getLastSequenceNo() : 0L;

        // Sum up credits and debits between startSeq (exclusive) and latestSequenceNo (inclusive)
        List<WalletTransaction> recentTxns = WalletTransaction.find(
                "wallet.id = ?1 and tenantId = ?2 and sequenceNo > ?3 and sequenceNo <= ?4",
                walletId, tenantCode, startSeq, latestSequenceNo).list();

        long availableDiff = 0;
        long pendingDiff = 0;

        for (WalletTransaction txn : recentTxns) {
            if ("CREDIT".equals(txn.getType())) {
                if ("PENDING".equals(txn.getTransactionType())) pendingDiff += txn.getAmountMinor();
                else availableDiff += txn.getAmountMinor();
            } else if ("DEBIT".equals(txn.getType())) {
                if ("PENDING".equals(txn.getTransactionType())) pendingDiff -= txn.getAmountMinor();
                else availableDiff -= txn.getAmountMinor();
            }
        }

        long newAvailable = (lastCheckpoint != null ? lastCheckpoint.getAvailableBalanceMinor() : 0) + availableDiff;
        long newPending = (lastCheckpoint != null ? lastCheckpoint.getPendingBalanceMinor() : 0) + pendingDiff;

        // Save new checkpoint
        LedgerCheckpoint checkpoint = new LedgerCheckpoint();
        checkpoint.setTenantId(tenantCode);
        checkpoint.setWallet(wallet);
        
        // Find last transaction ID for the latestSequenceNo
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

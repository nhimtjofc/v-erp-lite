package base.service;

import base.entity.LedgerCheckpoint;
import base.entity.Wallet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ReconciliationService {

    private static final Logger log = Logger.getLogger(ReconciliationService.class);
    private final EntityManager em;

    public ReconciliationService(EntityManager em) {
        this.em = em;
    }

    /**
     * Checks if the sum of the ledger entries matches the actual wallet balances for all wallets of a tenant.
     */
    public void reconcileAllWallets(String tenantCode) {
        log.info("Starting reconciliation for tenant: " + tenantCode);

        List<Wallet> wallets = Wallet.find("tenantId = ?1", tenantCode).list();
        int mismatched = 0;

        for (Wallet w : wallets) {
            boolean isMatch = reconcileWallet(w);
            if (!isMatch) {
                mismatched++;
                log.errorf("Wallet reconciliation FAILED for Wallet ID: %s. Ledger does not match actual balance.", w.getId());
                // In production, trigger an alert or lock the wallet
            }
        }

        log.infof("Finished reconciliation for tenant: %s. Mismatched: %d / %d", tenantCode, mismatched, wallets.size());
    }

    private boolean reconcileWallet(Wallet wallet) {
        // 1. Get the latest checkpoint
        LedgerCheckpoint checkpoint = LedgerCheckpoint.find("wallet.id = ?1 and tenantId = ?2 ORDER BY lastSequenceNo DESC", 
                wallet.getId(), wallet.getTenantId()).firstResult();

        long baseAvailable = checkpoint != null ? checkpoint.getAvailableBalanceMinor() : 0L;
        long basePending = checkpoint != null ? checkpoint.getPendingBalanceMinor() : 0L;
        long startSeq = checkpoint != null ? checkpoint.getLastSequenceNo() : 0L;

        // 2. Sum up transactions since checkpoint using JPQL
        Object[] diffs = (Object[]) em.createQuery(
                "SELECT " +
                "  SUM(CASE WHEN wt.type = 'CREDIT' AND wt.transactionType != 'PENDING' THEN wt.amountMinor ELSE 0 END) - " +
                "  SUM(CASE WHEN wt.type = 'DEBIT' AND wt.transactionType != 'PENDING' THEN wt.amountMinor ELSE 0 END), " +
                "  SUM(CASE WHEN wt.type = 'CREDIT' AND wt.transactionType = 'PENDING' THEN wt.amountMinor ELSE 0 END) - " +
                "  SUM(CASE WHEN wt.type = 'DEBIT' AND wt.transactionType = 'PENDING' THEN wt.amountMinor ELSE 0 END) " +
                "FROM WalletTransaction wt " +
                "WHERE wt.wallet.id = :walletId AND wt.tenantId = :tenantId AND wt.sequenceNo > :startSeq")
                .setParameter("walletId", wallet.getId())
                .setParameter("tenantId", wallet.getTenantId())
                .setParameter("startSeq", startSeq)
                .getSingleResult();

        long availDiff = diffs[0] != null ? ((Number) diffs[0]).longValue() : 0L;
        long pendDiff = diffs[1] != null ? ((Number) diffs[1]).longValue() : 0L;

        long calculatedAvailable = baseAvailable + availDiff;
        long calculatedPending = basePending + pendDiff;

        // 3. Compare with wallet's current balance
        boolean availableMatches = calculatedAvailable == wallet.getAvailableBalanceMinor();
        boolean pendingMatches = calculatedPending == wallet.getPendingBalanceMinor();

        return availableMatches && pendingMatches;
    }
}

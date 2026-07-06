package base.service;

import base.entity.LedgerCheckpoint;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class LedgerCheckpointService {

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
}

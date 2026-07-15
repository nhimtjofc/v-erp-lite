package base.service;

import base.entity.LedgerCheckpoint;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestTransaction
class LedgerCheckpointServiceTest {

    @Inject
    WalletService walletService;

    @Inject
    LedgerCheckpointService checkpointService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000333");

    @Test
    void checkpointReflectsBalanceAfterSingleDeposit() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId);
        var mutation = walletService.creditAvailable(tenantId, wallet.getId(), Money.of(100_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);

        flushAndClear();
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, wallet.getId());
        assertNotNull(checkpoint);
        assertEquals(mutation.transaction().getId(), checkpoint.getLastTransactionId());
        assertEquals(1, checkpoint.getLastSequenceNo());
        assertEquals(100_000, checkpoint.getAvailableBalanceMinor());
        assertEquals(0, checkpoint.getPendingBalanceMinor());
    }

    @Test
    void checkpointUpdatesAfterMultipleMutations() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId);

        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(200_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);
        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(50_000, "VND"),
                "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null);

        flushAndClear();
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, wallet.getId());
        assertNotNull(checkpoint);
        assertEquals(150_000, checkpoint.getAvailableBalanceMinor());
        assertEquals(0, checkpoint.getPendingBalanceMinor());
        assertEquals(2, checkpoint.getLastSequenceNo());
    }

    @Test
    void checkpointTracksPendingBalance() {
        String tenantId = tenant();
        UUID sellerId = UUID.randomUUID();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", sellerId, "VND", actorId);
        UUID orderId = UUID.randomUUID();

        walletService.holdEscrowForSeller(tenantId, sellerId, Money.of(75_000, "VND"), "ORDER", orderId, actorId, null);

        flushAndClear();
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, wallet.getId());
        assertNotNull(checkpoint);
        assertEquals(0, checkpoint.getAvailableBalanceMinor());
        assertEquals(75_000, checkpoint.getPendingBalanceMinor());

        walletService.releaseEscrowForSeller(tenantId, sellerId, Money.of(75_000, "VND"), "ORDER", orderId, actorId, null);

        flushAndClear();
        checkpoint = checkpointService.latestForWallet(tenantId, wallet.getId());
        assertEquals(75_000, checkpoint.getAvailableBalanceMinor());
        assertEquals(0, checkpoint.getPendingBalanceMinor());
    }

    @Test
    void checkpointPlusDeltaEqualsWalletBalance() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId);

        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(500_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);
        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(100_000, "VND"),
                "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null);
        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(50_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);

        flushAndClear();
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, wallet.getId());
        assertNotNull(checkpoint);
        long checkpointSeq = checkpoint.getLastSequenceNo();

        List<WalletTransaction> deltaTxns = WalletTransaction.list(
                "tenantId = ?1 and wallet.id = ?2 and sequenceNo > ?3 order by sequenceNo asc",
                tenantId, wallet.getId(), checkpointSeq);

        long deltaAvailable = 0;
        long deltaPending = 0;
        for (WalletTransaction t : deltaTxns) {
            String txnType = t.getTransactionType();
            if ("ESCROW_HOLD".equals(txnType)) {
                deltaPending += "CREDIT".equals(t.getType()) ? t.getAmountMinor() : -t.getAmountMinor();
            } else if ("ESCROW_REFUND".equals(txnType)) {
                deltaPending += "DEBIT".equals(t.getType()) ? -t.getAmountMinor() : t.getAmountMinor();
            } else if ("ESCROW_RELEASE".equals(txnType)) {
                deltaPending -= t.getAmountMinor();
                deltaAvailable += t.getAmountMinor();
            } else {
                deltaAvailable += "CREDIT".equals(t.getType()) ? t.getAmountMinor() : -t.getAmountMinor();
            }
        }

        long projectedAvailable = checkpoint.getAvailableBalanceMinor() + deltaAvailable;
        long projectedPending = checkpoint.getPendingBalanceMinor() + deltaPending;

        flushAndClear();
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        assertEquals(projectedAvailable, reloaded.getAvailableBalanceMinor(),
                "Checkpoint + delta should project current available balance");
        assertEquals(projectedPending, reloaded.getPendingBalanceMinor(),
                "Checkpoint + delta should project current pending balance");
    }

    @Test
    void noCheckpointBeforeAnyMutation() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId);

        flushAndClear();
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, wallet.getId());
        assertNull(checkpoint);
    }

    @Test
    void latestForWalletIsTenantScoped() {
        String tenantA = tenant();
        String tenantB = tenant();
        UUID ownerId = UUID.randomUUID();
        Wallet walletA = walletService.getOrCreateWallet(tenantA, "SELLER", ownerId, "VND", actorId);
        walletService.getOrCreateWallet(tenantB, "SELLER", ownerId, "VND", actorId);

        walletService.creditAvailable(tenantA, walletA.getId(), Money.of(10_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);

        flushAndClear();
        LedgerCheckpoint checkpointA = checkpointService.latestForWallet(tenantA, walletA.getId());
        assertNotNull(checkpointA);
        assertEquals(10_000, checkpointA.getAvailableBalanceMinor());

        flushAndClear();
        Wallet walletB = walletService.listWallets(tenantB, "SELLER", ownerId, null).get(0);
        LedgerCheckpoint checkpointB = checkpointService.latestForWallet(tenantB, walletB.getId());
        assertNull(checkpointB);
    }

    private Wallet createWallet(String tenantId) {
        return walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String tenant() {
        return "checkpoint-" + UUID.randomUUID();
    }
}

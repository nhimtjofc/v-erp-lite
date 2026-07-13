package base.service;

import base.entity.LedgerCheckpoint;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LedgerInvariantTest {

    @Inject
    WalletService walletService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000444");

    @Test
    void ledgerRejectsDirectUpdate() {
        WalletTransaction txn = seedTransaction();
        assertThrows(PersistenceException.class, () ->
                entityManager.createNativeQuery(
                        "UPDATE wallet_transactions SET description = 'hacked' WHERE id = :id")
                        .setParameter("id", txn.getId())
                        .executeUpdate());
    }

    @Test
    void ledgerRejectsDirectDelete() {
        WalletTransaction txn = seedTransaction();
        assertThrows(PersistenceException.class, () ->
                entityManager.createNativeQuery("DELETE FROM wallet_transactions WHERE id = :id")
                        .setParameter("id", txn.getId())
                        .executeUpdate());
    }

    @Test
    void adjustmentTransactionCreatesLedgerEntry() {
        String tenantId = tenant();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);

        walletService.adjustBalance(tenantId, wallet.getId(), 50_000, "Manual top-up", actorId);
        entityManager.clear();
        assertEquals(50_000, walletService.getWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());

        walletService.adjustBalance(tenantId, wallet.getId(), -10_000, "Manual deduction", actorId);
        entityManager.clear();
        assertEquals(40_000, walletService.getWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());

        List<WalletTransaction> txns = walletService.listTransactions(tenantId, wallet.getId());
        assertEquals(2, txns.size());
        assertEquals("CREDIT", txns.get(1).getType());
        assertEquals("DEBIT", txns.get(0).getType());
        assertEquals("ADJUSTMENT", txns.get(0).getTransactionType());
    }

    @Test
    void balanceProjectionMatchesLedgerSum() {
        String tenantId = tenant();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);

        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(200_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);
        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(30_000, "VND"),
                "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null);
        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(50_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);
        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(20_000, "VND"),
                "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null);

        entityManager.clear();
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        assertEquals(200_000, reloaded.getAvailableBalanceMinor());

        List<WalletTransaction> txns = WalletTransaction.list(
                "tenantId = ?1 and wallet.id = ?2 order by sequenceNo asc",
                tenantId, wallet.getId());
        long sumCredits = txns.stream().filter(t -> "CREDIT".equals(t.getType()))
                .mapToLong(WalletTransaction::getAmountMinor).sum();
        long sumDebits = txns.stream().filter(t -> "DEBIT".equals(t.getType()))
                .mapToLong(WalletTransaction::getAmountMinor).sum();
        assertEquals(sumCredits - sumDebits, reloaded.getAvailableBalanceMinor());
    }

    @Test
    void escrowProjectionMatchesPendingBalance() {
        String tenantId = tenant();
        UUID sellerId = UUID.randomUUID();
        walletService.getOrCreateWallet(tenantId, "SELLER", sellerId, "VND", actorId);

        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        walletService.holdEscrowForSeller(tenantId, sellerId, Money.of(100_000, "VND"), "ORDER", order1, actorId, null);
        walletService.holdEscrowForSeller(tenantId, sellerId, Money.of(50_000, "VND"), "ORDER", order2, actorId, null);

        entityManager.clear();
        Wallet wallet = walletService.listWallets(tenantId, "SELLER", sellerId, "VND").get(0);
        assertEquals(150_000, wallet.getPendingBalanceMinor());

        walletService.releaseEscrowForSeller(tenantId, sellerId, Money.of(100_000, "VND"), "ORDER", order1, actorId, null);

        entityManager.clear();
        wallet = walletService.listWallets(tenantId, "SELLER", sellerId, "VND").get(0);
        assertEquals(100_000, wallet.getAvailableBalanceMinor());
        assertEquals(50_000, wallet.getPendingBalanceMinor());

        List<WalletTransaction> txns = WalletTransaction.list(
                "tenantId = ?1 and wallet.id = ?2 order by sequenceNo asc",
                tenantId, wallet.getId());
        long netAvailable = 0;
        long netPending = 0;
        for (WalletTransaction t : txns) {
            switch (t.getTransactionType()) {
                case "ESCROW_HOLD" -> netPending += "CREDIT".equals(t.getType()) ? t.getAmountMinor() : -t.getAmountMinor();
                case "ESCROW_RELEASE" -> { netPending -= t.getAmountMinor(); netAvailable += t.getAmountMinor(); }
                case "ESCROW_REFUND" -> netPending -= t.getAmountMinor();
                default -> netAvailable += "CREDIT".equals(t.getType()) ? t.getAmountMinor() : -t.getAmountMinor();
            }
        }
        assertEquals(wallet.getAvailableBalanceMinor(), netAvailable);
        assertEquals(wallet.getPendingBalanceMinor(), netPending);
    }

    @Test
    void zeroAdjustmentIsNoOp() {
        String tenantId = tenant();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
        assertDoesNotThrow(() ->
                walletService.adjustBalance(tenantId, wallet.getId(), 0, "zero", actorId));
        entityManager.clear();
        assertEquals(0, walletService.getWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertEquals(0, WalletTransaction.count("tenantId = ?1 and wallet.id = ?2", tenantId, wallet.getId()));
    }

    private WalletTransaction seedTransaction() {
        String tenantId = "invariant-" + UUID.randomUUID();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
        var mutation = walletService.creditAvailable(tenantId, wallet.getId(), Money.of(10_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, "seed");
        entityManager.clear();
        return WalletTransaction.findById(mutation.transaction().getId());
    }

    private String tenant() {
        return "invariant-" + UUID.randomUUID();
    }
}

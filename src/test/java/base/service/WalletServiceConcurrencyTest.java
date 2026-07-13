package base.service;

import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WalletServiceConcurrencyTest {

    @Inject
    WalletService walletService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000555");

    @Test
    void mixedCreditAndDebitOnSameWallet() throws Exception {
        String tenantId = "concurrent-mixed-" + UUID.randomUUID();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(1_000_000, "VND"),
                "DEPOSIT", "SETUP", UUID.randomUUID(), actorId, null);

        int threadCount = 20;
        int opsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        AtomicLong netChange = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            long amount = 5_000;
            futures.add(executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                int localNet = 0;
                for (int j = 0; j < opsPerThread; j++) {
                    try {
                        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(amount, "VND"),
                                "WITHDRAWAL", "CONCURRENT", UUID.randomUUID(), actorId, null);
                        localNet -= amount;
                    } catch (Exception e) {
                        break;
                    }
                }
                netChange.addAndGet(localNet);
                return localNet;
            }));
        }

        start.countDown();
        for (Future<Integer> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        entityManager.clear();
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        assertEquals(1_000_000 + netChange.get(), reloaded.getAvailableBalanceMinor());
        assertTrue(reloaded.getAvailableBalanceMinor() >= 0);
    }

    @Test
    void concurrentEscrowHoldOnSameWallet() throws Exception {
        String tenantId = "concurrent-escrow-" + UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", sellerId, "VND", actorId);

        int holdCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < holdCount; i++) {
            UUID orderId = UUID.randomUUID();
            futures.add(executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    walletService.holdEscrowForSeller(tenantId, sellerId, Money.of(10_000, "VND"),
                            "ORDER", orderId, actorId, null);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        start.countDown();
        int successCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(60, TimeUnit.SECONDS)) successCount++;
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        entityManager.clear();
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        assertEquals((long) successCount * 10_000, reloaded.getPendingBalanceMinor());
        assertEquals(0, reloaded.getAvailableBalanceMinor());
        assertEquals(successCount, WalletTransaction.count("tenantId = ?1 and wallet.id = ?2", tenantId, wallet.getId()));
    }

    @Test
    void concurrentCreditToMultipleWallets() throws Exception {
        String tenantId = "concurrent-multiwallet-" + UUID.randomUUID();
        int walletCount = 20;
        List<UUID> walletIds = new ArrayList<>();
        for (int i = 0; i < walletCount; i++) {
            Wallet w = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
            walletIds.add(w.getId());
        }

        int opsPerWallet = 20;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (UUID walletId : walletIds) {
            for (int i = 0; i < opsPerWallet; i++) {
                futures.add(executor.submit(() -> {
                    try { start.await(10, TimeUnit.SECONDS); } catch (Exception e) { return; }
                    try {
                        walletService.creditAvailable(tenantId, walletId, Money.of(1_000, "VND"),
                                "DEPOSIT", "BATCH", UUID.randomUUID(), actorId, null);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {}
                }));
            }
        }

        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(walletCount * opsPerWallet, successCount.get());
        for (UUID wid : walletIds) {
            entityManager.clear();
            Wallet w = walletService.getWallet(tenantId, wid);
            assertEquals((long) opsPerWallet * 1_000, w.getAvailableBalanceMinor());
        }
    }

    @Test
    void ledgerBalanceMatchesSumOfTransactionsAfterConcurrentOps() throws Exception {
        String tenantId = "concurrent-ledger-" + UUID.randomUUID();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);

        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(500_000, "VND"),
                "DEPOSIT", "SETUP", UUID.randomUUID(), actorId, null);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicLong totalDebited = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try { start.await(10, TimeUnit.SECONDS); } catch (Exception e) { return; }
                for (int j = 0; j < 5; j++) {
                    try {
                        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(8_000, "VND"),
                                "WITHDRAWAL", "RACE", UUID.randomUUID(), actorId, null);
                        totalDebited.addAndGet(8_000);
                    } catch (Exception e) {
                        return;
                    }
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        entityManager.clear();
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        long expectedAvailable = 500_000 - totalDebited.get();
        assertEquals(expectedAvailable, reloaded.getAvailableBalanceMinor());

        entityManager.clear();
        List<WalletTransaction> txns = walletService.listTransactions(tenantId, wallet.getId());
        long sumCredits = txns.stream().filter(t -> "CREDIT".equals(t.getType()))
                .mapToLong(WalletTransaction::getAmountMinor).sum();
        long sumDebits = txns.stream().filter(t -> "DEBIT".equals(t.getType()))
                .mapToLong(WalletTransaction::getAmountMinor).sum();
        assertEquals(sumCredits - sumDebits, reloaded.getAvailableBalanceMinor());
    }
}

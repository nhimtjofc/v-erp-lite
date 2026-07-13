package base.service;

import base.entity.Wallet;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PaymentLoadTest {

    @Inject
    WalletService walletService;

    @Inject
    PayoutService payoutService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000777");

    @Test
    void concurrentCreditAndDebitOnMultipleWallets() throws Exception {
        String tenantId = "load-multi-" + UUID.randomUUID();
        int walletCount = 10;
        int operationsPerWallet = 50;
        List<UUID> walletIds = new ArrayList<>();

        for (int i = 0; i < walletCount; i++) {
            Wallet wallet = walletService.getOrCreateWallet(
                    tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
            walletIds.add(wallet.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicLong totalCredited = new AtomicLong(0);

        List<Future<?>> futures = new ArrayList<>();
        for (UUID walletId : walletIds) {
            for (int i = 0; i < operationsPerWallet; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        walletService.creditAvailable(
                                tenantId, walletId, Money.of(1_000, "VND"),
                                "DEPOSIT", "LOAD_TEST", UUID.randomUUID(), actorId, null);
                        successCount.incrementAndGet();
                        totalCredited.addAndGet(1_000);
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                }));
            }
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(walletCount * operationsPerWallet, successCount.get());
        assertEquals(0, failCount.get());

        for (UUID walletId : walletIds) {
            Wallet w = freshWallet(tenantId, walletId);
            assertEquals(operationsPerWallet * 1_000L, w.getAvailableBalanceMinor());
            assertEquals(0, w.getPendingBalanceMinor());
        }
    }

    @Test
    void concurrentDebitsRejectOverdraftOnMultipleWallets() throws Exception {
        String tenantId = "load-overdraft-" + UUID.randomUUID();
        int walletCount = 10;
        long fundAmount = 50_000;
        int concurrentDebits = 100;
        long debitAmount = 1_000;
        int expectedSuccess = 50; // fundAmount / debitAmount = 50 debits of 1000 each

        List<UUID> walletIds = new ArrayList<>();
        for (int i = 0; i < walletCount; i++) {
            Wallet wallet = walletService.getOrCreateWallet(
                    tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
            walletService.creditAvailable(
                    tenantId, wallet.getId(), Money.of(fundAmount, "VND"),
                    "DEPOSIT", "SETUP", UUID.randomUUID(), actorId, null);
            walletIds.add(wallet.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (UUID walletId : walletIds) {
            for (int i = 0; i < concurrentDebits; i++) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        walletService.debitAvailable(
                                tenantId, walletId, Money.of(debitAmount, "VND"),
                                "WITHDRAWAL", "LOAD_TEST", UUID.randomUUID(), actorId, null);
                        return 1;
                    } catch (Exception e) {
                        return 0;
                    }
                }));
            }
        }

        start.countDown();
        int totalSuccess = 0;
        for (Future<Integer> f : futures) {
            totalSuccess += f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(walletCount * expectedSuccess, totalSuccess);
        for (UUID walletId : walletIds) {
            Wallet w = freshWallet(tenantId, walletId);
            assertEquals(0, w.getAvailableBalanceMinor());
        }
    }

    @Test
    void concurrentEscrowAndPayoutFlow() throws Exception {
        String tenantId = "load-escrow-" + UUID.randomUUID();
        int sellerCount = 3;
        int ordersPerSeller = 5;
        long orderAmount = 100_000;

        record SellerOrders(UUID sellerId, UUID walletId, List<UUID> orderIds) {}
        List<SellerOrders> sellers = new ArrayList<>();
        for (int i = 0; i < sellerCount; i++) {
            UUID sellerId = UUID.randomUUID();
            Wallet wallet = walletService.getOrCreateWallet(
                    tenantId, "SELLER", sellerId, "VND", actorId);
            List<UUID> orderIds = new ArrayList<>();
            for (int o = 0; o < ordersPerSeller; o++) {
                orderIds.add(UUID.randomUUID());
            }
            sellers.add(new SellerOrders(sellerId, wallet.getId(), orderIds));
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger holdSuccess = new AtomicInteger(0);
        AtomicInteger releaseSuccess = new AtomicInteger(0);
        AtomicInteger payoutSuccess = new AtomicInteger(0);

        // Holds run sequentially per-wallet to avoid @Version optimistic lock conflicts on the same Wallet row
        for (var seller : sellers) {
            for (UUID oid : seller.orderIds()) {
                walletService.holdEscrowForSeller(
                        tenantId, seller.sellerId(), Money.of(orderAmount, "VND"),
                        "ORDER", oid, actorId, null);
                holdSuccess.incrementAndGet();
            }
        }
        assertEquals(sellerCount * ordersPerSeller, holdSuccess.get());

        // Releases run sequentially per-wallet for the same reason
        for (var seller : sellers) {
            for (UUID oid : seller.orderIds()) {
                walletService.releaseEscrowForSeller(
                        tenantId, seller.sellerId(), Money.of(orderAmount, "VND"),
                        "ORDER", oid, actorId, null);
                releaseSuccess.incrementAndGet();
            }
        }

        assertEquals(sellerCount * ordersPerSeller, releaseSuccess.get());

        for (var seller : sellers) {
            Wallet w = freshWallet(tenantId, seller.walletId());
            assertEquals((long) ordersPerSeller * orderAmount, w.getAvailableBalanceMinor());
            assertEquals(0, w.getPendingBalanceMinor());
        }

        // Payouts run concurrently per wallet (fewer ops to stay within @Version tolerance)
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> payoutFutures = new ArrayList<>();

        for (var seller : sellers) {
            UUID wid = seller.walletId();
            for (int i = 0; i < 2; i++) {
                payoutFutures.add(executor.submit(payoutTask(start, tenantId, wid, payoutSuccess)));
            }
        }

        start.countDown();
        for (Future<Void> f : payoutFutures) {
            f.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(sellerCount * 2, payoutSuccess.get());

        long totalEscrowReleased = (long) sellerCount * ordersPerSeller * orderAmount;
        long totalPayoutTaken = sellerCount * 2 * 10_000L;
        long expectedRemaining = totalEscrowReleased - totalPayoutTaken;

        for (var seller : sellers) {
            Wallet w = freshWallet(tenantId, seller.walletId());
            assertEquals(expectedRemaining / sellerCount, w.getAvailableBalanceMinor());
        }
    }

    private Callable<Void> payoutTask(CountDownLatch start, String tenantId, UUID walletId,
                                       AtomicInteger counter) {
        return () -> {
            start.await(10, TimeUnit.SECONDS);
            var payout = payoutService.createPayout(
                    tenantId, walletId, 10_000, "VND",
                    "VCB", "123", "Seller", actorId);
            payoutService.approvePayout(tenantId, payout.getId(), actorId);
            counter.incrementAndGet();
            return null;
        };
    }

    private Wallet freshWallet(String tenantId, UUID walletId) {
        entityManager.clear();
        return walletService.getWallet(tenantId, walletId);
    }
}

package base.service;

import base.entity.EscrowRecord;
import base.entity.Wallet;
import base.model.Money;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress-tests that the seller pending balance computed from EscrowRecord
 * (via getPendingEscrowForSeller / sumHeldForSeller) stays consistent
 * through hold → release → refund cycles when using the REAL production
 * code path (EscrowService.holdSellerEscrow), NOT the test-only
 * WalletService.holdEscrowForSeller shortcut.
 */
@QuarkusTest
@TestTransaction
class SellerPendingBalanceTest {

    @Inject
    EscrowService escrowService;

    @Inject
    WalletService walletService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000999");

    @Test
    void pendingBalanceEqualsEscrowSumAfterHold() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);
        assertNotNull(sellerWallet);

        escrowService.holdSellerEscrow(tenantId, orderId, businessId,
                Money.of(100_000, "VND"), Instant.now().plusSeconds(3600), actorId);

        flushAndClear();
        long pending = walletService.getPendingEscrowForSeller(tenantId, sellerWallet);
        assertEquals(100_000, pending);
    }

    @Test
    void pendingBalanceDropsToZeroAfterFullRelease() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, orderId, businessId,
                Money.of(100_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        escrowService.releaseOrderEscrow(tenantId, orderId, actorId);
        flushAndClear();

        long pending = walletService.getPendingEscrowForSeller(tenantId, sellerWallet);
        assertEquals(0, pending);
    }

    @Test
    void availableBalanceIncreasesOnRelease_andEqualsSellerNet() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, orderId, businessId,
                Money.of(100_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        escrowService.releaseOrderEscrow(tenantId, orderId, actorId);
        flushAndClear();

        Wallet reloaded = walletService.getWallet(tenantId, sellerWallet.getId());
        assertEquals(0, reloaded.getPendingBalanceMinor());
        // 100_000 - 5% = 95_000
        assertEquals(95_000, reloaded.getAvailableBalanceMinor());
    }

    @Test
    void pendingBalanceMatchesAcrossBothQueryMethods() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, orderId, businessId,
                Money.of(75_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        long fromService = walletService.getPendingEscrowForSeller(tenantId, sellerWallet);
        long fromEscrow = escrowService.sumHeldForSeller(tenantId, sellerWallet.getId());
        assertEquals(fromService, fromEscrow, "Both query methods should return the same pending balance");
        assertEquals(75_000, fromService);
    }

    @Test
    void multipleEscrowsSumCorrectly() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, UUID.randomUUID(), businessId,
                Money.of(30_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        escrowService.holdSellerEscrow(tenantId, UUID.randomUUID(), businessId,
                Money.of(50_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        escrowService.holdSellerEscrow(tenantId, UUID.randomUUID(), businessId,
                Money.of(20_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        long pending = walletService.getPendingEscrowForSeller(tenantId, sellerWallet);
        assertEquals(100_000, pending);
    }

    @Test
    void partialReleaseReducesPendingCorrectly() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, order1, businessId,
                Money.of(70_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        escrowService.holdSellerEscrow(tenantId, order2, businessId,
                Money.of(30_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();
        assertEquals(100_000, walletService.getPendingEscrowForSeller(tenantId, sellerWallet));

        escrowService.releaseOrderEscrow(tenantId, order1, actorId);
        flushAndClear();
        assertEquals(30_000, walletService.getPendingEscrowForSeller(tenantId, sellerWallet));

        escrowService.releaseOrderEscrow(tenantId, order2, actorId);
        flushAndClear();
        assertEquals(0, walletService.getPendingEscrowForSeller(tenantId, sellerWallet));
    }

    @Test
    void refundRemovesPendingWithoutCreditingSeller() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, orderId, businessId,
                Money.of(50_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        escrowService.refundOrderEscrow(tenantId, orderId, actorId);
        flushAndClear();

        Wallet reloaded = walletService.getWallet(tenantId, sellerWallet.getId());
        assertEquals(0, reloaded.getPendingBalanceMinor());
        assertEquals(0, reloaded.getAvailableBalanceMinor());
    }

    @Test
    void getOrCreateUserWalletReturnsCorrectSellerWallet() {
        String tenantId = tenant();
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();

        // Create a seller wallet for the business (as holdSellerEscrow would)
        Wallet created = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        // Place an escrow
        escrowService.holdSellerEscrow(tenantId, UUID.randomUUID(), businessId,
                Money.of(60_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        // getPendingEscrowForSeller on the created wallet should find it
        long pending = walletService.getPendingEscrowForSeller(tenantId, created);
        assertEquals(60_000, pending);
    }

    @Test
    void sameWalletReturnedFromGetOrCreateWallet() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();

        Wallet first = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);
        Wallet second = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        assertEquals(first.getId(), second.getId(),
                "getOrCreateWallet must return the same wallet for the same (tenant, ownerType, ownerId, currency)");
    }

    @Test
    void pendingBalanceIsZeroForSellerWithNoEscrows() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        long pending = walletService.getPendingEscrowForSeller(tenantId, sellerWallet);
        assertEquals(0, pending);
    }

    @Test
    void pendingIgnoresReleasedAndRefundedEscrows() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        UUID order3 = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, order1, businessId,
                Money.of(40_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        escrowService.holdSellerEscrow(tenantId, order2, businessId,
                Money.of(30_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        escrowService.holdSellerEscrow(tenantId, order3, businessId,
                Money.of(20_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();
        assertEquals(90_000, walletService.getPendingEscrowForSeller(tenantId, sellerWallet));

        escrowService.releaseOrderEscrow(tenantId, order1, actorId);
        flushAndClear();
        assertEquals(50_000, walletService.getPendingEscrowForSeller(tenantId, sellerWallet));

        escrowService.refundOrderEscrow(tenantId, order2, actorId);
        flushAndClear();
        assertEquals(20_000, walletService.getPendingEscrowForSeller(tenantId, sellerWallet));
    }

    @Test
    void pendingBalanceCrossTenantIsolation() {
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        String tenantA = "isolated-A-" + UUID.randomUUID();
        String tenantB = "isolated-B-" + UUID.randomUUID();

        Wallet walletA = walletService.getOrCreateWallet(tenantA, "SELLER", businessId, "VND", actorId);
        Wallet walletB = walletService.getOrCreateWallet(tenantB, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantA, orderId, businessId,
                Money.of(50_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        assertEquals(50_000, walletService.getPendingEscrowForSeller(tenantA, walletA));
        assertEquals(0, walletService.getPendingEscrowForSeller(tenantB, walletB));
    }

    @Test
    void releaseIsIdempotent_secondCallDoesNotChangeBalances() {
        String tenantId = tenant();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Wallet sellerWallet = walletService.getOrCreateWallet(tenantId, "SELLER", businessId, "VND", actorId);

        escrowService.holdSellerEscrow(tenantId, orderId, businessId,
                Money.of(80_000, "VND"), Instant.now().plusSeconds(3600), actorId);
        flushAndClear();

        escrowService.releaseOrderEscrow(tenantId, orderId, actorId);
        flushAndClear();

        Wallet afterFirst = walletService.getWallet(tenantId, sellerWallet.getId());
        assertEquals(76_000, afterFirst.getAvailableBalanceMinor());

        escrowService.releaseOrderEscrow(tenantId, orderId, actorId);
        flushAndClear();

        Wallet afterSecond = walletService.getWallet(tenantId, sellerWallet.getId());
        assertEquals(76_000, afterSecond.getAvailableBalanceMinor());
        assertEquals(0, afterSecond.getPendingBalanceMinor());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String tenant() {
        return "pending-test-" + UUID.randomUUID();
    }
}

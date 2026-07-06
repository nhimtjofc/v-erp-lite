package base.service;

import base.entity.EscrowRecord;
import base.entity.PaymentIntent;
import base.entity.PaymentTransaction;
import base.entity.Wallet;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class Dev2PaymentCoreServiceTest {

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentService paymentService;

    @Inject
    EscrowService escrowService;

    @Inject
    WalletService walletService;

    @Inject
    Dev2PaymentTestDataFactory testDataFactory;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Test
    void paymentIntentLifecycleCreatesEscrowAndPlatformCommissionOnce() {
        String tenantId = tenant();
        Dev2PaymentTestDataFactory.SeedOrder seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(100_000));
        String idempotencyKey = "intent-" + UUID.randomUUID();

        PaymentIntent first = paymentIntentService.createIntent(
                tenantId,
                seed.order().getId(),
                seed.customer().getId(),
                100_000,
                "VND",
                idempotencyKey,
                actorId
        );
        PaymentIntent second = paymentIntentService.createIntent(
                tenantId,
                seed.order().getId(),
                seed.customer().getId(),
                100_000,
                "VND",
                idempotencyKey,
                actorId
        );
        assertEquals(first.getId(), second.getId());

        paymentIntentService.confirmIntent(tenantId, first.getId(), actorId);
        PaymentIntent succeeded = paymentIntentService.markSucceeded(
                tenantId,
                first.getId(),
                "mock",
                "gw-" + UUID.randomUUID(),
                actorId
        );
        assertEquals("SUCCEEDED", succeeded.getStatus());

        List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, seed.order().getId());
        assertEquals(1, escrows.size());
        assertEquals("HELD", escrows.get(0).getStatus());
        assertEquals(95_000, escrows.get(0).getAmountMinor());

        Wallet sellerWallet = walletService.getWallet(tenantId, escrows.get(0).getSellerWallet().getId());
        assertEquals(0, sellerWallet.getAvailableBalanceMinor());
        assertEquals(95_000, sellerWallet.getPendingBalanceMinor());

        Wallet platformWallet = walletService.listWallets(
                tenantId,
                "PLATFORM",
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "VND"
        ).get(0);
        assertEquals(5_000, platformWallet.getAvailableBalanceMinor());

        paymentService.settleSucceededIntent(succeeded, actorId);
        assertEquals(1, escrowService.listByOrder(tenantId, seed.order().getId()).size());
        assertEquals(1, PaymentTransaction.count("tenantId = ?1 and paymentIntent.id = ?2", tenantId, first.getId()));
    }

    @Test
    void escrowReleaseAndRefundMovePendingBalanceExactlyOnce() {
        String tenantId = tenant();
        UUID sellerId = UUID.randomUUID();
        UUID releaseOrderId = UUID.randomUUID();
        UUID refundOrderId = UUID.randomUUID();

        EscrowRecord releaseEscrow = escrowService.holdSellerEscrow(
                tenantId,
                releaseOrderId,
                sellerId,
                Money.of(40_000, "VND"),
                Instant.now(),
                actorId
        );
        escrowService.releaseOrderEscrow(tenantId, releaseOrderId, actorId);
        escrowService.releaseOrderEscrow(tenantId, releaseOrderId, actorId);

        Wallet releaseWallet = walletService.getWallet(tenantId, releaseEscrow.getSellerWallet().getId());
        assertEquals(40_000, releaseWallet.getAvailableBalanceMinor());
        assertEquals(0, releaseWallet.getPendingBalanceMinor());
        assertEquals("RELEASED", escrowService.listByOrder(tenantId, releaseOrderId).get(0).getStatus());

        EscrowRecord refundEscrow = escrowService.holdSellerEscrow(
                tenantId,
                refundOrderId,
                sellerId,
                Money.of(25_000, "VND"),
                Instant.now(),
                actorId
        );
        escrowService.refundOrderEscrow(tenantId, refundOrderId, actorId);
        escrowService.refundOrderEscrow(tenantId, refundOrderId, actorId);

        Wallet refundWallet = walletService.getWallet(tenantId, refundEscrow.getSellerWallet().getId());
        assertEquals(40_000, refundWallet.getAvailableBalanceMinor());
        assertEquals(0, refundWallet.getPendingBalanceMinor());
        assertEquals("REFUNDED", escrowService.listByOrder(tenantId, refundOrderId).get(0).getStatus());
    }

    @Test
    void paymentCoreRejectsInvalidStatesAndMismatchedAmounts() {
        String tenantId = tenant();
        Dev2PaymentTestDataFactory.SeedOrder seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(10_000));
        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId,
                seed.order().getId(),
                seed.customer().getId(),
                9_000,
                "VND",
                "intent-" + UUID.randomUUID(),
                actorId
        );

        assertThrows(BadRequestException.class, () -> paymentIntentService.markSucceeded(
                tenantId,
                intent.getId(),
                "MOCK",
                "gw-" + UUID.randomUUID(),
                actorId
        ));

        PaymentIntent cancelled = paymentIntentService.cancelIntent(tenantId, intent.getId(), actorId);
        assertEquals("CANCELLED", cancelled.getStatus());
        assertThrows(BadRequestException.class, () -> paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId));
    }

    private String tenant() {
        return "dev2-payment-" + UUID.randomUUID();
    }
}

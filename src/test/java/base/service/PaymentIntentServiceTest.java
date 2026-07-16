package base.service;

import base.entity.PaymentIntent;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PaymentIntentServiceTest {

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    WalletService walletService;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000222");

    // ─── State Machine ──────────────────────────────────────────────

    @Test
    void intentLifecycleValidTransitions() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));

        PaymentIntent intent = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", "sm-" + UUID.randomUUID(), actorId);
        assertEquals("REQUIRES_PAYMENT_METHOD", intent.getStatus());

        intent = paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);
        assertEquals("PROCESSING", intent.getStatus());

        intent = paymentIntentService.markSucceeded(tenantId, intent.getId(), "MOCK",
                "gw-sm-" + UUID.randomUUID(), actorId);
        assertEquals("SUCCEEDED", intent.getStatus());
    }

    @Test
    void cancelledIntentRejectsConfirmAndMarkSucceeded() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", "cancel-" + UUID.randomUUID(), actorId);

        paymentIntentService.cancelIntent(tenantId, intent.getId(), actorId);

        assertThrows(BadRequestException.class,
                () -> paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId));
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.markSucceeded(tenantId, intent.getId(), "MOCK",
                        "gw-cancelled", actorId));
    }

    @Test
    void succeededIntentRejectsFurtherTransitions() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", "succeeded-" + UUID.randomUUID(), actorId);

        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);
        paymentIntentService.markSucceeded(tenantId, intent.getId(), "MOCK",
                "gw-final", actorId);

        assertThrows(BadRequestException.class,
                () -> paymentIntentService.markFailed(tenantId, intent.getId(), "MOCK",
                        "gw-late", actorId));
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.cancelIntent(tenantId, intent.getId(), actorId));
    }

    @Test
    void failedIntentRejectsMarkSucceeded() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", "retry-" + UUID.randomUUID(), actorId);

        PaymentIntent failedIntent = paymentIntentService.markFailed(tenantId, intent.getId(), "MOCK",
                "gw-fail-1", actorId);
        assertEquals("FAILED", failedIntent.getStatus());

        UUID failedIntentId = failedIntent.getId();
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.markSucceeded(tenantId, failedIntentId, "MOCK",
                        "gw-fail-2", actorId));
    }

    // ─── Idempotency ────────────────────────────────────────────────

    @Test
    void sameIdempotencyKeyReturnsSameIntent() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        String idempotencyKey = "idem-" + UUID.randomUUID();

        PaymentIntent first = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", idempotencyKey, actorId);
        PaymentIntent second = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", idempotencyKey, actorId);

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getStatus(), second.getStatus());
    }

    @Test
    void listOrderIntentsIsScopedToTenantAndOrder() {
        String tenantId = tenant();
        var firstOrder = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        var secondOrder = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("25000"));

        PaymentIntent first = paymentIntentService.createIntent(
                tenantId,
                firstOrder.order().getId(),
                firstOrder.customer().getId(),
                50_000,
                "VND",
                "list-first-" + UUID.randomUUID(),
                actorId
        );
        paymentIntentService.createIntent(
                tenantId,
                secondOrder.order().getId(),
                secondOrder.customer().getId(),
                25_000,
                "VND",
                "list-second-" + UUID.randomUUID(),
                actorId
        );

        List<PaymentIntent> intents = paymentIntentService.listOrderIntents(
                tenantId,
                firstOrder.order().getId()
        );

        assertEquals(1, intents.size());
        assertEquals(first.getId(), intents.getFirst().getId());
        assertTrue(paymentIntentService.listOrderIntents(
                "other-" + tenantId,
                firstOrder.order().getId()
        ).isEmpty());
    }

    // ─── Split Payment / Settlement ─────────────────────────────────

    @Test
    void settleSucceededIntentRunsOnlyOnce() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("100000"));
        PaymentIntent intent = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 100_000, "VND", "settle-" + UUID.randomUUID(), actorId);

        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);
        intent = paymentIntentService.markSucceeded(tenantId, intent.getId(), "MOCK",
                "gw-settle-" + UUID.randomUUID(), actorId);
        assertEquals("SUCCEEDED", intent.getStatus());

        var escrows = paymentService.settleSucceededIntent(intent, actorId);
        assertEquals(1, escrows.size(), "Should create one escrow for the seller");

        var escrowsAgain = paymentService.settleSucceededIntent(intent, actorId);
        assertEquals(1, escrowsAgain.size(), "Re-settle should return existing escrows without side effects");

        assertEquals(escrows.get(0).getId(), escrowsAgain.get(0).getId());

        entityManager.clear();
        var platformWallets = walletService.listWallets(tenantId, "PLATFORM",
                UUID.fromString("00000000-0000-0000-0000-000000000000"), "VND");
        assertEquals(1, platformWallets.size());
        assertEquals(5_000, platformWallets.get(0).getAvailableBalanceMinor(),
                "Platform commission should only be credited once");
    }

    // ─── Validation ─────────────────────────────────────────────────

    @Test
    void createIntentRequiresOrderAndCustomer() {
        String tenantId = tenant();
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.createIntent(tenantId, null, UUID.randomUUID(),
                        100, "VND", "key", actorId));
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.createIntent(tenantId, UUID.randomUUID(), null,
                        100, "VND", "key", actorId));
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.createIntent(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                        100, "VND", null, actorId));
        assertThrows(BadRequestException.class,
                () -> paymentIntentService.createIntent(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                        100, "VND", "", actorId));
    }

    @Test
    void getIntentNotFoundForWrongTenant() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND", "isolation-" + UUID.randomUUID(), actorId);

        assertThrows(NotFoundException.class,
                () -> paymentIntentService.getIntent("other-" + tenantId, intent.getId()));
    }

    @jakarta.inject.Inject
    jakarta.persistence.EntityManager entityManager;

    private String tenant() {
        return "intent-test-" + UUID.randomUUID();
    }
}

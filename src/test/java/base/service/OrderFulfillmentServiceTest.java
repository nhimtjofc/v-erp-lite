package base.service;

import base.entity.EscrowRecord;
import base.entity.Notification;
import base.entity.Order;
import base.entity.PaymentIntent;
import base.entity.PaymentRefund;
import base.entity.User;
import base.entity.Wallet;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OrderFulfillmentServiceTest {

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    OrderFulfillmentService fulfillmentService;

    @Inject
    EscrowService escrowService;

    @Inject
    WalletService walletService;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000444");

    @Test
    void acceptanceKeepsEscrowHeldAndRejectsAnotherOwner() {
        String tenantId = tenant();
        UUID ownerId = UUID.randomUUID();
        PaymentTestDataFactory.SeedOrder seed = paidOrder(tenantId, ownerId, 100_000);
        User owner = user(ownerId);

        Order accepted = fulfillmentService.decide(
                tenantId, seed.order().getId(), owner, "ACCEPT", null, actorId);

        assertEquals("OWNER_ACCEPTED", accepted.getStatus());
        EscrowRecord escrow = escrowService.listByOrder(tenantId, seed.order().getId()).getFirst();
        assertEquals("HELD", escrow.getStatus());
        Wallet wallet = walletService.getWallet(tenantId, escrow.getSellerWallet().getId());
        assertEquals(0, wallet.getAvailableBalanceMinor());
        assertEquals(95_000, wallet.getPendingBalanceMinor());
        assertEquals(1, Notification.count("tenantId = ?1 and orderId = ?2", tenantId, seed.order().getId()));

        PaymentTestDataFactory.SeedOrder otherSeed = paidOrder(tenant(), UUID.randomUUID(), 50_000);
        assertThrows(ForbiddenException.class, () -> fulfillmentService.decide(
                otherSeed.order().getTenantId(), otherSeed.order().getId(), owner, "ACCEPT", null, actorId));
    }

    @Test
    void declineRefundsOriginalPaymentAndUnwindsEscrowAndCommission() {
        String tenantId = tenant();
        UUID ownerId = UUID.randomUUID();
        PaymentTestDataFactory.SeedOrder seed = paidOrder(tenantId, ownerId, 100_000);

        Order declined = fulfillmentService.decide(
                tenantId, seed.order().getId(), user(ownerId), "DECLINE", "Out of stock", actorId);

        assertEquals("OWNER_DECLINED", declined.getStatus());
        List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, seed.order().getId());
        assertEquals("REFUNDED", escrows.getFirst().getStatus());
        Wallet sellerWallet = walletService.getWallet(tenantId, escrows.getFirst().getSellerWallet().getId());
        assertEquals(0, sellerWallet.getAvailableBalanceMinor());
        assertEquals(0, sellerWallet.getPendingBalanceMinor());
        assertEquals(1, PaymentRefund.count("tenantId = ?1 and orderId = ?2", tenantId, seed.order().getId()));
        assertEquals(1, Notification.count("tenantId = ?1 and orderId = ?2", tenantId, seed.order().getId()));
    }

    @Test
    void expiredPaidOrderIsAutoDeclinedWithSystemReason() {
        String tenantId = tenant();
        UUID ownerId = UUID.randomUUID();
        PaymentTestDataFactory.SeedOrder seed = paidOrder(tenantId, ownerId, 50_000);
        QuarkusTransaction.requiringNew().run(() -> {
            Order order = Order.findById(seed.order().getId());
            order.setOwnerDecisionDeadline(Instant.now().minusSeconds(1));
        });

        assertTrue(fulfillmentService.autoDecline(tenantId, seed.order().getId(), Instant.now()));
        Order declined = Order.findById(seed.order().getId());
        assertEquals("OWNER_DECLINED", declined.getStatus());
        assertTrue(base.entity.OrderHistory.count(
                "tenantId = ?1 and order.id = ?2 and note = ?3",
                tenantId, seed.order().getId(), OrderFulfillmentService.NO_RESPONSE_REASON) > 0);
    }

    private PaymentTestDataFactory.SeedOrder paidOrder(String tenantId, UUID ownerId, long amountMinor) {
        PaymentTestDataFactory.SeedOrder seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(amountMinor));
        QuarkusTransaction.requiringNew().run(() -> {
            base.entity.Business seller = base.entity.Business.findById(seed.seller().getId());
            seller.setCreatedBy(ownerId);
        });
        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(), amountMinor, "VND",
                "owner-flow-" + UUID.randomUUID(), actorId);
        paymentIntentService.markSucceeded(
                tenantId, intent.getId(), "MOCK", "owner-flow-gw-" + UUID.randomUUID(), actorId);
        return seed;
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private String tenant() {
        return "owner-flow-" + UUID.randomUUID();
    }
}

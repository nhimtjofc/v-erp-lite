package base.service;

import base.entity.EscrowRecord;
import base.entity.LedgerCheckpoint;
import base.entity.Order;
import base.entity.PaymentIntent;
import base.entity.PayoutRequest;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PaymentFlowRegressionTest {

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentService paymentService;

    @Inject
    EscrowService escrowService;

    @Inject
    WalletService walletService;

    @Inject
    PayoutService payoutService;

    @Inject
    LedgerCheckpointService checkpointService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000999");

    @Test
    void fullFlowFromOrderToPayout() {
        String tenantId = tenant();

        // 1. Buyer creates order (via PaymentTestDataFactory)
        PaymentTestDataFactory.SeedOrder seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(100_000));
        assertEquals("PENDING", seed.order().getStatus());

        // 2. Payment intent created, confirmed, and marked succeeded
        String idempotencyKey = "regression-" + UUID.randomUUID();
        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId,
                seed.order().getId(),
                seed.customer().getId(),
                100_000,
                "VND",
                idempotencyKey,
                actorId
        );
        assertEquals("REQUIRES_PAYMENT_METHOD", intent.getStatus());
        assertEquals(100_000, intent.getAmountMinor());

        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);
        PaymentIntent succeeded = paymentIntentService.markSucceeded(
                tenantId,
                intent.getId(),
                "MOCK_GATEWAY",
                "gw-txn-" + UUID.randomUUID(),
                actorId
        );
        assertEquals("SUCCEEDED", succeeded.getStatus());
        Order paidOrder = Order.findById(seed.order().getId());
        assertEquals("PAID_ESCROW", paidOrder.getStatus());

        // 3. Verify escrow created (seller gets 95_000 after 5% commission, platform gets 5_000)
        List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, seed.order().getId());
        assertEquals(1, escrows.size(), "One escrow record for the seller");
        EscrowRecord escrow = escrows.get(0);
        assertEquals("HELD", escrow.getStatus());
        assertEquals(95_000, escrow.getAmountMinor());

        Wallet sellerWallet = freshWallet(tenantId, escrow.getSellerWallet().getId());
        assertEquals(0, sellerWallet.getAvailableBalanceMinor());
        assertEquals(95_000, sellerWallet.getPendingBalanceMinor());

        List<Wallet> platformWallets = walletService.listWallets(
                tenantId,
                "PLATFORM",
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "VND"
        );
        assertEquals(1, platformWallets.size());
        Wallet platformWallet = freshWallet(tenantId, platformWallets.get(0).getId());
        assertEquals(5_000, platformWallet.getAvailableBalanceMinor());
        assertEquals(0, platformWallet.getPendingBalanceMinor());

        // 4. Complete order -> release escrow to seller wallet
        escrowService.releaseOrderEscrow(tenantId, seed.order().getId(), actorId);

        sellerWallet = freshWallet(tenantId, escrow.getSellerWallet().getId());
        assertEquals(95_000, sellerWallet.getAvailableBalanceMinor());
        assertEquals(0, sellerWallet.getPendingBalanceMinor());

        escrows = escrowService.listByOrder(tenantId, seed.order().getId());
        assertEquals(1, escrows.size());
        assertEquals("RELEASED", escrows.get(0).getStatus());

        // 5. Verify ledger checkpoints match wallet balances
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, escrow.getSellerWallet().getId());
        assertNotNull(checkpoint);
        assertEquals(95_000, checkpoint.getAvailableBalanceMinor());
        assertEquals(0, checkpoint.getPendingBalanceMinor());

        // 6. Seller creates payout request for 40_000
        PayoutRequest payout = payoutService.createPayout(
                tenantId,
                sellerWallet.getId(),
                40_000,
                "VND",
                "Vietcombank",
                "0123456789",
                "Test Seller",
                actorId
        );
        assertEquals("FLAGGED", payout.getStatus());
        assertEquals(40_000, payout.getAmountMinor());

        // 7. Admin approves payout
        PayoutRequest approved = payoutService.approvePayout(tenantId, payout.getId(), actorId);
        assertEquals("PAID", approved.getStatus());

        // 8. Verify final balances: 95_000 - 40_000 = 55_000
        sellerWallet = freshWallet(tenantId, escrow.getSellerWallet().getId());
        assertEquals(55_000, sellerWallet.getAvailableBalanceMinor());
        assertEquals(0, sellerWallet.getPendingBalanceMinor());

        // 9. Verify ledger transactions
        List<WalletTransaction> transactions = walletService.listTransactions(tenantId, sellerWallet.getId());
        assertEquals(3, transactions.size(), "ESCROW_HOLD + ESCROW_RELEASE + PAYOUT debit");

        WalletTransaction payoutTxn = transactions.get(0); // most recent first (DESC)
        assertEquals("DEBIT", payoutTxn.getType());
        assertEquals("PAYOUT", payoutTxn.getTransactionType());
        assertEquals("PAYOUT_REQUEST", payoutTxn.getReferenceType());
        assertEquals(payout.getId(), payoutTxn.getReferenceId());
        assertEquals(40_000, payoutTxn.getAmountMinor());
        assertEquals(3, payoutTxn.getSequenceNo());

        // 10. Verify platform wallet still has 5_000 (commission unaffected by payout)
        platformWallet = freshWallet(tenantId, platformWallets.get(0).getId());
        assertEquals(5_000, platformWallet.getAvailableBalanceMinor());

        // 11. Idempotency: re-approving same payout returns the same result
        PayoutRequest reapproved = payoutService.approvePayout(tenantId, payout.getId(), actorId);
        assertEquals("PAID", reapproved.getStatus());
        sellerWallet = freshWallet(tenantId, escrow.getSellerWallet().getId());
        assertEquals(55_000, sellerWallet.getAvailableBalanceMinor());

        // 12. Insufficient funds: remaining is 55_000, approving a 60_000 payout must fail
        PayoutRequest oversizedPayout = payoutService.createPayout(
                tenantId, sellerWallet.getId(), 60_000, "VND",
                "Vietcombank", "0123456789", "Test Seller", actorId
        );
        assertThrows(BadRequestException.class,
                () -> payoutService.approvePayout(tenantId, oversizedPayout.getId(), actorId));

        // 13. Tenant isolation: other tenant cannot see this payout or wallet
        String otherTenant = tenant();
        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> payoutService.getPayout(otherTenant, payout.getId()));
    }

    private String tenant() {
        return "regression-" + UUID.randomUUID();
    }

    private Wallet freshWallet(String tenantId, UUID walletId) {
        entityManager.clear();
        return walletService.getWallet(tenantId, walletId);
    }
}

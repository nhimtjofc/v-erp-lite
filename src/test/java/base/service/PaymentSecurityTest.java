package base.service;

import base.entity.PaymentIntent;
import base.entity.PayoutRequest;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PaymentSecurityTest {

    @Inject
    WalletService walletService;

    @Inject
    PayoutService payoutService;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    MoneyService moneyService;

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000888");

    // ─── Parameter Tampering Tests ─────────────────────────────────

    @Test
    void currencyMismatchRejectedOnCredit() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        assertThrows(BadRequestException.class, () ->
                walletService.creditAvailable(tenantId, wallet.getId(), Money.of(100, "USD"),
                        "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null));
    }

    @Test
    void currencyMismatchRejectedOnDebit() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 10_000);
        assertThrows(BadRequestException.class, () ->
                walletService.debitAvailable(tenantId, wallet.getId(), Money.of(100, "USD"),
                        "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null));
    }

    @Test
    void zeroAmountRejectedOnCredit() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        assertThrows(BadRequestException.class, () ->
                walletService.creditAvailable(tenantId, wallet.getId(), Money.of(0, "VND"),
                        "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null));
    }

    @Test
    void negativeAmountRejected() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        assertThrows(BadRequestException.class, () ->
                walletService.creditAvailable(tenantId, wallet.getId(), Money.of(-1, "VND"),
                        "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null));
    }

    @Test
    void nullOwnerTypeRejectedOnWalletCreate() {
        String tenantId = tenant();
        assertThrows(BadRequestException.class, () ->
                walletService.getOrCreateWallet(tenantId, null, UUID.randomUUID(), "VND", actorId));
    }

    @Test
    void nullOwnerIdRejectedOnWalletCreate() {
        String tenantId = tenant();
        assertThrows(BadRequestException.class, () ->
                walletService.getOrCreateWallet(tenantId, "SELLER", null, "VND", actorId));
    }

    @Test
    void nullCurrencyDefaultsToVndOnWalletCreate() {
        String tenantId = tenant();
        Wallet wallet = walletService.getOrCreateWallet(
                tenantId, "SELLER", UUID.randomUUID(), null, actorId);
        assertEquals("VND", wallet.getCurrency());
    }

    @Test
    void wrongCurrencyPayoutRejected() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 100_000);
        assertThrows(BadRequestException.class, () ->
                payoutService.createPayout(tenantId, wallet.getId(), 10_000, "USD",
                        "VCB", "123", "Seller", actorId));
    }

    @Test
    void emptyBankDetailsRejectedOnPayout() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 100_000);
        assertThrows(BadRequestException.class, () ->
                payoutService.createPayout(tenantId, wallet.getId(), 10_000, "VND",
                        "", "123", "Seller", actorId));
        assertThrows(BadRequestException.class, () ->
                payoutService.createPayout(tenantId, wallet.getId(), 10_000, "VND",
                        "VCB", "", "Seller", actorId));
        assertThrows(BadRequestException.class, () ->
                payoutService.createPayout(tenantId, wallet.getId(), 10_000, "VND",
                        "VCB", "123", "", actorId));
    }

    @Test
    void cancelledIntentCannotTransitionToSucceeded() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("100000"));
        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                100_000, "VND", "tamper-" + UUID.randomUUID(), actorId);
        paymentIntentService.cancelIntent(tenantId, intent.getId(), actorId);

        assertThrows(BadRequestException.class, () ->
                paymentIntentService.markSucceeded(tenantId, intent.getId(), "MOCK",
                        "gw-tampered", actorId));
        assertThrows(BadRequestException.class, () ->
                paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId));
    }

    @Test
    void cannotUseOtherTenantWallet() {
        String tenantA = tenant();
        String tenantB = tenant();
        Wallet wallet = walletService.getOrCreateWallet(tenantA, "SELLER", UUID.randomUUID(), "VND", actorId);
        assertThrows(NotFoundException.class, () ->
                walletService.getWallet(tenantB, wallet.getId()));
        assertThrows(NotFoundException.class, () ->
                walletService.debitAvailable(tenantB, wallet.getId(), Money.of(1, "VND"),
                        "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null));
    }

    @Test
    void overDebitRejected() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 5_000);
        assertThrows(BadRequestException.class, () ->
                walletService.debitAvailable(tenantId, wallet.getId(), Money.of(10_000, "VND"),
                        "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null));
    }

    // ─── Replay Attack Tests ───────────────────────────────────────

    @Test
    void sameIdempotencyKeyReturnsSameIntent() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        String idempotencyKey = "replay-" + UUID.randomUUID();

        PaymentIntent first = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                50_000, "VND", idempotencyKey, actorId);
        PaymentIntent second = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                50_000, "VND", idempotencyKey, actorId);
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void sameReferenceIdBlocksDoubleCredit() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        UUID refId = UUID.randomUUID();

        WalletService.WalletMutation first = walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(20_000, "VND"),
                "DEPOSIT", "ORDER", refId, actorId, null);
        WalletService.WalletMutation second = walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(20_000, "VND"),
                "DEPOSIT", "ORDER", refId, actorId, null);

        assertEquals(first.transaction().getId(), second.transaction().getId());
        assertEquals(20_000, freshBalance(tenantId, wallet.getId()));
        assertEquals(1, countTxns(tenantId, wallet.getId()));
    }

    @Test
    void sameReferenceIdBlocksDoubleDebit() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 50_000);
        UUID refId = UUID.randomUUID();

        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(10_000, "VND"),
                "WITHDRAWAL", "PAYOUT", refId, actorId, null);
        walletService.debitAvailable(tenantId, wallet.getId(), Money.of(10_000, "VND"),
                "WITHDRAWAL", "PAYOUT", refId, actorId, null);

        assertEquals(40_000, freshBalance(tenantId, wallet.getId()));
        assertEquals(2, countTxns(tenantId, wallet.getId()));
    }

    @Test
    void approvePayoutTwiceDoesNotDoubleDebit() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 100_000);
        PayoutRequest payout = payoutService.createPayout(
                tenantId, wallet.getId(), 40_000, "VND", "VCB", "123", "Seller", actorId);

        payoutService.approvePayout(tenantId, payout.getId(), actorId);
        payoutService.approvePayout(tenantId, payout.getId(), actorId);

        assertEquals(60_000, freshBalance(tenantId, wallet.getId()));
        assertEquals(2, countTxns(tenantId, wallet.getId()));
    }

    @Test
    void releaseEscrowTwiceDoesNotDoubleCredit() {
        String tenantId = tenant();
        UUID sellerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        walletService.holdEscrowForSeller(tenantId, sellerId, Money.of(50_000, "VND"),
                "ORDER", orderId, actorId, null);

        var wallet = walletService.listWallets(tenantId, "SELLER", sellerId, "VND").get(0);
        assertEquals(50_000, wallet.getPendingBalanceMinor());

        walletService.releaseEscrowForSeller(tenantId, sellerId, Money.of(50_000, "VND"),
                "ORDER", orderId, actorId, null);
        walletService.releaseEscrowForSeller(tenantId, sellerId, Money.of(50_000, "VND"),
                "ORDER", orderId, actorId, null);

        entityManager.clear();
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        assertEquals(50_000, reloaded.getAvailableBalanceMinor());
        assertEquals(0, reloaded.getPendingBalanceMinor());
        assertEquals(2, countTxns(tenantId, wallet.getId()));
    }

    @Test
    void refundEscrowTwiceDoesNotDoubleRefund() {
        String tenantId = tenant();
        UUID sellerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        walletService.holdEscrowForSeller(tenantId, sellerId, Money.of(30_000, "VND"),
                "ORDER", orderId, actorId, null);

        walletService.refundEscrowForSeller(tenantId, sellerId, Money.of(30_000, "VND"),
                "ORDER", orderId, actorId, null);
        walletService.refundEscrowForSeller(tenantId, sellerId, Money.of(30_000, "VND"),
                "ORDER", orderId, actorId, null);

        entityManager.clear();
        var wallet = walletService.listWallets(tenantId, "SELLER", sellerId, "VND").get(0);
        Wallet reloaded = walletService.getWallet(tenantId, wallet.getId());
        assertEquals(0, reloaded.getAvailableBalanceMinor());
        assertEquals(0, reloaded.getPendingBalanceMinor());
        assertEquals(2, countTxns(tenantId, wallet.getId()));
    }

    @Test
    void payoutRequestWithSameReferenceIdempotentViaAdjustment() {
        String tenantId = tenant();
        Wallet wallet = createVndWallet(tenantId);
        fundWallet(tenantId, wallet, 100_000);

        walletService.adjustBalance(tenantId, wallet.getId(), -40_000, "payout-for-order-123", actorId);
        walletService.adjustBalance(tenantId, wallet.getId(), -40_000, "payout-for-order-123", actorId);

        assertEquals(60_000, freshBalance(tenantId, wallet.getId()));
        assertEquals(2, countTxns(tenantId, wallet.getId()));
    }

    // ─── HMAC Signature Tests ──────────────────────────────────────

    @Test
    void validSignatureVerified() {
        String intentId = UUID.randomUUID().toString();
        String txnId = "MOCK_1234567890_abcd1234";
        long amount = 100_000;
        String sig = paymentGatewayService.signPayloadForTest(intentId, txnId, amount);
        assertTrue(paymentGatewayService.verifySignature(intentId, txnId, amount, sig));
    }

    @Test
    void tamperedAmountRejectedBySignature() {
        String intentId = UUID.randomUUID().toString();
        String txnId = "MOCK_1234567890_abcd1234";
        String sig = paymentGatewayService.signPayloadForTest(intentId, txnId, 100_000);
        assertFalse(paymentGatewayService.verifySignature(intentId, txnId, 99_999, sig));
    }

    @Test
    void tamperedIntentIdRejectedBySignature() {
        String txnId = "MOCK_1234567890_abcd1234";
        String sig = paymentGatewayService.signPayloadForTest(
                UUID.randomUUID().toString(), txnId, 100_000);
        assertFalse(paymentGatewayService.verifySignature(
                UUID.randomUUID().toString(), txnId, 100_000, sig));
    }

    @Test
    void tamperedTransactionIdRejectedBySignature() {
        String intentId = UUID.randomUUID().toString();
        String sig = paymentGatewayService.signPayloadForTest(intentId, "MOCK_ORIGINAL", 100_000);
        assertFalse(paymentGatewayService.verifySignature(intentId, "MOCK_TAMPERED", 100_000, sig));
    }

    @Test
    void emptySignatureRejected() {
        assertFalse(paymentGatewayService.verifySignature(
                UUID.randomUUID().toString(), "txn-1", 100_000, ""));
        assertFalse(paymentGatewayService.verifySignature(
                UUID.randomUUID().toString(), "txn-1", 100_000, null));
    }

    @Test
    void mockGatewaySessionKeepsStableTransactionIdForIntent() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                50_000, "VND", "stable-session-" + UUID.randomUUID(), actorId);

        var first = paymentGatewayService.initiatePayment(intent, "MOCK");
        var second = paymentGatewayService.initiatePayment(intent, "MOCK");

        assertEquals(first.gatewayTransactionId(), second.gatewayTransactionId());
        assertEquals(first.paymentUrl(), second.paymentUrl());
        assertEquals(first.qrCodeContent(), second.qrCodeContent());
    }

    @Test
    void validRawPayloadSignatureVerified() {
        String payload = "{\"status\":\"SUCCESS\"}";
        String sig = paymentGatewayService.hmacSha256ForTest(payload);
        assertTrue(paymentGatewayService.verifySignature(payload, sig));
    }

    @Test
    void tamperedRawPayloadRejectedBySignature() {
        String payload = "{\"status\":\"SUCCESS\"}";
        String sig = paymentGatewayService.hmacSha256ForTest(payload);
        assertFalse(paymentGatewayService.verifySignature("{\"status\":\"FAILED\"}", sig));
    }

    // ─── MONEY Service Validation ──────────────────────────────────

    @Test
    void invalidCurrencyRejectedByMoneyService() {
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("VNDD"));
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("VN"));
        assertEquals("VND", moneyService.normalizeCurrency(""));
        assertEquals("VND", moneyService.normalizeCurrency(null));
    }

    // ─── Helpers ───────────────────────────────────────────────────

    @Test
    void webhookRejectsTamperedSignature() {
        var seed = testDataFactory.seedOrder(tenant(), new java.math.BigDecimal("50000"));
        String tenantId = seed.order().getTenantId();
        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                50_000, "VND", "webhook-tamper-" + UUID.randomUUID(), actorId);

        String txnId = "MOCK_TAMPER_" + System.currentTimeMillis();

        String realSig = paymentGatewayService.signPayloadForTest(
                intent.getId().toString(), txnId, 50_000);
        assertTrue(paymentGatewayService.verifySignature(
                intent.getId().toString(), txnId, 50_000, realSig));

        assertFalse(paymentGatewayService.verifySignature(
                intent.getId().toString(), txnId, 49_999, realSig));
    }

    private Wallet createVndWallet(String tenantId) {
        return walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
    }

    private void fundWallet(String tenantId, Wallet wallet, long amount) {
        walletService.creditAvailable(tenantId, wallet.getId(), Money.of(amount, "VND"),
                "DEPOSIT", "SETUP", UUID.randomUUID(), actorId, "fund");
    }

    private long freshBalance(String tenantId, UUID walletId) {
        entityManager.clear();
        return walletService.getWallet(tenantId, walletId).getAvailableBalanceMinor();
    }

    private long countTxns(String tenantId, UUID walletId) {
        entityManager.clear();
        return WalletTransaction.count("tenantId = ?1 and wallet.id = ?2", tenantId, walletId);
    }

    private String tenant() {
        return "security-" + UUID.randomUUID();
    }
}

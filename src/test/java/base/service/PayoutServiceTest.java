package base.service;

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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PayoutServiceTest {

    @Inject
    WalletService walletService;

    @Inject
    PayoutService payoutService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void createPayoutValidatesWalletCurrencyAmountAndBankDetails() {
        String tenantId = tenant();
        Wallet wallet = createFundedWallet(tenantId, 100_000);

        PayoutRequest payout = payoutService.createPayout(
                tenantId,
                wallet.getId(),
                50_000,
                "vnd",
                " VCB ",
                " 123456789 ",
                " Test Seller ",
                actorId
        );

        assertEquals("FLAGGED", payout.getStatus());
        assertFalse(payout.isAutoApproved());
        assertEquals(50_000, payout.getAmountMinor());
        assertEquals("VND", payout.getCurrency());
        assertEquals("VCB", payout.getBankName());
        assertEquals("123456789", payout.getBankAccountNumber());
        assertEquals("Test Seller", payout.getBankAccountHolder());

        assertThrows(BadRequestException.class, () -> payoutService.createPayout(
                tenantId, wallet.getId(), 0, "VND", "VCB", "123", "Seller", actorId));
        assertThrows(BadRequestException.class, () -> payoutService.createPayout(
                tenantId, wallet.getId(), 1, "USD", "VCB", "123", "Seller", actorId));
        assertThrows(BadRequestException.class, () -> payoutService.createPayout(
                tenantId, wallet.getId(), 1, "VND", "", "123", "Seller", actorId));
        assertThrows(BadRequestException.class, () -> payoutService.createPayout(
                tenantId, wallet.getId(), 1, "VND", "VCB", "", "Seller", actorId));
        assertThrows(BadRequestException.class, () -> payoutService.createPayout(
                tenantId, wallet.getId(), 1, "VND", "VCB", "123", "", actorId));
        assertThrows(NotFoundException.class, () -> payoutService.createPayout(
                "other-" + tenantId, wallet.getId(), 1, "VND", "VCB", "123", "Seller", actorId));
    }

    @Test
    void approvePayoutDebitsWalletOnceAndAppendsLedger() {
        String tenantId = tenant();
        Wallet wallet = createFundedWallet(tenantId, 100_000);
        PayoutRequest payout = payoutService.createPayout(
                tenantId, wallet.getId(), 40_000, "VND", "VCB", "123", "Người bán", actorId);

        PayoutRequest approved = payoutService.approvePayout(tenantId, payout.getId(), actorId);
        PayoutRequest approvedRetry = payoutService.approvePayout(tenantId, payout.getId(), actorId);

        assertEquals("PAID", approved.getStatus());
        assertEquals("PAID", approvedRetry.getStatus());
        assertNotNull(approved.getTransferReference());
        assertEquals(60_000, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());

        List<WalletTransaction> transactions = walletService.listTransactions(tenantId, wallet.getId());
        assertEquals(2, transactions.size());
        WalletTransaction payoutTransaction = transactions.get(0);
        assertEquals("DEBIT", payoutTransaction.getType());
        assertEquals("PAYOUT", payoutTransaction.getTransactionType());
        assertEquals("PAYOUT_REQUEST", payoutTransaction.getReferenceType());
        assertEquals(payout.getId(), payoutTransaction.getReferenceId());
        assertEquals(2, payoutTransaction.getSequenceNo());
        assertEquals(40_000, payoutTransaction.getAmountMinor());
    }

    @Test
    void approvePayoutRejectsInsufficientFundsAndLeavesPayoutPending() {
        String tenantId = tenant();
        Wallet wallet = createFundedWallet(tenantId, 10_000);
        PayoutRequest payout = payoutService.createPayout(
                tenantId, wallet.getId(), 20_000, "VND", "VCB", "123", "Người bán", actorId);

        assertThrows(BadRequestException.class, () -> payoutService.approvePayout(tenantId, payout.getId(), actorId));

        assertEquals("FLAGGED", payoutService.getPayout(tenantId, payout.getId()).getStatus());
        assertEquals(10_000, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertEquals(1, WalletTransaction.count("tenantId = ?1 and wallet.id = ?2", tenantId, wallet.getId()));
    }

    @Test
    void rejectPayoutDoesNotMoveWalletAndBlocksLaterApproval() {
        String tenantId = tenant();
        Wallet wallet = createFundedWallet(tenantId, 100_000);
        PayoutRequest payout = payoutService.createPayout(
                tenantId, wallet.getId(), 40_000, "VND", "VCB", "123", "Người bán", actorId);

        PayoutRequest rejected = payoutService.rejectPayout(tenantId, payout.getId(), actorId);
        PayoutRequest rejectedRetry = payoutService.rejectPayout(tenantId, payout.getId(), actorId);

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("REJECTED", rejectedRetry.getStatus());
        assertEquals(100_000, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertEquals(1, WalletTransaction.count("tenantId = ?1 and wallet.id = ?2", tenantId, wallet.getId()));
        assertThrows(BadRequestException.class, () -> payoutService.approvePayout(tenantId, payout.getId(), actorId));
    }

    @Test
    void approvedPayoutCannotBeRejectedAndPayoutQueriesAreTenantScoped() {
        String tenantA = tenant();
        String tenantB = tenant();
        Wallet walletA = createFundedWallet(tenantA, 100_000);
        Wallet walletB = createFundedWallet(tenantB, 100_000);
        PayoutRequest payoutA = payoutService.createPayout(
                tenantA, walletA.getId(), 10_000, "VND", "VCB", "123", "Seller A", actorId);
        PayoutRequest payoutB = payoutService.createPayout(
                tenantB, walletB.getId(), 20_000, "VND", "VCB", "456", "Seller B", actorId);

        payoutService.approvePayout(tenantA, payoutA.getId(), actorId);

        assertThrows(BadRequestException.class, () -> payoutService.rejectPayout(tenantA, payoutA.getId(), actorId));
        assertThrows(NotFoundException.class, () -> payoutService.getPayout(tenantB, payoutA.getId()));
        assertEquals(1, payoutService.listPayouts(tenantA, null, "PAID").size());
        assertEquals(0, payoutService.listPayouts(tenantB, null, "PAID").size());
        assertEquals(List.of(payoutB.getId()), payoutService.listPayouts(tenantB, walletB.getId(), "FLAGGED")
                .stream()
                .map(PayoutRequest::getId)
                .toList());
    }

    @Test
    void csvExportUsesTheCurrenciesMinorUnitScale() {
        String tenantId = tenant();
        Wallet wallet = createFundedWallet(tenantId, 100_000);
        payoutService.createPayout(
                tenantId, wallet.getId(), 50_000, "VND", "VCB", "123", "Seller", actorId);

        String csv = payoutService.generateCsv(tenantId, "FLAGGED");

        assertTrue(csv.contains(",50000,VND,FLAGGED,"));
    }

    private Wallet createFundedWallet(String tenantId, long amountMinor) {
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
        walletService.creditAvailable(
                tenantId,
                wallet.getId(),
                Money.of(amountMinor, "VND"),
                "DEPOSIT",
                "TEST_SETUP",
                UUID.randomUUID(),
                actorId,
                "Test setup"
        );
        return freshWallet(tenantId, wallet.getId());
    }

    private String tenant() {
        return "test-" + UUID.randomUUID();
    }

    private Wallet freshWallet(String tenantId, UUID walletId) {
        Wallet wallet = walletService.getWallet(tenantId, walletId);
        entityManager.clear();
        return walletService.getWallet(tenantId, walletId);
    }
}

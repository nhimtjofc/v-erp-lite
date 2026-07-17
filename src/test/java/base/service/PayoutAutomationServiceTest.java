package base.service;

import base.entity.PayoutRequest;
import base.entity.VendorRiskEvent;
import base.entity.Wallet;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PayoutAutomationServiceTest {

    @Inject
    WalletService walletService;

    @Inject
    PayoutService payoutService;

    @Inject
    PayoutAutomationService automationService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000333");

    @Test
    void firstPayoutIsFlaggedAndRepeatedRunsReuseTheActiveRequest() {
        String tenantId = tenant();
        Wallet wallet = fundedWallet(tenantId, 200_000);
        setBankDetails(tenantId, wallet, "VCB", "111", "Seller One");

        PayoutRequest first = automationService.processWallet(wallet.getId());
        PayoutRequest retry = automationService.processWallet(wallet.getId());

        assertEquals("FLAGGED", first.getStatus());
        assertFalse(first.isAutoApproved());
        assertTrue(first.getFlagReason().contains("first-ever payout"));
        assertEquals(first.getId(), retry.getId());
        assertEquals(1, PayoutRequest.count("tenantId = ?1 and wallet.id = ?2", tenantId, wallet.getId()));
        assertEquals(200_000, walletService.getWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
    }

    @Test
    void establishedOwnerIsAutoPaidOnce() {
        String tenantId = tenant();
        Wallet wallet = fundedWallet(tenantId, 100_000);
        setBankDetails(tenantId, wallet, "VCB", "222", "Seller Two");

        PayoutRequest prior = payoutService.createPayout(
                tenantId, wallet.getId(), 100_000, "VND", "VCB", "222", "Seller Two", actorId);
        payoutService.approvePayout(tenantId, prior.getId(), actorId);
        walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(150_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, "New earnings");

        PayoutRequest automated = automationService.processWallet(wallet.getId());
        PayoutRequest noSecondPayment = automationService.processWallet(wallet.getId());

        assertEquals("PAID", automated.getStatus());
        assertTrue(automated.isAutoApproved());
        assertNull(automated.getFlagReason());
        assertNotNull(automated.getTransferReference());
        assertNull(noSecondPayment);
        entityManager.clear();
        assertEquals(0, walletService.getWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertEquals(2, PayoutRequest.count("tenantId = ?1 and wallet.id = ?2 and status = 'PAID'", tenantId, wallet.getId()));
    }

    @Test
    void bankChangesAndPreviousFailuresAreSurfacedAsFlagReasons() {
        String tenantId = tenant();
        Wallet wallet = fundedWallet(tenantId, 100_000);
        setBankDetails(tenantId, wallet, "VCB", "333", "Seller Three");
        PayoutRequest prior = payoutService.createPayout(
                tenantId, wallet.getId(), 100_000, "VND", "VCB", "333", "Seller Three", actorId);
        payoutService.approvePayout(tenantId, prior.getId(), actorId);

        setBankDetails(tenantId, wallet, "ACB", "444", "Seller Three");
        walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(120_000, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, "New earnings");
        VendorRiskEvent failure = new VendorRiskEvent();
        failure.setId(UUID.randomUUID());
        failure.setTenantId(tenantId);
        failure.setOwnerId(wallet.getOwnerId());
        failure.setEventType("PAYOUT_BOUNCED");
        failure.setOccurredAt(Instant.now());
        QuarkusTransaction.requiringNew().run(failure::persist);

        PayoutRequest flagged = automationService.processWallet(wallet.getId());

        assertEquals("FLAGGED", flagged.getStatus());
        assertTrue(flagged.getFlagReason().contains("bank detail change"));
        assertTrue(flagged.getFlagReason().contains("failed or bounced"));
    }

    private Wallet fundedWallet(String tenantId, long amountMinor) {
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
        walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(amountMinor, "VND"),
                "DEPOSIT", "TEST", UUID.randomUUID(), actorId, "Test funding");
        return walletService.getWallet(tenantId, wallet.getId());
    }

    private void setBankDetails(String tenantId, Wallet wallet, String bank, String account, String holder) {
        walletService.updateBankDetails(tenantId, wallet.getId(), bank, account, holder, actorId);
    }

    private String tenant() {
        return "payout-auto-" + UUID.randomUUID();
    }
}

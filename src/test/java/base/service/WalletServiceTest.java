package base.service;

import base.entity.Bank;
import base.entity.Business;
import base.entity.LedgerCheckpoint;
import base.entity.User;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WalletServiceTest {

    @Inject
    MoneyService moneyService;

    @Inject
    WalletService walletService;

    @Inject
    LedgerCheckpointService checkpointService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Test
    void moneyServiceNormalizesCurrencyAndRejectsInvalidMoney() {
        assertEquals("VND", moneyService.normalizeCurrency(" vnd "));
        assertThrows(BadRequestException.class, () -> moneyService.positiveMoney(12_345, "USD"));

        assertEquals("VND", moneyService.normalizeCurrency(null));
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("VN"));
        assertThrows(BadRequestException.class, () -> moneyService.positiveMoney(0, "VND"));
        assertThrows(BadRequestException.class, () -> moneyService.positiveMoney(-1, "VND"));
        assertThrows(BadRequestException.class, () -> moneyService.requireSameCurrency("VND", "USD"));
    }

    @Test
    void getOrCreateWalletNormalizesFieldsAndIsIdempotent() {
        String tenantId = tenant();
        UUID ownerId = UUID.randomUUID();

        Wallet first = walletService.getOrCreateWallet(tenantId, "seller", ownerId, "vnd", actorId);
        Wallet second = walletService.getOrCreateWallet(tenantId, "SELLER", ownerId, "VND", actorId);

        assertEquals(first.getId(), second.getId());
        assertEquals("SELLER", first.getOwnerType());
        assertEquals("VND", first.getCurrency());
        assertEquals(1, walletService.listWallets(tenantId, "seller", ownerId, "vnd").size());
        assertEquals(1, walletService.listWallets(tenantId, "seller", ownerId, null).size());
        assertTrue(walletService.listWallets("other-" + tenantId, null, null, null).isEmpty());

        assertThrows(BadRequestException.class, () -> walletService.getOrCreateWallet(tenantId, "", ownerId, "VND", actorId));
        assertThrows(BadRequestException.class, () -> walletService.getOrCreateWallet(tenantId, "SELLER", null, "VND", actorId));
        assertThrows(NotFoundException.class, () -> walletService.getWallet(tenantId, UUID.randomUUID()));
    }

    @Test
    void updateBankDetailsUsesActiveCatalogBank() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId, "VND");
        Bank bank = Bank.find("code", "VIETCOMBANK").firstResult();

        Wallet updated = walletService.updateBankDetails(
                tenantId, wallet.getId(), bank.getId(), "0123456789", "NGUYEN VAN A", actorId);

        assertEquals(bank.getId(), updated.getBank().getId());
        assertEquals(bank.getName(), updated.getBankName());
        assertEquals("0123456789", updated.getBankAccountNumber());
        assertEquals("NGUYEN VAN A", updated.getBankAccountHolder());
        assertNotNull(updated.getBankDetailsUpdatedAt());
        assertThrows(BadRequestException.class, () -> walletService.updateBankDetails(
                tenantId, wallet.getId(), UUID.randomUUID(), "0123456789", "NGUYEN VAN A", actorId));
    }

    @Test
    void getOrCreateUserWalletLinksOneVndWalletToUser() {
        String tenantId = tenant();
        UUID userId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setId(userId);
            user.setTenantId(tenantId);
            user.setEmail(userId + "@example.test");
            user.setActive(true);
            user.persist();
        });

        Wallet first = walletService.getOrCreateUserWallet(tenantId, userId, "vnd", actorId);
        Wallet second = walletService.getOrCreateUserWallet(tenantId, userId, "VND", actorId);

        assertEquals(first.getId(), second.getId());
        assertEquals("USER", first.getOwnerType());
        assertEquals(userId, first.getOwnerId());
        assertEquals(userId, first.getUser().getId());
        assertEquals("VND", first.getCurrency());
    }

    @Test
    void getOrCreateUserWalletPrefersSellerWalletWhenUserOwnsBusiness() {
        String tenantId = tenant();
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            User user = new User();
            user.setId(userId);
            user.setTenantId(tenantId);
            user.setEmail(userId + "@example.test");
            user.setActive(true);
            user.persist();
        });
        Wallet personalWallet = walletService.getOrCreateUserWallet(tenantId, userId, "VND", actorId);
        QuarkusTransaction.requiringNew().run(() -> {
            Business business = new Business();
            business.setId(businessId);
            business.setTenantId(tenantId);
            business.setName("Seller " + businessId);
            business.setCreatedBy(userId);
            business.setActive(true);
            business.persist();
        });

        Wallet sellerWallet = walletService.getOrCreateUserWallet(tenantId, userId, "VND", actorId);

        assertNotEquals(personalWallet.getId(), sellerWallet.getId());
        assertEquals("SELLER", sellerWallet.getOwnerType());
        assertEquals(businessId, sellerWallet.getOwnerId());
        assertEquals(userId, sellerWallet.getUser().getId());
    }

    @Test
    void creditAndDebitAvailableAppendLedgerAndWriteCheckpoints() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId, "VND");
        UUID depositRef = UUID.randomUUID();
        UUID debitRef = UUID.randomUUID();

        WalletService.WalletMutation credit = walletService.creditAvailable(
                tenantId,
                wallet.getId(),
                Money.of(100_000, "VND"),
                "DEPOSIT",
                "MANUAL_TEST",
                depositRef,
                actorId,
                "Nạp số dư kiểm thử"
        );

        assertEquals(100_000, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertLedger(credit.transaction(), "CREDIT", "DEPOSIT", "MANUAL_TEST", depositRef, 1, 100_000);
        assertCheckpointMatchesWallet(tenantId, wallet.getId(), credit.transaction().getId(), 1, 100_000, 0);

        WalletService.WalletMutation debit = walletService.debitAvailable(
                tenantId,
                wallet.getId(),
                Money.of(40_000, "VND"),
                "WITHDRAWAL",
                "MANUAL_TEST",
                debitRef,
                actorId,
                "Trừ tiền kiểm thử"
        );

        Wallet reloaded = freshWallet(tenantId, wallet.getId());
        assertEquals(60_000, reloaded.getAvailableBalanceMinor());
        assertEquals(0, reloaded.getPendingBalanceMinor());
        assertLedger(debit.transaction(), "DEBIT", "WITHDRAWAL", "MANUAL_TEST", debitRef, 2, 40_000);
        assertCheckpointMatchesWallet(tenantId, wallet.getId(), debit.transaction().getId(), 2, 60_000, 0);
    }

    @Test
    void balanceMutationsRejectBadInputsAndDoNotCreateLedgerRows() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId, "VND");

        assertThrows(BadRequestException.class, () -> walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(0, "VND"), "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null));
        assertThrows(BadRequestException.class, () -> walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(100, "USD"), "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null));
        assertThrows(BadRequestException.class, () -> walletService.debitAvailable(
                tenantId, wallet.getId(), Money.of(1, "VND"), "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null));
        assertThrows(NotFoundException.class, () -> walletService.debitAvailable(
                tenantId, UUID.randomUUID(), Money.of(1, "VND"), "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null));

        assertEquals(0, countTransactions(tenantId, wallet.getId()));
        assertEquals(0, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
    }

    @Test
    void referenceIdempotencyPreventsDoubleCreditAndDoubleDebit() {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId, "VND");
        UUID depositRef = UUID.randomUUID();
        UUID debitRef = UUID.randomUUID();

        WalletService.WalletMutation firstCredit = walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(100_000, "VND"), "DEPOSIT", "ORDER", depositRef, actorId, null);
        WalletService.WalletMutation secondCredit = walletService.creditAvailable(
                tenantId, wallet.getId(), Money.of(100_000, "VND"), "DEPOSIT", "ORDER", depositRef, actorId, null);

        assertEquals(firstCredit.transaction().getId(), secondCredit.transaction().getId());
        assertEquals(100_000, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertEquals(1, countTransactions(tenantId, wallet.getId()));

        WalletService.WalletMutation firstDebit = walletService.debitAvailable(
                tenantId, wallet.getId(), Money.of(20_000, "VND"), "WITHDRAWAL", "PAYOUT", debitRef, actorId, null);
        WalletService.WalletMutation secondDebit = walletService.debitAvailable(
                tenantId, wallet.getId(), Money.of(20_000, "VND"), "WITHDRAWAL", "PAYOUT", debitRef, actorId, null);

        assertEquals(firstDebit.transaction().getId(), secondDebit.transaction().getId());
        assertEquals(80_000, freshWallet(tenantId, wallet.getId()).getAvailableBalanceMinor());
        assertEquals(2, countTransactions(tenantId, wallet.getId()));
    }

    @Test
    void pendingEscrowHoldReleaseAndRefundMaintainBalancesAndLedger() {
        String tenantId = tenant();
        Wallet releaseWallet = createWallet(tenantId, "VND");
        UUID releaseOrderRef = UUID.randomUUID();

        WalletService.WalletMutation hold = walletService.creditPending(
                tenantId, releaseWallet.getId(), Money.of(70_000, "VND"), "ESCROW_HOLD", "ORDER", releaseOrderRef, actorId, null);
        assertLedger(hold.transaction(), "CREDIT", "ESCROW_HOLD", "ORDER", releaseOrderRef, 1, 70_000);
        assertBalances(tenantId, releaseWallet.getId(), 0, 70_000);

        WalletService.WalletMutation release = walletService.releasePendingToAvailable(
                tenantId, releaseWallet.getId(), Money.of(70_000, "VND"), "ORDER", releaseOrderRef, actorId, null);
        assertLedger(release.transaction(), "CREDIT", "ESCROW_RELEASE", "ORDER", releaseOrderRef, 2, 70_000);
        assertBalances(tenantId, releaseWallet.getId(), 70_000, 0);

        WalletService.WalletMutation releaseRetry = walletService.releasePendingToAvailable(
                tenantId, releaseWallet.getId(), Money.of(70_000, "VND"), "ORDER", releaseOrderRef, actorId, null);
        assertEquals(release.transaction().getId(), releaseRetry.transaction().getId());
        assertEquals(2, countTransactions(tenantId, releaseWallet.getId()));

        Wallet refundWallet = createWallet(tenantId, "VND");
        UUID refundOrderRef = UUID.randomUUID();
        walletService.creditPending(
                tenantId, refundWallet.getId(), Money.of(25_000, "VND"), "ESCROW_HOLD", "ORDER", refundOrderRef, actorId, null);
        WalletService.WalletMutation refund = walletService.refundPending(
                tenantId, refundWallet.getId(), Money.of(25_000, "VND"), "ORDER", refundOrderRef, actorId, null);
        assertLedger(refund.transaction(), "DEBIT", "ESCROW_REFUND", "ORDER", refundOrderRef, 2, 25_000);
        assertBalances(tenantId, refundWallet.getId(), 0, 0);

        assertThrows(BadRequestException.class, () -> walletService.releasePendingToAvailable(
                tenantId, refundWallet.getId(), Money.of(1, "VND"), "ORDER", UUID.randomUUID(), actorId, null));
        assertThrows(BadRequestException.class, () -> walletService.refundPending(
                tenantId, refundWallet.getId(), Money.of(1, "VND"), "ORDER", UUID.randomUUID(), actorId, null));
    }

    @Test
    void sellerEscrowHelpersCreateSellerWalletAndAreIdempotent() {
        String tenantId = tenant();
        UUID sellerOwnerId = UUID.randomUUID();
        UUID orderRef = UUID.randomUUID();

        WalletService.WalletMutation hold = walletService.holdEscrowForSeller(
                tenantId, sellerOwnerId, Money.of(90_000, "VND"), "PAYMENT_INTENT", orderRef, actorId, null);
        assertEquals("SELLER", hold.wallet().getOwnerType());
        assertEquals(sellerOwnerId, hold.wallet().getOwnerId());
        assertBalances(tenantId, hold.wallet().getId(), 0, 90_000);

        WalletService.WalletMutation holdRetry = walletService.holdEscrowForSeller(
                tenantId, sellerOwnerId, Money.of(90_000, "VND"), "PAYMENT_INTENT", orderRef, actorId, null);
        assertEquals(hold.transaction().getId(), holdRetry.transaction().getId());
        assertEquals(1, countTransactions(tenantId, hold.wallet().getId()));

        WalletService.WalletMutation release = walletService.releaseEscrowForSeller(
                tenantId, sellerOwnerId, Money.of(90_000, "VND"), "ORDER", orderRef, actorId, null);
        assertLedger(release.transaction(), "CREDIT", "ESCROW_RELEASE", "ORDER", orderRef, 2, 90_000);
        assertBalances(tenantId, hold.wallet().getId(), 90_000, 0);
    }

    @Test
    void tenantIsolationAppliesToReadsAndMutations() {
        String tenantA = tenant();
        String tenantB = tenant();
        UUID ownerId = UUID.randomUUID();
        Wallet walletA = walletService.getOrCreateWallet(tenantA, "SELLER", ownerId, "VND", actorId);
        Wallet walletB = walletService.getOrCreateWallet(tenantB, "SELLER", ownerId, "VND", actorId);

        walletService.creditAvailable(
                tenantA, walletA.getId(), Money.of(10_000, "VND"), "DEPOSIT", "TEST", UUID.randomUUID(), actorId, null);

        assertEquals(10_000, freshWallet(tenantA, walletA.getId()).getAvailableBalanceMinor());
        assertEquals(0, freshWallet(tenantB, walletB.getId()).getAvailableBalanceMinor());
        assertThrows(NotFoundException.class, () -> walletService.getWallet(tenantB, walletA.getId()));
        assertThrows(NotFoundException.class, () -> walletService.debitAvailable(
                tenantB, walletA.getId(), Money.of(1, "VND"), "WITHDRAWAL", "TEST", UUID.randomUUID(), actorId, null));
    }

    @Test
    void concurrentDebitsCannotOverdraftWallet() throws Exception {
        String tenantId = tenant();
        Wallet wallet = createWallet(tenantId, "VND");
        walletService.creditAvailable(
                tenantId,
                wallet.getId(),
                Money.of(1_000_000, "VND"),
                "DEPOSIT",
                "SETUP",
                UUID.randomUUID(),
                actorId,
                null
        );

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 50; i++) {
                results.add(executor.submit(() -> debitAfterStart(start, tenantId, wallet.getId(), 20_000)));
            }

            start.countDown();
            int successfulDebits = 0;
            for (Future<Boolean> result : results) {
                successfulDebits += result.get(15, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertEquals(50, successfulDebits);
            assertBalances(tenantId, wallet.getId(), 0, 0);
            assertEquals(51, countTransactions(tenantId, wallet.getId()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Wallet createWallet(String tenantId, String currency) {
        return walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), currency, actorId);
    }

    private String tenant() {
        return "test-" + UUID.randomUUID();
    }

    private boolean debitAfterStart(CountDownLatch start, String tenantId, UUID walletId, long amountMinor) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            walletService.debitAvailable(
                    tenantId,
                    walletId,
                    Money.of(amountMinor, "VND"),
                    "WITHDRAWAL",
                    "CONCURRENCY_TEST",
                    UUID.randomUUID(),
                    actorId,
                    "Trừ tiền song song"
            );
            return true;
        } catch (BadRequestException expected) {
            return false;
        }
    }

    private long countTransactions(String tenantId, UUID walletId) {
        entityManager.clear();
        return WalletTransaction.count("tenantId = ?1 and wallet.id = ?2", tenantId, walletId);
    }

    private void assertBalances(String tenantId, UUID walletId, long availableMinor, long pendingMinor) {
        Wallet wallet = freshWallet(tenantId, walletId);
        assertEquals(availableMinor, wallet.getAvailableBalanceMinor());
        assertEquals(pendingMinor, wallet.getPendingBalanceMinor());
    }

    private Wallet freshWallet(String tenantId, UUID walletId) {
        entityManager.clear();
        return walletService.getWallet(tenantId, walletId);
    }

    private void assertLedger(WalletTransaction transaction, String type, String transactionType, String referenceType,
                              UUID referenceId, long sequenceNo, long amountMinor) {
        assertNotNull(transaction.getId());
        assertEquals(type, transaction.getType());
        assertEquals(transactionType, transaction.getTransactionType());
        assertEquals(referenceType, transaction.getReferenceType());
        assertEquals(referenceId, transaction.getReferenceId());
        assertEquals(sequenceNo, transaction.getSequenceNo());
        assertEquals(amountMinor, transaction.getAmountMinor());
    }

    private void assertCheckpointMatchesWallet(String tenantId, UUID walletId, UUID transactionId, long sequenceNo,
                                               long availableMinor, long pendingMinor) {
        entityManager.clear();
        LedgerCheckpoint checkpoint = checkpointService.latestForWallet(tenantId, walletId);
        assertNotNull(checkpoint);
        assertEquals(transactionId, checkpoint.getLastTransactionId());
        assertEquals(sequenceNo, checkpoint.getLastSequenceNo());
        assertEquals(availableMinor, checkpoint.getAvailableBalanceMinor());
        assertEquals(pendingMinor, checkpoint.getPendingBalanceMinor());
    }
}

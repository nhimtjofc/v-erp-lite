package base.service;

import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class PaymentModuleInvariantTest {

    @Inject
    WalletService walletService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000222");

    @Test
    void moneyRoundingUsesMinorUnitsAndHalfUpPolicy() {
        Money usd = Money.fromDecimal(new BigDecimal("10.005"), "USD");
        assertEquals(1001, usd.getAmountMinor());
        assertEquals(new BigDecimal("10.01"), usd.toDecimal());

        Money commission = Money.of(100, "USD").multiply(0.05);
        assertEquals(5, commission.getAmountMinor());

        assertThrows(BadRequestException.class, () -> Money.fromDecimal(new BigDecimal("-1"), "USD"));
    }

    @Test
    @Transactional
    void ledgerEntriesRemainAppendOnly() {
        String tenantId = "payment-invariants-" + UUID.randomUUID();
        Wallet wallet = walletService.getOrCreateWallet(tenantId, "SELLER", UUID.randomUUID(), "VND", actorId);
        walletService.creditAvailable(
                tenantId,
                wallet.getId(),
                Money.of(50_000, "VND"),
                "DEPOSIT",
                "TEST",
                UUID.randomUUID(),
                actorId,
                "seed"
        );

        WalletTransaction transaction = (WalletTransaction) WalletTransaction.list(
                "tenantId = ?1 and wallet.id = ?2 order by sequenceNo asc",
                tenantId,
                wallet.getId()
        ).get(0);

        assertThrows(PersistenceException.class, () -> {
            entityManager.createNativeQuery("UPDATE wallet_transactions SET description = 'tampered' WHERE id = :id")
                    .setParameter("id", transaction.getId())
                    .executeUpdate();
            entityManager.flush();
        });

        assertThrows(PersistenceException.class, () -> {
            entityManager.createNativeQuery("DELETE FROM wallet_transactions WHERE id = :id")
                    .setParameter("id", transaction.getId())
                    .executeUpdate();
            entityManager.flush();
        });
    }
}

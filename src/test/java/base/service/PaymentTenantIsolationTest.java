package base.service;

import base.entity.Wallet;
import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class PaymentTenantIsolationTest {

    @Inject
    WalletService walletService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000333");

    @Test
    void walletMutationsStayScopedToTenant() {
        String tenantA = "tenant-a-" + UUID.randomUUID();
        String tenantB = "tenant-b-" + UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Wallet walletA = walletService.getOrCreateWallet(tenantA, "SELLER", ownerId, "VND", actorId);
        Wallet walletB = walletService.getOrCreateWallet(tenantB, "SELLER", ownerId, "VND", actorId);

        walletService.creditAvailable(
                tenantA,
                walletA.getId(),
                Money.of(12_000, "VND"),
                "DEPOSIT",
                "TEST",
                UUID.randomUUID(),
                actorId,
                null
        );

        assertEquals(12_000, freshWallet(tenantA, walletA.getId()).getAvailableBalanceMinor());
        assertEquals(0, freshWallet(tenantB, walletB.getId()).getAvailableBalanceMinor());
        assertThrows(NotFoundException.class, () -> walletService.getWallet(tenantB, walletA.getId()));
        assertThrows(NotFoundException.class, () -> walletService.debitAvailable(
                tenantB,
                walletA.getId(),
                Money.of(1, "VND"),
                "WITHDRAWAL",
                "TEST",
                UUID.randomUUID(),
                actorId,
                null
        ));
    }

    private Wallet freshWallet(String tenantId, UUID walletId) {
        entityManager.clear();
        return walletService.getWallet(tenantId, walletId);
    }
}

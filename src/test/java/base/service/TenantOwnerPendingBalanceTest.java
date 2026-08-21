package base.service;

import base.model.Money;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestTransaction
class TenantOwnerPendingBalanceTest {

    @Inject
    EscrowService escrowService;

    @Inject
    WalletService walletService;

    @Inject
    EntityManager entityManager;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000999");

    @Test
    void aggregatesHeldEscrowAcrossAllTenantBusinesses() {
        String tenantId = "owner-pending-" + UUID.randomUUID();
        String otherTenantId = "other-owner-pending-" + UUID.randomUUID();

        escrowService.holdSellerEscrow(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Money.of(40_000, "VND"),
                Instant.now().plusSeconds(3600),
                actorId);
        escrowService.holdSellerEscrow(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Money.of(60_000, "VND"),
                Instant.now().plusSeconds(3600),
                actorId);
        escrowService.holdSellerEscrow(
                otherTenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Money.of(75_000, "VND"),
                Instant.now().plusSeconds(3600),
                actorId);

        entityManager.flush();
        entityManager.clear();

        assertEquals(
                100_000,
                walletService.getPendingEscrowForTenantBusinesses(tenantId, "VND"));
        assertEquals(
                walletService.getWalletStats(tenantId).get("escrowBalance").longValue(),
                walletService.getPendingEscrowForTenantBusinesses(tenantId, "VND"));
    }
}

package base.service;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntitlementServiceTest {

    @Test
    void calculatesMonthlyUpgradeFromRemainingDays() {
        assertEquals(
                845_500L,
                EntitlementService.calculateProratedAmount(299_000L, 1_990_000L, 15L, "MONTHLY")
        );
    }

    @Test
    void roundsUpgradeAmountUpToTheSmallestCurrencyUnit() {
        assertEquals(
                56_367L,
                EntitlementService.calculateProratedAmount(299_000L, 1_990_000L, 1L, "MONTHLY")
        );
    }

    @Test
    void neverChargesMoreThanOneBillingPeriod() {
        assertEquals(
                1_691_000L,
                EntitlementService.calculateProratedAmount(299_000L, 1_990_000L, 45L, "MONTHLY")
        );
    }

    @Test
    void roundsPartialDaysUp() {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");

        assertEquals(1L, EntitlementService.remainingDays(now, now.plusSeconds(1L)));
        assertEquals(1L, EntitlementService.remainingDays(now, now.plusSeconds(86_400L)));
        assertEquals(2L, EntitlementService.remainingDays(now, now.plusSeconds(86_401L)));
    }

    @Test
    void rejectsInvalidUpgradePrices() {
        assertThrows(
                BadRequestException.class,
                () -> EntitlementService.calculateProratedAmount(1_990_000L, 299_000L, 10L, "MONTHLY")
        );
    }
}

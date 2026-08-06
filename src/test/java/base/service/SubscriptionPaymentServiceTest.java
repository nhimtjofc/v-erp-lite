package base.service;

import base.entity.SubscriptionPayment;
import base.entity.TenantSubscription;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionPaymentServiceTest {

    @Test
    void calculatesRecognizedProfitAndActiveSubscriptionBreakdown() {
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        SubscriptionPayment approvedThisMonth = payment("paid-active", "APPROVED", 100_000L, Instant.parse("2026-08-03T00:00:00Z"));
        SubscriptionPayment approvedLastMonth = payment("paid-historical", "APPROVED", 300_000L, Instant.parse("2026-07-31T23:59:59Z"));
        SubscriptionPayment submitted = payment("pending", "SUBMITTED", 50_000L, null);
        SubscriptionPayment rejected = payment("rejected", "REJECTED", 99_000L, null);

        TenantSubscription activeBase = subscription("paid-active", "ACTIVE", "BASE", 299_000L, now, now.plusSeconds(3_600L));
        TenantSubscription activePremiumWithoutEnd = subscription("opening-premium", "ACTIVE", "PREMIUM", 899_000L, now, null);
        TenantSubscription expiredBase = subscription("expired", "ACTIVE", "BASE", 299_000L, now, now.minusSeconds(1L));
        TenantSubscription suspendedPremium = subscription("suspended", "SUSPENDED", "PREMIUM", 899_000L, now, now.plusSeconds(3_600L));

        SubscriptionPaymentService.SubscriptionFinancialStats stats = SubscriptionPaymentService.calculateFinancialStats(
                List.of(approvedThisMonth, approvedLastMonth, submitted, rejected),
                List.of(activeBase, activePremiumWithoutEnd, expiredBase, suspendedPremium),
                now
        );

        assertEquals(400_000L, stats.recognizedProfitMinor());
        assertEquals(100_000L, stats.currentMonthProfitMinor());
        assertEquals(50_000L, stats.pendingRevenueMinor());
        assertEquals(2L, stats.approvedPaymentCount());
        assertEquals(2L, stats.activeSubscriptionCount());
        assertEquals(1_198_000L, stats.activeSubscriptionValueMinor());
        assertEquals(1L, stats.baseSubscriptionCount());
        assertEquals(1L, stats.premiumSubscriptionCount());
        assertEquals("VND", stats.currency());
    }

    @Test
    void keepsSeededProfitAfterSubscriptionExpires() {
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        TenantSubscription expiredSeededSubscription = subscription(
                "seeded-expired",
                "ACTIVE",
                "BASE",
                299_000L,
                now.minusSeconds(86_400L),
                now.minusSeconds(1L)
        );
        expiredSeededSubscription.setCreatedBy(null);

        SubscriptionPaymentService.SubscriptionFinancialStats stats = SubscriptionPaymentService.calculateFinancialStats(
                List.of(),
                List.of(expiredSeededSubscription),
                now
        );

        assertEquals(299_000L, stats.recognizedProfitMinor());
        assertEquals(299_000L, stats.currentMonthProfitMinor());
        assertEquals(1L, stats.approvedPaymentCount());
        assertEquals(0L, stats.activeSubscriptionCount());
        assertEquals(0L, stats.activeSubscriptionValueMinor());
    }

    @Test
    void includesSeededSubscriptionInAdminPaymentList() {
        Instant startedAt = Instant.parse("2026-08-01T00:00:00Z");
        TenantSubscription seededSubscription = subscription(
                "seeded-default",
                "ACTIVE",
                "BASE",
                299_000L,
                startedAt,
                startedAt.plusSeconds(30L * 86_400L)
        );
        seededSubscription.setCreatedBy(null);

        List<SubscriptionPayment> payments = SubscriptionPaymentService.includeSeededSubscriptionPayments(
                List.of(),
                List.of(seededSubscription),
                null
        );

        assertEquals(1, payments.size());
        assertEquals("seeded-default", payments.getFirst().getTenantId());
        assertEquals("APPROVED", payments.getFirst().getStatus());
        assertEquals("OPENING-seeded-default", payments.getFirst().getPaymentReference());
        assertEquals(299_000L, payments.getFirst().getAmountMinor());
        assertEquals(
                0,
                SubscriptionPaymentService.includeSeededSubscriptionPayments(
                        List.of(),
                        List.of(seededSubscription),
                        "SUBMITTED"
                ).size()
        );
    }

    private SubscriptionPayment payment(String tenantId, String status, long amountMinor, Instant reviewedAt) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setTenantId(tenantId);
        payment.setStatus(status);
        payment.setAmountMinor(amountMinor);
        payment.setReviewedAt(reviewedAt);
        return payment;
    }

    private TenantSubscription subscription(
            String tenantId,
            String status,
            String planCode,
            long priceMinor,
            Instant startedAt,
            Instant periodEndsAt
    ) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setCreatedBy(UUID.randomUUID());
        subscription.setStatus(status);
        subscription.setPlanCode(planCode);
        subscription.setPriceMinor(priceMinor);
        subscription.setStartedAt(startedAt);
        subscription.setCurrentPeriodEndsAt(periodEndsAt);
        return subscription;
    }
}

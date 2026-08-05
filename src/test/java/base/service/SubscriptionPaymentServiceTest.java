package base.service;

import base.entity.SubscriptionPayment;
import base.entity.TenantSubscription;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionPaymentServiceTest {

    @Test
    void calculatesRecognizedProfitAndActiveSubscriptionBreakdown() {
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        SubscriptionPayment approvedThisMonth = payment("APPROVED", 100_000L, Instant.parse("2026-08-03T00:00:00Z"));
        SubscriptionPayment approvedLastMonth = payment("APPROVED", 300_000L, Instant.parse("2026-07-31T23:59:59Z"));
        SubscriptionPayment submitted = payment("SUBMITTED", 50_000L, null);
        SubscriptionPayment rejected = payment("REJECTED", 99_000L, null);

        TenantSubscription activeBase = subscription("ACTIVE", "BASE", now.plusSeconds(3_600L));
        TenantSubscription activePremiumWithoutEnd = subscription("ACTIVE", "PREMIUM", null);
        TenantSubscription expiredBase = subscription("ACTIVE", "BASE", now.minusSeconds(1L));
        TenantSubscription suspendedPremium = subscription("SUSPENDED", "PREMIUM", now.plusSeconds(3_600L));

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
        assertEquals(1L, stats.baseSubscriptionCount());
        assertEquals(1L, stats.premiumSubscriptionCount());
        assertEquals("VND", stats.currency());
    }

    private SubscriptionPayment payment(String status, long amountMinor, Instant reviewedAt) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setStatus(status);
        payment.setAmountMinor(amountMinor);
        payment.setReviewedAt(reviewedAt);
        return payment;
    }

    private TenantSubscription subscription(String status, String planCode, Instant periodEndsAt) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setStatus(status);
        subscription.setPlanCode(planCode);
        subscription.setCurrentPeriodEndsAt(periodEndsAt);
        return subscription;
    }
}

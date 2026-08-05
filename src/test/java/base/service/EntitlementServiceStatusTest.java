package base.service;

import base.entity.TenantSubscription;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntitlementServiceStatusTest {

    private final EntitlementService service = new EntitlementService();

    @Test
    void reportsExpiredAfterPaidPeriodEnds() {
        Instant now = Instant.parse("2026-08-05T08:00:00Z");
        TenantSubscription subscription = subscription("ACTIVE", now.minusSeconds(1));

        assertEquals("EXPIRED", service.effectiveStatus(subscription, now));
    }

    @Test
    void keepsPastDueDuringGracePeriod() {
        Instant now = Instant.parse("2026-08-05T08:00:00Z");
        TenantSubscription subscription = subscription("ACTIVE", now.minusSeconds(1));
        subscription.setGraceEndsAt(now.plusSeconds(3_600));

        assertEquals("PAST_DUE", service.effectiveStatus(subscription, now));
    }

    @Test
    void keepsActiveBeforePeriodEnds() {
        Instant now = Instant.parse("2026-08-05T08:00:00Z");
        TenantSubscription subscription = subscription("ACTIVE", now.plusSeconds(1));

        assertEquals("ACTIVE", service.effectiveStatus(subscription, now));
    }

    private TenantSubscription subscription(String status, Instant periodEnd) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setStatus(status);
        subscription.setCurrentPeriodEndsAt(periodEnd);
        return subscription;
    }
}
package base.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionEntitlementFilterTest {

    @Test
    void normalizesRootPathBeforeCheckingSubscriptionExemption() {
        String path = SubscriptionEntitlementFilter.normalizePath("/api/subscriptions/payments");

        assertEquals("subscriptions/payments", path);
        assertTrue(SubscriptionEntitlementFilter.isSubscriptionRecoveryPath(path));
    }

    @Test
    void acceptsPathAlreadyRelativeToApiRoot() {
        String path = SubscriptionEntitlementFilter.normalizePath("subscriptions/payments");

        assertEquals("subscriptions/payments", path);
        assertTrue(SubscriptionEntitlementFilter.isSubscriptionRecoveryPath(path));
    }
}

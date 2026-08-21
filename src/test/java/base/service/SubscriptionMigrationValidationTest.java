package base.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SubscriptionMigrationValidationTest {

    @Inject
    EntityManager entityManager;

    @Test
    void createsSubscriptionPlansAndTenantSubscriptions() {
        assertTrue(tableExists("subscription_plans"));
        assertTrue(tableExists("tenant_subscriptions"));
        assertTrue(columnExists("tenant_subscriptions", "tenant_id"));
        assertTrue(columnExists("tenant_subscriptions", "plan_code"));
        assertTrue(columnExists("tenant_subscriptions", "current_period_ends_at"));
    }

    @Test
    void renamesPlansToBasePlusAndPremium() {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM subscription_plans WHERE code IN ('BASE', 'PLUS', 'PREMIUM')"
        ).getSingleResult();
        assertEquals(3L, count.longValue());
    }
    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = :tableName"
        ).setParameter("tableName", tableName).getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = :tableName AND column_name = :columnName"
        ).setParameter("tableName", tableName)
                .setParameter("columnName", columnName)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }
}

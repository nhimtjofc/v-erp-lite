package base.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WooCommerceMigrationValidationTest.NoKeycloakProfile.class)
class WooCommerceMigrationValidationTest {

    @Inject
    EntityManager entityManager;

    @Test
    void createsTenantScopedIntegrationFoundationAndDeduplicationConstraints() {
        assertTrue(tableExists("sales_channels"));
        assertTrue(columnExists("sales_channels", "tenant_id"));
        assertTrue(columnExists("sales_channels", "business_id"));
        assertTrue(constraintExists(
                "sales_channels",
                "uq_sales_channels_tenant_business_type",
                "UNIQUE"
        ));

        assertTrue(tableExists("external_entity_mappings"));
        assertTrue(columnExists("external_entity_mappings", "tenant_id"));
        assertTrue(constraintExists(
                "external_entity_mappings",
                "uq_external_mappings_erp",
                "UNIQUE"
        ));
        assertTrue(constraintExists(
                "external_entity_mappings",
                "uq_external_mappings_external",
                "UNIQUE"
        ));

        assertTrue(columnExists("webhook_events", "sales_channel_id"));
        assertTrue(columnExists("webhook_events", "external_event_id"));
        assertTrue(columnExists("webhook_events", "channel_metadata"));
        assertTrue(constraintExists(
                "webhook_events",
                "uq_webhook_events_channel_event",
                "UNIQUE"
        ));
    }

    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = :tableName"
        ).setParameter("tableName", tableName).getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = :tableName "
                        + "AND column_name = :columnName"
        ).setParameter("tableName", tableName)
                .setParameter("columnName", columnName)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private boolean constraintExists(String tableName, String constraintName, String constraintType) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = DATABASE() AND table_name = :tableName "
                        + "AND constraint_name = :constraintName AND constraint_type = :constraintType"
        ).setParameter("tableName", tableName)
                .setParameter("constraintName", constraintName)
                .setParameter("constraintType", constraintType)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    public static class NoKeycloakProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.keycloak.admin-client.server-url", "http://127.0.0.1:1"
            );
        }
    }
}

package base.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MigrationValidationTest {

    @Inject
    EntityManager entityManager;

    @Test
    void flywayMigrationCreatesCoreWalletTablesAndColumns() {
        assertTrue(tableExists("wallets"));
        assertTrue(tableExists("wallet_transactions"));
        assertTrue(tableExists("banks"));
        assertTrue(columnExists("wallet_transactions", "tenant_id"));
        assertTrue(columnExists("wallet_transactions", "sequence_no"));
        assertTrue(columnExists("wallets", "available_balance_minor"));
        assertTrue(columnExists("wallets", "pending_balance_minor"));
        assertTrue(columnExists("wallets", "bank_id"));
        assertTrue(columnExists("banks", "code"));
        assertTrue(columnExists("banks", "bin"));
    }

    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = :tableName"
        ).setParameter("tableName", tableName).getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = :tableName AND column_name = :columnName"
        ).setParameter("tableName", tableName)
                .setParameter("columnName", columnName)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }
}

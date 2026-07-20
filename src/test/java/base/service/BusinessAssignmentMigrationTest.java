package base.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class BusinessAssignmentMigrationTest {

    @Inject
    EntityManager entityManager;

    @Test
    void seededOperatorUsesTheOwnersBusiness() {
        UUID ownerBusinessId = assignedBusinessId("owner@v-erp.com");
        UUID operatorBusinessId = assignedBusinessId("operator@v-erp.com");

        assertNotNull(ownerBusinessId);
        assertEquals(ownerBusinessId, operatorBusinessId);
    }

    private UUID assignedBusinessId(String email) {
        Object value = entityManager.createNativeQuery(
                        "SELECT business_id FROM users WHERE tenant_id = 'v-erp-app' AND email = :email"
                )
                .setParameter("email", email)
                .getSingleResult();
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}

package base.service;

import base.entity.Business;
import base.entity.Role;
import base.entity.User;
import base.security.BusinessAccess;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class BusinessAccessTest {

    @Inject
    BusinessAccess businessAccess;

    @Test
    void newOperatorIsSynchronizedWithTheTenantOwnerBusiness() {
        User owner = User.find(
                "email = ?1 and tenantId = ?2",
                "owner@v-erp.com",
                "v-erp-app"
        ).firstResult();
        assertNotNull(owner);
        Business ownerBusiness = businessAccess.findForUser(owner, "v-erp-app");
        assertNotNull(ownerBusiness);

        User operator = new User();
        operator.setId(UUID.randomUUID());
        operator.setTenantId("v-erp-app");
        operator.setRole(Role.find("name", "operator").firstResult());

        businessAccess.synchronizeAssignment(operator, "v-erp-app");

        assertNotNull(operator.getBusiness());
        assertEquals(ownerBusiness.getId(), operator.getBusiness().getId());
    }
}

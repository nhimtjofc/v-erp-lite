package base.service;

import base.entity.Customer;
import base.entity.Role;
import base.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    @Inject
    WalletService walletService;

    @Transactional
    public User provisionUser(String email, String tenantCode, String keycloakId, String firstName, String lastName, String avatarUrl) {
        // Double check inside transaction to prevent duplicates in case of concurrent requests
        User user = User.find("email = ?1 and tenantId = ?2", email, tenantCode).firstResult();
        if (user != null) {
            boolean modified = false;
            if (user.getKeycloakId() == null && keycloakId != null) {
                user.setKeycloakId(keycloakId);
                modified = true;
            }
            if (user.getAvatarUrl() == null && avatarUrl != null) {
                user.setAvatarUrl(avatarUrl);
                modified = true;
            }
            walletService.getOrCreateUserWallet(tenantCode, user.getId(), "VND", user.getId());
            return user;
        }

        log.info("Auto-provisioning user in database: email={}, tenant={}, keycloakId={}", email, tenantCode, keycloakId);

        // Resolve customer role
        Role role = Role.find("name", "customer").firstResult();
        if (role == null) {
            log.warn("Customer role not found in database; using null role");
        }

        user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantCode);
        user.setEmail(email);
        user.setFirstName(firstName != null ? firstName : "Google");
        user.setLastName(lastName != null ? lastName : "User");
        user.setKeycloakId(keycloakId);
        user.setAvatarUrl(avatarUrl);
        user.setRole(role);
        user.setActive(true);
        user.persist();
        walletService.getOrCreateUserWallet(tenantCode, user.getId(), "VND", user.getId());

        // Create customer profile locally immediately
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantCode);
        customer.setEmail(email);
        customer.setFirstName(firstName != null ? firstName : "Google");
        customer.setLastName(lastName != null ? lastName : "User");
        customer.setActive(true);
        customer.persist();

        return user;
    }
}

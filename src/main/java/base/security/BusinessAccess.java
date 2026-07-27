package base.security;

import base.entity.Business;
import base.entity.User;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RequestScoped
public class BusinessAccess {

    private static final Logger log = LoggerFactory.getLogger(BusinessAccess.class);

    @Inject
    CallerContext caller;

    public boolean isScopedStaff() {
        return !caller.hasRole("admin") && caller.hasRole("operator");
    }

    public Business findCurrentBusiness() {
        return findForUser(caller.resolveUser(), caller.tenantCode());
    }

    public Business requireCurrentBusiness() {
        Business business = findCurrentBusiness();
        if (business == null) {
            throw new ForbiddenException("No business is assigned to the current user");
        }
        return business;
    }

    public Business findForUser(User user, String tenantId) {
        log.info("findForUser: user={} email={} tenantId={}", user != null ? user.getId() : null, user != null ? user.getEmail() : null, tenantId);
        if (user == null || tenantId == null) {
            log.warn("findForUser: user or tenantId is null");
            return null;
        }

        Business assignedBusiness = user.getBusiness();
        log.info("findForUser: user.getBusiness() = {}", assignedBusiness != null ? assignedBusiness.getId() + "/" + assignedBusiness.getName() + "/" + assignedBusiness.getTenantId() : "null");
        if (assignedBusiness != null && tenantId.equals(assignedBusiness.getTenantId())) {
            log.info("findForUser: RETURNING assignedBusiness={}", assignedBusiness.getId());
            return assignedBusiness;
        }

        // Fallback: business created by this user
        log.info("findForUser: checking createdBy={}", user.getId());
        Business business = Business.find("tenantId = ?1 and createdBy = ?2", tenantId, user.getId()).firstResult();
        if (business != null) {
            log.info("findForUser: RETURNING createdBy business={}", business.getId());
            return business;
        }
        log.info("findForUser: no business found for createdBy={}", user.getId());

        // For staff roles, fall back to the first owner's business, then any business in the tenant
        boolean isStaff = caller.hasRole("operator") || caller.hasRole("businessOwner");
        log.info("findForUser: isStaff={} (operator={} businessOwner={})",
                isStaff, caller.hasRole("operator"), caller.hasRole("businessOwner"));
        if (isStaff) {
            User owner = User.find("email = ?1 and tenantId = ?2", "owner@v-erp.com", tenantId).firstResult();
            log.info("findForUser: owner@v-erp.com found={}", owner != null);
            if (owner != null) {
                business = Business.find("tenantId = ?1 and createdBy = ?2", tenantId, owner.getId()).firstResult();
                log.info("findForUser: owner's business={}", business != null ? business.getId() : "null");
            }
            if (business == null) {
                business = Business.find("tenantId = ?1", tenantId).firstResult();
                log.info("findForUser: first business in tenant={}", business != null ? business.getId() : "null");
            }
        }

        log.info("findForUser: RETURNING final business={}", business != null ? business.getId() : "null");
        return business;
    }

    public void synchronizeAssignment(User user, String tenantId) {
        if (user == null || user.getRole() == null) {
            return;
        }

        String roleName = user.getRole().getName();
        if ("businessOwner".equals(roleName)) {
            user.setBusiness(findForUser(user, tenantId));
            return;
        }
        if (!"operator".equals(roleName)) {
            user.setBusiness(null);
            return;
        }

        Business assignedBusiness = user.getBusiness();
        if (assignedBusiness != null && assignedBusiness.getId() != null) {
            Business managedBusiness = Business.find(
                    "id = ?1 and tenantId = ?2",
                    assignedBusiness.getId(),
                    tenantId
            ).firstResult();
            if (managedBusiness != null) {
                user.setBusiness(managedBusiness);
                return;
            }
        }

        User owner = User.find(
                "tenantId = ?1 and role.name = ?2 ORDER BY createdAt",
                tenantId,
                "businessOwner"
        ).firstResult();
        user.setBusiness(findForUser(owner, tenantId));
    }

    public UUID requireCurrentBusinessId() {
        return requireCurrentBusiness().getId();
    }

    public void requireAccess(UUID businessId) {
        if (!isScopedStaff()) {
            return;
        }
        if (businessId == null || !requireCurrentBusinessId().equals(businessId)) {
            throw new ForbiddenException("Business access denied");
        }
    }
}

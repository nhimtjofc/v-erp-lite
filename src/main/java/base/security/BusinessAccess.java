package base.security;

import base.entity.Business;
import base.entity.User;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.UUID;

@RequestScoped
public class BusinessAccess {

    @Inject
    CallerContext caller;

    public boolean isScopedStaff() {
        return !caller.hasRole("admin")
                && (caller.hasRole("businessOwner") || caller.hasRole("operator"));
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
        if (user == null || tenantId == null) {
            return null;
        }

        Business assignedBusiness = user.getBusiness();
        if (assignedBusiness != null && tenantId.equals(assignedBusiness.getTenantId())) {
            return assignedBusiness;
        }

        return Business.find("tenantId = ?1 and createdBy = ?2", tenantId, user.getId()).firstResult();
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

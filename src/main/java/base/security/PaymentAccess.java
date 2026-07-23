package base.security;

import base.entity.Customer;
import base.entity.Order;
import base.entity.PaymentIntent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

@RequestScoped
public class PaymentAccess {

    @Inject
    CallerContext caller;

    @Inject
    BusinessAccess businessAccess;

    public void requireCustomerAccess(Customer customer) {
        if (customer == null || !isCustomerOnly()) {
            return;
        }

        Customer authenticatedCustomer = resolveCustomer();
        if (!customer.getId().equals(authenticatedCustomer.getId()) && !customer.getEmail().equalsIgnoreCase(caller.userEmail())) {
            throw new ForbiddenException("You are not allowed to checkout for this customer");
        }
    }

    public void requireOrderAccess(Order order) {
        if (order == null) {
            return;
        }

        if (isCustomerOnly()) {
            Customer customer = resolveCustomer();
            boolean isOwner = (order.getCustomer() != null && order.getCustomer().getId().equals(customer.getId()))
                    || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
            if (!isOwner) {
                throw new ForbiddenException("You are not allowed to pay for this order");
            }
        }

        if (businessAccess.isScopedStaff()) {
            if (order.getBusiness() == null) {
                throw new ForbiddenException("You are not allowed to access payments for this vendor order");
            }
            businessAccess.requireAccess(order.getBusiness().getId());
        }
    }

    public void requireIntentAccess(PaymentIntent intent) {
        if (intent == null) {
            return;
        }

        if (isCustomerOnly()) {
            Customer customer = resolveCustomer();
            if (!intent.getCustomerId().equals(customer.getId())) {
                throw new ForbiddenException("You are not allowed to access this payment");
            }
        }

        if (businessAccess.isScopedStaff()) {
            Order order = Order.find(
                    "id = ?1 and tenantId = ?2 and deletedAt IS NULL",
                    intent.getOrderId(),
                    caller.tenantCode()
            ).firstResult();
            if (order == null) {
                throw new ForbiddenException("You are not allowed to access this payment");
            }
            requireOrderAccess(order);
        }
    }

    private Customer resolveCustomer() {
        Customer customer = Customer.find(
                "email = ?1 and tenantId = ?2",
                caller.userEmail(),
                caller.tenantCode()
        ).firstResult();
        if (customer == null) {
            customer = new Customer();
            customer.setId(java.util.UUID.randomUUID());
            customer.setTenantId(caller.tenantCode());
            customer.setEmail(caller.userEmail());
            String preferredUsername = caller.preferredUsername();
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                customer.setFirstName(preferredUsername);
                customer.setLastName("");
            } else {
                customer.setFirstName("Customer");
                customer.setLastName("");
            }
            customer.setActive(true);
            customer.setCreatedBy(caller.userId());
            customer.persist();
        }
        return customer;
    }

    private boolean isCustomerOnly() {
        return (caller.hasRole("customer") || caller.hasRole("user"))
                && !caller.hasRole("admin")
                && !caller.hasRole("businessOwner")
                && !caller.hasRole("operator");
    }

}

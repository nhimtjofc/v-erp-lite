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

    public void requireCustomerAccess(Customer customer) {
        if (customer == null || !isCustomerOnly()) {
            return;
        }

        Customer authenticatedCustomer = resolveCustomer();
        if (!customer.getId().equals(authenticatedCustomer.getId())) {
            throw new ForbiddenException("You are not allowed to checkout for this customer");
        }
    }

    public void requireOrderAccess(Order order) {
        if (order == null || !isCustomerOnly()) {
            return;
        }

        Customer customer = resolveCustomer();
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customer.getId())) {
            throw new ForbiddenException("You are not allowed to pay for this order");
        }
    }

    public void requireIntentAccess(PaymentIntent intent) {
        if (intent == null || !isCustomerOnly()) {
            return;
        }

        Customer customer = resolveCustomer();
        if (!intent.getCustomerId().equals(customer.getId())) {
            throw new ForbiddenException("You are not allowed to access this payment");
        }
    }

    private Customer resolveCustomer() {
        Customer customer = Customer.find(
                "email = ?1 and tenantId = ?2",
                caller.userEmail(),
                caller.tenantCode()
        ).firstResult();
        if (customer == null) {
            throw new ForbiddenException("Customer profile not found for authenticated caller");
        }
        return customer;
    }

    private boolean isCustomerOnly() {
        return caller.hasRole("customer")
                && !caller.hasRole("admin")
                && !caller.hasRole("businessOwner")
                && !caller.hasRole("operator");
    }
}

package base.security;

import base.entity.Customer;
import base.entity.Order;
import base.entity.PaymentIntent;
import base.entity.User;
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
        if (order == null) {
            return;
        }

        if (isCustomerOnly()) {
            Customer customer = resolveCustomer();
            if (order.getCustomer() == null || !order.getCustomer().getId().equals(customer.getId())) {
                throw new ForbiddenException("You are not allowed to pay for this order");
            }
        }

        if (isBusinessOwnerOnly()) {
            User owner = caller.resolveUser();
            if (order.getBusiness() == null || order.getBusiness().getCreatedBy() == null
                    || !order.getBusiness().getCreatedBy().equals(owner.getId())) {
                throw new ForbiddenException("You are not allowed to access payments for this vendor order");
            }
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

        if (isBusinessOwnerOnly()) {
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

    private boolean isBusinessOwnerOnly() {
        return caller.hasRole("businessOwner")
                && !caller.hasRole("admin")
                && !caller.hasRole("operator");
    }
}

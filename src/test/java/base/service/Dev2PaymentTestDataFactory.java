package base.service;

import base.entity.Business;
import base.entity.Customer;
import base.entity.Order;
import base.entity.OrderItem;
import base.entity.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class Dev2PaymentTestDataFactory {

    @Transactional
    public SeedOrder seedOrder(String tenantId, BigDecimal finalAmount) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTenantId(tenantId);
        customer.setFirstName("Khách");
        customer.setLastName("Kiểm thử");
        customer.setEmail("customer-" + UUID.randomUUID() + "@example.com");
        customer.persist();

        Business seller = new Business();
        seller.setId(UUID.randomUUID());
        seller.setTenantId(tenantId);
        seller.setName("Người bán kiểm thử");
        seller.persist();

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setTenantId(tenantId);
        product.setBusiness(seller);
        product.setSku("SKU-" + UUID.randomUUID());
        product.setName("Sản phẩm kiểm thử");
        product.setBasePrice(finalAmount);
        product.persist();

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTenantId(tenantId);
        order.setBusiness(seller);
        order.setCustomer(customer);
        order.setOrderNumber("ORD-" + UUID.randomUUID());
        order.setOrderDate(Instant.now());
        order.setStatus("PENDING");
        order.setShippingName("Khách kiểm thử");
        order.setShippingPhone("0900000000");
        order.setShippingAddress("Địa chỉ kiểm thử");
        order.setSubtotalAmount(finalAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setFinalAmount(finalAmount);
        order.persist();

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(finalAmount);
        item.setSubtotalAmount(finalAmount);
        item.persist();
        order.getItems().add(item);

        return new SeedOrder(order, customer, seller);
    }

    public record SeedOrder(Order order, Customer customer, Business seller) {}
}

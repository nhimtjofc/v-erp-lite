package base.rest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import base.entity.Business;
import base.entity.Customer;
import base.entity.CustomerVoucher;
import base.entity.EscrowRecord;
import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.OrderItem;
import base.entity.PaymentRefund;
import base.entity.Product;
import base.entity.ProductVariant;
import base.entity.User;
import base.entity.Voucher;
import base.security.BusinessAccess;
import base.security.CallerContext;
import base.security.PaymentAccess;
import base.service.EscrowService;
import base.service.OrderFulfillmentService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final long FLAT_SHIPPING_FEE_MINOR = 1000;
    private static final long FREE_SHIPPING_THRESHOLD_MINOR = 10000;
    private static final double TAX_RATE = 0.10;

    @Inject
    CallerContext caller;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    EscrowService escrowService;

    @Inject
    OrderFulfillmentService fulfillmentService;

    @Inject
    PaymentAccess paymentAccess;

    @Inject
    BusinessAccess businessAccess;

    public static class CartItem {
        public UUID productId;
        public UUID productVariantId;
        public int quantity;
    }

    public static class CheckoutRequest {
        public UUID customerId;
        public String voucherCode;
        public String shippingName;
        public String shippingPhone;
        public String shippingAddress;
        public String shippingNote;
        public List<CartItem> items;
    }

    public static class StatusUpdateRequest {
        public String status;
        public String note;
    }

    public static class OwnerDecisionRequest {
        public String decision;
        public String reason;
    }

    public static class OrderUpdateRequest {
        public String notes;
        public Long shippingFeeMinor;
        public BigDecimal discountPercent;
        public BigDecimal vatPercent;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List / Search
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public List<Order> listOrders(@QueryParam("search") String search,
                            @QueryParam("status") String status) {
        String tenantCode = caller.tenantCode();
        boolean isCustomer = securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");

        UUID customerId = null;
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), tenantCode).firstResult();
            if (c == null) {
                return List.of();
            }
            customerId = c.getId();
        }

        boolean isBusinessStaff = businessAccess.isScopedStaff();

        String normalizedSearch = search == null ? null : search.trim();
        String normalizedStatus = status == null ? null : status.trim();

        // Defensive: ensure null-safe access below even if analyzer cannot prove non-null
        if (normalizedSearch != null && normalizedSearch.isBlank()) {
            normalizedSearch = null;
        }
        if (normalizedStatus != null && normalizedStatus.isBlank()) {
            normalizedStatus = null;
        }

        boolean hasSearch = normalizedSearch != null;
        boolean hasStatus = normalizedStatus != null;

        StringBuilder query = new StringBuilder("tenantId = :tenantId AND deletedAt IS NULL");
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantCode);

        if (isCustomer) {
            query.append(" AND customer.id = :customerId");
            params.put("customerId", customerId);
        }

        if (isBusinessStaff) {
            Business business = businessAccess.findCurrentBusiness();
            if (business != null) {
                query.append(" AND business.id = :businessId");
                params.put("businessId", business.getId());
            } else {
                query.append(" AND id IS NULL");
            }
        }

        if (hasStatus) {
            query.append(" AND status = :status");
            params.put("status", normalizedStatus.toUpperCase());
        }

        if (hasSearch) {
            String escaped = normalizedSearch.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.append(" AND (LOWER(orderNumber) LIKE LOWER(CONCAT('%',:search,'%')) ESCAPE '\\' OR LOWER(shippingName) LIKE LOWER(CONCAT('%',:search,'%')) ESCAPE '\\' OR LOWER(shippingPhone) LIKE LOWER(CONCAT('%',:search,'%')) ESCAPE '\\')");
            params.put("search", escaped);
        }

        if ("PAID_ESCROW".equalsIgnoreCase(normalizedStatus)) {
            query.append(" ORDER BY ownerDecisionDeadline ASC");
        } else {
            query.append(" ORDER BY orderDate DESC");
        }
        return Order.find(query.toString(), params).list();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/stats")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public Map<String, Object> getStats() {
        String tenantCode = caller.tenantCode();
        boolean isBusinessStaff = businessAccess.isScopedStaff();
        UUID businessId = null;
        if (isBusinessStaff) {
            Business business = businessAccess.findCurrentBusiness();
            if (business != null) {
                businessId = business.getId();
            }
        }

        // Count per status
        List<Object[]> rows;
        if (isBusinessStaff) {
            String sql = "SELECT status, COUNT(*) FROM orders WHERE tenant_id = ?1 AND business_id = ?2 AND deleted_at IS NULL GROUP BY status";
            rows = Order.getEntityManager().createNativeQuery(sql)
                    .setParameter(1, tenantCode)
                    .setParameter(2, businessId)
                    .getResultList();
        } else {
            String sql = "SELECT status, COUNT(*) FROM orders WHERE tenant_id = ?1 AND deleted_at IS NULL GROUP BY status";
            rows = Order.getEntityManager().createNativeQuery(sql)
                    .setParameter(1, tenantCode)
                    .getResultList();
        }
        Map<String, Long> countByStatus = new HashMap<>();
        for (String s : List.of("PENDING_PAYMENT", "PAID_ESCROW", "OWNER_ACCEPTED", "FULFILLING",
                "DELIVERED", "COMPLETED", "OWNER_DECLINED", "REFUNDED", "CANCELLED",
                "PENDING", "CONFIRMED", "SHIPPING", "SHIPPED")) {
            countByStatus.put(s, 0L);
        }
        for (Object[] row : rows) {
            countByStatus.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Total revenue (COMPLETED orders only)
        List<Order> completed;
        if (isBusinessStaff) {
            completed = Order.list("tenantId = ?1 AND status = ?2 AND business.id = ?3", tenantCode, "COMPLETED", businessId);
        } else {
            completed = Order.list("tenantId = ?1 AND status = ?2", tenantCode, "COMPLETED");
        }
        long totalRevenue = completed.stream()
                .mapToLong(Order::getFinalAmountMinor)
                .sum();

        // Recent 5 orders
        List<Order> recentOrders;
        if (isBusinessStaff) {
            recentOrders = Order.find("tenantId = ?1 AND business.id = ?2 AND deletedAt IS NULL ORDER BY orderDate DESC", tenantCode, businessId)
                    .page(io.quarkus.panache.common.Page.ofSize(5))
                    .list();
        } else {
            recentOrders = Order.find("tenantId = ?1 AND deletedAt IS NULL ORDER BY orderDate DESC", tenantCode)
                    .page(io.quarkus.panache.common.Page.ofSize(5))
                    .list();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalRevenue", totalRevenue);
        result.put("totalOrders", countByStatus.values().stream().mapToLong(Long::longValue).sum());
        result.put("countByStatus", countByStatus);
        result.put("recentOrders", recentOrders);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get Single
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public Order getOrder(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            if (c == null || !order.getCustomer().getId().equals(c.getId())) {
                throw new ForbiddenException("You are not allowed to view this order");
            }
        }
        return order;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Order History
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}/history")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public List<OrderHistory> getHistory(@PathParam("id") UUID id) {
        // Verify order belongs to tenant
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            if (c == null || !order.getCustomer().getId().equals(c.getId())) {
                throw new ForbiddenException("You are not allowed to view this order history");
            }
        }
        return OrderHistory.list("order.id = ?1 ORDER BY createdAt DESC", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Escrow Records
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}/escrow")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public List<EscrowRecord> getEscrowRecords(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            if (c == null || !order.getCustomer().getId().equals(c.getId())) {
                throw new ForbiddenException("You are not allowed to view escrow records for this order");
            }
        }
        return EscrowRecord.find("tenantId = ?1 AND orderId = ?2 ORDER BY createdAt ASC", caller.tenantCode(), id).list();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment Refunds
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}/refunds")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public List<PaymentRefund> getRefunds(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            if (c == null || !order.getCustomer().getId().equals(c.getId())) {
                throw new ForbiddenException("You are not allowed to view refunds for this order");
            }
        }
        return PaymentRefund.find("tenantId = ?1 AND orderId = ?2 ORDER BY createdAt ASC", caller.tenantCode(), id).list();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Checkout
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/checkout")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    @Transactional
    public Response checkoutOrder(CheckoutRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.customerId == null) {
            throw new BadRequestException("Customer ID is required");
        }
        if (request.items == null || request.items.isEmpty()) {
            throw new BadRequestException("Order must contain at least one item");
        }
        if (request.shippingName == null || request.shippingName.isBlank()) {
            throw new BadRequestException("Shipping name is required");
        }
        if (request.shippingPhone == null || request.shippingPhone.isBlank()) {
            throw new BadRequestException("Shipping phone number is required");
        }
        if (request.shippingAddress == null || request.shippingAddress.isBlank()) {
            throw new BadRequestException("Shipping address is required");
        }

        String tenantCode = caller.tenantCode();
        UUID userId = caller.userId();

        // 1. Resolve Customer
        Customer customer = Customer.find("id = ?1 and tenantId = ?2", request.customerId, tenantCode).firstResult();
        if (customer == null) {
            throw new BadRequestException("Customer not found");
        }
        paymentAccess.requireCustomerAccess(customer);

        // 2. Generate Order Number
        String orderNumber = "ORD-" + Instant.now().getEpochSecond() + "-" + (int)(Math.random() * 900 + 100);

        // 3. Process Cart Items and calculate Subtotal
        long subtotal = 0;
        List<OrderItem> orderItems = new ArrayList<>();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTenantId(tenantCode);
        order.setCreatedBy(userId);
        Business orderBusiness = null;

        for (CartItem item : request.items) {
            if (item.quantity <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }

            // Resolve Product
            Product product = Product.find("id = ?1 and tenantId = ?2", item.productId, tenantCode).firstResult();
            if (product == null) {
                throw new BadRequestException("Product not found");
            }
            if (product.getBusiness() == null) {
                throw new BadRequestException("Every product in an order must belong to a business");
            }
            if (orderBusiness == null) {
                orderBusiness = product.getBusiness();
            } else if (!orderBusiness.getId().equals(product.getBusiness().getId())) {
                throw new BadRequestException("Multi-vendor order decisions are not supported; checkout one vendor at a time");
            }

            long unitPrice;
            ProductVariant variant = null;

            if (item.productVariantId != null) {
                // Resolve Variant
                variant = ProductVariant.find("id = ?1 and product.id = ?2 and tenantId = ?3",
                        item.productVariantId, item.productId, tenantCode).firstResult();
                if (variant == null) {
                    throw new BadRequestException("Product variant not found");
                }

                if (variant.getAvailableQuantity() < item.quantity) {
                    throw new BadRequestException("Product variant " + variant.getSku() + " is out of stock (Available: " + variant.getAvailableQuantity() + ")");
                }

                unitPrice = variant.getPriceMinor();
                variant.setReservedQuantity(variant.getReservedQuantity() + item.quantity);
                variant.setUpdatedBy(userId);
            } else {
                // Handle base product stock and price
                if (product.getAvailableQuantity() < item.quantity) {
                    throw new BadRequestException("Product " + product.getName() + " is out of stock (Available: " + product.getAvailableQuantity() + ")");
                }

                // Check sale price validity
                Instant now = Instant.now();
                if (product.getSalePriceMinor() != null && product.getSaleStartsAt() != null && product.getSaleEndsAt() != null
                        && now.isAfter(product.getSaleStartsAt()) && now.isBefore(product.getSaleEndsAt())) {
                    unitPrice = product.getSalePriceMinor();
                } else {
                    unitPrice = product.getBasePriceMinor();
                }

                product.setReservedQuantity(product.getReservedQuantity() + item.quantity);
                product.setUpdatedBy(userId);
            }

            long itemSubtotal = unitPrice * item.quantity;
            subtotal += itemSubtotal;

            OrderItem orderItem = new OrderItem();
            orderItem.setId(UUID.randomUUID());
            orderItem.setTenantId(tenantCode);
            orderItem.setCreatedBy(userId);
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductVariant(variant);
            orderItem.setQuantity(item.quantity);
            orderItem.setUnitPriceMinor(unitPrice);
            orderItem.setSubtotalAmountMinor(itemSubtotal);
            orderItems.add(orderItem);
        }

        // 4. Handle Voucher
        long discount = 0;
        Voucher voucher = null;
        if (request.voucherCode != null && !request.voucherCode.isBlank()) {
            voucher = Voucher.find("code = ?1 and tenantId = ?2", request.voucherCode.trim(), tenantCode).firstResult();
            if (voucher == null) {
                throw new BadRequestException("Invalid voucher code");
            }
            if (!voucher.isValid(subtotal)) {
                throw new BadRequestException("Voucher is not valid for this order");
            }
            discount = voucher.calculateDiscount(subtotal);
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucher.setUpdatedBy(userId);

            // Mark customer's collected voucher as used
            CustomerVoucher cv = CustomerVoucher.find("customerId = ?1 and voucherId = ?2 and tenantId = ?3 and isUsed = false", 
                    request.customerId, voucher.getId(), tenantCode).firstResult();
            if (cv != null) {
                cv.setUsed(true);
            }
        }

        // 5. Calculate Shipping Fee (USD)
        // Flat rate: $10. Free shipping if subtotal - discount >= $100.
        long orderAfterDiscount = subtotal - discount;
        long shippingFee = FLAT_SHIPPING_FEE_MINOR;
        if (orderAfterDiscount >= FREE_SHIPPING_THRESHOLD_MINOR) {
            shippingFee = 0;
        }

        // 6. Calculate VAT Tax (10% of order value after discount)
        long tax = Math.round((double) orderAfterDiscount * TAX_RATE);

        // 7. Calculate Final Amount
        long finalAmount = orderAfterDiscount + shippingFee + tax;

        // 8. Populate Order Details
        order.setCustomer(customer);
        order.setBusiness(orderBusiness);
        order.setVoucher(voucher);
        order.setOrderNumber(orderNumber);
        order.setStatus("PENDING_PAYMENT");
        order.setShippingName(request.shippingName);
        order.setShippingPhone(request.shippingPhone);
        order.setShippingAddress(request.shippingAddress);
        order.setShippingNote(request.shippingNote);
        order.setSubtotalAmountMinor(subtotal);
        order.setDiscountAmountMinor(discount);
        order.setShippingFeeMinor(shippingFee);
        order.setTaxAmountMinor(tax);
        order.setFinalAmountMinor(finalAmount);
        order.setItems(orderItems);

        order.persist();

        persistHistory(order, null, "PENDING_PAYMENT", "Order created", userId, tenantCode);

        return Response.created(URI.create("/api/orders/" + order.getId())).entity(order).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update Status
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/{id}/owner-decision")
    @RolesAllowed({"businessOwner"})
    @Transactional
    public Order ownerDecision(@PathParam("id") UUID id, OwnerDecisionRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        User owner = caller.resolveUser();
        return fulfillmentService.decide(
                caller.tenantCode(), id, owner, request.decision, request.reason, caller.userId());
    }

    @POST
    @Path("/{id}/confirm-receipt")
    @RolesAllowed("customer")
    @Transactional
    public Order confirmReceipt(@PathParam("id") UUID id) {
        boolean isCustomerOnly = securityIdentity.hasRole("customer")
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (!isCustomerOnly) {
            throw new ForbiddenException("Only the buyer can confirm receipt");
        }

        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        paymentAccess.requireOrderAccess(order);

        String oldStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if ("COMPLETED".equals(oldStatus)) {
            return order;
        }
        if (!"DELIVERED".equals(oldStatus)) {
            throw new BadRequestException("Only a delivered order can be confirmed as received");
        }

        UUID userId = caller.userId();
        completeOrder(order, tenantCode, userId);
        order.setStatus("COMPLETED");
        order.setUpdatedBy(userId);
        persistHistory(order, oldStatus, "COMPLETED", "Buyer confirmed receipt", userId, tenantCode);
        return order;
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public Order updateStatus(@PathParam("id") UUID id, StatusUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.status == null || request.status.isBlank()) {
            throw new BadRequestException("Status is required");
        }

        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);

        String oldStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        String newStatus = request.status.trim().toUpperCase();

        if (oldStatus.equals(newStatus)) {
            return order;
        }

        if (!List.of("PENDING_PAYMENT", "OWNER_ACCEPTED", "FULFILLING", "DELIVERED", "COMPLETED",
                "OWNER_DECLINED", "REFUNDED", "CANCELLED", "PENDING", "CONFIRMED", "SHIPPING",
                "SHIPPED").contains(newStatus)) {
            throw new BadRequestException("Invalid status value");
        }

        if (!isAllowedStatusTransition(oldStatus, newStatus)) {
            throw new BadRequestException("Cannot change order status from " + oldStatus + " to " + newStatus);
        }

        UUID userId = caller.userId();

        // Stock and Reservation Lifecycle Management
        // Unpaid cancellation releases reservations. Paid declines are handled
        // only by the owner-decision endpoint so the payment refund is atomic.
        if (List.of("PENDING", "PENDING_PAYMENT", "CONFIRMED", "SHIPPING").contains(oldStatus)
                && "CANCELLED".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                if (item.getProductVariant() != null) {
                    ProductVariant variant = item.getProductVariant();
                    variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - item.getQuantity()));
                    variant.setUpdatedBy(userId);
                } else {
                    Product product = item.getProduct();
                    product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - item.getQuantity()));
                    product.setUpdatedBy(userId);
                }
            }
        }
        // Inventory becomes committed when fulfillment starts.
        else if (("SHIPPING".equals(oldStatus) && "SHIPPED".equals(newStatus))
                || (List.of("PENDING", "CONFIRMED", "OWNER_ACCEPTED").contains(oldStatus)
                && List.of("SHIPPED", "FULFILLING", "DELIVERED", "COMPLETED").contains(newStatus))) {
            for (OrderItem item : order.getItems()) {
                if (item.getProductVariant() != null) {
                    ProductVariant variant = item.getProductVariant();
                    variant.setStockQuantity(Math.max(0, variant.getStockQuantity() - item.getQuantity()));
                    variant.setReservedQuantity(Math.max(0, variant.getReservedQuantity() - item.getQuantity()));
                    variant.setUpdatedBy(userId);
                } else {
                    Product product = item.getProduct();
                    product.setStockQuantity(Math.max(0, product.getStockQuantity() - item.getQuantity()));
                    product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - item.getQuantity()));
                    product.setUpdatedBy(userId);
                }
            }
        }
        // 3. From SHIPPED/COMPLETED to CANCELLED: return actual stock
        else if (List.of("SHIPPED", "COMPLETED").contains(oldStatus) && "CANCELLED".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                if (item.getProductVariant() != null) {
                    ProductVariant variant = item.getProductVariant();
                    variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                    variant.setUpdatedBy(userId);
                } else {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    product.setUpdatedBy(userId);
                }
            }
        }

        // Reactivate voucher if order is cancelled
        if ("CANCELLED".equals(newStatus)) {
            if (order.getVoucher() != null) {
                Voucher v = order.getVoucher();
                v.setUsedCount(Math.max(0, v.getUsedCount() - 1));
                v.setUpdatedBy(userId);

                CustomerVoucher cv = CustomerVoucher.find("customerId = ?1 and voucherId = ?2 and tenantId = ?3",
                        order.getCustomer().getId(), v.getId(), tenantCode).firstResult();
                if (cv != null) {
                    cv.setUsed(false);
                }
            }
        }

        // Award points, update membership, and release escrow on completion.
        if ("COMPLETED".equals(newStatus) && !"COMPLETED".equals(oldStatus)) {
            completeOrder(order, tenantCode, userId);
        }

        if ("CANCELLED".equals(newStatus) && !"CANCELLED".equals(oldStatus)) {
            escrowService.refundOrderEscrow(tenantCode, order.getId(), userId);
        }

        order.setStatus(newStatus);
        order.setUpdatedBy(userId);

        // Record history
        persistHistory(order, oldStatus, newStatus, request.note, userId, tenantCode);

        return order;
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public Order updateOrder(@PathParam("id") UUID id, OrderUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);

        BigDecimal discountPct = request.discountPercent != null ? request.discountPercent : order.getDiscountPercent();
        BigDecimal vatPct = request.vatPercent != null ? request.vatPercent : order.getVatPercent();
        if (discountPct == null) {
            discountPct = BigDecimal.ZERO;
        }
        if (vatPct == null) {
            vatPct = BigDecimal.ZERO;
        }
        validatePercent("Discount percent", discountPct);
        validatePercent("VAT percent", vatPct);

        long shippingFee = request.shippingFeeMinor != null ? request.shippingFeeMinor : order.getShippingFeeMinor();
        if (shippingFee < 0) {
            throw new BadRequestException("Shipping fee must be greater than or equal to 0");
        }

        long subtotal = order.getItems().stream()
                .mapToLong(OrderItem::getSubtotalAmountMinor)
                .sum();
        order.setSubtotalAmountMinor(subtotal);

        long discountAmount = percentageOf(subtotal, discountPct);
        long afterDiscount = Math.max(0, subtotal - discountAmount);
        long vatAmount = percentageOf(afterDiscount, vatPct);
        long finalAmount = Math.max(0, afterDiscount + shippingFee + vatAmount);

        order.setDiscountPercent(discountPct);
        order.setDiscountAmountMinor(discountAmount);
        order.setVatPercent(vatPct);
        order.setVatAmountMinor(vatAmount);
        order.setTaxAmountMinor(vatAmount);
        order.setShippingFeeMinor(shippingFee);
        order.setFinalAmountMinor(finalAmount);
        if (request.notes != null) {
            order.setNotes(request.notes);
        }
        order.setUpdatedBy(caller.userId());

        return order;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helper
    // ─────────────────────────────────────────────────────────────────────────

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response deleteOrder(@PathParam("id") UUID id) {
        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("COMPLETED", "CANCELLED", "OWNER_DECLINED", "REFUNDED").contains(status)) {
            throw new BadRequestException("Only completed or cancelled orders can be deleted");
        }

        order.setDeletedAt(Instant.now());
        order.setUpdatedBy(caller.userId());

        return Response.noContent().build();
    }

    private void persistHistory(Order order, String oldStatus, String newStatus, String note, UUID userId, String tenantCode) {
        OrderHistory history = new OrderHistory();
        history.setId(UUID.randomUUID());
        history.setOrder(order);
        history.setTenantId(tenantCode);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setNote(note);
        history.setCreatedBy(userId);
        history.persist();
    }

    private void completeOrder(Order order, String tenantCode, UUID userId) {
        Customer customer = order.getCustomer();
        if (customer != null) {
            long amount = order.getFinalAmountMinor();
            if (customer.getTotalSpendMinor() == null) {
                customer.setTotalSpendMinor(0L);
            }
            customer.setTotalSpendMinor(customer.getTotalSpendMinor() + amount);
            customer.setPoints(customer.getPoints() + (int) (amount / 100));

            long totalSpend = customer.getTotalSpendMinor();
            if (totalSpend >= 300000) {
                customer.setTier("DIAMOND");
            } else if (totalSpend >= 100000) {
                customer.setTier("PLATINUM");
            } else if (totalSpend >= 20000) {
                customer.setTier("GOLD");
            } else {
                customer.setTier("SILVER");
            }
        }
        escrowService.releaseOrderEscrow(tenantCode, order.getId(), userId);
    }

    private void requireBusinessStaffAccess(Order order) {
        if (!businessAccess.isScopedStaff()) {
            return;
        }
        if (order.getBusiness() == null) {
            throw new ForbiddenException("You are not allowed to access this vendor order");
        }
        businessAccess.requireAccess(order.getBusiness().getId());
    }

    private void validatePercent(String label, BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BadRequestException(label + " must be between 0 and 100");
        }
    }

    private long percentageOf(long amountMinor, BigDecimal percent) {
        return BigDecimal.valueOf(amountMinor)
                .multiply(percent)
                .divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private boolean isAllowedStatusTransition(String oldStatus, String newStatus) {
        if ("PENDING_PAYMENT".equals(oldStatus)) {
            return "CANCELLED".equals(newStatus);
        }
        if ("OWNER_ACCEPTED".equals(oldStatus)) {
            return "FULFILLING".equals(newStatus);
        }
        if ("FULFILLING".equals(oldStatus)) {
            return "DELIVERED".equals(newStatus);
        }
        if ("DELIVERED".equals(oldStatus)) {
            return "COMPLETED".equals(newStatus);
        }
        if ("OWNER_DECLINED".equals(oldStatus)) {
            return "REFUNDED".equals(newStatus);
        }
        if ("PENDING".equals(oldStatus)) {
            return List.of("CONFIRMED", "CANCELLED").contains(newStatus);
        }
        if ("CONFIRMED".equals(oldStatus)) {
            return List.of("SHIPPING", "CANCELLED").contains(newStatus);
        }
        if ("SHIPPING".equals(oldStatus)) {
            return List.of("SHIPPED", "CANCELLED").contains(newStatus);
        }
        if ("SHIPPED".equals(oldStatus)) {
            return "COMPLETED".equals(newStatus);
        }
        return false;
    }
}

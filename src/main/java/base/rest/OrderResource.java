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

import base.entity.Customer;
import base.entity.CustomerVoucher;
import base.entity.Order;
import base.entity.OrderHistory;
import base.entity.OrderItem;
import base.entity.Product;
import base.entity.ProductVariant;
import base.entity.Voucher;
import base.security.CallerContext;
import base.service.EscrowService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

    @Inject
    CallerContext caller;

    @Inject
    EscrowService escrowService;

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

    // ─────────────────────────────────────────────────────────────────────────
    // List / Search
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public List<Order> list(@QueryParam("search") String search,
                            @QueryParam("status") String status) {
        String tenantCode = caller.tenantCode();

        boolean hasSearch = search != null && !search.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        if (hasSearch && hasStatus) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String upperStatus = status.trim().toUpperCase();
            return Order.find(
                    "tenantId = ?1 AND deletedAt IS NULL AND status = ?2 AND (LOWER(orderNumber) LIKE LOWER(CONCAT('%',?3,'%')) ESCAPE '\\' OR LOWER(shippingName) LIKE LOWER(CONCAT('%',?3,'%')) ESCAPE '\\' OR LOWER(shippingPhone) LIKE LOWER(CONCAT('%',?3,'%')) ESCAPE '\\')",
                    tenantCode, upperStatus, escaped
            ).list();
        } else if (hasSearch) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            return Order.find(
                    "tenantId = ?1 AND deletedAt IS NULL AND (LOWER(orderNumber) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\' OR LOWER(shippingName) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\' OR LOWER(shippingPhone) LIKE LOWER(CONCAT('%',?2,'%')) ESCAPE '\\')",
                    tenantCode, escaped
            ).list();
        } else if (hasStatus) {
            return Order.list("tenantId = ?1 AND deletedAt IS NULL AND status = ?2 ORDER BY orderDate DESC", tenantCode, status.trim().toUpperCase());
        }

        return Order.list("tenantId = ?1 AND deletedAt IS NULL ORDER BY orderDate DESC", tenantCode);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/stats")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public Map<String, Object> getStats() {
        String tenantCode = caller.tenantCode();

        // Count per status
        Map<String, Long> countByStatus = new HashMap<>();
        for (String s : List.of("PENDING", "CONFIRMED", "SHIPPED", "COMPLETED", "CANCELLED")) {
            long count = Order.count("tenantId = ?1 AND deletedAt IS NULL AND status = ?2", tenantCode, s);
            countByStatus.put(s, count);
        }

        // Total revenue (COMPLETED orders only)
        List<Order> completed = Order.list("tenantId = ?1 AND status = ?2", tenantCode, "COMPLETED");
        long totalRevenue = completed.stream()
                .mapToLong(Order::getFinalAmountMinor)
                .sum();

        // Recent 5 orders
        List<Order> recentOrders = Order.find("tenantId = ?1 AND deletedAt IS NULL ORDER BY orderDate DESC", tenantCode)
                .page(0, 5)
                .list();

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
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public Order get(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        return order;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Order History
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}/history")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public List<OrderHistory> getHistory(@PathParam("id") UUID id) {
        // Verify order belongs to tenant
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        return OrderHistory.list("order.id = ?1 ORDER BY createdAt ASC", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Checkout
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/checkout")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public Response checkout(CheckoutRequest request) {
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

        // 2. Generate Order Number
        String orderNumber = "ORD-" + Instant.now().getEpochSecond() + "-" + (int)(Math.random() * 900 + 100);

        // 3. Process Cart Items and calculate Subtotal
        long subtotal = 0;
        List<OrderItem> orderItems = new ArrayList<>();
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTenantId(tenantCode);
        order.setCreatedBy(userId);

        for (CartItem item : request.items) {
            if (item.quantity <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }

            // Resolve Product
            Product product = Product.find("id = ?1 and tenantId = ?2", item.productId, tenantCode).firstResult();
            if (product == null) {
                throw new BadRequestException("Product not found");
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
        long shippingFee = 1000; // Flat rate: $10 (1000 minor units). Free shipping if subtotal - discount >= $100.
        if (orderAfterDiscount >= 10000) {
            shippingFee = 0;
        }

        // 6. Calculate VAT Tax (10% of order value after discount)
        long tax = Math.round((double) orderAfterDiscount * 0.10);

        // 7. Calculate Final Amount
        long finalAmount = orderAfterDiscount + shippingFee + tax;

        // 8. Populate Order Details
        order.setCustomer(customer);
        order.setVoucher(voucher);
        order.setOrderNumber(orderNumber);
        order.setStatus("PENDING");
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

        // 9. Write initial OrderHistory record (order created as PENDING)
        persistHistory(order, null, "PENDING", "Order created", userId, tenantCode);

        return Response.created(URI.create("/api/orders/" + order.getId())).entity(order).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update Status
    // ─────────────────────────────────────────────────────────────────────────

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public Order updateStatus(@PathParam("id") UUID id, StatusUpdateRequest request) {
        if (request.status == null || request.status.isBlank()) {
            throw new BadRequestException("Status is required");
        }

        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        String oldStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        String newStatus = request.status.trim().toUpperCase();

        if (oldStatus.equals(newStatus)) {
            return order;
        }

        // Valid statuses: PENDING, CONFIRMED, SHIPPED, COMPLETED, CANCELLED
        if (!List.of("PENDING", "CONFIRMED", "SHIPPED", "COMPLETED", "CANCELLED").contains(newStatus)) {
            throw new BadRequestException("Invalid status value");
        }

        if (!isAllowedStatusTransition(oldStatus, newStatus)) {
            throw new BadRequestException("Cannot change order status from " + oldStatus + " to " + newStatus);
        }

        UUID userId = caller.userId();

        // Stock and Reservation Lifecycle Management
        // 1. From PENDING/CONFIRMED to CANCELLED: release reserved quantity
        if (List.of("PENDING", "CONFIRMED").contains(oldStatus) && "CANCELLED".equals(newStatus)) {
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
        // 2. From PENDING/CONFIRMED to SHIPPED/COMPLETED: deduct actual stock and clear reservation
        else if (List.of("PENDING", "CONFIRMED").contains(oldStatus) && List.of("SHIPPED", "COMPLETED").contains(newStatus)) {
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

        // Award points & update membership tier upon order completion
        if ("COMPLETED".equals(newStatus) && !"COMPLETED".equals(oldStatus)) {
            Customer customer = order.getCustomer();
            if (customer != null) {
                long amount = order.getFinalAmountMinor();
                if (customer.getTotalSpendMinor() == null) {
                    customer.setTotalSpendMinor(0L);
                }
                customer.setTotalSpendMinor(customer.getTotalSpendMinor() + amount);
                // 1 point per 100 units of minor currency
                int earnedPoints = (int) (amount / 100);
                    customer.setPoints(customer.getPoints() + earnedPoints);

                    // Update tier dynamically based on new totalSpend
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

        if ("CANCELLED".equals(newStatus) && !"CANCELLED".equals(oldStatus)) {
            escrowService.refundOrderEscrow(tenantCode, order.getId(), userId);
        }

        order.setStatus(newStatus);
        order.setUpdatedBy(userId);

        // Record history
        persistHistory(order, oldStatus, newStatus, request.note, userId, tenantCode);

        return order;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helper
    // ─────────────────────────────────────────────────────────────────────────

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("COMPLETED", "CANCELLED").contains(status)) {
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

    private boolean isAllowedStatusTransition(String oldStatus, String newStatus) {
        if ("PENDING".equals(oldStatus)) {
            return List.of("CONFIRMED", "CANCELLED").contains(newStatus);
        }
        if ("CONFIRMED".equals(oldStatus)) {
            return "SHIPPED".equals(newStatus);
        }
        if ("SHIPPED".equals(oldStatus)) {
            return "COMPLETED".equals(newStatus);
        }
        return false;
    }
}

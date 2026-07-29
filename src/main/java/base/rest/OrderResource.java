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
import base.entity.Wallet;
import base.security.BusinessAccess;
import base.security.CallerContext;
import base.security.PaymentAccess;
import base.service.EscrowService;
import base.service.InventoryCostService;
import base.service.NotificationService;
import base.service.OrderFulfillmentService;
import base.service.PaymentRefundService;
import base.service.PaymentService;
import base.service.ReturnRefundService;
import base.service.VietQRService;
import base.service.WalletService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String CURRENCY = "VND";
    private static final long FLAT_SHIPPING_FEE_MINOR = 250_000;
    private static final long FREE_SHIPPING_THRESHOLD_MINOR = 2_500_000;
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
    VietQRService vietQRService;

    @Inject
    BusinessAccess businessAccess;

    @Inject
    InventoryCostService inventoryCostService;

    @Inject
    NotificationService notificationService;

    @Inject
    WalletService walletService;

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentRefundService paymentRefundService;

    @Inject
    ReturnRefundService returnRefundService;

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
        public String shippingWard;
        public String shippingProvince;
        public String shippingNote;
        public String paymentMethod; // e.g. COD or BANK_TRANSFER
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

    public static class CancelItemRequest {
        public String reason;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List / Search
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @RolesAllowed({"admin", "businessOwner", "operator", "customer", "user"})
    public List<Order> listOrders(@QueryParam("search") String search,
                            @QueryParam("status") String status) {
        String tenantCode = caller.tenantCode();
        boolean isCustomer = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");

        UUID customerId = null;
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), tenantCode).firstResult();
            if (c != null) {
                customerId = c.getId();
            }
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
            if (customerId != null) {
                query.append(" AND (customer.id = :customerId OR createdBy = :userId)");
                params.put("customerId", customerId);
                params.put("userId", caller.userId());
            } else {
                query.append(" AND createdBy = :userId");
                params.put("userId", caller.userId());
            }
        }

        if (isBusinessStaff) {
            Business business = businessAccess.findCurrentBusiness();
            log.info("listOrders: isBusinessStaff=true, business={}", business != null ? business.getId() + "/" + business.getName() : "null");
            if (business != null) {
                query.append(" AND business.id = :businessId");
                params.put("businessId", business.getId());
            } else {
                query.append(" AND id IS NULL");
                log.warn("listOrders: businessAccess.findCurrentBusiness() returned null for staff user! Query will return no results.");
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
                "DELIVERED", "PENDING_COMPLETION", "COMPLETED", "OWNER_DECLINED", "REFUNDED", "CANCELLED",
                "RETURN_REQUESTED", "RETURN_APPROVED")) {
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
    @RolesAllowed({"admin", "businessOwner", "operator", "customer", "user"})
    public Order getOrder(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            UUID customerId = c != null ? c.getId() : null;
            boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                    || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
            if (!isOwner) {
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
    @RolesAllowed({"admin", "businessOwner", "operator", "customer", "user"})
    public List<OrderHistory> getHistory(@PathParam("id") UUID id) {
        // Verify order belongs to tenant
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            UUID customerId = c != null ? c.getId() : null;
            boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                    || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
            if (!isOwner) {
                throw new ForbiddenException("You are not allowed to view this order history");
            }
        }
        return OrderHistory.list("order.id = ?1 ORDER BY createdAt DESC, sequenceNo DESC", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Escrow Records
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}/escrow")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer", "user"})
    public List<EscrowRecord> getEscrowRecords(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            UUID customerId = c != null ? c.getId() : null;
            boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                    || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
            if (!isOwner) {
                throw new ForbiddenException("You are not allowed to view escrow records for this order");
            }
        }
        return EscrowRecord.find("tenantId = ?1 AND orderId = ?2 ORDER BY createdAt ASC", caller.tenantCode(), id).list();
    }

    @POST
    @Path("/{id}/escrow/force-release")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public List<EscrowRecord> forceReleaseEscrow(@PathParam("id") UUID id) {
        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        return escrowService.releaseOrderEscrow(tenantCode, id, caller.userId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment Refunds
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{id}/refunds")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer", "user"})
    public List<PaymentRefund> getRefunds(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);
        boolean isCustomer = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (isCustomer) {
            Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), caller.tenantCode()).firstResult();
            UUID customerId = c != null ? c.getId() : null;
            boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                    || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
            if (!isOwner) {
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
    @RolesAllowed({"admin", "businessOwner", "operator", "customer", "user"})
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
        if (request.shippingWard == null || request.shippingWard.isBlank()) {
            throw new BadRequestException("Shipping ward is required");
        }
        if (request.shippingProvince == null || request.shippingProvince.isBlank()) {
            throw new BadRequestException("Shipping province is required");
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
        String orderCurrency = CURRENCY;

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
            
            if (!CURRENCY.equalsIgnoreCase(product.getCurrency())) {
                throw new BadRequestException("All products in an order must use VND");
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
            orderItem.setUnitCostMinor(inventoryCostService.resolveUnitCost(product, variant));
            orderItem.setSubtotalAmountMinor(itemSubtotal);
            orderItem.setCurrency(CURRENCY);
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

        // 5. Calculate Shipping Fee (VND)
        // Flat rate: 250,000 VND. Free shipping from 2,500,000 VND.
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
        log.info("checkoutOrder: setting business={} (id={}) on order",
                orderBusiness != null ? orderBusiness.getName() : "null",
                orderBusiness != null ? orderBusiness.getId() : "null");
        order.setCustomer(customer);
        order.setBusiness(orderBusiness);
        order.setVoucher(voucher);
        order.setOrderNumber(orderNumber);
        order.setCurrency(orderCurrency);
        order.setPaymentMethod(request.paymentMethod);

        String initialStatus = "PENDING_PAYMENT";
        order.setStatus(initialStatus);
        
        order.setShippingName(request.shippingName);
        order.setShippingPhone(request.shippingPhone);
        order.setShippingAddress(request.shippingAddress);
        order.setShippingWard(request.shippingWard);
        order.setShippingProvince(request.shippingProvince);
        order.setShippingNote(request.shippingNote);
        order.setSubtotalAmountMinor(subtotal);
        order.setDiscountAmountMinor(discount);
        order.setShippingFeeMinor(shippingFee);
        order.setTaxAmountMinor(tax);
        order.setFinalAmountMinor(finalAmount);
        order.setItems(orderItems);

        order.persist();

        persistHistory(order, null, initialStatus, "Order created", userId, tenantCode);

        if ("WALLET".equalsIgnoreCase(request.paymentMethod)) {
            User callerDbUser = caller.resolveUser();
            Wallet userWallet = walletService.getOrCreateUserWallet(
                    tenantCode, callerDbUser.getId(), "VND", caller.userId());
            paymentService.processWalletPayment(order, userWallet, caller.userId());
        } else if ("BANK_TRANSFER".equalsIgnoreCase(request.paymentMethod)) {
            Wallet wallet = Wallet.find("tenantId = ?1 and ownerType = 'SELLER' and ownerId = ?2 and currency = 'VND'", tenantCode, orderBusiness.getId()).firstResult();

            if (wallet == null || wallet.getBankName() == null || wallet.getBankAccountNumber() == null) {
                throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(400)
                    .entity(java.util.Map.of("message", "Shop owner has not configured banking information for VietQR"))
                    .build()
                );
            }
            String message = "Thanh toan " + orderNumber;
            String qrBase64 = vietQRService.generateQrBase64(wallet.getBankName(), wallet.getBankAccountNumber(), finalAmount, message);
            order.setPaymentQrCodeBase64(qrBase64);
        }

        notificationService.notifyBusinessStaff(
                order,
                "ORDER_PLACED",
                "New order " + orderNumber + " has been placed and is waiting for processing.",
                userId);

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
        if ("PENDING_COMPLETION".equals(oldStatus) || "COMPLETED".equals(oldStatus)) {
            return order;
        }
        if (!"DELIVERED".equals(oldStatus)) {
            throw new BadRequestException("Only a delivered order can be confirmed as received");
        }

        UUID userId = caller.userId();
        order.setStatus("PENDING_COMPLETION");
        order.setUpdatedBy(userId);
        persistHistory(order, oldStatus, "PENDING_COMPLETION", "Buyer confirmed receipt; awaiting 7-day escrow hold", userId, tenantCode);
        notificationService.notifyBusinessStaff(
                order,
                "ORDER_RECEIVED",
                "The buyer confirmed receipt of order " + order.getOrderNumber() + ". Escrow hold starts now (7 days).",
                userId);
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

        if (!List.of("PENDING_PAYMENT", "PAID_ESCROW", "OWNER_ACCEPTED", "FULFILLING", "DELIVERED",
                "PENDING_COMPLETION", "COMPLETED", "OWNER_DECLINED", "REFUNDED", "CANCELLED",
                "RETURN_REQUESTED", "RETURN_APPROVED").contains(newStatus)) {
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
                if (item.getCancelledAt() != null) {
                    continue;
                }
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
        // 3. From SHIPPED/COMPLETED/PENDING_COMPLETION to CANCELLED: return actual stock
        else if (List.of("SHIPPED", "COMPLETED", "PENDING_COMPLETION").contains(oldStatus) && "CANCELLED".equals(newStatus)) {
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
            reactivateVoucher(order, tenantCode, userId);
        }

        // Auto-simulate: DELIVERED immediately transitions to PENDING_COMPLETION
        // (7-day escrow hold starts automatically without manual button)
        String effectiveStatus = newStatus;
        if ("DELIVERED".equals(newStatus) && !"DELIVERED".equals(oldStatus)) {
            effectiveStatus = "PENDING_COMPLETION";
        }

        // Award points, update membership, and release escrow on completion.
        if ("COMPLETED".equals(effectiveStatus) && !"COMPLETED".equals(oldStatus)) {
            completeOrder(order, tenantCode, userId);
        }

        if ("CANCELLED".equals(newStatus) && !"CANCELLED".equals(oldStatus)) {
            if (List.of("CONFIRMED", "SHIPPING").contains(oldStatus)) {
                paymentRefundService.refundOrder(tenantCode, order.getId(),
                        "Order cancelled by staff", userId);
            } else {
                escrowService.refundOrderEscrow(tenantCode, order.getId(), userId);
            }
        }

        order.setStatus(effectiveStatus);
        order.setUpdatedBy(userId);

        // Record history
        String historyNote = "DELIVERED".equals(newStatus)
                ? "Delivered; escrow hold auto-started (7 days)"
                : request.note;
        persistHistory(order, oldStatus, effectiveStatus, historyNote, userId, tenantCode);
        notificationService.notifyBuyer(
                order,
                "ORDER_STATUS_UPDATED",
                "Order " + order.getOrderNumber() + " status changed to " + effectiveStatus + ".",
                userId);

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
        if (!List.of("COMPLETED", "CANCELLED", "OWNER_DECLINED", "REFUNDED", "PENDING_COMPLETION").contains(status)) {
            throw new BadRequestException("Only completed or cancelled orders can be deleted");
        }

        order.setDeletedAt(Instant.now());
        order.setUpdatedBy(caller.userId());

        return Response.noContent().build();
    }

    @POST
    @Path("/{orderId}/items/{itemId}/cancel")
    @RolesAllowed({"customer", "user"})
    @Transactional
    public Order cancelOrderItem(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId, CancelItemRequest request) {
        boolean isCustomerOnly = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (!isCustomerOnly) {
            throw new ForbiddenException("Only the buyer can cancel order items");
        }

        String tenantCode = caller.tenantCode();
        UUID userId = caller.userId();

        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", orderId, tenantCode)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), tenantCode).firstResult();
        UUID customerId = c != null ? c.getId() : null;
        boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
        if (!isOwner) {
            throw new ForbiddenException("You are not allowed to cancel items in this order");
        }

        String orderStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("PENDING_PAYMENT", "PAID_ESCROW", "CONFIRMED").contains(orderStatus)) {
            throw new BadRequestException("Cannot cancel items when order is in " + orderStatus + " status");
        }

        OrderItem item = OrderItem.find("id = ?1 and order.id = ?2 and tenantId = ?3", itemId, orderId, tenantCode).firstResult();
        if (item == null) {
            throw new NotFoundException("Order item not found");
        }
        if (item.getCancelledAt() != null) {
            throw new BadRequestException("This item has already been cancelled");
        }

        String reason = (request != null && request.reason != null) ? request.reason.trim() : null;
        fulfillmentService.cancelItem(tenantCode, orderId, itemId, reason, userId);

        order = Order.find("id = ?1 and tenantId = ?2", orderId, tenantCode).firstResult();

        // Reactivate voucher if all items cancelled
        if (areAllItemsCancelled(order)) {
            reactivateVoucher(order, tenantCode, userId);
        }

        String productName = item.getProduct() != null ? item.getProduct().getName() : "";
        notificationService.notifyBusinessStaff(
                order,
                "ORDER_ITEM_CANCELLED",
                productName + " in order " + order.getOrderNumber() + " was cancelled by the customer."
                        + (reason != null ? " Reason: " + reason : ""),
                userId);

        notificationService.notifyBuyer(
                order,
                "ITEM_CANCELLED",
                "You cancelled " + productName + " in order " + order.getOrderNumber() + "."
                        + (reason != null ? " Reason: " + reason : ""),
                userId);

        return order;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Owner / Staff Cancel Item
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/{orderId}/items/{itemId}/owner-cancel")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    @Transactional
    public OrderItem ownerCancelItem(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId, CancelItemRequest request) {
        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", orderId, tenantCode)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        requireBusinessStaffAccess(order);

        String reason = (request != null && request.reason != null) ? request.reason.trim() : null;
        OrderItem item = fulfillmentService.cancelItem(tenantCode, orderId, itemId, reason, caller.userId());

        Order refreshed = Order.find("id = ?1 and tenantId = ?2", orderId, tenantCode).firstResult();
        String productName = item.getProduct() != null ? item.getProduct().getName() : "";
        notificationService.notifyBuyer(
                refreshed,
                "ITEM_CANCELLED_BY_OWNER",
                "The vendor cancelled " + productName + " in order " + refreshed.getOrderNumber() + "."
                        + (reason != null ? " Reason: " + reason : ""),
                caller.userId());

        return item;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Customer Cancel Entire Order
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/{id}/cancel")
    @RolesAllowed({"customer", "user"})
    @Transactional
    public Response cancelOrder(@PathParam("id") UUID id, CancelItemRequest request) {
        boolean isCustomerOnly = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (!isCustomerOnly) {
            throw new ForbiddenException("Only the buyer can cancel the order");
        }

        String tenantCode = caller.tenantCode();
        UUID userId = caller.userId();

        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", id, tenantCode)
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), tenantCode).firstResult();
        UUID customerId = c != null ? c.getId() : null;
        boolean isOwner = (customerId != null && order.getCustomer() != null && customerId.equals(order.getCustomer().getId()))
                || (caller.userId() != null && caller.userId().equals(order.getCreatedBy()));
        if (!isOwner) {
            throw new ForbiddenException("You are not allowed to cancel this order");
        }

        String orderStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("PENDING_PAYMENT", "PAID_ESCROW", "CONFIRMED").contains(orderStatus)) {
            throw new BadRequestException("Cannot cancel order when it is in " + orderStatus + " status");
        }

        String reason = (request != null && request.reason != null) ? request.reason.trim() : null;

        // Cancel all remaining items via cancelItem (handles proportional refund correctly)
        List<OrderItem> activeItems = order.getItems().stream()
                .filter(oi -> oi.getCancelledAt() == null).toList();
        for (OrderItem item : activeItems) {
            fulfillmentService.cancelItem(tenantCode, id, item.getId(), reason, userId);
        }

        order = Order.find("id = ?1 and tenantId = ?2", id, tenantCode).firstResult();

        boolean isPaid = List.of("PAID_ESCROW", "CONFIRMED").contains(orderStatus);
        reactivateVoucher(order, tenantCode, userId);

        notificationService.notifyBusinessStaff(
                order, "ORDER_CANCELLED",
                "Order " + order.getOrderNumber() + " was cancelled by the customer."
                        + (reason != null ? " Reason: " + reason : ""), userId);

        notificationService.notifyBuyer(
                order, "ORDER_CANCELLED",
                "You cancelled order " + order.getOrderNumber() + "."
                        + (isPaid ? " A full refund has been processed." : "")
                        + (reason != null ? " Reason: " + reason : ""), userId);

        return Response.ok(order).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Return / Refund per item
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/{orderId}/items/{itemId}/request-return")
    @RolesAllowed({"customer", "user"})
    @Transactional
    public OrderItem requestItemReturn(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId, CancelItemRequest request) {
        boolean isCustomerOnly = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (!isCustomerOnly) {
            throw new ForbiddenException("Only the buyer can request return");
        }
        String reason = (request != null && request.reason != null) ? request.reason.trim() : null;
        return returnRefundService.requestItemReturn(
                caller.tenantCode(), orderId, itemId, reason, caller.userId(), caller.userEmail());
    }

    @POST
    @Path("/{orderId}/items/{itemId}/approve-return")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public OrderItem approveItemReturn(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId) {
        return returnRefundService.approveItemReturn(
                caller.tenantCode(), orderId, itemId, caller.userId());
    }

    @POST
    @Path("/{orderId}/items/{itemId}/reject-return")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public OrderItem rejectItemReturn(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId) {
        return returnRefundService.rejectItemReturn(
                caller.tenantCode(), orderId, itemId, caller.userId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel request / Approve / Reject (PAID_ESCROW flow)
    // ─────────────────────────────────────────────────────────────────────────

    @POST
    @Path("/{orderId}/items/{itemId}/request-cancel")
    @RolesAllowed({"customer", "user"})
    @Transactional
    public OrderItem requestCancelItem(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId, CancelItemRequest request) {
        boolean isCustomerOnly = (securityIdentity.hasRole("customer") || securityIdentity.hasRole("user"))
                && !securityIdentity.hasRole("admin")
                && !securityIdentity.hasRole("businessOwner")
                && !securityIdentity.hasRole("operator");
        if (!isCustomerOnly) {
            throw new ForbiddenException("Only the buyer can request cancellation");
        }
        String reason = (request != null && request.reason != null) ? request.reason.trim() : null;
        return returnRefundService.requestCancelItem(
                caller.tenantCode(), orderId, itemId, reason, caller.userId(), caller.userEmail());
    }

    @POST
    @Path("/{orderId}/items/{itemId}/approve-cancel")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public OrderItem approveCancelItem(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId) {
        return returnRefundService.approveCancelItem(
                caller.tenantCode(), orderId, itemId, caller.userId());
    }

    @POST
    @Path("/{orderId}/items/{itemId}/reject-cancel")
    @RolesAllowed({"admin", "businessOwner"})
    @Transactional
    public OrderItem rejectCancelItem(@PathParam("orderId") UUID orderId, @PathParam("itemId") UUID itemId) {
        return returnRefundService.rejectCancelItem(
                caller.tenantCode(), orderId, itemId, caller.userId());
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
            customer.setPoints(customer.getPoints() + (int) (amount / 10_000));

            long totalSpend = customer.getTotalSpendMinor();
            if (totalSpend >= 75_000_000) {
                customer.setTier("DIAMOND");
            } else if (totalSpend >= 25_000_000) {
                customer.setTier("PLATINUM");
            } else if (totalSpend >= 5_000_000) {
                customer.setTier("GOLD");
            } else {
                customer.setTier("SILVER");
            }
        }
        escrowService.releaseOrderEscrow(tenantCode, order.getId(), userId);
    }

    private void reactivateVoucher(Order order, String tenantCode, UUID userId) {
        if (order.getVoucher() == null) {
            return;
        }
        Voucher voucher = order.getVoucher();
        voucher.setUsedCount(Math.max(0, voucher.getUsedCount() - 1));
        voucher.setUpdatedBy(userId);

        if (order.getCustomer() == null) {
            return;
        }
        CustomerVoucher customerVoucher = CustomerVoucher.find(
                "customerId = ?1 and voucherId = ?2 and tenantId = ?3",
                order.getCustomer().getId(), voucher.getId(), tenantCode).firstResult();
        if (customerVoucher != null) {
            customerVoucher.setUsed(false);
        }
    }

    static List<OrderItem> cancellationTargets(Order order, OrderItem requestedItem) {
        return List.of(requestedItem);
    }

    static boolean areAllItemsCancelled(Order order) {
        return order.getItems() != null
                && !order.getItems().isEmpty()
                && order.getItems().stream().allMatch(item -> item.getCancelledAt() != null);
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
            return List.of("PAID_ESCROW", "CANCELLED").contains(newStatus);
        }
        if ("PAID_ESCROW".equals(oldStatus)) {
            return List.of("OWNER_ACCEPTED", "OWNER_DECLINED", "CANCELLED").contains(newStatus);
        }
        if ("OWNER_ACCEPTED".equals(oldStatus)) {
            return "FULFILLING".equals(newStatus);
        }
        if ("FULFILLING".equals(oldStatus)) {
            return "DELIVERED".equals(newStatus);
        }
        if ("DELIVERED".equals(oldStatus)) {
            return "PENDING_COMPLETION".equals(newStatus);
        }
        if ("PENDING_COMPLETION".equals(oldStatus)) {
            return List.of("COMPLETED", "CANCELLED").contains(newStatus);
        }
        if ("OWNER_DECLINED".equals(oldStatus)) {
            return "REFUNDED".equals(newStatus);
        }
        if ("RETURN_REQUESTED".equals(oldStatus)) {
            return "RETURN_APPROVED".equals(newStatus);
        }
        if ("RETURN_APPROVED".equals(oldStatus)) {
            return "REFUNDED".equals(newStatus);
        }
        return false;
    }
}

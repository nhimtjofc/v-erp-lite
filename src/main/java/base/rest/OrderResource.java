package base.rest;

import base.entity.Customer;
import base.entity.Order;
import base.security.CallerContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class OrderResource {

    @Inject
    CallerContext caller;

    private static final List<String> VALID_STATUSES = List.of("PENDING", "CONFIRMED", "SHIPPED", "COMPLETED", "CANCELLED");
    private static final java.util.Map<String, List<String>> VALID_TRANSITIONS = java.util.Map.of(
        "PENDING", List.of("CONFIRMED", "CANCELLED"),
        "CONFIRMED", List.of("SHIPPED", "CANCELLED"),
        "SHIPPED", List.of("COMPLETED", "CANCELLED"),
        "COMPLETED", List.of(),
        "CANCELLED", List.of()
    );

    @GET
    public List<Order> list(
            @QueryParam("customerId") UUID customerId,
            @QueryParam("status") String status,
            @QueryParam("search") String search) {
        String tenantCode = caller.tenantCode();
        StringBuilder query = new StringBuilder("FROM Order o WHERE o.tenantId = :tenantId");
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantCode);
        if (customerId != null) {
            query.append(" AND o.customer.id = :customerId");
            params.put("customerId", customerId);
        }
        if (status != null && !status.isBlank()) {
            query.append(" AND o.status = :status");
            params.put("status", status);
        }
        if (search != null && !search.isBlank()) {
            String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.append(" AND (LOWER(o.customer.firstName) LIKE LOWER(CONCAT('%',:search,'%')) ESCAPE '\\'");
            query.append(" OR LOWER(o.customer.lastName) LIKE LOWER(CONCAT('%',:search,'%')) ESCAPE '\\')");
            params.put("search", escaped);
        }
        query.append(" ORDER BY o.createdAt DESC");
        return Order.find(query.toString(), params).list();
    }

    @GET
    @Path("/{id}")
    public Order get(@PathParam("id") UUID id) {
        Order order = Order.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        return order;
    }

    @PUT
    @Path("/{id}/status")
    @Transactional
    public Order updateStatus(@PathParam("id") UUID id, StatusUpdateRequest request) {
        Order order = Order.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        if (request.status == null || !VALID_STATUSES.contains(request.status)) {
            throw new BadRequestException("Invalid status. Valid values: " + String.join(", ", VALID_STATUSES));
        }
        List<String> allowed = VALID_TRANSITIONS.getOrDefault(order.getStatus(), List.of());
        if (!allowed.contains(request.status)) {
            throw new BadRequestException("Cannot transition from " + order.getStatus() + " to " + request.status);
        }
        order.setStatus(request.status);
        order.setUpdatedBy(caller.userId());
        return order;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Order update(@PathParam("id") UUID id, OrderUpdateRequest request) {
        Order order = Order.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }
        if (request.notes != null) {
            order.setNotes(request.notes);
        }

        BigDecimal total = order.getTotalAmount();
        BigDecimal discountPct = request.discountPercent;
        BigDecimal vatPct = request.vatPercent;

        if (discountPct != null) {
            if (discountPct.compareTo(BigDecimal.ZERO) < 0 || discountPct.compareTo(new BigDecimal("100")) > 0) {
                throw new BadRequestException("Discount percent must be between 0 and 100");
            }
            order.setDiscountPercent(discountPct);
            order.setDiscountAmount(total.multiply(discountPct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP));
        }
        if (vatPct != null) {
            if (vatPct.compareTo(BigDecimal.ZERO) < 0 || vatPct.compareTo(new BigDecimal("100")) > 0) {
                throw new BadRequestException("VAT percent must be between 0 and 100");
            }
            order.setVatPercent(vatPct);
        }
        if (request.shippingFee != null) {
            order.setShippingFee(request.shippingFee);
        }

        BigDecimal discountAmount = order.getDiscountAmount();
        BigDecimal afterDiscount = total.subtract(discountAmount);
        BigDecimal vatAmount = afterDiscount.multiply(order.getVatPercent()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        order.setVatAmount(vatAmount);

        BigDecimal finalAmount = afterDiscount.add(order.getShippingFee()).add(vatAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) finalAmount = BigDecimal.ZERO;
        order.setFinalAmount(finalAmount);

        order.setUpdatedBy(caller.userId());
        return order;
    }

    public static class StatusUpdateRequest {
        public String status;
    }

    public static class OrderUpdateRequest {
        public String notes;
        public BigDecimal discountPercent;
        public BigDecimal vatPercent;
        public BigDecimal shippingFee;
    }
}

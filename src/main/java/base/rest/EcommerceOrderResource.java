package base.rest;

import base.entity.ExternalEntityMapping;
import base.entity.Order;
import base.entity.SalesChannel;
import base.entity.WebhookEvent;
import base.security.CallerContext;
import base.service.EcommerceOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

@Path("/integrations/ecommerce/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Ecommerce Orders", description = "Endpoints for Ecommerce order synchronization")
public class EcommerceOrderResource {

    private static final Logger log = LoggerFactory.getLogger(EcommerceOrderResource.class);

    @Inject
    EcommerceOrderService orderService;

    @Inject
    CallerContext caller;

    @Inject
    ObjectMapper objectMapper;

    @GET
    @RolesAllowed({"admin", "operator", "businessOwner"})
    @Operation(summary = "List Ecommerce Orders Sync Status", description = "Returns synced and failed Ecommerce orders with their sync details.")
    public List<EcommerceOrderDto> listEcommerceOrders(
            @QueryParam("salesChannelId") UUID salesChannelId,
            @QueryParam("syncStatus") String syncStatus,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset
    ) {
        String tenantId = caller.tenantCode();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("Tenant context not found");
        }

        UUID resolvedChannelId = salesChannelId;
        if (resolvedChannelId == null) {
            List<SalesChannel> channels = SalesChannel.listEcommerce(tenantId);
            if (channels.isEmpty()) {
                return List.of();
            }
            resolvedChannelId = channels.get(0).getId();
        }

        List<WebhookEvent> events = WebhookEvent.find(
                "tenantId = ?1 and salesChannelId = ?2 and externalEventId LIKE 'woo-order-%' order by createdAt desc",
                tenantId, resolvedChannelId
        ).list();

        Map<String, List<WebhookEvent>> eventsByWooId = new LinkedHashMap<>();
        for (WebhookEvent ev : events) {
            String extId = ev.getExternalEventId();
            String wooId = extractWooOrderId(extId);
            if (wooId != null) {
                eventsByWooId.computeIfAbsent(wooId, k -> new ArrayList<>()).add(ev);
            }
        }

        List<ExternalEntityMapping> mappings = ExternalEntityMapping.find(
                "tenantId = ?1 and salesChannelId = ?2 and entityType = 'ORDER'",
                tenantId, resolvedChannelId
        ).list();
        Map<String, ExternalEntityMapping> mappingByWooId = new HashMap<>();
        for (ExternalEntityMapping map : mappings) {
            mappingByWooId.put(map.getExternalEntityId(), map);
        }

        List<UUID> erpOrderIds = new ArrayList<>();
        for (ExternalEntityMapping map : mappings) {
            erpOrderIds.add(map.getErpEntityId());
        }
        Map<UUID, Order> orderById = new HashMap<>();
        if (!erpOrderIds.isEmpty()) {
            List<Order> erpOrders = Order.find("id in ?1 and tenantId = ?2", erpOrderIds, tenantId).list();
            for (Order o : erpOrders) {
                orderById.put(o.getId(), o);
            }
        }

        List<EcommerceOrderDto> result = new ArrayList<>();
        for (Map.Entry<String, List<WebhookEvent>> entry : eventsByWooId.entrySet()) {
            String wooId = entry.getKey();
            List<WebhookEvent> wooEvents = entry.getValue();

            ExternalEntityMapping mapping = mappingByWooId.get(wooId);
            Order erpOrder = mapping != null ? orderById.get(mapping.getErpEntityId()) : null;

            WebhookEvent problematicEvent = null;
            for (int i = wooEvents.size() - 1; i >= 0; i--) {
                WebhookEvent ev = wooEvents.get(i);
                if ("FAILED".equals(ev.getStatus()) || "PROCESSING".equals(ev.getStatus())) {
                    problematicEvent = ev;
                    break;
                }
            }

            WebhookEvent latestEvent = wooEvents.get(0);
            String statusStr = "SYNCED";
            String errorMsg = null;
            Instant lastAttempt = latestEvent.getProcessedAt() != null ? latestEvent.getProcessedAt() : latestEvent.getCreatedAt();

            if (problematicEvent != null) {
                statusStr = problematicEvent.getStatus();
                errorMsg = problematicEvent.getErrorMessage();
                lastAttempt = problematicEvent.getProcessedAt() != null ? problematicEvent.getProcessedAt() : problematicEvent.getCreatedAt();
            } else if ("FAILED".equals(latestEvent.getStatus())) {
                statusStr = "FAILED";
                errorMsg = latestEvent.getErrorMessage();
            } else if ("PROCESSING".equals(latestEvent.getStatus())) {
                statusStr = "PROCESSING";
            }

            String customerName = "Unknown";
            Instant orderDate = latestEvent.getCreatedAt();
            long amount = 0;

            if (erpOrder != null) {
                customerName = erpOrder.getCustomer().getFirstName() + " " + erpOrder.getCustomer().getLastName();
                orderDate = erpOrder.getOrderDate();
                amount = erpOrder.getFinalAmountMinor();
            } else {
                try {
                    JsonNode root = objectMapper.readTree(latestEvent.getRawPayload());
                    JsonNode orderNode = root.has("order") ? root.get("order") : root;
                    JsonNode billing = orderNode.path("billing");
                    String fName = billing.path("first_name").asText("").trim();
                    String lName = billing.path("last_name").asText("").trim();
                    customerName = (fName + " " + lName).trim();
                    if (customerName.isEmpty()) customerName = "Unknown Woo Customer";

                    String dateStr = orderNode.path("date_created").asText();
                    if (!dateStr.isBlank()) {
                        orderDate = Instant.parse(dateStr.endsWith("Z") ? dateStr : dateStr + "Z");
                    }
                    double total = orderNode.path("total").asDouble();
                    amount = Math.round(total * 100);
                } catch (Exception e) {
                }
            }

            boolean matchesSearch = true;
            if (search != null && !search.isBlank()) {
                String s = search.toLowerCase();
                String orderNo = erpOrder != null ? erpOrder.getOrderNumber().toLowerCase() : "";
                matchesSearch = customerName.toLowerCase().contains(s) || orderNo.contains(s) || wooId.contains(s);
            }

            boolean matchesStatus = true;
            if (syncStatus != null && !syncStatus.isBlank()) {
                matchesStatus = syncStatus.equalsIgnoreCase(statusStr);
            }

            if (matchesSearch && matchesStatus) {
                result.add(new EcommerceOrderDto(
                        wooId,
                        erpOrder != null ? erpOrder.getId() : null,
                        erpOrder != null ? erpOrder.getOrderNumber() : null,
                        customerName,
                        orderDate,
                        amount,
                        statusStr,
                        errorMsg,
                        lastAttempt
                ));
            }
        }

        int fromIndex = Math.min(offset, result.size());
        int toIndex = Math.min(offset + limit, result.size());
        return result.subList(fromIndex, toIndex);
    }

    private String extractWooOrderId(String externalEventId) {
        if (externalEventId == null) return null;
        if (externalEventId.startsWith("woo-order-create-")) {
            return externalEventId.substring("woo-order-create-".length());
        }
        if (externalEventId.startsWith("woo-order-status-")) {
            String sub = externalEventId.substring("woo-order-status-".length());
            int idx = sub.indexOf("-");
            if (idx != -1) {
                return sub.substring(0, idx);
            }
            return sub;
        }
        return null;
    }

    @POST
    @PermitAll
    @Operation(summary = "Receive Ecommerce Order Webhook", description = "Endpoint called by Ecommerce to sync new/updated orders to V-ERP.")
    public Response createOrder(
            @QueryParam("tenantId") String tenantId,
            @QueryParam("salesChannelId") UUID salesChannelId,
            @HeaderParam("X-WC-Webhook-Signature") String signatureHeader,
            @HeaderParam("X-WC-Webhook-Delivery-ID") String deliveryId,
            @HeaderParam("X-WC-Webhook-Topic") String topicHeader,
            String rawBody
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("Query param tenantId is required");
        }
        if (salesChannelId == null) {
            throw new BadRequestException("Query param salesChannelId is required");
        }

        orderService.verifyWebhookSignature(rawBody, signatureHeader, tenantId, salesChannelId);

        JsonNode rootNode;
        try {
            rootNode = new ObjectMapper().readTree(rawBody);
        } catch (Exception e) {
            throw new BadRequestException("Invalid JSON payload: " + e.getMessage());
        }

        JsonNode orderNode = rootNode.has("order") ? rootNode.get("order") : rootNode;
        String wooOrderId = orderNode.path("id").asText();
        if (wooOrderId == null || wooOrderId.isBlank() || "0".equals(wooOrderId)) {
            throw new BadRequestException("Ecommerce Order ID is missing in payload");
        }

        boolean isUpdate = topicHeader != null && topicHeader.toLowerCase().contains("updated");
        String externalEventId;
        if (isUpdate) {
            String status = orderNode.path("status").asText("unknown").trim().toLowerCase();
            externalEventId = "woo-order-status-" + wooOrderId + "-" + status;
        } else {
            externalEventId = "woo-order-create-" + wooOrderId;
        }

        WebhookEvent event = orderService.getOrCreateWebhookEvent(
                tenantId, salesChannelId, externalEventId, rawBody, deliveryId
        );

        if ("PROCESSED".equals(event.getStatus())) {
            String existingOrderIdStr = event.getChannelMetadata() != null 
                    ? (String) event.getChannelMetadata().get("order_id") 
                    : null;
            if (existingOrderIdStr != null) {
                Order order = Order.find("id = ?1 and tenantId = ?2", UUID.fromString(existingOrderIdStr), tenantId).firstResult();
                if (order != null) {
                    return Response.ok(order).build();
                }
            }
            return Response.ok(Map.of("message", "Order already processed (idempotent)")).build();
        }

        try {
            Order order;
            if (isUpdate) {
                order = orderService.updateOrderWebhook(tenantId, salesChannelId, externalEventId, orderNode, event);
            } else {
                order = orderService.processOrderWebhook(tenantId, salesChannelId, externalEventId, orderNode, event);
            }
            orderService.markWebhookEventSuccess(event.getId(), order.getId());
            return Response.ok(order).build();
        } catch (Exception e) {
            orderService.markWebhookEventFailed(event.getId(), e.getMessage());
            log.error("Failed to process Ecommerce order webhook: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to process order webhook: " + e.getMessage());
        }
    }

    @GET
    @Path("/{externalId}/status")
    @PermitAll
    @Operation(summary = "Get Ecommerce Order Status", description = "Returns the current ERP fulfillment status for a Ecommerce order, keyed by the Ecommerce order ID.")
    public Response getOrderStatus(
            @PathParam("externalId") String externalId,
            @QueryParam("tenantId") String tenantId,
            @QueryParam("salesChannelId") UUID salesChannelId,
            @HeaderParam("X-WC-Webhook-Signature") String signatureHeader
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("Query param tenantId is required");
        }
        if (salesChannelId == null) {
            throw new BadRequestException("Query param salesChannelId is required");
        }

        orderService.verifyWebhookSignature("", signatureHeader, tenantId, salesChannelId);

        Map<String, Object> status = orderService.getOrderStatus(tenantId, salesChannelId, externalId);
        return Response.ok(status).build();
    }

    @POST
    @Path("/{externalId}/reconcile")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    @Operation(summary = "Reconcile / Retry Order Sync", description = "Triggers a retry of a failed or stuck Ecommerce order webhook.")
    public Response reconcileOrder(
            @PathParam("externalId") String externalId,
            @QueryParam("salesChannelId") UUID salesChannelId,
            String rawBody
    ) {
        String tenantId = caller.tenantCode();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("Tenant context not found");
        }

        UUID resolvedChannelId = salesChannelId;
        if (resolvedChannelId == null) {
            List<SalesChannel> channels = SalesChannel.listEcommerce(tenantId);
            if (channels.isEmpty()) {
                throw new NotFoundException("No Ecommerce sales channel found for tenant");
            }
            resolvedChannelId = channels.get(0).getId();
        }

        List<WebhookEvent> events = WebhookEvent.find(
                "tenantId = ?1 and salesChannelId = ?2 and (externalEventId = ?3 or externalEventId LIKE ?4) order by createdAt asc",
                tenantId, resolvedChannelId, "woo-order-create-" + externalId, "woo-order-status-" + externalId + "-%"
        ).list();

        WebhookEvent eventToRetry = null;
        if (!events.isEmpty()) {
            for (WebhookEvent ev : events) {
                if ("FAILED".equals(ev.getStatus()) || "PROCESSING".equals(ev.getStatus())) {
                    eventToRetry = ev;
                    break;
                }
            }
            if (eventToRetry == null) {
                eventToRetry = events.get(events.size() - 1);
            }
        }

        String externalEventId = eventToRetry != null ? eventToRetry.getExternalEventId() : "woo-order-create-" + externalId;
        boolean isUpdate = externalEventId.startsWith("woo-order-status-");

        JsonNode orderNode = null;
        String payloadToUse = rawBody;

        if (rawBody != null && !rawBody.trim().isEmpty() && !rawBody.trim().equals("{}")) {
            try {
                JsonNode rootNode = new ObjectMapper().readTree(rawBody);
                orderNode = rootNode.has("order") ? rootNode.get("order") : rootNode;
            } catch (Exception e) {
                throw new BadRequestException("Invalid JSON payload in request body: " + e.getMessage());
            }
        } else if (eventToRetry != null && eventToRetry.getRawPayload() != null) {
            payloadToUse = eventToRetry.getRawPayload();
            try {
                JsonNode rootNode = new ObjectMapper().readTree(payloadToUse);
                orderNode = rootNode.has("order") ? rootNode.get("order") : rootNode;
            } catch (Exception e) {
                throw new BadRequestException("Invalid JSON payload saved in webhook event");
            }
        }

        if (orderNode == null) {
            throw new NotFoundException("No order payload found for reconcile. Please provide it in the body.");
        }

        if (eventToRetry == null) {
            eventToRetry = orderService.getOrCreateWebhookEvent(tenantId, resolvedChannelId, externalEventId, payloadToUse, null);
        }

        final WebhookEvent finalEvent = eventToRetry;
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            WebhookEvent ev = WebhookEvent.findById(finalEvent.getId());
            if (ev != null) {
                ev.setStatus("PROCESSING");
                ev.setErrorMessage(null);
            }
        });

        try {
            Order order;
            if (isUpdate) {
                order = orderService.updateOrderWebhook(tenantId, resolvedChannelId, externalEventId, orderNode, eventToRetry);
            } else {
                order = orderService.processOrderWebhook(tenantId, resolvedChannelId, externalEventId, orderNode, eventToRetry);
            }
            orderService.markWebhookEventSuccess(eventToRetry.getId(), order.getId());
            return Response.ok(order).build();
        } catch (Exception e) {
            orderService.markWebhookEventFailed(eventToRetry.getId(), e.getMessage());
            log.error("Reconciliation failed for Ecommerce order {} (event {}): {}", externalId, externalEventId, e.getMessage(), e);
            throw new BadRequestException("Reconciliation failed: " + e.getMessage());
        }
    }
}

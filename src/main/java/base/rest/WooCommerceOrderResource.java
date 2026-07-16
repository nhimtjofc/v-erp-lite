package base.rest;

import base.entity.Order;
import base.entity.SalesChannel;
import base.entity.WebhookEvent;
import base.security.CallerContext;
import base.service.WooCommerceOrderService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/integrations/woocommerce/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "WooCommerce Orders", description = "Endpoints for WooCommerce order synchronization")
public class WooCommerceOrderResource {

    private static final Logger log = LoggerFactory.getLogger(WooCommerceOrderResource.class);

    @Inject
    WooCommerceOrderService orderService;

    @Inject
    CallerContext caller;

    @POST
    @PermitAll
    @Operation(summary = "Receive WooCommerce Order Webhook", description = "Endpoint called by WooCommerce to sync new/updated orders to V-ERP.")
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

        // Verify HMAC signature
        orderService.verifyWebhookSignature(rawBody, signatureHeader, tenantId, salesChannelId);

        // Parse JSON Payload
        JsonNode rootNode;
        try {
            rootNode = new ObjectMapper().readTree(rawBody);
        } catch (Exception e) {
            throw new BadRequestException("Invalid JSON payload: " + e.getMessage());
        }

        JsonNode orderNode = rootNode.has("order") ? rootNode.get("order") : rootNode;
        String wooOrderId = orderNode.path("id").asText();
        if (wooOrderId == null || wooOrderId.isBlank() || "0".equals(wooOrderId)) {
            throw new BadRequestException("WooCommerce Order ID is missing in payload");
        }

        boolean isUpdate = topicHeader != null && topicHeader.toLowerCase().contains("updated");
        String externalEventId;
        if (isUpdate) {
            String status = orderNode.path("status").asText("unknown").trim().toLowerCase();
            externalEventId = "woo-order-status-" + wooOrderId + "-" + status;
        } else {
            externalEventId = "woo-order-create-" + wooOrderId;
        }

        // Retrieve or create WebhookEvent
        WebhookEvent event = orderService.getOrCreateWebhookEvent(
                tenantId, salesChannelId, externalEventId, rawBody, deliveryId
        );

        // If it was already processed successfully, return success with the order
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
            log.error("Failed to process WooCommerce order webhook: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to process order webhook: " + e.getMessage());
        }
    }

    @GET
    @Path("/{externalId}/status")
    @PermitAll
    @Operation(summary = "Get WooCommerce Order Status", description = "Returns the current ERP fulfillment status for a WooCommerce order, keyed by the WooCommerce order ID.")
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
    @Operation(summary = "Reconcile / Retry Order Sync", description = "Triggers a retry of a failed or stuck WooCommerce order webhook.")
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
            List<SalesChannel> channels = SalesChannel.listWooCommerce(tenantId);
            if (channels.isEmpty()) {
                throw new NotFoundException("No WooCommerce sales channel found for tenant");
            }
            resolvedChannelId = channels.get(0).getId();
        }

        String externalEventId = "woo-order-create-" + externalId;

        // Try to load WebhookEvent from DB
        WebhookEvent event = WebhookEvent.find(
                "tenantId = ?1 and salesChannelId = ?2 and externalEventId = ?3",
                tenantId, resolvedChannelId, externalEventId
        ).firstResult();

        JsonNode orderNode = null;
        String payloadToUse = rawBody;

        if (rawBody != null && !rawBody.trim().isEmpty() && !rawBody.trim().equals("{}")) {
            // Use payload from request body if provided
            try {
                JsonNode rootNode = new ObjectMapper().readTree(rawBody);
                orderNode = rootNode.has("order") ? rootNode.get("order") : rootNode;
            } catch (Exception e) {
                throw new BadRequestException("Invalid JSON payload in request body: " + e.getMessage());
            }
        } else if (event != null && event.getRawPayload() != null) {
            // Fall back to saved payload in event
            payloadToUse = event.getRawPayload();
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

        // If no WebhookEvent exists yet, create it
        if (event == null) {
            event = orderService.getOrCreateWebhookEvent(tenantId, resolvedChannelId, externalEventId, payloadToUse, null);
        }

        // Force status back to PROCESSING for retry
        final WebhookEvent finalEvent = event;
        org.jboss.threads.ContextHandler<Void> ch; // Avoid compiler warnings
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            WebhookEvent ev = WebhookEvent.findById(finalEvent.getId());
            if (ev != null) {
                ev.setStatus("PROCESSING");
                ev.setErrorMessage(null);
            }
        });

        try {
            Order order = orderService.processOrderWebhook(tenantId, resolvedChannelId, externalEventId, orderNode, event);
            orderService.markWebhookEventSuccess(event.getId(), order.getId());
            return Response.ok(order).build();
        } catch (Exception e) {
            orderService.markWebhookEventFailed(event.getId(), e.getMessage());
            log.error("Reconciliation failed for WooCommerce order {}: {}", externalId, e.getMessage(), e);
            throw new BadRequestException("Reconciliation failed: " + e.getMessage());
        }
    }
}

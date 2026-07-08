package base.rest;

import base.entity.PaymentIntent;
import base.entity.WebhookEvent;
import base.service.IdempotencyService;
import base.service.PaymentGatewayService;
import base.service.PaymentIntentService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Path("/payments/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentWebhookResource {

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    IdempotencyService idempotencyService;

    public static class WebhookPayload {
        public String paymentIntentId;
        public String gatewayTransactionId;
        public String status;
        public Long amountMinor;
        public String currency;
        public String signature;
    }

    @POST
    @Path("/{gateway}")
    @Transactional
    public Response handleWebhook(@PathParam("gateway") String gateway, WebhookPayload payload) {
        String gatewayName = gateway != null ? gateway.trim().toUpperCase() : "UNKNOWN";
        WebhookEvent event = createEvent(gatewayName, payload);

        if (payload == null) {
            return failEvent(event, "Payload is required");
        }
        if (payload.paymentIntentId == null || payload.paymentIntentId.isBlank()) {
            return failEvent(event, "paymentIntentId is required");
        }
        if (payload.gatewayTransactionId == null || payload.gatewayTransactionId.isBlank()) {
            return failEvent(event, "gatewayTransactionId is required");
        }

        UUID intentId;
        try {
            intentId = UUID.fromString(payload.paymentIntentId.trim());
        } catch (IllegalArgumentException e) {
            return failEvent(event, "Invalid paymentIntentId format");
        }

        PaymentIntent intent = PaymentIntent.find("id = ?1", intentId).firstResult();
        if (intent == null) {
            return failEvent(event, "PaymentIntent not found");
        }

        String tenantId = intent.getTenantId();
        event.setTenantId(tenantId);
        event.setPaymentIntentId(intentId);

        String txnId = payload.gatewayTransactionId.trim();
        long amountMinor = payload.amountMinor != null ? payload.amountMinor : 0;

        String rawPayload = String.format(
                "{\"payment_intent_id\":\"%s\",\"gateway_transaction_id\":\"%s\",\"status\":\"%s\",\"amount_minor\":%d,\"currency\":\"%s\"}",
                intentId, txnId, payload.status,
                amountMinor,
                payload.currency != null ? payload.currency : "USD"
        );
        event.setRawPayload(rawPayload);

        if (!paymentGatewayService.verifySignature(intentId.toString(), txnId, amountMinor, payload.signature)) {
            return failEvent(event, "Invalid webhook signature");
        }

        if ("SUCCEEDED".equals(intent.getStatus())) {
            return succeedEvent(event, "Payment already succeeded");
        }
        if ("CANCELLED".equals(intent.getStatus())) {
            return failEvent(event, "PaymentIntent was cancelled before webhook arrived");
        }
        if ("FAILED".equals(intent.getStatus())) {
            return failEvent(event, "PaymentIntent was already marked failed");
        }

        String idempotencyKey = gatewayName + ":" + txnId;

        var existing = idempotencyService.getResult(tenantId, idempotencyKey);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return succeedEvent(event, "Webhook already processed (idempotent)");
        }

        try {
            idempotencyService.checkAndCreate(tenantId, idempotencyKey);
        } catch (Exception e) {
            return Response.status(409)
                    .entity(Map.of("error", "Webhook is being processed", "message", e.getMessage()))
                    .build();
        }

        try {
            if ("SUCCESS".equalsIgnoreCase(payload.status)) {
                paymentIntentService.markSucceeded(tenantId, intent.getId(), gatewayName, txnId, null);
                idempotencyService.complete(tenantId, idempotencyKey, "SUCCESS", rawPayload);
                return succeedEvent(event, "Payment processed successfully");
            } else {
                paymentIntentService.markFailed(tenantId, intent.getId(), gatewayName, txnId, null);
                idempotencyService.complete(tenantId, idempotencyKey, "FAILED", rawPayload);
                return succeedEvent(event, "Payment failure recorded");
            }
        } catch (Exception e) {
            idempotencyService.complete(tenantId, idempotencyKey, "FAILED", rawPayload);
            return failEvent(event, "Failed to process webhook: " + e.getMessage());
        }
    }

    private WebhookEvent createEvent(String gateway, WebhookPayload payload) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID());
        event.setGateway(gateway);
        event.setEventType(payload != null ? payload.status : "UNKNOWN");
        event.setGatewayTransactionId(payload != null ? payload.gatewayTransactionId : null);
        event.setStatus("RECEIVED");
        event.persist();
        return event;
    }

    private Response failEvent(WebhookEvent event, String message) {
        event.setStatus("FAILED");
        event.setErrorMessage(message);
        event.setProcessedAt(Instant.now());
        event.persist();
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", message))
                .build();
    }

    private Response succeedEvent(WebhookEvent event, String message) {
        event.setStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        event.persist();
        return Response.ok(Map.of("status", "PROCESSED", "message", message))
                .build();
    }
}

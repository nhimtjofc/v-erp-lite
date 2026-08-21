package base.rest;

import base.entity.PaymentIntent;
import base.entity.WalletTopUp;
import base.entity.WebhookEvent;
import base.entity.InboundEventLog;
import base.security.HmacSecure;
import base.service.PaymentGatewayService;
import base.service.PaymentIntentService;
import base.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
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
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Path("/payments/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@HmacSecure
public class PaymentWebhookResource {

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    WalletService walletService;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    ObjectMapper objectMapper;

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
    public Response handleWebhook(@PathParam("gateway") String gateway, String body) {
        String gatewayName = gateway != null ? gateway.trim().toUpperCase() : "UNKNOWN";

        // 1. Extract eventId (gatewayTransactionId) before parsing full JSON (Deduplication)
        String eventId = extractField(body, "gatewayTransactionId");
        if (eventId == null || eventId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "gatewayTransactionId is required"))
                    .build();
        }

        // 2. Persist InboundEventLog in a new transaction to catch replay attacks immediately
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                InboundEventLog log = new InboundEventLog();
                log.setId(UUID.randomUUID());
                log.setGateway(gatewayName);
                log.setEventId(eventId);
                log.setStatus("RECEIVED");
                log.setProcessedAt(Instant.now());
                log.persist();
            });
        } catch (Exception e) {
            // Already processed (Unique constraint violation)
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Event already processed (replay detected)"))
                    .build();
        }

        // 3. Parse JSON
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(body, WebhookPayload.class);
        } catch (Exception e) {
            updateLogStatus(gatewayName, eventId, "FAILED");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid JSON payload"))
                    .build();
        }

        if (payload.paymentIntentId == null || payload.paymentIntentId.isBlank()) {
            updateLogStatus(gatewayName, eventId, "FAILED");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "paymentIntentId is required"))
                    .build();
        }

        UUID intentId;
        try {
            intentId = UUID.fromString(payload.paymentIntentId.trim());
        } catch (IllegalArgumentException e) {
            updateLogStatus(gatewayName, eventId, "FAILED");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid paymentIntentId format"))
                    .build();
        }

        // Log audit event using WebhookEvent entity
        WebhookEvent event = createEvent(gatewayName, payload);
        event.setPaymentIntentId(intentId);

        PaymentIntent intent = PaymentIntent.find("id = ?1", intentId).firstResult();
        WalletTopUp topUp = null;
        if (intent == null) {
            topUp = WalletTopUp.find("id = ?1", intentId).firstResult();
            if (topUp == null) {
                updateLogStatus(gatewayName, eventId, "FAILED");
                return failEvent(event, "PaymentIntent or WalletTopUp not found");
            }
        }

        String tenantId = intent != null ? intent.getTenantId() : topUp.getTenantId();
        event.setTenantId(tenantId);

        String txnId = payload.gatewayTransactionId.trim();
        long amountMinor = payload.amountMinor != null ? payload.amountMinor : 0;
        String currency = payload.currency == null ? "VND" : payload.currency.trim().toUpperCase();
        if (!"VND".equals(currency)) {
            updateLogStatus(gatewayName, eventId, "FAILED");
            return failEvent(event, "Only VND payments are supported");
        }

        String rawPayload = String.format(
                "{\"payment_intent_id\":\"%s\",\"gateway_transaction_id\":\"%s\",\"status\":\"%s\",\"amount_minor\":%d,\"currency\":\"%s\"}",
                intentId, txnId, payload.status,
                amountMinor,
                currency
        );
        event.setRawPayload(rawPayload);

        // Optional payload-level signature check if needed, but HMAC filter already verified the body.
        // We verify it here too for maximum compatibility with existing tests.
        if (!paymentGatewayService.verifySignature(intentId.toString(), txnId, amountMinor, payload.signature)) {
            updateLogStatus(gatewayName, eventId, "FAILED");
            return failEvent(event, "Invalid webhook signature");
        }

        if (intent != null) {
            if ("SUCCEEDED".equals(intent.getStatus())) {
                updateLogStatus(gatewayName, eventId, "PROCESSED");
                return succeedEvent(event, "Payment already succeeded");
            }
            if ("CANCELLED".equals(intent.getStatus())) {
                updateLogStatus(gatewayName, eventId, "FAILED");
                return failEvent(event, "PaymentIntent was cancelled before webhook arrived");
            }
            if ("FAILED".equals(intent.getStatus())) {
                updateLogStatus(gatewayName, eventId, "FAILED");
                return failEvent(event, "PaymentIntent was already marked failed");
            }

            try {
                // 4. Call PaymentIntentService to process webhook event (from Dev A logic)
                paymentIntentService.processWebhookEvent(tenantId, intent.getId(), gatewayName, txnId, payload.status);
                updateLogStatus(gatewayName, eventId, "PROCESSED");
                return succeedEvent(event, "Payment processed successfully");
            } catch (Exception e) {
                updateLogStatus(gatewayName, eventId, "FAILED");
                return failEvent(event, "Failed to process webhook: " + e.getMessage());
            }
        } else {
            if ("COMPLETED".equals(topUp.getStatus())) {
                updateLogStatus(gatewayName, eventId, "PROCESSED");
                return succeedEvent(event, "Top-up already completed");
            }
            try {
                if ("SUCCESS".equalsIgnoreCase(payload.status)) {
                    walletService.completeTopUp(tenantId, topUp.getId(), null);
                    updateLogStatus(gatewayName, eventId, "PROCESSED");
                    return succeedEvent(event, "Top-up processed successfully");
                } else {
                    updateLogStatus(gatewayName, eventId, "FAILED");
                    return failEvent(event, "Top-up failed at provider");
                }
            } catch (Exception e) {
                updateLogStatus(gatewayName, eventId, "FAILED");
                return failEvent(event, "Failed to process top-up webhook: " + e.getMessage());
            }
        }
    }

    private String extractField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void updateLogStatus(String gateway, String eventId, String status) {
        QuarkusTransaction.requiringNew().run(() -> {
            InboundEventLog log = InboundEventLog.find("gateway = ?1 and eventId = ?2", gateway, eventId).firstResult();
            if (log != null) {
                log.setStatus(status);
            }
        });
    }

    private WebhookEvent createEvent(String gateway, WebhookPayload payload) {
        return QuarkusTransaction.requiringNew().call(() -> {
            WebhookEvent event = new WebhookEvent();
            event.setId(UUID.randomUUID());
            event.setGateway(gateway);
            event.setEventType(payload != null ? payload.status : "UNKNOWN");
            event.setGatewayTransactionId(payload != null ? payload.gatewayTransactionId : null);
            event.setStatus("RECEIVED");
            event.persist();
            return event;
        });
    }

    private Response failEvent(WebhookEvent event, String message) {
        event.setStatus("FAILED");
        event.setErrorMessage(message);
        event.setProcessedAt(Instant.now());
        QuarkusTransaction.requiringNew().run(() -> {
            WebhookEvent.getEntityManager().merge(event);
        });
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", message))
                .build();
    }

    private Response succeedEvent(WebhookEvent event, String message) {
        event.setStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        QuarkusTransaction.requiringNew().run(() -> {
            WebhookEvent.getEntityManager().merge(event);
        });
        return Response.ok(Map.of("status", "PROCESSED", "message", message))
                .build();
    }
}

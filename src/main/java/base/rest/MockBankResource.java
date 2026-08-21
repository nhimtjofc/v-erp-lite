package base.rest;

import base.entity.PaymentIntent;
import base.security.CallerContext;
import base.security.PaymentAccess;
import base.service.PaymentGatewayService;
import base.service.PaymentIntentService;
import base.service.PaymentGatewayService.MockGatewayResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/payments/mockbank")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MockBankResource {

    @Inject
    CallerContext caller;

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentAccess paymentAccess;

    public static class ChargeRequest {
        public String paymentIntentId;
        public Long amountMinor;
        public String currency;
    }

    public static class SimulateTransferredRequest {
        public String paymentIntentId;
        public String gatewayTransactionId;
        public String status;
        public Long amountMinor;
        public String currency;
    }

    @POST
    @Path("/initiate-charge")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    @Transactional
    public Response initiateCharge(ChargeRequest request) {
        if (request == null || request.paymentIntentId == null || request.paymentIntentId.isBlank()) {
            throw new BadRequestException("paymentIntentId is required");
        }
        String tenantCode = caller.tenantCode();
        UUID intentId;
        try {
            intentId = UUID.fromString(request.paymentIntentId.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid paymentIntentId format");
        }
        PaymentIntent intent = PaymentIntent.find("id = ?1 and tenantId = ?2", intentId, tenantCode).firstResult();
        if (intent == null) {
            throw new NotFoundException("PaymentIntent not found");
        }
        paymentAccess.requireIntentAccess(intent);

        MockGatewayResponse gatewayResponse = paymentGatewayService.initiatePayment(intent, "MOCK");

        return Response.ok(Map.of(
            "transactionId", gatewayResponse.gatewayTransactionId(),
            "metadata", Map.of(
                "paymentUrl", gatewayResponse.paymentUrl(),
                "qrCodeContent", gatewayResponse.qrCodeContent()
            ),
            "expiresAt", gatewayResponse.expiresAt() != null ? gatewayResponse.expiresAt().toString() : null
        )).build();
    }

    @POST
    @Path("/simulate-transferred")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    @Transactional
    public Response simulateTransferred(SimulateTransferredRequest request) {
        if (request == null || request.paymentIntentId == null || request.paymentIntentId.isBlank()) {
            throw new BadRequestException("paymentIntentId is required");
        }
        String tenantCode = caller.tenantCode();
        UUID intentId;
        try {
            intentId = UUID.fromString(request.paymentIntentId.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid paymentIntentId format");
        }
        PaymentIntent intent = PaymentIntent.find("id = ?1 and tenantId = ?2", intentId, tenantCode).firstResult();
        if (intent == null) {
            throw new NotFoundException("PaymentIntent not found");
        }
        paymentAccess.requireIntentAccess(intent);

        String txnId = request.gatewayTransactionId != null ? request.gatewayTransactionId.trim()
                : paymentGatewayService.generateTransactionId("MOCK");
        String status = request.status != null ? request.status.trim().toUpperCase() : "SUCCESS";

        paymentIntentService.processWebhookEvent(
                tenantCode,
                intentId,
                "MOCK",
                txnId,
                status
        );

        return Response.ok(Map.of("status", "PROCESSED", "gatewayTransactionId", txnId)).build();
    }
}

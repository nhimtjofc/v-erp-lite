package base.rest;

import base.entity.PaymentIntent;
import base.security.CallerContext;
import base.service.PaymentGatewayService;
import base.service.PaymentIntentService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/payments/mock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "businessOwner", "operator", "customer"})
public class MockPaymentResource {

    @Inject
    CallerContext caller;

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    PaymentIntentService paymentIntentService;

    public static class InitiateMockRequest {
        public UUID paymentIntentId;
    }

    @POST
    @Path("/initiate")
    public Response initiateMockPayment(InitiateMockRequest request) {
        if (request == null || request.paymentIntentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "paymentIntentId is required"))
                    .build();
        }

        String tenantCode = caller.tenantCode();
        PaymentIntent intent = paymentIntentService.getIntent(tenantCode, request.paymentIntentId);

        if (!"REQUIRES_PAYMENT_METHOD".equals(intent.getStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "PaymentIntent is not in a payable state", "status", intent.getStatus()))
                    .build();
        }

        PaymentGatewayService.MockGatewayResponse gatewayResponse = paymentGatewayService.initiatePayment(intent, "MOCK");

        return Response.ok(Map.of(
                "gateway", gatewayResponse.gateway(),
                "gatewayTransactionId", gatewayResponse.gatewayTransactionId(),
                "paymentUrl", gatewayResponse.paymentUrl(),
                "qrCodeContent", gatewayResponse.qrCodeContent(),
                "mockSecret", gatewayResponse.mockSecret(),
                "expiresAt", gatewayResponse.expiresAt().toString(),
                "metadata", gatewayResponse.metadata()
        )).build();
    }

    @POST
    @Path("/{intentId}/complete")
    @Transactional
    public Response completeMockPayment(@PathParam("intentId") UUID intentId) {
        if (intentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "intentId is required"))
                    .build();
        }

        String tenantCode = caller.tenantCode();
        PaymentIntent intent = paymentIntentService.getIntent(tenantCode, intentId);

        if ("SUCCEEDED".equals(intent.getStatus())) {
            return Response.ok(Map.of("status", "ALREADY_PROCESSED")).build();
        }

        PaymentGatewayService.MockGatewayResponse gatewayResponse = paymentGatewayService.initiatePayment(intent, "MOCK");

        paymentIntentService.markSucceeded(
                tenantCode, intent.getId(), "MOCK",
                gatewayResponse.gatewayTransactionId(), caller.userId()
        );

        return Response.ok(Map.of(
                "status", "SUCCESS",
                "gatewayTransactionId", gatewayResponse.gatewayTransactionId(),
                "message", "Mock payment completed successfully"
        )).build();
    }

    @POST
    @Path("/{intentId}/fail")
    @Transactional
    public Response failMockPayment(@PathParam("intentId") UUID intentId) {
        if (intentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "intentId is required"))
                    .build();
        }

        String tenantCode = caller.tenantCode();
        PaymentIntent intent = paymentIntentService.getIntent(tenantCode, intentId);

        if ("SUCCEEDED".equals(intent.getStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Cannot fail an already succeeded payment"))
                    .build();
        }

        PaymentGatewayService.MockGatewayResponse gatewayResponse = paymentGatewayService.initiatePayment(intent, "MOCK");

        paymentIntentService.markFailed(
                tenantCode, intent.getId(), "MOCK",
                gatewayResponse.gatewayTransactionId(), caller.userId()
        );

        return Response.ok(Map.of(
                "status", "FAILED",
                "gatewayTransactionId", gatewayResponse.gatewayTransactionId(),
                "message", "Mock payment failed"
        )).build();
    }
}

package base.rest;

import base.security.CallerContext;
import base.service.MoneyService;
import base.service.PaymentGatewayService;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@UnlessBuildProfile("prod")
@Path("/payments/mockbank")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MockBankResource {

    private static final String MOCK_SECRET = "mock_secret_v_erp_lite_2025";

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    MoneyService moneyService;

    @Inject
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8081")
    int port;

    public static class ChargeRequest {
        public String paymentIntentId;
        public long amountMinor;
        public String currency;
    }

    public static class RefundRequest {
        public String gatewayTransactionId;
        public long amountMinor;
        public String currency;
        public String idempotencyKey;
    }

    public static class PayoutRequest {
        public long amountMinor;
        public String currency;
        public String bankName;
        public String bankAccountNumber;
        public String bankAccountHolder;
    }

    public static class SimulateTransferredRequest {
        public String paymentIntentId;
        public String gatewayTransactionId;
        public String status; // SUCCESS or FAILED
        public long amountMinor;
        public String currency;
    }

    @POST
    @Path("/initiate-charge")
    @RolesAllowed({"admin", "businessOwner", "operator", "customer"})
    public Response initiateCharge(ChargeRequest request) {
        if (request == null || request.paymentIntentId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "paymentIntentId is required"))
                    .build();
        }

        String currency = moneyService.normalizeCurrency(request.currency != null ? request.currency : "VND");
        String transactionId = paymentGatewayService.generateTransactionId("MOCK");
        String signature = paymentGatewayService.signPayload(request.paymentIntentId, transactionId, request.amountMinor);

        Map<String, String> metadata = Map.of(
                "intent_id", request.paymentIntentId,
                "gateway_transaction_id", transactionId,
                "amount_minor", String.valueOf(request.amountMinor),
                "currency", currency,
                "signature", signature
        );

        ProviderOperationResult result = new ProviderOperationResult(
                transactionId,
                "PENDING",
                "Mock charge initiated successfully. Awaiting webhook simulation.",
                metadata
        );

        return Response.ok(result).build();
    }

    @POST
    @Path("/initiate-refund")
    @RolesAllowed({"admin", "businessOwner", "operator"})
    public Response initiateRefund(RefundRequest request) {
        if (request == null || request.gatewayTransactionId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "gatewayTransactionId is required"))
                    .build();
        }

        String currency = moneyService.normalizeCurrency(request.currency != null ? request.currency : "VND");
        String transactionId = paymentGatewayService.refund(
                "MOCK",
                request.gatewayTransactionId,
                request.amountMinor,
                currency,
                request.idempotencyKey != null ? request.idempotencyKey : UUID.randomUUID().toString()
        );

        ProviderOperationResult result = new ProviderOperationResult(
                transactionId,
                "SUCCESS",
                "Mock refund processed successfully.",
                Map.of("gateway_transaction_id", request.gatewayTransactionId)
        );

        return Response.ok(result).build();
    }

    @POST
    @Path("/initiate-payout")
    @RolesAllowed({"admin"})
    public Response initiatePayout(PayoutRequest request) {
        if (request == null || request.bankAccountNumber == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "bankAccountNumber is required"))
                    .build();
        }

        String transactionId = "PAY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ProviderOperationResult result = new ProviderOperationResult(
                transactionId,
                "SUCCESS",
                "Mock payout transfer completed successfully.",
                Map.of(
                        "bank_name", request.bankName != null ? request.bankName : "MOCK_BANK",
                        "bank_account", request.bankAccountNumber
                )
        );

        return Response.ok(result).build();
    }

    @POST
    @Path("/simulate-transferred")
    @RolesAllowed({"qa", "demo", "admin", "businessOwner", "customer", "user"})
    public Response simulateTransferred(SimulateTransferredRequest request) {
        if (request == null || request.paymentIntentId == null || request.gatewayTransactionId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "paymentIntentId and gatewayTransactionId are required"))
                    .build();
        }

        String status = request.status != null ? request.status.trim().toUpperCase() : "SUCCESS";
        String currency = moneyService.normalizeCurrency(request.currency != null ? request.currency : "VND");

        try {
            // Generate inner signature
            String innerSig = paymentGatewayService.signPayload(
                    request.paymentIntentId,
                    request.gatewayTransactionId,
                    request.amountMinor
            );

            // Construct payload JSON
            String jsonPayload = String.format(
                    "{\"paymentIntentId\":\"%s\",\"gatewayTransactionId\":\"%s\",\"status\":\"%s\",\"amountMinor\":%d,\"currency\":\"%s\",\"signature\":\"%s\"}",
                    request.paymentIntentId,
                    request.gatewayTransactionId,
                    status,
                    request.amountMinor,
                    currency,
                    innerSig
            );

            // Calculate HMAC-SHA256 header signature over the entire payload bytes
            String headerSignature = hmacSha256(jsonPayload, MOCK_SECRET);

            // POST to webhook endpoint locally
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String webhookUrl = "http://localhost:" + port + "/api/payments/webhook/MOCK";
            HttpRequest webhookRequest = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", headerSignature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(webhookRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Response.ok(Map.of(
                        "status", "SIMULATION_SUCCESS",
                        "webhookResponseCode", response.statusCode(),
                        "webhookResponseBody", response.body()
                )).build();
            } else {
                return Response.status(response.statusCode())
                        .entity(Map.of(
                                "status", "SIMULATION_FAILED",
                                "webhookResponseCode", response.statusCode(),
                                "webhookResponseBody", response.body()
                        )).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "status", "SIMULATION_ERROR",
                            "message", e.getMessage()
                    )).build();
        }
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HmacSHA256 not available", e);
        }
    }
}

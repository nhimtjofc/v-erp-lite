package base.rest;

import base.entity.PaymentIntent;
import base.service.PaymentGatewayService;
import base.service.PaymentIntentService;
import base.service.PaymentTestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PaymentWebhookResourceTest {

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    ObjectMapper objectMapper;

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

    private String calculateHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    "mock_secret_v_erp_lite_2025".getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldProcessSuccessfulWebhook() throws Exception {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(100_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                100_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        String txnId = "VNP_" + UUID.randomUUID().toString().replace("-", "");
        String signature = paymentGatewayService.signPayload(
                intent.getId().toString(), txnId, intent.getAmountMinor()
        );

        Map<String, Object> bodyMap = Map.of(
                "paymentIntentId", intent.getId().toString(),
                "gatewayTransactionId", txnId,
                "status", "SUCCESS",
                "amountMinor", 100_000,
                "currency", "VND",
                "signature", signature
        );

        String json = objectMapper.writeValueAsString(bodyMap);
        String hmac = calculateHmac(json);

        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", hmac)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(200)
                .body("status", equalTo("PROCESSED"));

        PaymentIntent refreshed = PaymentIntent.findById(intent.getId());
        assertEquals("SUCCEEDED", refreshed.getStatus());
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(50_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                50_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        Map<String, Object> bodyMap = Map.of(
                "paymentIntentId", intent.getId().toString(),
                "gatewayTransactionId", "VNP_bad_txn",
                "status", "SUCCESS",
                "amountMinor", 50_000,
                "currency", "VND",
                "signature", "invalid_signature_value"
        );

        String json = objectMapper.writeValueAsString(bodyMap);

        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", "bad_signature")
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(401)
                .body("error", containsString("signature"));
    }

    @Test
    void shouldRejectMissingSignatureHeader() throws Exception {
        String json = "{}";
        given()
                .contentType(JSON)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(401)
                .body("error", containsString("signature"));
    }

    @Test
    void shouldRejectReplayAttack() throws Exception {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(120_000));

        PaymentIntent intent1 = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                120_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent1.getId(), actorId);

        String txnId = "VNP_replay_test_" + UUID.randomUUID().toString().substring(0, 8);
        String signature = paymentGatewayService.signPayload(
                intent1.getId().toString(), txnId, intent1.getAmountMinor()
        );

        Map<String, Object> bodyMap = Map.of(
                "paymentIntentId", intent1.getId().toString(),
                "gatewayTransactionId", txnId,
                "status", "SUCCESS",
                "amountMinor", 120_000,
                "currency", "VND",
                "signature", signature
        );

        String json = objectMapper.writeValueAsString(bodyMap);
        String hmac = calculateHmac(json);

        // First attempt - SUCCESS
        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", hmac)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(200);

        // Second attempt - CONFLICT (409) due to unique constraint on InboundEventLog
        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", hmac)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(409)
                .body("error", containsString("replay"));
    }

    @Test
    void shouldRejectNonExistentIntent() throws Exception {
        Map<String, Object> bodyMap = Map.of(
                "paymentIntentId", UUID.randomUUID().toString(),
                "gatewayTransactionId", "VNP_no_intent_" + UUID.randomUUID().toString().substring(0, 8),
                "status", "SUCCESS",
                "amountMinor", 100_000,
                "currency", "VND",
                "signature", "some_signature"
        );

        String json = objectMapper.writeValueAsString(bodyMap);
        String hmac = calculateHmac(json);

        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", hmac)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", containsString("PaymentIntent not found"));
    }

    @Test
    void shouldRejectMissingPaymentIntentId() throws Exception {
        Map<String, Object> bodyMap = Map.of(
                "gatewayTransactionId", "VNP_no_id_" + UUID.randomUUID().toString().substring(0, 8),
                "status", "SUCCESS",
                "amountMinor", 100_000,
                "currency", "VND",
                "signature", "some_signature"
        );

        String json = objectMapper.writeValueAsString(bodyMap);
        String hmac = calculateHmac(json);

        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", hmac)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", containsString("paymentIntentId"));
    }

    @Test
    void shouldProcessFailedWebhook() throws Exception {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(200_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                200_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        String txnId = "VNP_failed_test_" + UUID.randomUUID().toString().substring(0, 8);
        String signature = paymentGatewayService.signPayload(
                intent.getId().toString(), txnId, intent.getAmountMinor()
        );

        Map<String, Object> bodyMap = Map.of(
                "paymentIntentId", intent.getId().toString(),
                "gatewayTransactionId", txnId,
                "status", "FAILED",
                "amountMinor", 200_000,
                "currency", "VND",
                "signature", signature
        );

        String json = objectMapper.writeValueAsString(bodyMap);
        String hmac = calculateHmac(json);

        given()
                .contentType(JSON)
                .header("X-Webhook-Signature", hmac)
                .body(json)
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(200);

        PaymentIntent refreshed = PaymentIntent.findById(intent.getId());
        assertEquals("FAILED", refreshed.getStatus());
    }

    private static String tenant() {
        return "test-webhook-" + UUID.randomUUID();
    }
}

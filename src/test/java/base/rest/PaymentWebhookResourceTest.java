package base.rest;

import base.entity.PaymentIntent;
import base.service.PaymentGatewayService;
import base.service.PaymentIntentService;
import base.service.PaymentTestDataFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

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

    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

    @Test
    void shouldProcessSuccessfulWebhook() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(100_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                100_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        String txnId = "VNP_1234567890_test";
        String signature = paymentGatewayService.signPayload(
                intent.getId().toString(), txnId, intent.getAmountMinor()
        );

        given()
                .contentType(JSON)
                .body(Map.of(
                        "paymentIntentId", intent.getId().toString(),
                        "gatewayTransactionId", txnId,
                        "status", "SUCCESS",
                        "amountMinor", 100_000,
                        "currency", "VND",
                        "signature", signature
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(200)
                .body("status", equalTo("PROCESSED"));

        PaymentIntent refreshed = PaymentIntent.findById(intent.getId());
        assertEquals("SUCCEEDED", refreshed.getStatus());
    }

    @Test
    void shouldRejectInvalidSignature() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(50_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                50_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        given()
                .contentType(JSON)
                .body(Map.of(
                        "paymentIntentId", intent.getId().toString(),
                        "gatewayTransactionId", "VNP_bad_txn",
                        "status", "SUCCESS",
                        "amountMinor", 50_000,
                        "currency", "VND",
                        "signature", "invalid_signature_value"
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", containsString("signature"));
    }

    @Test
    void shouldHandleIdempotentWebhook() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(75_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                75_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        String txnId = "VNP_idempotent_test";
        String signature = paymentGatewayService.signPayload(
                intent.getId().toString(), txnId, intent.getAmountMinor()
        );

        Map<String, Object> body = Map.of(
                "paymentIntentId", intent.getId().toString(),
                "gatewayTransactionId", txnId,
                "status", "SUCCESS",
                "amountMinor", 75_000,
                "currency", "VND",
                "signature", signature
        );

        given().contentType(JSON).body(body).when().post("/payments/webhook/MOCK")
                .then().statusCode(200);

        given().contentType(JSON).body(body).when().post("/payments/webhook/MOCK")
                .then().statusCode(200).body("status", equalTo("PROCESSED"));
    }

    @Test
    void shouldRejectNonExistentIntent() {
        given()
                .contentType(JSON)
                .body(Map.of(
                        "paymentIntentId", UUID.randomUUID().toString(),
                        "gatewayTransactionId", "VNP_no_intent",
                        "status", "SUCCESS",
                        "amountMinor", 100_000,
                        "currency", "VND",
                        "signature", "some_signature"
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", containsString("PaymentIntent not found"));
    }

    @Test
    void shouldRejectMissingPaymentIntentId() {
        given()
                .contentType(JSON)
                .body(Map.of(
                        "gatewayTransactionId", "VNP_no_id",
                        "status", "SUCCESS",
                        "amountMinor", 100_000,
                        "currency", "VND",
                        "signature", "some_signature"
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", containsString("paymentIntentId"));
    }

    @Test
    void shouldProcessFailedWebhook() {
        String tenantId = tenant();
        var seed = testDataFactory.seedOrder(tenantId, BigDecimal.valueOf(200_000));

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantId, seed.order().getId(), seed.customer().getId(),
                200_000, "VND", "intent-" + UUID.randomUUID(), actorId
        );
        paymentIntentService.confirmIntent(tenantId, intent.getId(), actorId);

        String txnId = "VNP_failed_test";
        String signature = paymentGatewayService.signPayload(
                intent.getId().toString(), txnId, intent.getAmountMinor()
        );

        given()
                .contentType(JSON)
                .body(Map.of(
                        "paymentIntentId", intent.getId().toString(),
                        "gatewayTransactionId", txnId,
                        "status", "FAILED",
                        "amountMinor", 200_000,
                        "currency", "VND",
                        "signature", signature
                ))
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

package base.service;

import base.entity.PaymentIntent;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PaymentWebhookResourceTest {

    @Inject
    PaymentTestDataFactory testDataFactory;

    @Inject
    PaymentIntentService paymentIntentService;

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Test
    void validWebhookSucceeds() {
        String tenantId = "webhook-" + UUID.randomUUID();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = createIntent(tenantId, seed);
        String txnId = "MOCK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String sig = paymentGatewayService.signPayloadForTest(intent.getId().toString(), txnId, 50_000);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "paymentIntentId", intent.getId().toString(),
                        "gatewayTransactionId", txnId,
                        "status", "SUCCESS",
                        "amountMinor", 50_000L,
                        "currency", "VND",
                        "signature", sig
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(200)
                .body("status", equalTo("PROCESSED"));
    }

    @Test
    void webhookWithInvalidSignatureRejected() {
        String tenantId = "webhook-bad-sig-" + UUID.randomUUID();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = createIntent(tenantId, seed);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "paymentIntentId", intent.getId().toString(),
                        "gatewayTransactionId", "MOCK_tampered",
                        "status", "SUCCESS",
                        "amountMinor", 50_000L,
                        "currency", "VND",
                        "signature", "invalid_signature"
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid webhook signature"));
    }

    @Test
    void duplicateWebhookIsIdempotent() {
        String tenantId = "webhook-dedup-" + UUID.randomUUID();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = createIntent(tenantId, seed);
        String txnId = "MOCK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String sig = paymentGatewayService.signPayloadForTest(intent.getId().toString(), txnId, 50_000);

        Map<String, Object> body = Map.of(
                "paymentIntentId", intent.getId().toString(),
                "gatewayTransactionId", txnId,
                "status", "SUCCESS",
                "amountMinor", 50_000L,
                "currency", "VND",
                "signature", sig
        );

        given().contentType(ContentType.JSON).body(body)
                .when().post("/payments/webhook/MOCK")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/payments/webhook/MOCK")
                .then().statusCode(200)
                .body("message", anyOf(equalTo("Payment already succeeded"),
                        equalTo("Webhook already processed (idempotent)")));
    }

    @Test
    void webhookForCancelledIntentRejected() {
        String tenantId = "webhook-cancelled-" + UUID.randomUUID();
        var seed = testDataFactory.seedOrder(tenantId, new java.math.BigDecimal("50000"));
        PaymentIntent intent = createIntent(tenantId, seed);
        paymentIntentService.cancelIntent(tenantId, intent.getId(), null);

        String txnId = "MOCK_cancelled_" + System.currentTimeMillis();
        String sig = paymentGatewayService.signPayloadForTest(intent.getId().toString(), txnId, 50_000);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "paymentIntentId", intent.getId().toString(),
                        "gatewayTransactionId", txnId,
                        "status", "SUCCESS",
                        "amountMinor", 50_000L,
                        "currency", "VND",
                        "signature", sig
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400)
                .body("error", equalTo("PaymentIntent was cancelled before webhook arrived"));
    }

    @Test
    void webhookMissingFieldsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("status", "SUCCESS"))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400);
    }

    @Test
    void webhookWithInvalidIntentIdRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "paymentIntentId", "not-a-uuid",
                        "gatewayTransactionId", "txn-1",
                        "status", "SUCCESS"
                ))
                .when()
                .post("/payments/webhook/MOCK")
                .then()
                .statusCode(400);
    }

    private PaymentIntent createIntent(String tenantId, PaymentTestDataFactory.SeedOrder seed) {
        return paymentIntentService.createIntent(tenantId, seed.order().getId(),
                seed.customer().getId(), 50_000, "VND",
                "wh-" + UUID.randomUUID(), null);
    }
}

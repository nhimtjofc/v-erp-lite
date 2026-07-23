package base.service;

import base.entity.PaymentIntent;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import base.entity.Order;
import base.entity.Wallet;
import jakarta.inject.Inject;

@ApplicationScoped
public class PaymentGatewayService {

    private static final String MOCK_SECRET = "mock_secret_v_erp_lite_2025";

    @Inject
    VietQRService vietQRService;

    public record MockGatewayResponse(
            String gateway,
            String gatewayTransactionId,
            String paymentUrl,
            String qrCodeContent,
            String mockSecret,
            Instant expiresAt,
            Map<String, String> metadata
    ) {}

    public MockGatewayResponse initiatePayment(PaymentIntent intent, String gateway) {
        if (intent == null) {
            throw new IllegalArgumentException("PaymentIntent is required");
        }
        String normalizedGateway = normalizeGateway(gateway);
        String transactionId = transactionIdForIntent(normalizedGateway, intent.getId());

        String paymentUrl = buildMockPaymentUrl(intent, transactionId, normalizedGateway);
        
        String qrContent = buildQrCodeContent(intent, transactionId, normalizedGateway);
        if ("MOCK".equals(normalizedGateway)) {
            Order order = Order.find("id", intent.getOrderId()).firstResult();
            if (order != null && order.getBusiness() != null) {
                Wallet wallet = Wallet.find("tenantId = ?1 and ownerType = 'SELLER' and ownerId = ?2 and currency = 'VND'", 
                        intent.getTenantId(), order.getBusiness().getId()).firstResult();
                if (wallet == null) {
                    wallet = Wallet.find("tenantId = ?1 and ownerType = 'SELLER' and ownerId = ?2", 
                            intent.getTenantId(), order.getBusiness().getId()).firstResult();
                }
                if (wallet != null && wallet.getBankName() != null && wallet.getBankAccountNumber() != null) {
                    qrContent = vietQRService.generateQrPayload(wallet.getBankName(), wallet.getBankAccountNumber(), intent.getAmountMinor(), "Thanh toan " + order.getOrderNumber());
                }
            }
        }
        
        String signature = signPayload(intent.getId().toString(), transactionId, intent.getAmountMinor());

        return new MockGatewayResponse(
                normalizedGateway,
                transactionId,
                paymentUrl,
                qrContent,
                signature,
                intent.getExpiresAt(),
                Map.of(
                        "intent_id", intent.getId().toString(),
                        "order_id", intent.getOrderId().toString(),
                        "amount_minor", String.valueOf(intent.getAmountMinor()),
                        "currency", intent.getCurrency(),
                        "signature", signature
                )
        );
    }

    public String getWebhookPayload(PaymentIntent intent, String gatewayTransactionId, boolean success) {
        String status = success ? "SUCCESS" : "FAILED";
        String signature = signPayload(intent.getId().toString(), gatewayTransactionId, intent.getAmountMinor());
        return String.format(
                "{\"payment_intent_id\":\"%s\",\"gateway_transaction_id\":\"%s\",\"status\":\"%s\",\"amount_minor\":%d,\"currency\":\"%s\",\"signature\":\"%s\"}",
                intent.getId(),
                gatewayTransactionId,
                status,
                intent.getAmountMinor(),
                intent.getCurrency(),
                signature
        );
    }

    public boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        if (io.quarkus.runtime.LaunchMode.current() == io.quarkus.runtime.LaunchMode.NORMAL) {
            return false;
        }
        String expected = hmacSha256(payload, MOCK_SECRET);
        return expected.equals(signature.trim());
    }

    public boolean verifySignature(String intentId, String gatewayTransactionId, long amountMinor, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        if (io.quarkus.runtime.LaunchMode.current() == io.quarkus.runtime.LaunchMode.NORMAL) {
            return false;
        }
        String expected = signPayload(intentId, gatewayTransactionId, amountMinor);
        return expected.equals(signature.trim());
    }

    /**
     * Refund adapter seam. The lite profile uses a deterministic mock reference;
     * a real gateway implementation must use {@code idempotencyKey} when calling
     * the provider so retries cannot create a second refund.
     */
    public String refund(String gateway, String gatewayTransactionId, long amountMinor, String currency,
                         String idempotencyKey) {
        if (gatewayTransactionId == null || gatewayTransactionId.isBlank()) {
            throw new IllegalArgumentException("Gateway transaction ID is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        String stableKey = String.join(":", normalizeGateway(gateway), gatewayTransactionId,
                String.valueOf(amountMinor), currency, idempotencyKey);
        return "REF_" + UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateTransactionId(String gateway) {
        String prefix = switch (normalizeGateway(gateway)) {
            case "VNPAY" -> "VNP";
            case "STRIPE" -> "STR";
            default -> "MOCK";
        };
        return prefix + "_" + Instant.now().toEpochMilli() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String transactionIdForIntent(String gateway, UUID intentId) {
        String prefix = switch (normalizeGateway(gateway)) {
            case "VNPAY" -> "VNP";
            case "STRIPE" -> "STR";
            default -> "MOCK";
        };
        return prefix + "_" + intentId.toString().replace("-", "");
    }

    private String buildMockPaymentUrl(PaymentIntent intent, String transactionId, String gateway) {
        String baseUrl = switch (gateway) {
            case "VNPAY" -> "https://sandbox.vnpay.vn/payment";
            case "STRIPE" -> "https://checkout.stripe.com/pay";
            default -> "https://mock-gateway.v-erp-lite.local/pay";
        };
        String params = String.format(
                "transaction_id=%s&order_id=%s&amount=%d&currency=%s&signature=%s",
                transactionId,
                intent.getOrderId(),
                intent.getAmountMinor(),
                intent.getCurrency(),
                URLEncoder.encode(signPayload(intent.getId().toString(), transactionId, intent.getAmountMinor()), StandardCharsets.UTF_8)
        );
        return baseUrl + "?" + params;
    }

    private String buildQrCodeContent(PaymentIntent intent, String transactionId, String gateway) {
        return String.format(
                "v-erp://payment/%s?intent=%s&txn=%s&amount=%d&currency=%s",
                gateway.toLowerCase(),
                intent.getId(),
                transactionId,
                intent.getAmountMinor(),
                intent.getCurrency()
        );
    }

    public String signPayload(String intentId, String transactionId, long amountMinor) {
        String payload = String.format("%s:%s:%d:%s", intentId, transactionId, amountMinor, MOCK_SECRET);
        return hmacSha256(payload, MOCK_SECRET);
    }

    // Test-support: package-private aliases for security tests in the same package
    String signPayloadForTest(String intentId, String transactionId, long amountMinor) {
        return signPayload(intentId, transactionId, amountMinor);
    }

    String hmacSha256ForTest(String data) {
        return hmacSha256(data, MOCK_SECRET);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HmacSHA256 not available", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid HMAC key", e);
        }
    }

    private String normalizeGateway(String gateway) {
        if (gateway == null || gateway.isBlank()) {
            return "MOCK";
        }
        String upper = gateway.trim().toUpperCase();
        return switch (upper) {
            case "VNPAY" -> "VNPAY";
            case "STRIPE" -> "STRIPE";
            default -> "MOCK";
        };
    }
}

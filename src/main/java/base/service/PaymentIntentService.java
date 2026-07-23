package base.service;

import base.entity.PaymentIntent;
import base.entity.PaymentTransaction;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentIntentService {

    private static final int DEFAULT_EXPIRY_MINUTES = 30;

    @Inject
    MoneyService moneyService;

    @Inject
    PaymentService paymentService;

    @Transactional
    public PaymentIntent createIntent(String tenantId, UUID orderId, UUID customerId, long amountMinor,
                                      String currency, String idempotencyKey, UUID actorId) {
        if (orderId == null) {
            throw new BadRequestException("Vui lòng nhập mã đơn hàng");
        }
        if (customerId == null) {
            throw new BadRequestException("Vui lòng nhập mã khách hàng");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Vui lòng nhập khóa idempotency");
        }

        PaymentIntent existing = PaymentIntent.find(
                "tenantId = ?1 and idempotencyKey = ?2",
                tenantId,
                idempotencyKey.trim()
        ).firstResult();
        if (existing != null) {
            return existing;
        }

        Money amount = moneyService.positiveMoney(amountMinor, currency);
        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setTenantId(tenantId);
        intent.setOrderId(orderId);
        intent.setCustomerId(customerId);
        intent.setAmountMinor(amount.getAmountMinor());
        intent.setCurrency(amount.getCurrency());
        intent.setStatus("REQUIRES_PAYMENT_METHOD");
        intent.setIdempotencyKey(idempotencyKey.trim());
        intent.setExpiresAt(Instant.now().plus(DEFAULT_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        intent.setCreatedBy(actorId);
        intent.persist();
        return intent;
    }

    public PaymentIntent getIntent(String tenantId, UUID intentId) {
        PaymentIntent intent = PaymentIntent.find("id = ?1 and tenantId = ?2", intentId, tenantId).firstResult();
        if (intent == null) {
            throw new NotFoundException("Không tìm thấy ý định thanh toán");
        }
        return intent;
    }

    public List<PaymentIntent> listOrderIntents(String tenantId, UUID orderId) {
        if (orderId == null) {
            throw new BadRequestException("Vui lòng nhập mã đơn hàng");
        }
        return PaymentIntent.list(
                "tenantId = ?1 and orderId = ?2 order by createdAt desc",
                tenantId,
                orderId
        );
    }

    public List<PaymentTransaction> listTransactions(String tenantId, UUID intentId) {
        getIntent(tenantId, intentId);
        return PaymentTransaction.list(
                "tenantId = ?1 and paymentIntent.id = ?2 order by createdAt desc",
                tenantId,
                intentId
        );
    }

    @Transactional
    public PaymentIntent confirmIntent(String tenantId, UUID intentId, UUID actorId) {
        PaymentIntent intent = intentForUpdate(tenantId, intentId);
        if ("PROCESSING".equals(intent.getStatus())) {
            return intent;
        }
        requireStatus(intent, "REQUIRES_PAYMENT_METHOD");
        ensureNotExpired(intent);
        intent.setStatus("PROCESSING");
        intent.setUpdatedBy(actorId);
        return intent;
    }

    @Transactional
    public PaymentIntent markSucceeded(String tenantId, UUID intentId, String gateway, String gatewayTransactionId,
                                       UUID actorId) {
        PaymentIntent intent = intentForUpdate(tenantId, intentId);
        if ("SUCCEEDED".equals(intent.getStatus())) {
            return intent;
        }
        if (!List.of("REQUIRES_PAYMENT_METHOD", "PROCESSING").contains(intent.getStatus())) {
            throw new BadRequestException("Chỉ có thể hoàn tất ý định thanh toán đang chờ xử lý");
        }
        appendGatewayTransaction(intent, gateway, gatewayTransactionId, "SUCCESS", actorId);
        intent.setStatus("SUCCEEDED");
        intent.setUpdatedBy(actorId);
        paymentService.settleSucceededIntent(intent, actorId);
        return intent;
    }

    @Transactional
    public PaymentIntent markFailed(String tenantId, UUID intentId, String gateway, String gatewayTransactionId,
                                    UUID actorId) {
        PaymentIntent intent = intentForUpdate(tenantId, intentId);
        if ("FAILED".equals(intent.getStatus())) {
            return intent;
        }
        if ("SUCCEEDED".equals(intent.getStatus())) {
            throw new BadRequestException("Không thể đánh dấu thất bại cho thanh toán đã thành công");
        }
        appendGatewayTransaction(intent, gateway, gatewayTransactionId, "FAILED", actorId);
        intent.setStatus("FAILED");
        intent.setUpdatedBy(actorId);
        return intent;
    }

    @Transactional
    public PaymentIntent cancelIntent(String tenantId, UUID intentId, UUID actorId) {
        PaymentIntent intent = intentForUpdate(tenantId, intentId);
        if ("CANCELLED".equals(intent.getStatus())) {
            return intent;
        }
        if ("SUCCEEDED".equals(intent.getStatus())) {
            throw new BadRequestException("Không thể hủy thanh toán đã thành công");
        }
        intent.setStatus("CANCELLED");
        intent.setUpdatedBy(actorId);
        return intent;
    }

    @Transactional
    public PaymentIntent expireIntent(String tenantId, UUID intentId, UUID actorId) {
        PaymentIntent intent = intentForUpdate(tenantId, intentId);
        if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(intent.getStatus())) {
            return intent;
        }
        if (intent.getExpiresAt().isAfter(Instant.now())) {
            throw new BadRequestException("Ý định thanh toán chưa hết hạn");
        }
        intent.setStatus("CANCELLED");
        intent.setUpdatedBy(actorId);
        return intent;
    }

    private PaymentIntent intentForUpdate(String tenantId, UUID intentId) {
        if (intentId == null) {
            throw new BadRequestException("Vui lòng nhập mã ý định thanh toán");
        }
        PaymentIntent intent = PaymentIntent.find("id = ?1 and tenantId = ?2", intentId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (intent == null) {
            throw new NotFoundException("Không tìm thấy ý định thanh toán");
        }
        return intent;
    }

    private void appendGatewayTransaction(PaymentIntent intent, String gateway, String gatewayTransactionId,
                                          String status, UUID actorId) {
        if (gateway == null || gateway.isBlank()) {
            throw new BadRequestException("Vui lòng nhập cổng thanh toán");
        }
        if (gatewayTransactionId == null || gatewayTransactionId.isBlank()) {
            throw new BadRequestException("Vui lòng nhập mã giao dịch cổng thanh toán");
        }
        PaymentTransaction existing = PaymentTransaction.find(
                "tenantId = ?1 and gateway = ?2 and gatewayTransactionId = ?3",
                intent.getTenantId(),
                gateway.trim().toUpperCase(),
                gatewayTransactionId.trim()
        ).firstResult();
        if (existing != null) {
            return;
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setTenantId(intent.getTenantId());
        transaction.setPaymentIntent(intent);
        transaction.setGateway(gateway.trim().toUpperCase());
        transaction.setGatewayTransactionId(gatewayTransactionId.trim());
        transaction.setAmountMinor(intent.getAmountMinor());
        transaction.setCurrency(intent.getCurrency());
        transaction.setStatus(status);
        transaction.setCreatedBy(actorId);
        transaction.persist();
    }

    @Transactional
    public void processWebhookEvent(String tenantId, UUID intentId, String gateway, String gatewayTransactionId, String status) {
        if ("SUCCESS".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status)) {
            markSucceeded(tenantId, intentId, gateway, gatewayTransactionId, null);
        } else {
            markFailed(tenantId, intentId, gateway, gatewayTransactionId, null);
        }
    }

    private void requireStatus(PaymentIntent intent, String expectedStatus) {
        if (!expectedStatus.equals(intent.getStatus())) {
            throw new BadRequestException("Trạng thái ý định thanh toán không hợp lệ");
        }
    }

    private void ensureNotExpired(PaymentIntent intent) {
        if (intent.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Ý định thanh toán đã hết hạn");
        }
    }
}

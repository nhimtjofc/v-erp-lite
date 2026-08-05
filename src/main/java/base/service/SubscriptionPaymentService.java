package base.service;

import base.entity.SubscriptionPayment;
import base.entity.SubscriptionPlan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SubscriptionPaymentService {

    @Inject
    EntitlementService entitlementService;

    @Transactional
    public SubscriptionPayment createPayment(
            String tenantCode,
            String planCode,
            String billingPeriod,
            String idempotencyKey,
            UUID actorId
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("idempotencyKey is required");
        }

        SubscriptionPayment existing = SubscriptionPayment.find(
                "tenantId = ?1 and idempotencyKey = ?2",
                tenantCode,
                idempotencyKey.trim()
        ).firstResult();
        if (existing != null) {
            return existing;
        }

        SubscriptionPayment pendingPayment = SubscriptionPayment.find(
                "tenantId = ?1 and status = ?2",
                tenantCode,
                "PENDING"
        ).firstResult();
        if (pendingPayment != null) {
            throw new BadRequestException("A subscription payment is already awaiting transfer");
        }
        SubscriptionPlan plan = requirePlan(planCode);
        String period = normalizePeriod(billingPeriod);
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setId(UUID.randomUUID());
        payment.setTenantId(tenantCode);
        payment.setPlanCode(plan.getCode());
        payment.setBillingPeriod(period);
        payment.setAmountMinor("ANNUAL".equals(period) ? plan.getAnnualPriceMinor() : plan.getMonthlyPriceMinor());
        payment.setCurrency(plan.getCurrency());
        payment.setStatus("PENDING");
        payment.setIdempotencyKey(idempotencyKey.trim());
        payment.setCreatedBy(actorId);
        payment.persist();
        return payment;
    }

    public List<SubscriptionPayment> listTenantPayments(String tenantCode) {
        return SubscriptionPayment.list("tenantId = ?1 order by createdAt desc", tenantCode);
    }

    public List<SubscriptionPayment> listPayments(String status) {
        if (status == null || status.isBlank()) {
            return SubscriptionPayment.list("order by submittedAt desc, createdAt desc");
        }
        return SubscriptionPayment.list("status = ?1 order by submittedAt desc, createdAt desc", normalizeStatus(status));
    }

    @Transactional
    public SubscriptionPayment processMockWebhook(String tenantCode, UUID paymentId, String gateway, String status) {
        SubscriptionPayment payment = findForUpdate(tenantCode, paymentId);
        if (!"PENDING".equals(payment.getStatus())) {
            throw new BadRequestException("Only pending subscription payments can receive a mock webhook");
        }
        String normalizedGateway = gateway == null || gateway.isBlank() ? "VIETQR" : gateway.trim().toUpperCase();
        if (!"VIETQR".equals(normalizedGateway)) {
            throw new BadRequestException("Only VietQR is available for subscription payments");
        }
        String normalizedStatus = status == null || status.isBlank() ? "SUCCESS" : status.trim().toUpperCase();
        if (!List.of("SUCCESS", "FAILED").contains(normalizedStatus)) {
            throw new BadRequestException("Mock webhook status must be SUCCESS or FAILED");
        }
        payment.setPaymentReference("MOCK-" + normalizedGateway + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setSubmittedAt(Instant.now());
        payment.setStatus("SUCCESS".equals(normalizedStatus) ? "SUBMITTED" : "FAILED");
        return payment;
    }
    @Transactional
    public SubscriptionPayment approvePayment(UUID paymentId, UUID actorId) {
        SubscriptionPayment payment = findForUpdate(null, paymentId);
        if (!"SUBMITTED".equals(payment.getStatus())) {
            throw new BadRequestException("Only submitted subscription payments can be approved");
        }
        entitlementService.changeSubscription(
                payment.getTenantId(),
                payment.getPlanCode(),
                payment.getBillingPeriod(),
                "ACTIVE",
                actorId
        );
        payment.setStatus("APPROVED");
        payment.setReviewedAt(Instant.now());
        payment.setReviewedBy(actorId);
        payment.setUpdatedBy(actorId);
        return payment;
    }

    @Transactional
    public SubscriptionPayment rejectPayment(UUID paymentId, String reason, UUID actorId) {
        SubscriptionPayment payment = findForUpdate(null, paymentId);
        if (!"SUBMITTED".equals(payment.getStatus())) {
            throw new BadRequestException("Only submitted subscription payments can be rejected");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("rejectionReason is required");
        }
        payment.setStatus("REJECTED");
        payment.setRejectionReason(reason.trim());
        payment.setReviewedAt(Instant.now());
        payment.setReviewedBy(actorId);
        payment.setUpdatedBy(actorId);
        return payment;
    }

    private SubscriptionPayment findForUpdate(String tenantCode, UUID paymentId) {
        if (paymentId == null) {
            throw new BadRequestException("paymentId is required");
        }
        String query = tenantCode == null ? "id = ?1" : "id = ?1 and tenantId = ?2";
        SubscriptionPayment payment = tenantCode == null
                ? SubscriptionPayment.find(query, paymentId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult()
                : SubscriptionPayment.find(query, paymentId, tenantCode).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (payment == null) {
            throw new NotFoundException("Subscription payment not found");
        }
        return payment;
    }

    private SubscriptionPlan requirePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new BadRequestException("planCode is required");
        }
        SubscriptionPlan plan = SubscriptionPlan.find("code", planCode.trim().toUpperCase()).firstResult();
        if (plan == null || !plan.isActive() || !List.of("BASE", "PREMIUM").contains(plan.getCode())) {
            throw new NotFoundException("Subscription plan not found: " + planCode);
        }
        return plan;
    }

    private String normalizePeriod(String billingPeriod) {
        String period = billingPeriod == null || billingPeriod.isBlank() ? "MONTHLY" : billingPeriod.trim().toUpperCase();
        if (!List.of("MONTHLY", "ANNUAL").contains(period)) {
            throw new BadRequestException("billingPeriod must be MONTHLY or ANNUAL");
        }
        return period;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!List.of("PENDING", "SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "FAILED").contains(normalized)) {
            throw new BadRequestException("Invalid subscription payment status");
        }
        return normalized;
    }
}

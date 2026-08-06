package base.service;

import base.entity.SubscriptionPayment;
import base.entity.SubscriptionPlan;
import base.entity.TenantSubscription;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SubscriptionPaymentService {

    public record SubscriptionFinancialStats(
            long recognizedProfitMinor,
            long currentMonthProfitMinor,
            long pendingRevenueMinor,
            long approvedPaymentCount,
            long activeSubscriptionCount,
            long activeSubscriptionValueMinor,
            long baseSubscriptionCount,
            long premiumSubscriptionCount,
            String currency,
            String profitBasis
    ) {}

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

        SubscriptionPlan plan = requirePlan(planCode);
        String period = normalizePeriod(billingPeriod);
        Instant now = Instant.now();
        TenantSubscription subscription = TenantSubscription.find("tenantId", tenantCode).firstResult();
        EntitlementService.UpgradeQuote upgradeQuote = entitlementService.getUpgradeQuote(subscription, now);
        long amountMinor;
        if (hasActivePaidTerm(subscription, now)) {
            if (upgradeQuote == null || !upgradeQuote.toPlanCode().equals(plan.getCode())) {
                throw new BadRequestException("Gói hiện tại đã được thanh toán và bị khóa đến ngày hết hạn");
            }
            if (!upgradeQuote.billingPeriod().equals(period)) {
                throw new BadRequestException("Nâng cấp phải giữ nguyên chu kỳ thanh toán hiện tại");
            }
            amountMinor = upgradeQuote.amountMinor();
        } else {
            amountMinor = "ANNUAL".equals(period) ? plan.getAnnualPriceMinor() : plan.getMonthlyPriceMinor();
        }

        SubscriptionPayment submittedPayment = SubscriptionPayment.find(
                "tenantId = ?1 and status = ?2",
                tenantCode,
                "SUBMITTED"
        ).firstResult();
        if (submittedPayment != null) {
            throw new BadRequestException("Đã có một thanh toán đang chờ xác nhận");
        }

        SubscriptionPayment pendingPayment = SubscriptionPayment.find(
                "tenantId = ?1 and status = ?2",
                tenantCode,
                "PENDING"
        ).firstResult();
        if (pendingPayment != null) {
            pendingPayment.setStatus("CANCELLED");
        }
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setId(UUID.randomUUID());
        payment.setTenantId(tenantCode);
        payment.setPlanCode(plan.getCode());
        payment.setBillingPeriod(period);
        payment.setAmountMinor(amountMinor);
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
        String normalizedStatus = null;
        List<SubscriptionPayment> payments;
        if (status == null || status.isBlank()) {
            payments = SubscriptionPayment.list("order by submittedAt desc, createdAt desc");
        } else {
            normalizedStatus = normalizeStatus(status);
            payments = SubscriptionPayment.list(
                    "status = ?1 order by submittedAt desc, createdAt desc",
                    normalizedStatus
            );
        }
        return includeSeededSubscriptionPayments(payments, TenantSubscription.listAll(), normalizedStatus);
    }

    static List<SubscriptionPayment> includeSeededSubscriptionPayments(
            List<SubscriptionPayment> payments,
            List<TenantSubscription> subscriptions,
            String status
    ) {
        List<SubscriptionPayment> result = new ArrayList<>(payments);
        if (status == null || "APPROVED".equals(status)) {
            subscriptions.stream()
                    .filter(SubscriptionPaymentService::isSeededSubscription)
                    .map(SubscriptionPaymentService::openingPayment)
                    .forEach(result::add);
        }
        result.sort(Comparator.comparing(
                SubscriptionPaymentService::displayTime,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return result;
    }

    private static SubscriptionPayment openingPayment(TenantSubscription subscription) {
        String openingKey = "opening-subscription:" + subscription.getTenantId();
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setId(UUID.nameUUIDFromBytes(openingKey.getBytes(StandardCharsets.UTF_8)));
        payment.setTenantId(subscription.getTenantId());
        payment.setPlanCode(subscription.getPlanCode());
        payment.setBillingPeriod(subscription.getBillingPeriod());
        payment.setAmountMinor(subscription.getPriceMinor());
        payment.setCurrency(subscription.getCurrency());
        payment.setStatus("APPROVED");
        payment.setIdempotencyKey(openingKey);
        payment.setPaymentReference("OPENING-" + subscription.getTenantId());
        payment.setSubmittedAt(subscription.getStartedAt());
        payment.setReviewedAt(subscription.getStartedAt());
        payment.setCreatedAt(subscription.getStartedAt());
        payment.setUpdatedAt(subscription.getStartedAt());
        return payment;
    }

    private static Instant displayTime(SubscriptionPayment payment) {
        return payment.getSubmittedAt() != null ? payment.getSubmittedAt() : payment.getCreatedAt();
    }

    private static boolean isSeededSubscription(TenantSubscription subscription) {
        return subscription.getCreatedBy() == null && subscription.getPriceMinor() > 0;
    }

    public SubscriptionFinancialStats getFinancialStats() {
        return calculateFinancialStats(
                SubscriptionPayment.listAll(),
                TenantSubscription.listAll(),
                Instant.now()
        );
    }

    static SubscriptionFinancialStats calculateFinancialStats(
            List<SubscriptionPayment> payments,
            List<TenantSubscription> subscriptions,
            Instant now
    ) {
        Instant monthStart = now.atZone(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        long recognizedProfitMinor = 0L;
        long currentMonthProfitMinor = 0L;
        long pendingRevenueMinor = 0L;
        long approvedPaymentCount = 0L;
        for (SubscriptionPayment payment : payments) {
            if ("APPROVED".equals(payment.getStatus())) {
                recognizedProfitMinor += payment.getAmountMinor();
                approvedPaymentCount++;
                if (payment.getReviewedAt() != null && !payment.getReviewedAt().isBefore(monthStart)) {
                    currentMonthProfitMinor += payment.getAmountMinor();
                }
            } else if ("SUBMITTED".equals(payment.getStatus())) {
                pendingRevenueMinor += payment.getAmountMinor();
            }
        }

        long activeSubscriptionCount = 0L;
        long activeSubscriptionValueMinor = 0L;
        long baseSubscriptionCount = 0L;
        long premiumSubscriptionCount = 0L;
        for (TenantSubscription subscription : subscriptions) {
            if (isSeededSubscription(subscription)) {
                recognizedProfitMinor += subscription.getPriceMinor();
                approvedPaymentCount++;
                if (subscription.getStartedAt() != null && !subscription.getStartedAt().isBefore(monthStart)) {
                    currentMonthProfitMinor += subscription.getPriceMinor();
                }
            }
            boolean active = "ACTIVE".equals(subscription.getStatus())
                    && (subscription.getCurrentPeriodEndsAt() == null
                    || now.isBefore(subscription.getCurrentPeriodEndsAt()));
            if (!active) {
                continue;
            }
            activeSubscriptionCount++;
            activeSubscriptionValueMinor += subscription.getPriceMinor();
            if ("BASE".equals(subscription.getPlanCode())) {
                baseSubscriptionCount++;
            } else if ("PREMIUM".equals(subscription.getPlanCode())) {
                premiumSubscriptionCount++;
            }
        }

        return new SubscriptionFinancialStats(
                recognizedProfitMinor,
                currentMonthProfitMinor,
                pendingRevenueMinor,
                approvedPaymentCount,
                activeSubscriptionCount,
                activeSubscriptionValueMinor,
                baseSubscriptionCount,
                premiumSubscriptionCount,
                "VND",
                "APPROVED_PAYMENTS_AND_SEEDED_SUBSCRIPTIONS"
        );
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
        SubscriptionPlan plan = requirePlan(payment.getPlanCode());
        long fullPriceMinor = "ANNUAL".equals(payment.getBillingPeriod())
                ? plan.getAnnualPriceMinor()
                : plan.getMonthlyPriceMinor();
        boolean proratedUpgrade = "PREMIUM".equals(payment.getPlanCode())
                && payment.getAmountMinor() < fullPriceMinor;
        if (proratedUpgrade) {
            entitlementService.upgradeSubscription(
                    payment.getTenantId(),
                    payment.getPlanCode(),
                    payment.getBillingPeriod(),
                    actorId
            );
        } else {
            TenantSubscription subscription = TenantSubscription.find("tenantId", payment.getTenantId())
                    .withLock(LockModeType.PESSIMISTIC_WRITE)
                    .firstResult();
            if (hasActivePaidTerm(subscription, Instant.now())) {
                throw new BadRequestException("Gói hiện tại đã được thanh toán và bị khóa đến ngày hết hạn");
            }
            entitlementService.changeSubscription(
                    payment.getTenantId(),
                    payment.getPlanCode(),
                    payment.getBillingPeriod(),
                    "ACTIVE",
                    actorId
            );
        }
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

    private boolean hasActivePaidTerm(TenantSubscription subscription, Instant now) {
        return subscription != null
                && subscription.getPriceMinor() > 0
                && "ACTIVE".equals(entitlementService.effectiveStatus(subscription, now));
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

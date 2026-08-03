package base.service;

import base.entity.Business;
import base.entity.Product;
import base.entity.SubscriptionPlan;
import base.entity.Tenant;
import base.entity.TenantSubscription;
import base.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server-side source of truth for subscription capabilities and quotas.
 * TenantConfig remains user-managed operational configuration and is never used
 * to grant subscription rights.
 */
@ApplicationScoped
public class EntitlementService {

    public static final String FEATURE_VOUCHERS = "vouchers";
    public static final String FEATURE_REPORTS = "reports";
    public static final String FEATURE_ECOMMERCE = "ecommerce";
    public static final String FEATURE_ONLINE_PAYMENTS = "onlinePayments";
    public static final String FEATURE_WALLET_PAYOUTS = "walletPayouts";

    public record Entitlements(
            String planCode,
            String planName,
            String productSlice,
            String status,
            String billingPeriod,
            long priceMinor,
            String currency,
            Instant currentPeriodEndsAt,
            boolean readOnly,
            Map<String, Boolean> features,
            Map<String, Long> limits
    ) {}

    public List<SubscriptionPlan> listActivePlans() {
        return SubscriptionPlan.list("active = true order by monthlyPriceMinor");
    }

    public Entitlements getEntitlements(String tenantCode) {
        TenantSubscription subscription = TenantSubscription.find("tenantId", tenantCode).firstResult();
        if (subscription == null) {
            requireTenant(tenantCode);
            return planEntitlements(requirePlan("BASE"), "TRIAL", "MONTHLY", 0L, null, false);
        }
        SubscriptionPlan plan = requirePlan(subscription.getPlanCode());
        String effectiveStatus = effectiveStatus(subscription, Instant.now());
        boolean readOnly = "SUSPENDED".equals(effectiveStatus) || "CANCELLED".equals(effectiveStatus);
        return planEntitlements(
                plan,
                effectiveStatus,
                subscription.getBillingPeriod(),
                subscription.getPriceMinor(),
                subscription.getCurrentPeriodEndsAt(),
                readOnly
        );
    }

    public void requireFeature(String tenantCode, String feature) {
        Entitlements entitlements = getEntitlements(tenantCode);
        requireWritable(entitlements);
        if (!Boolean.TRUE.equals(entitlements.features().get(feature))) {
            throw new ClientErrorException(
                    "Your current plan does not include feature: " + feature,
                    Response.Status.PAYMENT_REQUIRED
            );
        }
    }

    public void requireWritable(String tenantCode) {
        requireWritable(getEntitlements(tenantCode));
    }

    public void requireUserCapacity(String tenantCode) {
        requireCapacity(tenantCode, "maxUsers", User.count("tenantId = ?1 and isActive = true", tenantCode));
    }

    public void requireBusinessCapacity(String tenantCode) {
        requireCapacity(tenantCode, "maxBusinesses", Business.count("tenantId = ?1 and isActive = true", tenantCode));
    }

    public void requireProductCapacity(String tenantCode) {
        requireCapacity(tenantCode, "maxProducts", Product.count("tenantId = ?1 and isActive = true", tenantCode));
    }

    @Transactional
    public TenantSubscription startTrial(String tenantCode, UUID actorId) {
        TenantSubscription existing = TenantSubscription.find("tenantId", tenantCode).firstResult();
        if (existing != null) {
            return existing;
        }
        requireTenant(tenantCode);
        SubscriptionPlan plan = requirePlan("BASE");
        Instant now = Instant.now();
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setTenantId(tenantCode);
        subscription.setPlanCode(plan.getCode());
        subscription.setStatus("TRIAL");
        subscription.setBillingPeriod("MONTHLY");
        subscription.setPriceMinor(0L);
        subscription.setCurrency(plan.getCurrency());
        subscription.setStartedAt(now);
        subscription.setTrialEndsAt(now.plus(14, ChronoUnit.DAYS));
        subscription.setCurrentPeriodEndsAt(subscription.getTrialEndsAt());
        subscription.setCreatedBy(actorId);
        subscription.setUpdatedBy(actorId);
        subscription.persist();
        return subscription;
    }

    @Transactional
    public TenantSubscription changeSubscription(
            String tenantCode,
            String planCode,
            String billingPeriod,
            String status,
            UUID actorId
    ) {
        requireTenant(tenantCode);
        SubscriptionPlan plan = requirePlan(planCode);
        String normalizedPeriod = normalizePeriod(billingPeriod);
        String normalizedStatus = normalizeStatus(status);
        Instant now = Instant.now();
        TenantSubscription subscription = TenantSubscription.find("tenantId", tenantCode).firstResult();
        if (subscription == null) {
            subscription = new TenantSubscription();
            subscription.setId(UUID.randomUUID());
            subscription.setTenantId(tenantCode);
            subscription.setCreatedAt(null);
            subscription.setCreatedBy(actorId);
            subscription.setStartedAt(now);
            subscription.persist();
        }
        subscription.setPlanCode(plan.getCode());
        subscription.setStatus(normalizedStatus);
        subscription.setBillingPeriod(normalizedPeriod);
        subscription.setPriceMinor("ANNUAL".equals(normalizedPeriod)
                ? plan.getAnnualPriceMinor()
                : plan.getMonthlyPriceMinor());
        subscription.setCurrency(plan.getCurrency());
        subscription.setTrialEndsAt("TRIAL".equals(normalizedStatus)
                ? now.plus(14, ChronoUnit.DAYS)
                : null);
        subscription.setCurrentPeriodEndsAt("TRIAL".equals(normalizedStatus)
                ? subscription.getTrialEndsAt()
                : now.plus("ANNUAL".equals(normalizedPeriod) ? 365 : 30, ChronoUnit.DAYS));
        subscription.setGraceEndsAt(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setUpdatedBy(actorId);
        return subscription;
    }

    private void requireCapacity(String tenantCode, String limitName, long current) {
        Entitlements entitlements = getEntitlements(tenantCode);
        requireWritable(entitlements);
        long limit = entitlements.limits().getOrDefault(limitName, 0L);
        if (current >= limit) {
            throw new ClientErrorException(
                    "Plan quota reached for " + limitName + " (" + limit + ")",
                    Response.Status.PAYMENT_REQUIRED
            );
        }
    }

    private void requireWritable(Entitlements entitlements) {
        if (entitlements.readOnly()) {
            throw new ClientErrorException(
                    "Tenant subscription is " + entitlements.status() + "; the tenant is read-only",
                    Response.Status.PAYMENT_REQUIRED
            );
        }
    }

    private Entitlements planEntitlements(
            SubscriptionPlan plan,
            String status,
            String billingPeriod,
            long priceMinor,
            Instant currentPeriodEndsAt,
            boolean readOnly
    ) {
        return new Entitlements(
                plan.getCode(),
                plan.getName(),
                plan.getProductSlice(),
                status,
                billingPeriod,
                priceMinor,
                plan.getCurrency(),
                currentPeriodEndsAt,
                readOnly,
                new LinkedHashMap<>(plan.getFeatures()),
                new LinkedHashMap<>(plan.getLimits())
        );
    }

    private String effectiveStatus(TenantSubscription subscription, Instant now) {
        String status = normalizeStatus(subscription.getStatus());
        Instant periodEnd = subscription.getCurrentPeriodEndsAt();
        if (("TRIAL".equals(status) || "ACTIVE".equals(status) || "PAST_DUE".equals(status))
                && periodEnd != null && now.isAfter(periodEnd)) {
            if (subscription.getGraceEndsAt() != null && !now.isAfter(subscription.getGraceEndsAt())) {
                return "PAST_DUE";
            }
            return "SUSPENDED";
        }
        return status;
    }

    private SubscriptionPlan requirePlan(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            throw new BadRequestException("planCode is required");
        }
        SubscriptionPlan plan = SubscriptionPlan.find("code", planCode.trim().toUpperCase()).firstResult();
        if (plan == null || !plan.isActive()) {
            throw new NotFoundException("Subscription plan not found: " + planCode);
        }
        return plan;
    }

    private void requireTenant(String tenantCode) {
        if (Tenant.find("code", tenantCode).firstResult() == null) {
            throw new NotFoundException("Tenant not found: " + tenantCode);
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        if (!List.of("TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED").contains(normalized)) {
            throw new BadRequestException("Invalid subscription status");
        }
        return normalized;
    }

    private String normalizePeriod(String billingPeriod) {
        String normalized = billingPeriod == null || billingPeriod.isBlank()
                ? "MONTHLY"
                : billingPeriod.trim().toUpperCase();
        if (!List.of("MONTHLY", "ANNUAL").contains(normalized)) {
            throw new BadRequestException("billingPeriod must be MONTHLY or ANNUAL");
        }
        return normalized;
    }
}

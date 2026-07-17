package base.service;

import base.entity.PayoutRequest;
import base.entity.VendorRiskEvent;
import base.entity.Wallet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class PayoutAutomationService {

    @Inject
    PayoutService payoutService;

    @ConfigProperty(name = "payout.minimum-threshold-minor", defaultValue = "100000")
    long minimumThresholdMinor;

    @ConfigProperty(name = "payout.review-threshold-minor", defaultValue = "50000000")
    long reviewThresholdMinor;

    @ConfigProperty(name = "payout.risk-window", defaultValue = "P90D")
    Duration riskWindow;

    @ConfigProperty(name = "payout.balance-spike-multiplier", defaultValue = "3.0")
    double balanceSpikeMultiplier;

    public long minimumThresholdMinor() {
        return minimumThresholdMinor;
    }

    @Transactional
    public PayoutRequest processWallet(UUID walletId) {
        Wallet wallet = Wallet.findById(walletId, LockModeType.PESSIMISTIC_WRITE);
        if (wallet == null || !"SELLER".equals(wallet.getOwnerType())
                || wallet.getAvailableBalanceMinor() < minimumThresholdMinor) {
            return null;
        }

        PayoutRequest active = PayoutRequest.find(
                "wallet.id = ?1 and status in ('FLAGGED', 'AUTO_APPROVED', 'APPROVED')", walletId).firstResult();
        if (active != null) {
            return active;
        }

        String idempotencyKey = String.format(
                "wallet:%s:version:%d:balance:%d",
                wallet.getId(), wallet.getVersion(), wallet.getAvailableBalanceMinor());
        PayoutRequest existing = PayoutRequest.find(
                "tenantId = ?1 and idempotencyKey = ?2", wallet.getTenantId(), idempotencyKey).firstResult();
        if (existing != null) {
            return existing;
        }

        List<String> reasons = evaluateRules(wallet);
        boolean autoApproved = reasons.isEmpty();
        PayoutRequest payout = payoutService.createAutomatedRequest(
                wallet,
                autoApproved ? "AUTO_APPROVED" : "FLAGGED",
                autoApproved,
                autoApproved ? null : String.join("; ", reasons),
                idempotencyKey);
        if (autoApproved) {
            payoutService.executeTransfer(payout, null);
        }
        return payout;
    }

    private List<String> evaluateRules(Wallet wallet) {
        List<String> reasons = new ArrayList<>();
        long amount = wallet.getAvailableBalanceMinor();

        if (isBlank(wallet.getBankName()) || isBlank(wallet.getBankAccountNumber())
                || isBlank(wallet.getBankAccountHolder())) {
            reasons.add("Bank details are missing");
        }
        if (amount > reviewThresholdMinor) {
            reasons.add("Amount exceeds automatic payout threshold");
        }

        List<PayoutRequest> paidHistory = PayoutRequest.list(
                "tenantId = ?1 and wallet.ownerId = ?2 and status = 'PAID' order by paidAt desc",
                wallet.getTenantId(), wallet.getOwnerId());
        if (paidHistory.isEmpty()) {
            reasons.add("Owner's first-ever payout");
        } else {
            PayoutRequest lastPaid = paidHistory.getFirst();
            if (!sameBankDetails(wallet, lastPaid)) {
                reasons.add("First payout after bank detail change");
            }
            double average = paidHistory.stream().mapToLong(PayoutRequest::getAmountMinor).average().orElse(0);
            if (average > 0 && amount > average * balanceSpikeMultiplier) {
                reasons.add("Anomalous balance spike versus payout history");
            }
        }

        Instant cutoff = Instant.now().minus(riskWindow);
        long disputes = VendorRiskEvent.count(
                "tenantId = ?1 and ownerId = ?2 and eventType in ('OPEN_DISPUTE', 'CHARGEBACK') "
                        + "and occurredAt >= ?3 and resolvedAt is null",
                wallet.getTenantId(), wallet.getOwnerId(), cutoff);
        if (disputes > 0) {
            reasons.add("Open dispute or chargeback within the review window");
        }

        long failedPayouts = VendorRiskEvent.count(
                "tenantId = ?1 and ownerId = ?2 and eventType in ('PAYOUT_FAILED', 'PAYOUT_BOUNCED')",
                wallet.getTenantId(), wallet.getOwnerId());
        if (failedPayouts > 0) {
            reasons.add("Previous payout failed or bounced");
        }
        return reasons;
    }

    private boolean sameBankDetails(Wallet wallet, PayoutRequest payout) {
        return Objects.equals(wallet.getBankName(), payout.getBankName())
                && Objects.equals(wallet.getBankAccountNumber(), payout.getBankAccountNumber())
                && Objects.equals(wallet.getBankAccountHolder(), payout.getBankAccountHolder());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

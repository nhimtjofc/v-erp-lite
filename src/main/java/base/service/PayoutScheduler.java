package base.service;

import base.entity.Wallet;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PayoutScheduler {

    private static final Logger log = Logger.getLogger(PayoutScheduler.class);

    @Inject
    PayoutAutomationService automationService;

    @ConfigProperty(name = "payout.schedule.interval", defaultValue = "PT24H")
    Duration interval;

    @ConfigProperty(name = "payout.schedule.initial-delay", defaultValue = "PT1M")
    Duration initialDelay;

    private volatile Instant nextRunAt;

    @PostConstruct
    void initializeNextRun() {
        nextRunAt = Instant.now().plus(initialDelay);
    }

    @Scheduled(
            every = "${payout.schedule.interval}",
            delayed = "${payout.schedule.initial-delay}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void runAutomatedPayouts() {
        try {
            List<UUID> walletIds = Wallet.getEntityManager().createQuery(
                    "select w.id from Wallet w where w.ownerType = 'SELLER' and w.availableBalanceMinor >= :minimum",
                    UUID.class)
                    .setParameter("minimum", automationService.minimumThresholdMinor())
                    .getResultList();
            for (UUID walletId : walletIds) {
                try {
                    automationService.processWallet(walletId);
                } catch (Exception e) {
                    log.errorf(e, "Automated payout failed for wallet %s", walletId);
                }
            }
        } finally {
            nextRunAt = Instant.now().plus(interval);
        }
    }

    public Instant nextRunAt() {
        return nextRunAt;
    }
}

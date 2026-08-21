package base.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class EscrowReleaseScheduler {

    private static final Logger log = Logger.getLogger(EscrowReleaseScheduler.class);

    @Inject
    EscrowService escrowService;

    // Releases escrow when the hold period (7 days) has elapsed.
    // Also auto-completes orders in PENDING_COMPLETION status.
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void autoReleaseEscrows() {
        EscrowService.EscrowReleaseSummary summary = escrowService.releaseDueEscrows(Instant.now(), null);
        log.infof("Finished escrow release job. Success: %d, Failed: %d",
                summary.releasedOrders(), summary.failedOrders());
    }
}

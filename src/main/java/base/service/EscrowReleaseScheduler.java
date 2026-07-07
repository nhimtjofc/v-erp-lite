package base.service;

import base.entity.EscrowRecord;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class EscrowReleaseScheduler {

    private static final Logger log = Logger.getLogger(EscrowReleaseScheduler.class);

    @Inject
    EscrowService escrowService;

    // Runs every day at 1:00 AM (0 0 1 * * ?)
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void autoReleaseEscrows() {
        log.info("Starting automatic escrow release job...");

        Instant now = Instant.now();
        List<EscrowRecord> dueEscrows = EscrowRecord.find("status = ?1 and releaseDueAt <= ?2", "HELD", now).list();

        int successCount = 0;
        int failCount = 0;

        for (EscrowRecord escrow : dueEscrows) {
            try {
                // Delegate to EscrowService to handle business checks and wallet changes
                escrowService.releaseEscrow(escrow.getId());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to auto-release escrow: " + escrow.getId(), e);
                failCount++;
            }
        }

        log.infof("Finished escrow release job. Success: %d, Failed: %d", successCount, failCount);
    }
}

package base.service;

import base.entity.EscrowRecord;
import base.entity.Order;
import base.entity.OrderHistory;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        Instant now = Instant.now();
        List<Object[]> eligibleOrders = Order.getEntityManager().createQuery(
                "select distinct e.orderId, e.tenantId from EscrowRecord e "
                        + "where e.status = 'HELD' and e.releaseDueAt <= :now",
                Object[].class)
                .setParameter("now", now)
                .getResultList();

        int successCount = 0;
        int failCount = 0;
        for (Object[] eligible : eligibleOrders) {
            UUID orderId = (UUID) eligible[0];
            String tenantId = (String) eligible[1];
            try {
                escrowService.releaseOrderEscrow(tenantId, orderId, null);
                successCount++;
            } catch (Exception e) {
                log.errorf("Failed to release eligible escrow for order %s: %s", orderId, e.getMessage());
                failCount++;
            }
        }

        log.infof("Finished escrow release job. Success: %d, Failed: %d", successCount, failCount);
    }
}

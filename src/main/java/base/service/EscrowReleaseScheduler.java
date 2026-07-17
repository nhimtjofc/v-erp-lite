package base.service;

import base.entity.Order;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EscrowReleaseScheduler {

    private static final Logger log = Logger.getLogger(EscrowReleaseScheduler.class);

    @Inject
    EscrowService escrowService;

    // Safety net only: elapsed time never releases escrow. It merely retries
    // release for orders that have already reached DELIVERED or COMPLETED.
    @Scheduled(cron = "0 0 1 * * ?")
    public void autoReleaseEscrows() {
        List<Object[]> eligibleOrders = Order.getEntityManager().createQuery(
                "select distinct e.orderId, e.tenantId from EscrowRecord e, Order o "
                        + "where e.orderId = o.id and e.tenantId = o.tenantId and e.status = 'HELD' "
                        + "and o.status in ('DELIVERED', 'COMPLETED')",
                Object[].class).getResultList();

        int successCount = 0;
        int failCount = 0;
        for (Object[] eligible : eligibleOrders) {
            try {
                escrowService.releaseOrderEscrow((String) eligible[1], (UUID) eligible[0], null);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to release eligible escrow for order: " + eligible[0], e);
                failCount++;
            }
        }

        log.infof("Finished escrow release job. Success: %d, Failed: %d", successCount, failCount);
    }
}

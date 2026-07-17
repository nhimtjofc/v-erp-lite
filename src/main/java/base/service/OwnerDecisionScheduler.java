package base.service;

import base.entity.Order;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OwnerDecisionScheduler {

    private static final Logger log = Logger.getLogger(OwnerDecisionScheduler.class);

    @Inject
    OrderFulfillmentService fulfillmentService;

    @Scheduled(every = "${orders.owner-decision-scan-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void declineExpiredOrders() {
        Instant now = Instant.now();
        List<Object[]> expired = Order.getEntityManager().createQuery(
                "select o.id, o.tenantId from Order o where o.status = 'PAID_ESCROW' "
                        + "and o.ownerDecisionDeadline is not null and o.ownerDecisionDeadline <= :now",
                Object[].class)
                .setParameter("now", now)
                .getResultList();
        for (Object[] row : expired) {
            try {
                fulfillmentService.autoDecline((String) row[1], (UUID) row[0], now);
            } catch (Exception e) {
                log.errorf(e, "Could not auto-decline order %s", row[0]);
            }
        }
    }
}

package base.l2.settlement.inbound.application;

import base.l0.system.security.SystemTenantContext;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Drains the provider inbox.
 *
 * <p>A sweep rather than dispatch inside the webhook request, and that is the point of having an
 * inbox at all: the endpoint's job is to verify, store and answer 200 quickly, because a provider
 * that does not get a prompt 2xx retries. Doing the ledger work inline would make delivery success
 * depend on posting success, and a slow posting into a redelivery.
 *
 * <p>The two-phase call is not decoration. {@code resolve} runs unelevated and answers which tenant
 * the event concerns; only then can {@code apply} run as that tenant. They cannot be one method
 * because Hibernate fixes a session's tenant when the session opens, so the elevation has to be in
 * place before the transaction that does the work begins — which means the loop lives out here,
 * where each call crosses a proxy boundary and starts its own.
 */
@ApplicationScoped
public class InboundEventScheduler {

    private static final Logger log = Logger.getLogger(InboundEventScheduler.class);

    /** Bounded so one sweep cannot monopolise a worker; a backlog drains over several passes. */
    private static final int BATCH = 100;

    @Inject
    DispatchProviderEventUC dispatch;

    @Inject
    SystemTenantContext tenants;

    @Scheduled(cron = "{payment.inbox.cron:0 * * * * ?}")
    void drain() {
        List<UUID> pending = dispatch.pending(BATCH);
        if (pending.isEmpty()) {
            return;
        }

        int dispatched = 0;
        for (UUID eventUuid : pending) {
            try {
                DispatchProviderEventUC.Target target = dispatch.resolve(eventUuid);
                if (target == null) {
                    continue;
                }
                if (tenants.runAs(target.tenantCode(), () -> dispatch.apply(target))) {
                    dispatched++;
                }
            } catch (RuntimeException e) {
                // One event's failure must not stop the sweep: the rest of the batch is unrelated,
                // and this row stays unprocessed for the next pass with its attempt count raised.
                log.errorf(e, "Dispatch failed for provider event %s", eventUuid);
            }
        }
        log.debugf("Inbox sweep: %d of %d dispatched", dispatched, pending.size());
    }
}


package base.l2.recon.application;

import base.l2.recon.domain.InvariantBreach;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Runs the invariants on a schedule, so a breach is found by us rather than by a partner.
 *
 * <p>Two cadences, because the two checks cost different amounts. The frequent sweep folds forward
 * from the last closed period and stays cheap however long the ledger runs. The full audit re-folds
 * everything ever written and grows with history, so it runs daily — often enough that a bug in the
 * cheap path is caught in hours, rarely enough that the cost does not matter.
 */
@ApplicationScoped
public class InvariantScheduler {

    private static final Logger log = Logger.getLogger(InvariantScheduler.class);

    @Inject
    LedgerInvariantChecker checker;

    @Scheduled(cron = "{ledger.invariants.cron:0 0/15 * * * ?}")
    void check() {
        List<InvariantBreach> breaches = checker.runAll();
        long critical = breaches.stream()
                .filter(b -> b.severity() == InvariantBreach.Severity.CRITICAL)
                .count();
        if (critical > 0) {
            log.errorf("Ledger invariant sweep found %d critical breach(es) of %d total",
                    critical, breaches.size());
        } else {
            log.debugf("Ledger invariant sweep clean (%d warnings)", breaches.size());
        }
    }

    /** The unshortcut version of the balance checks. Expensive, and the reason the cheap one is safe. */
    @Scheduled(cron = "{ledger.invariants.deep-cron:0 30 3 * * ?}")
    void deepCheck() {
        List<InvariantBreach> breaches = checker.runFullAudit();
        if (breaches.isEmpty()) {
            log.debug("Ledger full audit clean");
        } else {
            log.errorf("Ledger FULL AUDIT found %d breach(es) the incremental sweep did not",
                    breaches.size());
        }
    }
}


package base.l1.platform.provider.inbox;

import base.l0.system.persistance.DuplicateKeys;
import base.l1.platform.provider.ProviderEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Commits a verified provider event, exactly once per provider event id.
 *
 * <p>Redelivery is the normal case, not the exception: a provider retries on any non-2xx, including
 * one caused by our own timeout, so the same event arriving twice is expected. Rather than treat
 * that as a conflict, this returns what is already stored, and the endpoint answers 200 either way
 * — which is what stops a redelivery loop.
 *
 * <p>The lookup alone cannot do that. Two deliveries arriving together both read "not there" before
 * either writes, and the unique constraint on {@code (provider, provider_event_id)} rejects the
 * loser: measured at eight concurrent deliveries of one event, seven came back as duplicate-key
 * violations. Every one of those would have been a 500, and a provider that collects enough 500s
 * stops delivering. So the insert runs in its own transaction and a duplicate is treated as what it
 * is — the row is there now, read it and carry on.
 */
@ApplicationScoped
public class RecordProviderEventUC {

    public ProviderEventRecord record(ProviderEvent event) {
        if (event.providerEventId() == null || event.providerEventId().isBlank()) {
            // Without it there is no idempotency key, so a redelivery would post twice. An adapter
            // whose provider genuinely sends no event id must synthesise a stable one from the
            // payload rather than leave this null.
            throw new base.l1.platform.provider.WebhookVerificationException(
                    "Provider '" + event.provider() + "' sent an event with no id; redelivery could not be detected");
        }

        Optional<ProviderEventRecord> existing = find(event.provider(), event.providerEventId());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return store(event);
        } catch (RuntimeException e) {
            if (!DuplicateKeys.isDuplicate(e)) {
                throw e;
            }
            // The winner has committed by now — InnoDB holds a duplicate-key insert until the
            // conflicting transaction resolves — so this read cannot come back empty.
            return find(event.provider(), event.providerEventId()).orElseThrow(() -> e);
        }
    }

    /**
     * REQUIRES_NEW so a losing race is recoverable.
     *
     * <p>A constraint violation marks its transaction rollback-only, so the re-read above has to
     * happen outside the transaction that failed. Joining the caller's transaction would mean the
     * duplicate poisoned it and there would be nothing left to recover into.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    ProviderEventRecord store(ProviderEvent event) {
        ProviderEventRecord record = new ProviderEventRecord();
        record.setProvider(event.provider());
        record.setProviderEventId(event.providerEventId());
        record.setSignal(event.signal().name());
        record.setProviderRef(event.providerRef());
        record.setAmountMinor(event.reportedAmount() == null ? null : event.reportedAmount().amountMinor());
        record.setCurrency(event.reportedAmount() == null ? null : event.reportedAmount().currency());
        record.setOccurredAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        record.setReceivedAt(Instant.now());
        record.setRawPayload(event.rawPayload());
        record.persist();
        // Forced now so a duplicate surfaces here rather than at commit, where the caller has
        // already been told it succeeded.
        ProviderEventRecord.flush();
        return record;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<ProviderEventRecord> find(String provider, String providerEventId) {
        return ProviderEventRecord.<ProviderEventRecord>find(
                        "provider = ?1 and providerEventId = ?2", provider, providerEventId)
                .firstResultOptional();
    }
}


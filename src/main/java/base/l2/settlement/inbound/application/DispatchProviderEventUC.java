package base.l2.settlement.inbound.application;

import base.l0.system.money.Money;
import base.l1.platform.provider.ProviderEvent;
import base.l1.platform.reference.ReferenceResolver;
import base.l2.settlement.payout.application.ConfirmPayoutUC;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a verified provider notification into a posting.
 *
 * <p>Until this existed the webhook path terminated in a table: an event was authenticated,
 * normalised, stored — and nothing read it, so a bank confirming a payout left the money sitting in
 * {@code clearing:payout:in_flight} until an invariant complained that a lifecycle had never
 * finished. Which was true; nobody had finished it.
 *
 * <p>Two things decide the shape of this class, and neither is about payments.
 *
 * <p><b>It lives in L2, and it has to.</b> {@code ModuleBoundaryTest} fails the build if the
 * platform layer depends on the business layer, so a dispatcher sitting next to the registry could
 * not call a settlement use case. It is business logic reaching down into the platform's inbox, not
 * the platform reaching up.
 *
 * <p><b>The tenant is discovered, not supplied.</b> A webhook is authenticated by its signature and
 * carries no bearer token, so at the moment it arrives nobody knows whose it is — which is why
 * {@code provider_events.tenant_code} is nullable. The reference we minted is what answers it, and
 * it has to be looked up across every tenant because the question is precisely which one. Only then
 * can the work run, elevated to that tenant, through {@code SystemTenantContext}.
 *
 * <p>Deliberately narrow. Only {@code PAYOUT_CONFIRMED} is dispatched, because it is the only
 * signal whose target use case takes arguments an event actually carries. A capture needs the
 * split across counterparties, a refund needs shares, a returned payout needs to know which
 * counterparty to charge back — none of which is in a webhook. Those need the originating document
 * read back, and inventing a value for them would be worse than leaving the row unprocessed with a
 * note saying why.
 */
@ApplicationScoped
public class DispatchProviderEventUC {

    private static final Logger log = Logger.getLogger(DispatchProviderEventUC.class);

    @Inject
    ReferenceResolver references;

    @Inject
    ConfirmPayoutUC confirmPayout;

    @Inject
    jakarta.persistence.EntityManager em;

    /** Events waiting to be looked at, oldest first. */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<UUID> pending(int limit) {
        return em.createNativeQuery(
                        "SELECT uuid FROM provider_events WHERE processed_at IS NULL "
                                + "ORDER BY received_at LIMIT " + Math.max(0, limit))
                .getResultList();
    }

    /**
     * Finds out whose event this is, and records it when the answer is nobody's.
     *
     * <p>Runs before any elevation and in its own transaction, because it is the step that decides
     * which tenant the rest belongs to. Returns null when there is nothing to act on, having
     * written down why.
     *
     * <p>Every write here is a statement rather than a setter on a loaded entity. Two reasons, and
     * neither is style. The attempt counter is {@code dispatch_attempts + 1} evaluated by the
     * database, so two sweeps overlapping cannot both read the same value and store the same
     * increment. And an entity loaded before a tenant elevation is detached afterwards — the
     * mistake {@link #apply} exists in its current form because of — so this class does not keep
     * managed entities across a call boundary at all.
     */
    @Transactional
    public Target resolve(UUID eventUuid) {
        Object[] row = load(eventUuid);
        if (row == null) {
            return null;
        }
        String providerRef = (String) row[0];
        String signalName = (String) row[1];
        Number amountMinor = (Number) row[2];
        String currency = (String) row[3];

        bumpAttempts(eventUuid);

        if (providerRef == null || providerRef.isBlank()) {
            return note(eventUuid, "Event carries no reference, so there is no document to act on");
        }
        Optional<ReferenceResolver.ReferenceOwner> owner = references.resolveAcrossTenants(providerRef);
        if (owner.isEmpty()) {
            return note(eventUuid, "No reference we issued matches '" + providerRef
                    + "'; this event is about something we did not initiate");
        }

        // Now attributable, whether or not it turns out to be actionable. Recorded before the
        // remaining checks so an event we cannot act on is still traceable to a tenant, and so the
        // ck_provider_events_attributed constraint can hold: nothing may be marked processed
        // without this column being set.
        attribute(eventUuid, owner.get().tenantCode());

        ProviderEvent.Signal signal = signalOf(signalName);
        if (signal != ProviderEvent.Signal.PAYOUT_CONFIRMED) {
            return note(eventUuid, "No dispatch for " + signalName + " yet");
        }
        if (!"PAYOUT".equals(owner.get().documentType())) {
            return note(eventUuid, "Reference names a " + owner.get().documentType()
                    + ", but the event is a payout confirmation");
        }
        if (amountMinor == null || currency == null) {
            return note(eventUuid, "Payout confirmation carries no amount");
        }

        return new Target(eventUuid, owner.get().tenantCode(), signal, owner.get().documentId(),
                Money.of(amountMinor.longValue(), currency), providerRef);
    }

    /**
     * Applies the event, with the tenant already elevated by the caller.
     *
     * <p>Claims the row before doing the work, with a conditional update rather than by setting a
     * field. Two reasons, and the second one cost an afternoon.
     *
     * <p>It is a compare-and-set: {@code WHERE processed_at IS NULL} means two dispatchers racing
     * over one event have exactly one winner, decided by the database, and the loser does nothing.
     * Claiming first also means a failed posting rolls the claim back with it, so the event returns
     * to the queue rather than being marked done by a transaction that did not finish.
     *
     * <p>And it does not depend on dirty checking, which does not survive the elevation. Changing
     * the acting tenant changes what the tenant resolver answers, so Hibernate opens a session for
     * the new identifier and an entity loaded before that point is detached — its setters silently
     * do nothing, and {@code persist()} on it attempts an insert. An explicit statement has no
     * opinion about which session loaded what.
     */
    @Transactional
    public boolean apply(Target target) {
        int claimed = em.createNativeQuery(
                        "UPDATE provider_events SET processed_at = ?1, dispatch_note = NULL "
                                + "WHERE uuid = ?2 AND processed_at IS NULL")
                .setParameter(1, java.sql.Timestamp.from(Instant.now()))
                .setParameter(2, target.eventUuid())
                .executeUpdate();
        if (claimed == 0) {
            return false;
        }

        confirmPayout.confirm(target.documentId(), target.amount(), target.providerRef());
        log.infof("Dispatched %s for %s in tenant %s", target.signal(), target.documentId(),
                target.tenantCode());
        return true;
    }

    /** The fields a decision needs, and nothing that would tempt anyone to hold the entity. */
    private Object[] load(UUID eventUuid) {
        return (Object[]) em.createNativeQuery(
                        "SELECT provider_ref, event_signal, amount_minor, currency "
                                + "FROM provider_events WHERE uuid = ?1 AND processed_at IS NULL")
                .setParameter(1, eventUuid)
                .getResultStream().findFirst().orElse(null);
    }

    private void bumpAttempts(UUID eventUuid) {
        em.createNativeQuery("UPDATE provider_events SET dispatch_attempts = dispatch_attempts + 1 "
                        + "WHERE uuid = ?1")
                .setParameter(1, eventUuid)
                .executeUpdate();
    }

    private void attribute(UUID eventUuid, String tenantCode) {
        em.createNativeQuery("UPDATE provider_events SET tenant_code = ?2 WHERE uuid = ?1")
                .setParameter(1, eventUuid)
                .setParameter(2, tenantCode)
                .executeUpdate();
    }

    private Target note(UUID eventUuid, String why) {
        em.createNativeQuery("UPDATE provider_events SET dispatch_note = ?2 WHERE uuid = ?1")
                .setParameter(1, eventUuid)
                .setParameter(2, why)
                .executeUpdate();
        return null;
    }

    private static ProviderEvent.Signal signalOf(String signalName) {
        try {
            return ProviderEvent.Signal.valueOf(signalName);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ProviderEvent.Signal.UNKNOWN;
        }
    }

    /** Everything the elevated call needs, resolved before the elevation begins. */
    public record Target(UUID eventUuid, String tenantCode, ProviderEvent.Signal signal,
                         UUID documentId, Money amount, String providerRef) {
    }
}


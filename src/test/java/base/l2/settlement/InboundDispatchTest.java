package base.l2.settlement;

import base.l0.system.money.Money;
import base.l0.system.security.SystemTenantContext;
import base.l1.platform.provider.ProviderEvent;
import base.l1.platform.provider.inbox.ProviderEventRecord;
import base.l1.platform.provider.inbox.RecordProviderEventUC;
import base.l1.platform.reference.IssuedReference;
import base.l1.platform.reference.ReferenceIssuer;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.settlement.inbound.application.DispatchProviderEventUC;
import base.l2.settlement.payout.application.InstructPayoutUC;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A provider notification reaching the ledger.
 *
 * <p>This is the seam the service was missing: everything on the way in was built and verified and
 * then stopped at a table. What it exercises is not really the payout — that has its own tests — but
 * the two steps between a signed webhook and a posting, which is finding out whose event it is and
 * doing the work as them.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class InboundDispatchTest {

    @Inject
    InstructPayoutUC instructPayout;

    @Inject
    ReferenceIssuer references;

    @Inject
    RecordProviderEventUC inbox;

    @Inject
    DispatchProviderEventUC dispatch;

    @Inject
    SystemTenantContext tenants;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void aConfirmationFromTheProviderClosesThePayout() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        Money amount = Money.of(7_500, "EUR");
        fundSeller(sellerId, 7_500);

        instructPayout.instruct(payoutId, sellerId, amount, null);
        IssuedReference reference = references.forDocument("PAYOUT", payoutId);
        assertNotNull(reference, "the payout mints the reference that comes back on the webhook");

        long inFlightBefore = fixture.balanceOf(fixture.inFlight("EUR").getUuid());
        ProviderEventRecord stored = inbox.record(event(reference.getReference(), amount,
                ProviderEvent.Signal.PAYOUT_CONFIRMED));
        assertNull(stored.getTenantCode(), "arriving, it belongs to nobody in particular");

        // The two phases the scheduler performs: find out whose it is, then act as them.
        DispatchProviderEventUC.Target target = dispatch.resolve(stored.getUuid());
        assertNotNull(target, "the reference should identify the payout");
        assertEquals("acme", target.tenantCode());
        assertTrue(tenants.runAs(target.tenantCode(), () -> dispatch.apply(target)));

        // in_flight is credit-normal, so instructing pushed it negative and confirming debits it
        // back toward zero. "Leaves in-flight" is an increase in a debit-positive ledger.
        assertEquals(inFlightBefore + 7_500, fixture.balanceOf(fixture.inFlight("EUR").getUuid()),
                "the money leaves in-flight, which is the whole point of the confirmation");
        Row after = reload(stored.getUuid());
        assertNotNull(after.getProcessedAt());
        assertEquals("acme", after.getTenantCode(), "and the event is attributable afterwards");
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
    }

    @Test
    void anEventAboutSomethingWeDidNotInitiateIsKeptAndExplained() {
        ProviderEventRecord stored = inbox.record(event("P0786ZZZZZZ9", Money.of(100, "EUR"),
                ProviderEvent.Signal.PAYOUT_CONFIRMED));

        assertNull(dispatch.resolve(stored.getUuid()));

        Row after = reload(stored.getUuid());
        assertNull(after.getProcessedAt(), "unprocessed, because there is nothing to process");
        assertTrue(after.getDispatchNote().contains("did not initiate"),
                "and the queue says why rather than looking like a backlog: " + after.getDispatchNote());
        assertEquals(1, after.getDispatchAttempts());
    }

    @Test
    void aSignalWithNoDispatchYetIsRecordedRatherThanGuessedAt() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        fundSeller(sellerId, 2_000);
        instructPayout.instruct(payoutId, sellerId, Money.of(2_000, "EUR"), null);
        String reference = references.forDocument("PAYOUT", payoutId).getReference();

        ProviderEventRecord stored = inbox.record(event(reference, Money.of(2_000, "EUR"),
                ProviderEvent.Signal.PAYOUT_RETURNED));

        // A return needs to know which counterparty to charge back, and a webhook does not say.
        assertNull(dispatch.resolve(stored.getUuid()));
        Row after = reload(stored.getUuid());
        assertNull(after.getProcessedAt());
        assertTrue(after.getDispatchNote().contains("PAYOUT_RETURNED"), after.getDispatchNote());
        assertEquals("acme", after.getTenantCode(),
                "attributable even when not actionable — the reference resolved fine");
    }

    @Test
    void dispatchingTwiceDoesNotPostTwice() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        Money amount = Money.of(1_100, "EUR");
        fundSeller(sellerId, 1_100);
        instructPayout.instruct(payoutId, sellerId, amount, null);
        String reference = references.forDocument("PAYOUT", payoutId).getReference();

        ProviderEventRecord stored = inbox.record(event(reference, amount,
                ProviderEvent.Signal.PAYOUT_CONFIRMED));
        DispatchProviderEventUC.Target target = dispatch.resolve(stored.getUuid());
        assertTrue(tenants.runAs(target.tenantCode(), () -> dispatch.apply(target)));

        long inFlight = fixture.balanceOf(fixture.inFlight("EUR").getUuid());
        assertFalse(tenants.runAs(target.tenantCode(), () -> dispatch.apply(target)),
                "already processed, so the second pass is a no-op rather than a second posting");
        assertEquals(inFlight, fixture.balanceOf(fixture.inFlight("EUR").getUuid()));
    }

    @Test
    void elevationChangesWhatTheCallerContextReports() {
        // The mechanism itself, asserted directly: without this the dispatcher would post into
        // whichever tenant the ambient context named, which for a webhook is the configured
        // default rather than the one the reference resolved to.
        String outside = caller.tenantCode();
        String inside = tenants.runAs("someone-else", () -> caller.tenantCode());
        assertEquals("acme", outside);
        assertEquals("someone-else", inside);
        assertEquals("acme", caller.tenantCode(), "and it is put back afterwards");
    }

    @Inject
    base.l0.system.security.CallerContext caller;

    @Test
    void elevationRefusesToNest() {
        assertThrows(IllegalStateException.class,
                () -> tenants.runAs("acme", () -> tenants.runAs("other", () -> "nope")));
    }

    // ---- helpers ----------------------------------------------------------------------

    private static ProviderEvent event(String reference, Money amount, ProviderEvent.Signal signal) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        return new ProviderEvent("localstripe", eventId, reference, signal, amount, Instant.now(),
                "{\"id\":\"" + eventId + "\"}");
    }

    /** Gives the counterparty a credit balance so a payout can be instructed against it. */
    private void fundSeller(UUID sellerId, long minor) {
        fixture.psp("EUR");
        capture(sellerId, minor);
    }

    private void capture(UUID sellerId, long minor) {
        capturePayment.capture(new base.l2.settlement.api.CaptureRequest(
                UUID.randomUUID(), "ORD-IN-" + minor, UUID.randomUUID(), "mock", "ch_in",
                Money.of(minor, "EUR"), null,
                java.util.List.of(new base.l2.settlement.api.CaptureRequest.SellerSplit(
                        sellerId, Money.of(minor, "EUR"), Money.zero("EUR"))),
                "capture:" + UUID.randomUUID()));
    }

    @Inject
    base.l2.settlement.intent.application.CapturePaymentUC capturePayment;

    /**
     * Reads the row itself, not a possibly-cached entity.
     *
     * <p>A {@code @QuarkusTest} keeps one request context for the whole method, so a persistence
     * context can outlive the transaction that wrote the row and hand back the copy it already had.
     * Clearing first and reading natively removes the question from the assertion entirely.
     */
    @Transactional
    Row reload(UUID uuid) {
        em.clear();
        Object[] r = (Object[]) em.createNativeQuery(
                        "SELECT processed_at, tenant_code, dispatch_note, dispatch_attempts "
                                + "FROM provider_events WHERE uuid = ?1")
                .setParameter(1, uuid)
                .getSingleResult();
        return new Row(r[0], (String) r[1], (String) r[2], ((Number) r[3]).intValue());
    }

    record Row(Object processedAt, String tenantCode, String dispatchNote, int dispatchAttempts) {
        Object getProcessedAt() {
            return processedAt;
        }

        String getTenantCode() {
            return tenantCode;
        }

        String getDispatchNote() {
            return dispatchNote;
        }

        int getDispatchAttempts() {
            return dispatchAttempts;
        }
    }

    @Inject
    jakarta.persistence.EntityManager em;
}


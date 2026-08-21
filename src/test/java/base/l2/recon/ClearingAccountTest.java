package base.l2.recon;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.recon.application.LedgerInvariantChecker;
import base.l2.recon.domain.InvariantBreach;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The query half of the clearing-account model.
 *
 * <p>The accounts existed from V0.001 — money instructed and unconfirmed sits in
 * {@code clearing:payout:in_flight}, money arrived and unexplained in
 * {@code clearing:suspense:unmatched} — but nothing read them. The check that was meant to
 * (`reconcileGroupsNetToZero`) summed every line of every entry in a group, and every entry balances
 * per currency by the posting procedure's own rule, so its total was structurally zero and it could
 * never fire. These tests exist mostly to prove the replacement <em>can</em>.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ClearingAccountTest {

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerInvariantChecker checker;

    @Inject
    LedgerTestFixture fixture;

    private static final String CURRENCY = "VND";

    /** Instruction leg: the partner is debited and the money parked in flight. */
    private void instruct(String group, UUID sellerId, long minor, Instant when) {
        LedgerAccount seller = fixture.seller(sellerId, CURRENCY, 5_000_000);
        LedgerAccount inFlight = fixture.inFlight(CURRENCY);
        postEntry.post(PostingRequest.of("PAYOUT_INSTRUCTED", "clr-instruct:" + group)
                .reconcileGroup(group)
                .effectiveAt(when)
                .line(PostingLine.debit(fixture.newId(), seller.getUuid(), Money.of(minor, CURRENCY)))
                .line(PostingLine.credit(fixture.newId(), inFlight.getUuid(), Money.of(minor, CURRENCY)))
                .build());
    }

    /** Confirmation leg: the money leaves the clearing account for the bank. */
    private void confirm(String group, long minor, Instant when) {
        LedgerAccount inFlight = fixture.inFlight(CURRENCY);
        LedgerAccount bank = fixture.bank(CURRENCY);
        postEntry.post(PostingRequest.of("PAYOUT_CONFIRMED", "clr-confirm:" + group)
                .reconcileGroup(group)
                .effectiveAt(when)
                .line(PostingLine.debit(fixture.newId(), inFlight.getUuid(), Money.of(minor, CURRENCY)))
                .line(PostingLine.credit(fixture.newId(), bank.getUuid(), Money.of(minor, CURRENCY)))
                .build());
    }

    private List<InvariantBreach> breachesFor(String subject) {
        return checker.runAll().stream().filter(b -> subject.equals(b.subject())).toList();
    }

    @Test
    void aPayoutInstructedAndNeverConfirmedNamesItself() {
        String group = "clearing-test:" + UUID.randomUUID();
        instruct(group, UUID.randomUUID(), 200_000, Instant.now().minus(5, ChronoUnit.DAYS));

        List<InvariantBreach> breaches = breachesFor(group);

        assertEquals(1, breaches.size(), "an unfinished lifecycle past tolerance must be reported");
        assertEquals("CLEARING_LIFECYCLE_SETTLES", breaches.getFirst().invariant());
        assertEquals(-200_000L, breaches.getFirst().magnitudeMinor(),
                "the amount still parked, credit-signed as it sits on the clearing account");
        assertTrue(breaches.getFirst().detail().contains("days"));
    }

    @Test
    void aCompletedLifecycleIsSilent() {
        String group = "clearing-test:" + UUID.randomUUID();
        Instant when = Instant.now().minus(5, ChronoUnit.DAYS);

        instruct(group, UUID.randomUUID(), 200_000, when);
        confirm(group, 200_000, when.plus(1, ChronoUnit.HOURS));

        assertTrue(breachesFor(group).isEmpty(), "the clearing account was left at zero for this group");
    }

    @Test
    void aLifecycleStillWithinToleranceIsSilent() {
        // In flight is the normal state of a payout for a day or two; only staying there is a fault.
        String group = "clearing-test:" + UUID.randomUUID();
        instruct(group, UUID.randomUUID(), 200_000, Instant.now());

        assertTrue(breachesFor(group).isEmpty(), "a payout instructed today is not a problem");
    }

    @Test
    void aLifecycleTouchingNoClearingAccountIsOutOfScope() {
        // Capture: PSP to partner. Neither is a clearing account, so an unbalanced-looking group here
        // is not this check's business.
        String group = "clearing-test:" + UUID.randomUUID();
        LedgerAccount psp = fixture.psp(CURRENCY);
        LedgerAccount seller = fixture.seller(UUID.randomUUID(), CURRENCY, 0);
        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", "clr-capture:" + group)
                .reconcileGroup(group)
                .effectiveAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(100_000, CURRENCY)))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(100_000, CURRENCY)))
                .build());

        assertTrue(breachesFor(group).isEmpty());
    }

    @Test
    void moneyParkedWithNoReconcileGroupIsReportedAgainstItsAccount() {
        // The group check can only see lifecycles that named themselves. This is the gap behind it.
        LedgerAccount suspense = fixture.suspense(CURRENCY);
        LedgerAccount bank = fixture.bank(CURRENCY);
        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "clr-untracked:" + UUID.randomUUID())
                .effectiveAt(Instant.now().minus(9, ChronoUnit.DAYS))
                .line(PostingLine.debit(fixture.newId(), bank.getUuid(), Money.of(70_000, CURRENCY)))
                .line(PostingLine.credit(fixture.newId(), suspense.getUuid(), Money.of(70_000, CURRENCY)))
                .build());

        List<InvariantBreach> breaches = breachesFor(suspense.getCode()).stream()
                .filter(b -> "CLEARING_ITEM_IS_TRACKED".equals(b.invariant())).toList();

        assertEquals(1, breaches.size());
        assertTrue(breaches.getFirst().detail().contains("no reconcile group"));
    }

    @Test
    void theClearingFlagIsOnTheRowNotInTheAccountCode() {
        // "Should trend to zero" used to be knowable only by reading AccountCode, so a new clearing
        // account stayed invisible to the checks until someone widened a LIKE pattern.
        assertTrue(fixture.inFlight(CURRENCY).isClearing());
        assertTrue(fixture.suspense(CURRENCY).isClearing());
        assertEquals((short) 2, fixture.inFlight(CURRENCY).getClearingToleranceDays());

        // A position we hold is not money in transit, however intermediate it looks.
        assertFalse(fixture.psp(CURRENCY).isClearing());
        assertFalse(fixture.bank(CURRENCY).isClearing());
    }
}


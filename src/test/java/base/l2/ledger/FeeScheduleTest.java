package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.fee.application.FeeScheduleQuery;
import base.l2.ledger.fee.application.PublishFeeScheduleUC;
import base.l2.ledger.fee.domain.FeeSchedule;
import base.l2.ledger.fee.domain.FeeScheduleException;
import base.l2.settlement.api.CaptureRequest;
import base.l2.settlement.intent.application.CapturePaymentUC;
import base.l2.settlement.intent.domain.CaptureRejectedException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rates as facts about a period, and the entry that records which one applied.
 *
 * <p>Every test publishes under a code of its own. Schedule windows are per code and abut exactly,
 * so two tests sharing one would be editing each other's history.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class FeeScheduleTest {

    @Inject
    PublishFeeScheduleUC publish;

    @Inject
    FeeScheduleQuery schedules;

    @Inject
    CapturePaymentUC capture;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    private static String uniqueCode() {
        return "sched-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void publishingANewRateClosesTheOldOneRatherThanEditingIt() {
        String code = uniqueCode();
        Instant january = Instant.parse("2026-01-01T00:00:00Z");
        Instant july = Instant.parse("2026-07-01T00:00:00Z");

        FeeSchedule first = publish.publish(code, january, 250, null, "launch rate");
        FeeSchedule second = publish.publish(code, july, 300, null, "raised");

        assertEquals(1, first.getVersion());
        assertEquals(2, second.getVersion());

        // The windows abut exactly: nothing falls between them, and nothing is in both.
        assertEquals(250, schedules.require(code, july.minus(1, ChronoUnit.SECONDS)).getBasisPoints(),
                "a capture a second before the change is priced at the old rate");
        assertEquals(300, schedules.require(code, july).getBasisPoints(),
                "and one at the instant of change at the new rate");
        assertEquals(250, schedules.require(code, january).getBasisPoints(),
                "raising a rate must not restate what was charged in January");
    }

    @Test
    void aVersionIsImmutableOnceItHasPricedSomething() {
        String code = uniqueCode();
        FeeSchedule published = publish.publish(code, Instant.parse("2026-02-01T00:00:00Z"), 199, null, null);

        // The guard is a trigger: this is a historical fact, not a setting.
        assertThrows(RuntimeException.class, () -> rewriteRate(published.getUuid(), 99));
        assertEquals(199, schedules.require(code, Instant.parse("2026-03-01T00:00:00Z")).getBasisPoints());
    }

    @Test
    void aRateWithNoVersionInForceIsRefusedRatherThanGuessed() {
        String code = uniqueCode();
        publish.publish(code, Instant.parse("2026-05-01T00:00:00Z"), 250, null, null);

        // Before the first version existed. Falling back to "the nearest" would put a number on an
        // entry that nothing can reproduce.
        assertThrows(FeeScheduleException.class,
                () -> schedules.require(code, Instant.parse("2026-04-30T23:59:59Z")));
    }

    @Test
    void theRateRoundsHalfUpRatherThanAlwaysDownwards() {
        String code = uniqueCode();
        FeeSchedule schedule = publish.publish(code, Instant.parse("2026-01-01T00:00:00Z"), 250, null, null);

        // 2.5% of 4,510 is 112.75 -> 113. Truncation would give 112 every time, and a systematic
        // bias of most of a minor unit per order is what reconciliation arguments are made of.
        assertEquals(Money.of(113, "EUR"), schedule.feeOn(Money.of(4_510, "EUR")));
        assertEquals(Money.of(112, "EUR"), schedule.feeOn(Money.of(4_490, "EUR")));
    }

    @Test
    void aLargeZeroDecimalAmountDoesNotOverflow() {
        String code = uniqueCode();
        FeeSchedule schedule = publish.publish(code, Instant.parse("2026-01-01T00:00:00Z"), 250, null, null);

        // 500 billion VND. gross x basisPoints passes 10^14 here, which is fine, but the same
        // shape at trillions overflows a long before the divide — hence BigInteger.
        assertEquals(Money.of(12_500_000_000L, "VND"), schedule.feeOn(Money.of(500_000_000_000L, "VND")));
    }

    @Test
    void aCaptureQuotingAScheduleHasItsNumberRecomputed() {
        String code = uniqueCode();
        UUID partyId = UUID.randomUUID();
        publish.publish(code, Instant.now().minusSeconds(3_600), 250, null, null);
        fixture.psp("EUR");
        fixture.seller(partyId, "EUR", 0);

        UUID orderId = UUID.randomUUID();
        capture.capture(new CaptureRequest(
                orderId, "ORD-F1", UUID.randomUUID(), "mock", "ch_f1",
                Money.of(10_000, "EUR"), null,
                List.of(new CaptureRequest.SellerSplit(partyId, Money.of(10_000, "EUR"),
                        Money.of(250, "EUR"), code)),
                "capture:" + UUID.randomUUID()));

        String metadata = metadataOf(orderId);
        assertTrue(metadata.contains(code + " v1"),
                "the entry must name the version that priced it, not just the amount: " + metadata);
    }

    @Test
    void aCaptureWhoseNumberDisagreesWithItsScheduleIsRefused() {
        String code = uniqueCode();
        UUID partyId = UUID.randomUUID();
        publish.publish(code, Instant.now().minusSeconds(3_600), 250, null, null);
        fixture.psp("EUR");
        fixture.seller(partyId, "EUR", 0);

        // Quotes 2.5% and charges 8.5%. This is the case the whole feature exists to catch.
        assertThrows(CaptureRejectedException.class, () -> capture.capture(new CaptureRequest(
                UUID.randomUUID(), "ORD-F2", UUID.randomUUID(), "mock", "ch_f2",
                Money.of(10_000, "EUR"), null,
                List.of(new CaptureRequest.SellerSplit(partyId, Money.of(10_000, "EUR"),
                        Money.of(850, "EUR"), code)),
                "capture:" + UUID.randomUUID())));
    }

    @Test
    void aCaptureQuotingNoScheduleIsStillAccepted() {
        UUID partyId = UUID.randomUUID();
        fixture.psp("EUR");
        fixture.seller(partyId, "EUR", 0);

        // The migration path: the ERP adopts schedules when it is ready, and until then this
        // service behaves exactly as it did.
        capture.capture(new CaptureRequest(
                UUID.randomUUID(), "ORD-F3", UUID.randomUUID(), "mock", "ch_f3",
                Money.of(1_000, "EUR"), null,
                List.of(new CaptureRequest.SellerSplit(partyId, Money.of(1_000, "EUR"), Money.of(37, "EUR"))),
                "capture:" + UUID.randomUUID()));

        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
    }

    @Test
    void noVersionsOverlap() {
        String code = uniqueCode();
        publish.publish(code, Instant.parse("2026-01-01T00:00:00Z"), 250, null, null);
        publish.publish(code, Instant.parse("2026-06-01T00:00:00Z"), 300, null, null);
        publish.publish(code, Instant.parse("2026-09-01T00:00:00Z"), 275, null, null);

        assertEquals(0L, overlapCount(code),
                "overlapping windows would make 'the rate at time T' ambiguous");
    }

    @Test
    void aVersionDatedBeforeAnExistingOneIsRefused() {
        String code = uniqueCode();
        publish.publish(code, Instant.parse("2030-01-01T00:00:00Z"), 100, null, "January");
        publish.publish(code, Instant.parse("2030-03-01T00:00:00Z"), 300, null, "March");

        // Dated before the earliest version, so no window contains it and publish() would have
        // nothing to close — leaving an open-ended version overlapping both of the others, with
        // two rows claiming the rate in force at any instant from March onward.
        assertThrows(FeeScheduleException.class, () -> publish.publish(
                code, Instant.parse("2029-06-01T00:00:00Z"), 200, null, "backdated"));

        // And between two existing versions, which used to fail with a message about immutability
        // that described the mechanism rather than the mistake.
        assertThrows(FeeScheduleException.class, () -> publish.publish(
                code, Instant.parse("2030-02-01T00:00:00Z"), 200, null, "out of order"));

        assertEquals(0L, overlapCount(code), "no instant may be claimed by two versions");
        assertEquals(300, schedules.require(code, Instant.parse("2030-04-01T00:00:00Z")).getBasisPoints());
    }

    // ---- helpers ----------------------------------------------------------------------

    @Transactional
    void rewriteRate(UUID uuid, int basisPoints) {
        em.createNativeQuery("UPDATE fee_schedules SET basis_points = ?1 WHERE uuid = ?2")
                .setParameter(1, basisPoints)
                .setParameter(2, uuid)
                .executeUpdate();
        em.flush();
    }

    @Transactional
    long overlapCount(String code) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COUNT(*) FROM v_fee_schedule_overlaps WHERE code = ?1")
                .setParameter(1, code)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    String metadataOf(UUID orderId) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(metadata, '') FROM journal_entries WHERE document_id = ?1")
                .setParameter(1, orderId)
                .getSingleResult();
        return String.valueOf(value);
    }
}


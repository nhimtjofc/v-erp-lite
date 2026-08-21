package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.period.application.ClosePeriodUC;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Folding forward from a close instead of from the beginning of time.
 *
 * <p>Two pieces of isolation, both forced by what a close actually is. It applies to a whole tenant
 * and it is cumulative — the guard asks whether <em>any</em> unreopened period covers an entry's
 * date — so closing anything here would otherwise seal the window every other test class posts
 * into. Hence a tenant of this class's own.
 *
 * <p>And within the class the months have to advance, because the same rule applies between these
 * tests: once one of them closes March, no later-running test can post into January. Hence the
 * explicit ordering, which makes each test's window begin after every earlier test's close instead
 * of leaving it to whichever order JUnit happens to pick.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "closebooks")})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PeriodClosingBalanceTest {

    @Inject
    ClosePeriodUC periods;

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    /**
     * The degenerate case, and it has to run first: once anything is closed this tenant can never
     * return to having no watermark.
     */
    @Test
    @Order(1)
    void withNoCloseTheIncrementalViewIsTheFullView() {
        String name = unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 320, day(2019, 1, 5));

        // A NULL watermark means every line counts as "after" it, so the incremental view is the
        // full view. This is what makes it correct on a database nobody has ever closed a period on.
        assertEquals(0L, watermarkCount(), "no close should exist yet");
        assertNoDisagreement();
    }

    @Test
    @Order(2)
    void aClosingBalanceIsTheFoldOfEverythingDatedByThen() {
        String name = unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");
        Instant close = day(2019, 2, 28);

        post(subject, other, 4_000, day(2019, 2, 10));
        post(subject, other, 1_500, day(2019, 2, 20));

        periods.close(close, "February 2019");

        assertEquals(5_500L, closingBalance(subject.getUuid()));
        assertEquals(-5_500L, closingBalance(other.getUuid()));
        // Not just the right number, the right rows: a balance that happened to match while folding
        // the wrong set would pass on the amount alone.
        assertEquals(2L, closingLineCount(subject.getUuid()));
        assertNoDisagreement();
    }

    @Test
    @Order(3)
    void anAccountThatNeverMovedStillGetsARow() {
        LedgerAccount idle = fixture.bank(unique() + "idle", "EUR");
        Instant close = day(2019, 3, 31);

        periods.close(close, "March 2019");

        // Zero is the right answer here by luck. It is the wrong answer for any account that had a
        // balance and did not move during the period, which is why every account gets a row rather
        // than only the ones with activity.
        assertEquals(0L, closingBalance(idle.getUuid()));
        assertEquals(1L, rowCountFor(idle.getUuid()));
        assertNoDisagreement();
    }

    @Test
    @Order(4)
    void theIncrementalAuditAgreesWithTheFullOne() {
        String name = unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 9_000, day(2019, 4, 5));
        periods.close(day(2019, 4, 30), "April 2019");
        post(subject, other, 2_000, day(2019, 5, 7));
        post(subject, other, 700, day(2019, 5, 20));

        // The whole point of the change: the shortcut has to produce what the long way produces.
        assertNoDisagreement();
        assertEquals(11_700L, derivedIncremental(subject.getUuid()));
        assertEquals(0L, driftIncremental(subject.getUuid()));
    }

    @Test
    @Order(5)
    void reopeningFallsBackToTheEarlierWatermark() {
        String name = unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 1_000, day(2019, 6, 5));
        periods.close(day(2019, 6, 30), "June 2019");
        post(subject, other, 2_000, day(2019, 7, 5));
        Instant july = day(2019, 7, 31);
        periods.close(july, "July 2019");

        periods.reopen(july, "A fee the bank confirmed only in August");

        // The watermark is June again, so July is folded from lines rather than read from a
        // closing balance. The answer must not move.
        assertNoDisagreement();
        assertEquals(3_000L, derivedIncremental(subject.getUuid()));
    }

    @Test
    @Order(6)
    void reClosingRecomputesWhatTheFirstCloseRecorded() {
        String name = unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");
        Instant close = day(2019, 8, 31);

        post(subject, other, 5_000, day(2019, 8, 10));
        periods.close(close, "August 2019");
        assertEquals(5_000L, closingBalance(subject.getUuid()));

        periods.reopen(close, "Missed a transfer dated 12 August");
        post(subject, other, 250, day(2019, 8, 12));
        periods.close(close, "August 2019, corrected");

        assertEquals(5_250L, closingBalance(subject.getUuid()),
                "the correction has to be inside the number the next period folds forward from");
        assertEquals(1L, rowCountFor(subject.getUuid()), "one answer per account per period");
        assertNoDisagreement();
    }

    @Test
    @Order(7)
    void closingIsRefusedWhileAnAccountDrifts() {
        String name = unique();
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");
        post(subject, other, 100, day(2019, 9, 5));

        forceDrift(subject.getUuid());
        try {
            assertTrue(threw(() -> periods.close(day(2019, 9, 30), "September 2019")),
                    "a period sealed over a drifting account would preserve the error, not the figure");
        } finally {
            undoDrift(subject.getUuid());
        }
        assertNoDisagreement();
    }

    // ---- helpers ----------------------------------------------------------------------

    private static boolean threw(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static String unique() {
        return "pcb" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Instant day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }

    private void post(LedgerAccount into, LedgerAccount from, long minor, Instant effectiveAt) {
        Money amount = Money.of(minor, "EUR");
        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "pcb:" + UUID.randomUUID())
                .effectiveAt(effectiveAt)
                .line(PostingLine.debit(fixture.newId(), into.getUuid(), amount))
                .line(PostingLine.credit(fixture.newId(), from.getUuid(), amount))
                .build());
    }

    /** Any account of this tenant where the shortcut and the long way disagree. */
    @Transactional
    void assertNoDisagreement() {
        em.clear();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT f.code, f.derived_balance_minor, i.derived_balance_minor, "
                                + "       f.drift_minor, i.drift_minor "
                                + "  FROM v_account_balance_audit f "
                                + "  JOIN v_account_balance_audit_incremental i "
                                + "    ON i.account_uuid = f.account_uuid "
                                + " WHERE f.tenant_code = 'closebooks' "
                                + "   AND (f.derived_balance_minor <> i.derived_balance_minor "
                                + "     OR f.drift_minor <> i.drift_minor)")
                .getResultList();
        assertTrue(rows.isEmpty(), () -> "incremental and full audit disagree on " + rows.size()
                + " account(s): " + rows.stream().map(r -> r[0] + " full=" + r[1] + "/" + r[3]
                        + " incremental=" + r[2] + "/" + r[4]).toList());

        assertEquals(tenantNet("v_trial_balance"), tenantNet("v_trial_balance_incremental"),
                "the two trial balances must agree too");
    }

    private long tenantNet(String view) {
        Object value = em.createNativeQuery("SELECT COALESCE(SUM(net_minor), 0) FROM " + view
                        + " WHERE tenant_code = 'closebooks'")
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    long watermarkCount() {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COUNT(*) FROM v_ledger_watermark WHERE tenant_code = 'closebooks'")
                .getSingleResult();
        return ((Number) value).longValue();
    }

    // Read by "this account's most recent closing balance" rather than by a bound timestamp, for
    // the same reason the insert copies the column: a hand-bound Instant does not compare equal to
    // what Hibernate wrote. Every assertion here is about the newest close at that point anyway.

    @Transactional
    long closingBalance(UUID accountUuid) {
        return scalar("SELECT balance_minor FROM period_closing_balances WHERE account_uuid = ?1 "
                + "ORDER BY closed_through DESC LIMIT 1", accountUuid);
    }

    @Transactional
    long closingLineCount(UUID accountUuid) {
        return scalar("SELECT line_count FROM period_closing_balances WHERE account_uuid = ?1 "
                + "ORDER BY closed_through DESC LIMIT 1", accountUuid);
    }

    @Transactional
    long rowCountFor(UUID accountUuid) {
        return scalar("SELECT COUNT(*) FROM period_closing_balances WHERE account_uuid = ?1 "
                + "AND closed_through = (SELECT MAX(closed_through) FROM period_closing_balances "
                + "                       WHERE account_uuid = ?1)", accountUuid);
    }

    @Transactional
    long derivedIncremental(UUID accountUuid) {
        return scalar("SELECT derived_balance_minor FROM v_account_balance_audit_incremental "
                + "WHERE account_uuid = ?1", accountUuid);
    }

    @Transactional
    long driftIncremental(UUID accountUuid) {
        return scalar("SELECT drift_minor FROM v_account_balance_audit_incremental "
                + "WHERE account_uuid = ?1", accountUuid);
    }

    private long scalar(String sql, UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery(sql)
                .setParameter(1, accountUuid)
                .getResultStream().findFirst().orElse(0L);
        return ((Number) value).longValue();
    }

    /**
     * Moves a stored balance without posting, which is normally exactly what not to do in a test.
     *
     * <p>The difference from a convenience shortcut is that drift is the subject here rather than
     * the setup. The write path cannot produce a drifting account — that is the guarantee — so a
     * test of how closing responds to one has no honest route to the state. Reaching around the
     * guard is the only way in, and it is undone immediately afterwards.
     */
    @Transactional
    void forceDrift(UUID accountUuid) {
        adjust(accountUuid, 1);
    }

    @Transactional
    void undoDrift(UUID accountUuid) {
        adjust(accountUuid, -1);
    }

    private void adjust(UUID accountUuid, int delta) {
        em.createNativeQuery("SET @ledger_posting = 1").executeUpdate();
        em.createNativeQuery("UPDATE ledger_accounts SET balance_minor = balance_minor + ?2 WHERE uuid = ?1")
                .setParameter(1, accountUuid).setParameter(2, delta).executeUpdate();
        em.createNativeQuery("SET @ledger_posting = 0").executeUpdate();
    }
}


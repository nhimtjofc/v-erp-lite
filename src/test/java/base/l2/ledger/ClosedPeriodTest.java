package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.period.application.ClosePeriodUC;
import base.l2.ledger.period.domain.PeriodRejectedException;
import base.l2.ledger.reporting.application.StatementQuery;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sealing a period against later backdating.
 *
 * <p>Each test closes through its own instant. Closures are per tenant and cumulative — the guard
 * asks whether <em>any</em> unreopened period covers the entry's date — so a test that sealed a
 * recent instant would close the door on every other test in the suite. They all work in a distant
 * past window instead.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ClosedPeriodTest {

    @Inject
    ClosePeriodUC periods;

    @Inject
    PostEntryUC postEntry;

    @Inject
    StatementQuery statements;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void aBackdatedEntryIntoAClosedPeriodIsRefused() {
        String name = "cp" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount a = fixture.bank(name, "EUR");
        LedgerAccount b = fixture.bank(name + "b", "EUR");
        Instant close = day(2021, 1, 31);

        post(a, b, 1_000, day(2021, 1, 10));
        long januaryClose = statements.balanceAsOf(a.getUuid(), LocalDate.of(2021, 1, 31));
        assertEquals(1_000L, januaryClose);

        periods.close(close, "January 2021");

        // The exact move V0.009 made correct, now made deliberate: dating a correction into a
        // month somebody has already reported.
        RuntimeException refused = assertThrows(RuntimeException.class,
                () -> post(a, b, 500, day(2021, 1, 20)));
        assertTrue(rootMessage(refused).contains("closed period"), rootMessage(refused));

        assertEquals(januaryClose, statements.balanceAsOf(a.getUuid(), LocalDate.of(2021, 1, 31)),
                "the reported figure must still be the reported figure");
    }

    @Test
    void anEntryAfterTheCloseIsUnaffected() {
        String name = "cp" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount a = fixture.bank(name, "EUR");
        LedgerAccount b = fixture.bank(name + "b", "EUR");

        periods.close(day(2021, 2, 28), "February 2021");

        post(a, b, 700, day(2021, 3, 5));
        assertEquals(700L, statements.balanceAsOf(a.getUuid(), LocalDate.of(2021, 3, 31)),
                "closing a period seals the past, not the future");
    }

    @Test
    void reopeningNeedsAReasonAndLeavesEvidence() {
        String name = "cp" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount a = fixture.bank(name, "EUR");
        LedgerAccount b = fixture.bank(name + "b", "EUR");
        Instant close = day(2021, 4, 30);

        periods.close(close, "April 2021");
        assertThrows(PeriodRejectedException.class, () -> periods.reopen(close, "  "));

        periods.reopen(close, "Bank confirmed a fee dated 12 April that we never received");
        post(a, b, 250, day(2021, 4, 12));

        assertEquals(250L, statements.balanceAsOf(a.getUuid(), LocalDate.of(2021, 4, 30)),
                "the correction lands in the month it belongs to, once someone has said why");
    }

    @Test
    void aPeriodClosedThroughTheFutureIsRefused() {
        assertThrows(PeriodRejectedException.class,
                () -> periods.close(Instant.now().plusSeconds(86_400), "next week"));
    }

    // ---- helpers ----------------------------------------------------------------------

    private static Instant day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }

    private void post(LedgerAccount into, LedgerAccount from, long minor, Instant effectiveAt) {
        Money amount = Money.of(minor, "EUR");
        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "period:" + UUID.randomUUID())
                .effectiveAt(effectiveAt)
                .line(PostingLine.debit(fixture.newId(), into.getUuid(), amount))
                .line(PostingLine.credit(fixture.newId(), from.getUuid(), amount))
                .build());
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? error.toString() : cursor.getMessage();
    }
}


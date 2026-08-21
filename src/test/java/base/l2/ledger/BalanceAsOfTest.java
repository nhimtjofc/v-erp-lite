package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.reporting.application.StatementQuery;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The balance an account stood at on a date, when postings did not arrive in date order.
 *
 * <p>Every account here is opened fresh, because these assert absolute balances rather than
 * deltas and the suite shares one database.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class BalanceAsOfTest {

    @Inject
    PostEntryUC postEntry;

    @Inject
    StatementQuery statements;

    @Inject
    LedgerTestFixture fixture;

    private static final LocalDate CLOSE = LocalDate.of(2026, 1, 31);

    @Test
    void aBackdatedEntryLandsInThePeriodItIsDatedFor() {
        String name = "asof" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 90_000, LocalDate.of(2026, 1, 15));
        assertEquals(90_000L, statements.balanceAsOf(subject.getUuid(), CLOSE));

        // February. Posted next, dated later — the ordinary case, where posting order and date
        // order agree and any method at all gets the right answer.
        post(subject, other, 60_000, LocalDate.of(2026, 2, 10));
        assertEquals(90_000L, statements.balanceAsOf(subject.getUuid(), CLOSE),
                "activity after the cutoff must not count toward a balance as of the cutoff");
        assertEquals(150_000L, statements.balanceAsOf(subject.getUuid(), LocalDate.of(2026, 2, 28)));

        // The case that matters: dated inside January, posted after February's activity. This is
        // what booking a movement the bank charged and we missed actually looks like.
        post(subject, other, 10_000, LocalDate.of(2026, 1, 20));

        assertEquals(100_000L, statements.balanceAsOf(subject.getUuid(), CLOSE),
                "a backdated correction belongs to January, and nothing dated February may follow "
                        + "it in just because it was posted first");
        assertEquals(160_000L, statements.balanceAsOf(subject.getUuid(), LocalDate.of(2026, 2, 28)));
    }

    @Test
    void aDateBeforeAnyActivityIsZeroRatherThanEmpty() {
        String name = "asof" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 5_000, LocalDate.of(2026, 1, 15));

        assertEquals(0L, statements.balanceAsOf(subject.getUuid(), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void theCutoffIncludesTheWholeOfItsOwnDay() {
        String name = "asof" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 7_000, CLOSE);

        assertEquals(7_000L, statements.balanceAsOf(subject.getUuid(), CLOSE),
                "a statement closes at the end of its date, not at midnight before it");
        assertEquals(0L, statements.balanceAsOf(subject.getUuid(), CLOSE.minusDays(1)));
    }

    @Test
    void everyLineCarriesItsEntrysDate() {
        String name = "asof" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        UUID entryUuid = post(subject, other, 1_200, LocalDate.of(2026, 1, 9)).getUuid();

        // Nothing passes effective_at when a line is inserted; a BEFORE INSERT trigger derives it
        // from the entry. If that ever stops holding, the fold above silently reads the wrong rows
        // for the right-looking reason, so assert the two columns agree rather than trusting it.
        assertEquals(0L, fixture.linesDisagreeingWithTheirEntry(entryUuid),
                "every line of an entry must carry that entry's effective date");
    }

    private base.l2.ledger.journal.domain.JournalEntry post(LedgerAccount into, LedgerAccount from,
                                                            long minor, LocalDate valueDate) {
        Money amount = Money.of(minor, "EUR");
        return postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "asof:" + UUID.randomUUID())
                .effectiveAt(valueDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                .line(PostingLine.debit(fixture.newId(), into.getUuid(), amount))
                .line(PostingLine.credit(fixture.newId(), from.getUuid(), amount))
                .build());
    }
}


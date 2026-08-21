package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.reporting.application.StatementQuery;
import base.l2.ledger.reporting.interfaces.dto.StatementLineDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement of account, rendered from the per-line snapshots.
 *
 * <p>This exists because it did not. {@code statement()} cast the driver's temporal values straight
 * to {@code java.sql.Timestamp} and {@code java.sql.Date}, which the MariaDB driver does not always
 * return — reached through some queries the same column arrives as a {@code LocalDateTime}. With no
 * test on the method, {@code GET /ledger/accounts/&#123;uuid&#125;/statement} threw a
 * {@code ClassCastException} at runtime while the suite stayed green.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class StatementQueryTest {

    @Inject
    PostEntryUC postEntry;

    @Inject
    StatementQuery statements;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void aStatementRendersEveryColumnItPromises() {
        String name = "stmt" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");
        Instant effectiveAt = LocalDate.of(2027, 3, 4).atStartOfDay().toInstant(ZoneOffset.UTC);
        LocalDate due = LocalDate.of(2027, 4, 1);

        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "stmt:" + UUID.randomUUID())
                .effectiveAt(effectiveAt)
                .memo("Statement rendering")
                .line(PostingLine.debit(fixture.newId(), subject.getUuid(), Money.of(2_500, "EUR"))
                        .dueOn(due)
                        .describedAs("Opening movement"))
                .line(PostingLine.credit(fixture.newId(), other.getUuid(), Money.of(2_500, "EUR")))
                .build());

        List<StatementLineDto> lines = statements.statement(subject.getUuid());

        assertEquals(1, lines.size());
        StatementLineDto line = lines.getFirst();
        assertEquals("DR", line.direction());
        assertEquals(2_500L, line.amountMinor());
        assertEquals(2_500L, line.balanceAfterMinor(), "the statement renders the snapshot, not a running total");
        // The two conversions that were broken. Asserting they are non-null is the whole point:
        // the previous version did not return a wrong value, it threw before returning anything.
        assertNotNull(line.effectiveAt(), "effective_at must survive whatever type the driver hands back");
        assertEquals(effectiveAt, line.effectiveAt());
        assertEquals(due, line.dueDate(), "and so must due_date");
    }

    @Test
    void aStatementIsInPostingOrderEvenWhenDatesAreNot() {
        String name = "stmt" + UUID.randomUUID().toString().substring(0, 8);
        LedgerAccount subject = fixture.bank(name, "EUR");
        LedgerAccount other = fixture.bank(name + "b", "EUR");

        post(subject, other, 1_000, LocalDate.of(2027, 6, 1));
        post(subject, other, 2_000, LocalDate.of(2027, 5, 1));

        List<StatementLineDto> lines = statements.statement(subject.getUuid());

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).sequenceNo() < lines.get(1).sequenceNo());
        // A statement is a record of what was done in the order it was done, so the backdated
        // second posting comes second — and its balance continues the first, which is why the
        // snapshot chain answers as-of-sequence and never as-of-date.
        assertEquals(1_000L, lines.get(0).balanceAfterMinor());
        assertEquals(3_000L, lines.get(1).balanceAfterMinor());
    }

    private void post(LedgerAccount into, LedgerAccount from, long minor, LocalDate date) {
        Money amount = Money.of(minor, "EUR");
        postEntry.post(PostingRequest.of("BANK_TRANSACTION_RECORDED", "stmt:" + UUID.randomUUID())
                .effectiveAt(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                .line(PostingLine.debit(fixture.newId(), into.getUuid(), amount))
                .line(PostingLine.credit(fixture.newId(), from.getUuid(), amount))
                .build());
    }
}


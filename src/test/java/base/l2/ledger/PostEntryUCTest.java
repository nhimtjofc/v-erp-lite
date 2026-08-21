package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.DuplicateEntryException;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.JournalLine;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.journal.domain.UnbalancedEntryException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class PostEntryUCTest {

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void aBalancedEntryPostsAndLeavesTheBookAtZero() {
        LedgerAccount psp = fixture.psp("VND");
        LedgerAccount seller = fixture.seller(UUID.randomUUID(), "VND", 0);
        LedgerAccount commission = fixture.commission("VND");

        JournalEntry entry = postEntry.post(
                PostingRequest.of("PAYMENT_CAPTURED", "test-settle:" + UUID.randomUUID())
                        .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(100_000, "VND")))
                        .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(95_000, "VND")))
                        .line(PostingLine.credit(fixture.newId(), commission.getUuid(), Money.of(5_000, "VND")))
                        .build());

        assertNotNull(entry);
        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"), "the book must net to zero");
        assertEquals(0L, fixture.accountDrift(psp.getUuid()));
        assertEquals(0L, fixture.accountDrift(seller.getUuid()));
    }

    @Test
    void anUnbalancedEntryIsRefused() {
        LedgerAccount psp = fixture.psp("VND");
        LedgerAccount seller = fixture.seller(UUID.randomUUID(), "VND", 0);

        UnbalancedEntryException e = assertThrows(UnbalancedEntryException.class, () ->
                postEntry.post(PostingRequest.of("BAD", "test-unbalanced:" + UUID.randomUUID())
                        .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(100_000, "VND")))
                        .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(90_000, "VND")))
                        .build()));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("VND"));
    }

    @Test
    void aSingleLineEntryIsRefused() {
        LedgerAccount psp = fixture.psp("VND");
        assertThrows(UnbalancedEntryException.class, () ->
                postEntry.post(PostingRequest.of("BAD", "test-oneline:" + UUID.randomUUID())
                        .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(100, "VND")))
                        .build()));
    }

    @Test
    void replayingAnIdempotencyKeyDoesNotPostTwice() {
        LedgerAccount psp = fixture.psp("VND");
        LedgerAccount seller = fixture.seller(UUID.randomUUID(), "VND", 0);
        String key = "test-replay:" + UUID.randomUUID();

        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", key)
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(50_000, "VND")))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(50_000, "VND")))
                .build());
        long afterFirst = fixture.balanceOf(seller.getUuid());

        assertThrows(DuplicateEntryException.class, () ->
                postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", key)
                        .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(50_000, "VND")))
                        .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(50_000, "VND")))
                        .build()));

        assertEquals(afterFirst, fixture.balanceOf(seller.getUuid()), "a replay must move nothing");
    }

    @Test
    void everyLineRecordsTheBalanceBeforeAndAfterIt() {
        LedgerAccount psp = fixture.psp("VND");
        LedgerAccount seller = fixture.seller(UUID.randomUUID(), "VND", 0);

        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", "test-snap-a:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(30_000, "VND")))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(30_000, "VND")))
                .build());
        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", "test-snap-b:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(20_000, "VND")))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(20_000, "VND")))
                .build());

        List<JournalLine> lines = JournalLine
                .list("accountUuid = ?1 order by accountEntrySeq", seller.getUuid());
        assertEquals(2, lines.size());
        assertEquals(0L, lines.get(0).getAccountPrevBalanceMinor());
        assertEquals(-30_000L, lines.get(0).getAccountNewBalanceMinor());
        assertEquals(-30_000L, lines.get(1).getAccountPrevBalanceMinor());
        assertEquals(-50_000L, lines.get(1).getAccountNewBalanceMinor());
        assertEquals(1L, lines.get(0).getAccountEntrySeq());
        assertEquals(2L, lines.get(1).getAccountEntrySeq());
    }

    @Test
    void aCurrencyConversionIsOneEntryThatBalancesInEachCurrency() {
        // The rate is the shape of the entry, not a constant compiled into a settlement path. The
        // predecessor hardcoded `amount * 254` in three places and failed every legitimate payment
        // the moment the real rate moved.
        LedgerAccount buyerUsd = fixture.buyer(UUID.randomUUID(), "USD");
        LedgerAccount liquidityUsd = fixture.psp("USD");
        LedgerAccount liquidityVnd = fixture.psp("VND");
        LedgerAccount sellerVnd = fixture.seller(UUID.randomUUID(), "VND", 0);

        // The buyer has to be in funds before they can spend. Debiting a buyer from zero would
        // mean the buyer owes us, which a custodial balance must never allow — and the policy
        // correctly refuses it.
        postEntry.post(PostingRequest.of("TOP_UP", "test-fx-fund:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(), liquidityUsd.getUuid(), Money.of(1_000, "USD")))
                .line(PostingLine.credit(fixture.newId(), buyerUsd.getUuid(), Money.of(1_000, "USD")))
                .build());

        postEntry.post(PostingRequest.of("FX_SETTLEMENT", "test-fx:" + UUID.randomUUID())
                .meta("rate", "25400")
                .line(PostingLine.debit(fixture.newId(), buyerUsd.getUuid(), Money.of(1_000, "USD")))
                .line(PostingLine.credit(fixture.newId(), liquidityUsd.getUuid(), Money.of(1_000, "USD")))
                .line(PostingLine.debit(fixture.newId(), liquidityVnd.getUuid(), Money.of(254_000, "VND")))
                .line(PostingLine.credit(fixture.newId(), sellerVnd.getUuid(), Money.of(254_000, "VND")))
                .build());

        assertEquals(0L, fixture.trialBalanceNet("acme", "USD"));
        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"));
        assertEquals(-254_000L, fixture.balanceOf(sellerVnd.getUuid()));
    }
}


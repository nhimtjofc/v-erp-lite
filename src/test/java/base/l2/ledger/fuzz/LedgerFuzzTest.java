package base.l2.ledger.fuzz;

import base.l0.system.api.Page;
import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.allocation.application.AllocateItemUC;
import base.l2.ledger.fee.application.PublishFeeScheduleUC;
import base.l2.ledger.fee.domain.FeeSchedule;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.ledger.reporting.application.AccountFilter;
import base.l2.ledger.reporting.application.AccountQuery;
import base.l2.ledger.reporting.application.StatementQuery;
import base.l2.ledger.reporting.interfaces.dto.AccountBalanceDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Properties that must hold for any sequence of operations, checked against generated ones.
 *
 * <p>The suite's other tests assert what happens in situations somebody thought of. These assert
 * relationships that cannot stop being true — the balance is the fold of the lines, an as-of-date
 * balance is the sum of what was dated by then, open items net to the balance — and let generated
 * input look for the case nobody thought of.
 *
 * <p>Seeds are fixed so a failure is reproducible. The seed is printed in every failure message,
 * because a property test that fails once and never again has told you nothing you can act on.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class LedgerFuzzTest {

    @Inject
    PostEntryUC postEntry;

    @Inject
    StatementQuery statements;

    @Inject
    AllocateItemUC allocate;

    @Inject
    AccountQuery accounts;

    @Inject
    PublishFeeScheduleUC feeSchedules;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    /**
     * An as-of-date balance equals the sum of everything dated at or before it.
     *
     * <p>The oracle is kept in Java as the entries are generated, so it cannot share a bug with the
     * query under test. Effective dates are drawn independently of posting order, which is the
     * whole point: it produces the backdating that made the snapshot-based version of this query
     * wrong, at whatever density the generator happens to pick.
     */
    @Test
    void asOfDateBalanceIsTheSumOfWhatWasDatedByThen() {
        for (long seed : new long[]{1L, 7L, 42L, 1337L}) {
            Random random = new Random(seed);
            String tag = "fz" + UUID.randomUUID().toString().substring(0, 8);
            LedgerAccount subject = fixture.bank(tag, "EUR");
            LedgerAccount other = fixture.bank(tag + "b", "EUR");

            List<long[]> oracle = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                int dayOffset = random.nextInt(60);
                LocalDate date = LocalDate.of(2027, 1, 1).plusDays(dayOffset);
                long amount = 1 + random.nextInt(5_000);
                boolean inbound = random.nextBoolean();
                post(subject, other, amount, inbound, date);
                oracle.add(new long[]{dayOffset, inbound ? amount : -amount});
            }

            for (int probe = 0; probe < 15; probe++) {
                int cutoff = random.nextInt(70);
                LocalDate asOf = LocalDate.of(2027, 1, 1).plusDays(cutoff);
                long expected = oracle.stream()
                        .filter(row -> row[0] <= cutoff)
                        .mapToLong(row -> row[1])
                        .sum();
                assertEquals(expected, statements.balanceAsOf(subject.getUuid(), asOf),
                        "seed=" + seed + " asOf=" + asOf);
            }
        }
    }

    /**
     * Open items net to the account balance, whatever has been allocated against what.
     *
     * <p>This is the invariant that ties allocation back to the ledger. Every allocation adds the
     * same amount to one debit line and one credit line, so it cancels in the signed total — which
     * means no sequence of allocations can move the net of the open items away from the balance.
     * If it ever does, either the allocation table has a row it should not or {@code v_open_items}
     * is computing the wrong thing, and both are silent failures that surface as a wrong aging
     * report months later.
     */
    @Test
    void openItemsAlwaysNetToTheAccountBalance() {
        for (long seed : new long[]{3L, 11L, 99L}) {
            Random random = new Random(seed);
            UUID partyId = UUID.randomUUID();
            LedgerAccount party = fixture.seller(partyId, "EUR", 1_000_000);
            LedgerAccount counter = fixture.psp("EUR");

            for (int i = 0; i < 25; i++) {
                long amount = 1 + random.nextInt(3_000);
                boolean owedToThem = random.nextBoolean();
                UUID lineUuid = fixture.newId();

                PostingRequest.Builder builder =
                        PostingRequest.of("MANUAL_ADJUSTMENT", "fuzz-alloc:" + UUID.randomUUID());
                if (owedToThem) {
                    builder.line(PostingLine.credit(lineUuid, party.getUuid(), Money.of(amount, "EUR")))
                           .line(PostingLine.debit(fixture.newId(), counter.getUuid(), Money.of(amount, "EUR")));
                } else {
                    builder.line(PostingLine.debit(lineUuid, party.getUuid(), Money.of(amount, "EUR")))
                           .line(PostingLine.credit(fixture.newId(), counter.getUuid(), Money.of(amount, "EUR")));
                }
                postEntry.post(builder.build());

                // Settle roughly half the time, so the generator explores both fully-matched and
                // partially-matched states rather than only one of them.
                if (random.nextBoolean()) {
                    allocate.settleOldestFirst(party.getUuid(), lineUuid, Money.of(amount, "EUR"));
                }

                assertEquals(fixture.balanceOf(party.getUuid()), openSignedTotal(party.getUuid()),
                        "seed=" + seed + " step=" + i);
                assertEquals(0L, negativeOpenItems(party.getUuid()),
                        "an item may never be over-allocated; seed=" + seed + " step=" + i);
            }

            // Without this the property is vacuous: if the generator never produced a settleable
            // pair, every assertion above holds for the trivial reason that nothing was allocated.
            assertTrue(allocationCount(party.getUuid()) > 0,
                    "seed=" + seed + " allocated nothing, so the property proved nothing");
        }
    }

    @Transactional
    long allocationCount(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery("""
                SELECT COUNT(*) FROM item_allocations a
                 WHERE a.open_line_uuid IN (SELECT uuid FROM journal_lines WHERE account_uuid = ?1)
                """)
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    /** Any sequence of balanced entries leaves the book at zero and no account drifting. */
    @Test
    void randomBalancedEntriesLeaveTheBookConsistent() {
        Random random = new Random(2024L);
        String tag = "fz" + UUID.randomUUID().toString().substring(0, 8);
        List<LedgerAccount> pool = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pool.add(fixture.bank(tag + i, "EUR"));
        }

        for (int i = 0; i < 40; i++) {
            int width = 2 + random.nextInt(3);
            long unit = 1 + random.nextInt(2_000);
            List<LedgerAccount> chosen = new ArrayList<>(pool);
            java.util.Collections.shuffle(chosen, random);

            PostingRequest.Builder builder =
                    PostingRequest.of("MANUAL_ADJUSTMENT", "fuzz-entry:" + UUID.randomUUID());
            builder.line(PostingLine.debit(fixture.newId(), chosen.getFirst().getUuid(),
                    Money.of(unit * (width - 1), "EUR")));
            for (int j = 1; j < width; j++) {
                builder.line(PostingLine.credit(fixture.newId(), chosen.get(j).getUuid(),
                        Money.of(unit, "EUR")));
            }
            postEntry.post(builder.build());
        }

        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
        for (LedgerAccount account : pool) {
            assertEquals(0L, fixture.accountDrift(account.getUuid()), account.getCode());
        }
    }

    /** A cursor walk sees every row once, at any page size. */
    @Test
    void pagingVisitsEveryAccountExactlyOnceAtAnyPageSize() {
        Random random = new Random(555L);
        String prefix = "fzpage:" + UUID.randomUUID().toString().substring(0, 8);
        int total = 17 + random.nextInt(20);
        seedAccounts(prefix, total);

        for (int limit : new int[]{1, 2, 3, 5, 8, 13, 50}) {
            Set<UUID> seen = new HashSet<>();
            String cursor = null;
            int guard = 0;
            while (guard++ < 500) {
                Page<AccountBalanceDto> page = accounts.search(
                        new AccountFilter(prefix, null, null, null, null, null), cursor, limit);
                for (AccountBalanceDto dto : page.items()) {
                    assertTrue(seen.add(dto.accountUuid()), "repeat at limit=" + limit);
                }
                assertTrue(page.items().size() <= page.limit(), "page overran its limit");
                if (!page.hasMore()) {
                    break;
                }
                cursor = page.nextCursor();
            }
            assertEquals(total, seen.size(), "lost rows at limit=" + limit);
        }
    }

    /** The fee a schedule charges matches an independent decimal computation, rounded half-up. */
    @Test
    void feeMatchesAnIndependentDecimalComputation() {
        Random random = new Random(8675309L);
        for (int i = 0; i < 40; i++) {
            int basisPoints = random.nextInt(2_001);
            long gross = 1 + (long) (random.nextDouble() * 5_000_000_000L);
            FeeSchedule schedule = new FeeSchedule();
            schedule.setBasisPoints(basisPoints);
            schedule.setFixedMinor(0);

            BigDecimal expected = BigDecimal.valueOf(gross)
                    .multiply(BigDecimal.valueOf(basisPoints))
                    .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP);

            assertEquals(expected.longValueExact(),
                    schedule.feeOn(Money.of(gross, "VND")).amountMinor(),
                    "gross=" + gross + " bp=" + basisPoints);
        }
    }

    /** Money arithmetic refuses to wrap rather than silently producing a plausible wrong number. */
    @Test
    void moneyRefusesToOverflowSilently() {
        Money huge = Money.of(Long.MAX_VALUE - 10, "VND");
        assertFalse(huge.isNegative());
        assertTrue(assertThrowsAny(() -> huge.add(Money.of(100, "VND"))),
                "adding past Long.MAX_VALUE must throw, not wrap into a large negative balance");
    }

    // ---- helpers ----------------------------------------------------------------------

    private static boolean assertThrowsAny(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void post(LedgerAccount subject, LedgerAccount other, long amount, boolean inbound,
                      LocalDate effectiveDate) {
        Money money = Money.of(amount, "EUR");
        Instant effectiveAt = effectiveDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        PostingRequest.Builder builder =
                PostingRequest.of("BANK_TRANSACTION_RECORDED", "fuzz-asof:" + UUID.randomUUID())
                        .effectiveAt(effectiveAt);
        if (inbound) {
            builder.line(PostingLine.debit(fixture.newId(), subject.getUuid(), money))
                   .line(PostingLine.credit(fixture.newId(), other.getUuid(), money));
        } else {
            builder.line(PostingLine.credit(fixture.newId(), subject.getUuid(), money))
                   .line(PostingLine.debit(fixture.newId(), other.getUuid(), money));
        }
        postEntry.post(builder.build());
    }

    @Transactional
    long openSignedTotal(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(SUM(open_signed_minor), 0) FROM v_open_items WHERE account_uuid = ?1")
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    long negativeOpenItems(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COUNT(*) FROM v_open_items WHERE account_uuid = ?1 AND open_minor < 0")
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    void seedAccounts(String prefix, int count) {
        for (int i = 0; i < count; i++) {
            em.createNativeQuery("""
                    INSERT INTO ledger_accounts (uuid, tenant_code, code, account_class, account_role,
                        normal_side, owner_type, owner_id, currency, allow_debit_balance,
                        allow_credit_balance, credit_limit_minor, balance_minor, entry_seq)
                    VALUES (UUID(), 'acme', ?1, 'LIABILITY', 'SUBLEDGER', 'CR', 'SELLER', UUID(),
                            'EUR', TRUE, TRUE, 1000, 0, 0)
                    """)
                    .setParameter(1, prefix + ":" + String.format("%04d", i))
                    .executeUpdate();
        }
        em.flush();
        em.clear();
    }
}


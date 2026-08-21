package base.l2.ledger;

import base.l0.system.api.Page;
import base.l2.ledger.account.domain.AccountRole;
import base.l2.ledger.reporting.application.AccountFilter;
import base.l2.ledger.reporting.application.AccountQuery;
import base.l2.ledger.reporting.application.InvalidCursorException;
import base.l2.ledger.reporting.interfaces.dto.AccountBalanceDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import base.l0.system.money.Money;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paging and search over accounts.
 *
 * <p>Every test works inside its own code prefix, because the suite shares one database and one
 * tenant — an assertion about "all accounts" would depend on which other tests had run first.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class AccountPagingTest {

    @Inject
    AccountQuery accounts;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    @Inject
    PostEntryUC postEntry;

    @Test
    void walkingTheCursorVisitsEveryAccountExactlyOnce() {
        String prefix = seed(25);

        Set<UUID> seen = new HashSet<>();
        List<UUID> order = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            Page<AccountBalanceDto> page = accounts.search(prefixFilter(prefix), cursor, 10);
            page.items().forEach(dto -> {
                assertTrue(seen.add(dto.accountUuid()), "no account may appear on two pages");
                order.add(dto.accountUuid());
            });
            cursor = page.nextCursor();
            pages++;
            if (!page.hasMore()) {
                assertNull(cursor, "the last page must not offer a cursor to nowhere");
                break;
            }
        } while (pages < 20);

        assertEquals(25, seen.size(), "and every account must appear on one");
        assertEquals(3, pages, "10 + 10 + 5");
        assertEquals(order.stream().sorted().toList(), order, "pages arrive in key order");
    }

    @Test
    void anAccountOpenedMidWalkCannotDisplaceOneNotYetSeen() {
        String prefix = seed(12);

        Page<AccountBalanceDto> first = accounts.search(prefixFilter(prefix), null, 6);
        assertTrue(first.hasMore());
        Set<UUID> seen = new HashSet<>(first.items().stream().map(AccountBalanceDto::accountUuid).toList());

        // Twelve more accounts arrive between page one and page two. Under OFFSET paging this is
        // exactly where rows get skipped or repeated; under a cursor the new rows can only append.
        seedMore(prefix, 12, 12);

        String cursor = first.nextCursor();
        int remainingOriginals = 0;
        while (cursor != null) {
            Page<AccountBalanceDto> next = accounts.search(prefixFilter(prefix), cursor, 6);
            for (AccountBalanceDto dto : next.items()) {
                assertTrue(seen.add(dto.accountUuid()), "a cursor walk must never repeat a row");
                remainingOriginals++;
            }
            cursor = next.hasMore() ? next.nextCursor() : null;
        }

        assertEquals(18, remainingOriginals,
                "the six unseen originals plus the twelve that arrived, none lost and none doubled");
    }

    @Test
    void aRequestedLimitIsCappedRatherThanObeyed() {
        String prefix = seed(3);

        Page<AccountBalanceDto> page = accounts.search(prefixFilter(prefix), null, 1_000_000);

        assertEquals(AccountQuery.MAX_LIMIT, page.limit(),
                "an uncapped caller-supplied limit is the unbounded query with a query parameter");
        assertFalse(page.hasMore());
    }

    @Test
    void noLimitMeansTheDefaultRatherThanEverything() {
        String prefix = seed(2);
        assertEquals(AccountQuery.DEFAULT_LIMIT, accounts.search(prefixFilter(prefix), null, null).limit());
        assertEquals(AccountQuery.DEFAULT_LIMIT, accounts.search(prefixFilter(prefix), null, 0).limit());
    }

    @Test
    void aCursorWeDidNotIssueIsRefusedRatherThanIgnored() {
        // Restarting from the beginning would hand a caller page one while it believed it was
        // several pages in, which is the failure this whole change exists to prevent.
        assertThrows(InvalidCursorException.class,
                () -> accounts.search(AccountFilter.none(), "page-2", 10));
    }

    @Test
    void searchNarrowsByCodePrefix() {
        String mine = seed(4);
        String theirs = seed(4);

        Page<AccountBalanceDto> page = accounts.search(prefixFilter(mine), null, 50);

        assertEquals(4, page.items().size());
        assertTrue(page.items().stream().allMatch(dto -> dto.code().startsWith(mine)));
        assertFalse(page.items().stream().anyMatch(dto -> dto.code().startsWith(theirs)));
    }

    @Test
    void searchNarrowsByRole() {
        String prefix = seed(3);
        Page<AccountBalanceDto> page = accounts.search(
                new AccountFilter(prefix, null, null, AccountRole.CONTROL, null, null), null, 50);

        assertEquals(0, page.items().size(), "the seeded accounts are sub-ledgers, not controls");
        assertNull(page.nextCursor());
    }

    @Test
    void debtorsAreThoseOwingAtLeastTheThreshold() {
        UUID small = UUID.randomUUID();
        UUID large = UUID.randomUUID();
        putIntoDebit(small, 50);
        putIntoDebit(large, 90_000);

        List<UUID> owingALot = ownerIds(accounts.listDebtors(10_000, null, 500));
        assertTrue(owingALot.contains(large));
        assertFalse(owingALot.contains(small), "an exposure report wants the ones worth acting on");

        List<UUID> owingAnything = ownerIds(accounts.listDebtors(0, null, 500));
        assertTrue(owingAnything.contains(small), "the threshold defaults to any debit balance");
        assertTrue(owingAnything.contains(large));
    }

    // ---- helpers ----------------------------------------------------------------------

    private static AccountFilter prefixFilter(String prefix) {
        return new AccountFilter(prefix, null, null, null, null, null);
    }

    private static List<UUID> ownerIds(Page<AccountBalanceDto> page) {
        return page.items().stream().map(AccountBalanceDto::ownerId).toList();
    }

    /** Opens {@code count} sub-ledger accounts under a code prefix nothing else uses. */
    private String seed(int count) {
        String prefix = "page:" + UUID.randomUUID().toString().substring(0, 8);
        insert(prefix, count, 0);
        return prefix;
    }

    /** Adds more accounts under an existing prefix, continuing the numbering. */
    private void seedMore(String prefix, int count, int from) {
        insert(prefix, count, from);
    }

    @Transactional
    void insert(String prefix, int count, int from) {
        for (int i = from; i < from + count; i++) {
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

    /**
     * Puts a counterparty into debit by posting an entry, not by writing the balance.
     *
     * <p>The short version would be to UPDATE balance_minor behind {@code @ledger_posting = 1}. It
     * would work — the guard trigger honours that flag and no build rule scans test sources for it.
     * That is exactly why it is worth not doing: a test that reaches around the single write path
     * teaches the next person that reaching around it is available, and the value of "there is one
     * way money moves" is that it has no exceptions, including convenient ones.
     */
    private void putIntoDebit(UUID partyId, long minor) {
        Money amount = Money.of(minor, "EUR");
        postEntry.post(PostingRequest.of("MANUAL_ADJUSTMENT", "paging-debit:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(),
                        fixture.seller(partyId, "EUR", 100_000).getUuid(), amount))
                .line(PostingLine.credit(fixture.newId(), fixture.psp("EUR").getUuid(), amount))
                .build());
    }
}


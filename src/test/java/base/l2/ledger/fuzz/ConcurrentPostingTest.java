package base.l2.ledger.fuzz;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many transactions posting over the same accounts at once.
 *
 * <p>This is the part of the design with the most reasoning behind it and, until now, no test at
 * all. {@code ledger_post_entry} does two things to survive concurrency: it locks every account an
 * entry touches in {@code uuid} order before applying any line, so two entries over the same
 * accounts cannot deadlock by taking them in opposite orders; and it re-reads
 * {@code balance_minor}/{@code entry_seq} {@code FOR UPDATE} inside the loop, because under
 * REPEATABLE READ a plain {@code SELECT} returns the snapshot taken before the lock was waited on
 * and would hand back a stale sequence.
 *
 * <p>Both failures are invisible in a single-threaded suite and neither is subtle in production:
 * the first shows up as deadlock rollbacks, the second as unique-constraint violations on
 * {@code uq_journal_lines_account_seq}. So the entries here are generated to collide on purpose —
 * a small account pool, random subsets, random line order.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ConcurrentPostingTest {

    private static final int THREADS = 8;
    private static final int ENTRIES_PER_THREAD = 15;
    private static final int ACCOUNT_POOL = 5;

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    @Test
    void concurrentEntriesOverTheSameAccountsAllSucceedAndLeaveNoGaps() throws Exception {
        String tag = "fuzz" + UUID.randomUUID().toString().substring(0, 8);
        List<LedgerAccount> pool = new ArrayList<>();
        for (int i = 0; i < ACCOUNT_POOL; i++) {
            pool.add(fixture.bank(tag + i, "EUR"));
        }
        List<UUID> poolUuids = pool.stream().map(LedgerAccount::getUuid).toList();

        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger posted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try (ExecutorService pool2 = Executors.newFixedThreadPool(THREADS)) {
            for (int t = 0; t < THREADS; t++) {
                final int threadNo = t;
                pool2.submit(() -> {
                    // A worker thread has no request context, and PostEntryUC reads the tenant and
                    // actor from one. Activating it here rather than letting the resolver fall back
                    // keeps these writes in the same tenant the assertions read from.
                    Arc.container().requestContext().activate();
                    try {
                        start.await();
                        Random random = new Random(threadNo * 7919L);
                        for (int e = 0; e < ENTRIES_PER_THREAD; e++) {
                            try {
                                postRandom(pool, random, tag + ":" + threadNo + ":" + e);
                                posted.incrementAndGet();
                            } catch (Exception ex) {
                                failures.add(rootMessage(ex));
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        Arc.container().requestContext().terminate();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(120, TimeUnit.SECONDS), "workers did not finish");
        }

        assertEquals(List.of(), new ArrayList<>(failures),
                "every concurrent posting must succeed; a deadlock or a sequence collision here is "
                        + "the exact failure the lock ordering and FOR UPDATE exist to prevent");
        assertEquals(THREADS * ENTRIES_PER_THREAD, posted.get());

        // The book still balances, and every account's stored balance still matches its own lines.
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
        for (UUID accountUuid : poolUuids) {
            assertEquals(0L, fixture.accountDrift(accountUuid), "drift on " + accountUuid);
        }

        // The sequence per account is dense and unique: no gaps from a rolled-back attempt, no
        // duplicates from a stale read that the constraint happened to let through.
        for (UUID accountUuid : poolUuids) {
            assertEquals(0L, sequenceAnomalies(accountUuid),
                    "entry_seq must be 1..n with no gaps or repeats on " + accountUuid);
            assertEquals(0L, snapshotChainBreaks(accountUuid),
                    "each line's prev balance must be the previous line's new balance on " + accountUuid);
        }
    }

    private void postRandom(List<LedgerAccount> pool, Random random, String key) {
        List<LedgerAccount> chosen = new ArrayList<>(pool);
        Collections.shuffle(chosen, random);
        int width = 2 + random.nextInt(Math.min(3, pool.size() - 1));
        chosen = chosen.subList(0, width);

        long unit = 100 + random.nextInt(900);
        Money each = Money.of(unit, "EUR");
        Money total = Money.of(unit * (width - 1), "EUR");

        PostingRequest.Builder builder = PostingRequest.of("MANUAL_ADJUSTMENT", key);
        // Debit one, credit the rest — the line order is deliberately not the lock order, which is
        // what makes two threads over the same accounts try to take them in opposite orders.
        builder.line(PostingLine.debit(UUID.randomUUID(), chosen.getFirst().getUuid(), total));
        for (int i = 1; i < width; i++) {
            builder.line(PostingLine.credit(UUID.randomUUID(), chosen.get(i).getUuid(), each));
        }
        postEntry.post(builder.build());
    }

    /** Rows where entry_seq is not exactly one more than the previous line's on that account. */
    @Transactional
    long sequenceAnomalies(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery("""
                SELECT COUNT(*) FROM (
                    SELECT account_entry_seq,
                           LAG(account_entry_seq) OVER (ORDER BY account_entry_seq) AS prev_seq
                      FROM journal_lines WHERE account_uuid = ?1
                ) chain
                WHERE prev_seq IS NOT NULL AND account_entry_seq <> prev_seq + 1
                """)
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    /** Rows whose prev balance does not continue the previous line's new balance. */
    @Transactional
    long snapshotChainBreaks(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery("""
                SELECT COUNT(*) FROM (
                    SELECT account_prev_balance_minor,
                           LAG(account_new_balance_minor) OVER (ORDER BY account_entry_seq) AS prev_new
                      FROM journal_lines WHERE account_uuid = ?1
                ) chain
                WHERE prev_new IS NOT NULL AND account_prev_balance_minor <> prev_new
                """)
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    private static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getClass().getSimpleName() + ": " + cursor.getMessage();
    }
}


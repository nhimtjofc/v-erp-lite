package base.l2.ledger.fuzz;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.allocation.application.AllocateItemUC;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several transactions settling the same open item at once.
 *
 * <p>{@code trg_item_allocations_within_open} decides whether an allocation fits by summing what
 * has already been allocated against each line. That sum is a plain {@code SELECT} inside a
 * {@code BEFORE INSERT} trigger, and under REPEATABLE READ a plain {@code SELECT} answers from the
 * transaction's own snapshot — so two transactions can each read "nothing allocated yet", each
 * conclude there is room, and both insert. It is the same shape as the stale-{@code entry_seq} bug
 * the posting procedure carries a {@code FOR UPDATE} to prevent, on a different table.
 *
 * <p>Whether it actually bites depends on InnoDB's locking, which is why this is a test rather than
 * an argument: an item allocated beyond its own value is money counted twice, and it would surface
 * as a counterparty appearing settled while still owed.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class ConcurrentAllocationTest {

    private static final int THREADS = 6;
    private static final long OPEN_AMOUNT = 10_000;
    private static final long EACH_ALLOCATION = 4_000;

    @Inject
    PostEntryUC postEntry;

    @Inject
    AllocateItemUC allocate;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    @Test
    void anItemCannotBeAllocatedBeyondWhatIsOpenOnIt() throws Exception {
        UUID partyId = UUID.randomUUID();
        LedgerAccount party = fixture.seller(partyId, "EUR", 1_000_000);
        LedgerAccount counter = fixture.psp("EUR");

        // One open credit of 10,000: room for exactly two allocations of 4,000.
        UUID openLine = fixture.newId();
        postEntry.post(PostingRequest.of("MANUAL_ADJUSTMENT", "conc-alloc-open:" + UUID.randomUUID())
                .line(PostingLine.credit(openLine, party.getUuid(), Money.of(OPEN_AMOUNT, "EUR")))
                .line(PostingLine.debit(fixture.newId(), counter.getUuid(), Money.of(OPEN_AMOUNT, "EUR")))
                .build());

        // A settling debit per thread, so the settle side of the check is never the limiting one.
        List<UUID> settleLines = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            UUID settleLine = fixture.newId();
            postEntry.post(PostingRequest.of("MANUAL_ADJUSTMENT", "conc-alloc-settle:" + UUID.randomUUID())
                    .line(PostingLine.debit(settleLine, party.getUuid(), Money.of(EACH_ALLOCATION, "EUR")))
                    .line(PostingLine.credit(fixture.newId(), counter.getUuid(), Money.of(EACH_ALLOCATION, "EUR")))
                    .build());
            settleLines.add(settleLine);
        }

        java.util.concurrent.ConcurrentLinkedQueue<String> reasons =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try (ExecutorService workers = Executors.newFixedThreadPool(THREADS)) {
            for (UUID settleLine : settleLines) {
                workers.submit(() -> {
                    Arc.container().requestContext().activate();
                    try {
                        start.await();
                        allocate.allocate(openLine, settleLine, Money.of(EACH_ALLOCATION, "EUR"));
                        accepted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        refused.incrementAndGet();
                        Throwable root = e;
                        while (root.getCause() != null) {
                            root = root.getCause();
                        }
                        reasons.add(root.getClass().getSimpleName() + ": " + root.getMessage());
                    } finally {
                        Arc.container().requestContext().terminate();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "workers did not finish");
        }

        long allocated = allocatedAgainst(openLine);
        System.out.printf("ALLOC-PROBE accepted=%d refused=%d allocatedTotal=%d openAmount=%d openMinor=%d%n",
                accepted.get(), refused.get(), allocated, OPEN_AMOUNT, openMinor(openLine));
        reasons.stream().distinct().forEach(r -> System.out.println("ALLOC-REASON " + r));

        assertTrue(allocated <= OPEN_AMOUNT,
                "an item was allocated " + allocated + " against a face value of " + OPEN_AMOUNT
                        + " — the same amount has been settled twice");
        assertEquals(0L, negativeOpenItems(party.getUuid()),
                "no open item may go negative");
        assertEquals(fixture.balanceOf(party.getUuid()), openSignedTotal(party.getUuid()),
                "open items must still net to the balance");
    }

    /**
     * Two payouts to the same counterparty at once must both go through.
     *
     * <p>{@code settleOldestFirst} plans from {@code v_open_items}, which is a snapshot read, so
     * concurrent callers start from the same picture and immediately disagree with reality. If that
     * were treated as a conflict, the loser would roll back — taking its payout entry with it —
     * over a situation that is not a conflict at all: there is plenty open, just not the particular
     * item both of them looked at first. They should queue and place what fits.
     */
    @Test
    void concurrentSettlementsAgainstOneCounterpartyAllSucceed() throws Exception {
        UUID partyId = UUID.randomUUID();
        LedgerAccount party = fixture.seller(partyId, "EUR", 1_000_000);
        LedgerAccount counter = fixture.psp("EUR");

        // Six open credits of 1,000, and six threads each settling 1,000. Every thread's plan will
        // name the same oldest item first, so all but one of them must adapt.
        for (int i = 0; i < THREADS; i++) {
            postEntry.post(PostingRequest.of("MANUAL_ADJUSTMENT", "conc-settle-open:" + UUID.randomUUID())
                    .line(PostingLine.credit(fixture.newId(), party.getUuid(), Money.of(1_000, "EUR")))
                    .line(PostingLine.debit(fixture.newId(), counter.getUuid(), Money.of(1_000, "EUR")))
                    .build());
        }

        List<UUID> settleLines = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            UUID settleLine = fixture.newId();
            postEntry.post(PostingRequest.of("MANUAL_ADJUSTMENT", "conc-settle-pay:" + UUID.randomUUID())
                    .line(PostingLine.debit(settleLine, party.getUuid(), Money.of(1_000, "EUR")))
                    .line(PostingLine.credit(fixture.newId(), counter.getUuid(), Money.of(1_000, "EUR")))
                    .build());
            settleLines.add(settleLine);
        }

        java.util.concurrent.ConcurrentLinkedQueue<String> failures =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        AtomicInteger settled = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try (ExecutorService workers = Executors.newFixedThreadPool(THREADS)) {
            for (UUID settleLine : settleLines) {
                workers.submit(() -> {
                    Arc.container().requestContext().activate();
                    try {
                        start.await();
                        allocate.settleOldestFirst(party.getUuid(), settleLine, Money.of(1_000, "EUR"));
                        settled.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        Throwable root = e;
                        while (root.getCause() != null) {
                            root = root.getCause();
                        }
                        failures.add(root.getClass().getSimpleName() + ": " + root.getMessage());
                    } finally {
                        Arc.container().requestContext().terminate();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "workers did not finish");
        }

        assertEquals(List.of(), new ArrayList<>(failures),
                "concurrent settlements must queue behind each other, not roll back");
        assertEquals(THREADS, settled.get());

        assertEquals(0L, fixture.balanceOf(party.getUuid()),
                "six credits settled by six debits leaves nothing owed either way");
        assertEquals(0L, openSignedTotal(party.getUuid()),
                "and nothing open, because every item found a counterpart");
        assertEquals(0L, negativeOpenItems(party.getUuid()));
    }

    @Transactional
    long allocatedAgainst(UUID lineUuid) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(SUM(amount_minor), 0) FROM item_allocations "
                                + "WHERE open_line_uuid = ?1 OR settle_line_uuid = ?1")
                .setParameter(1, lineUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    long openMinor(UUID lineUuid) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(open_minor, 0) FROM v_open_items WHERE line_uuid = ?1")
                .setParameter(1, lineUuid)
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
    long openSignedTotal(UUID accountUuid) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(SUM(open_signed_minor), 0) FROM v_open_items WHERE account_uuid = ?1")
                .setParameter(1, accountUuid)
                .getSingleResult();
        return ((Number) value).longValue();
    }
}


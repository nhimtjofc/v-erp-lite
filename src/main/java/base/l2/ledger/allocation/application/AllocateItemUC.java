package base.l2.ledger.allocation.application;

import base.l0.system.money.Money;
import base.l0.system.security.CallerContext;
import base.l2.ledger.allocation.domain.AllocationRejectedException;
import base.l2.ledger.allocation.domain.ItemAllocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Matches a settling line against the open items it closes.
 *
 * <p>Until something calls this, nothing in the ledger is ever <em>closed</em>. The table, its
 * constraints and the trigger that bounds an allocation to what remains open have existed since
 * V0.001; no code wrote to them. The visible cost was the aging report: {@code v_open_items}
 * computes an item's outstanding amount as its face value minus what has been allocated against it,
 * so with the allocation table permanently empty every line stayed fully open forever and
 * {@code GET /ledger/aging} returned lifetime turnover per counterparty under the heading of
 * outstanding balance.
 *
 * <p>The amount rules are enforced by {@code trg_item_allocations_within_open}, not here: an
 * allocation may never exceed what is still open on either line it touches, and checking that in
 * Java would be a read-then-write race between two people settling the same invoice. This class
 * decides <em>which</em> items to close and in what order; the database decides whether the numbers
 * are allowed.
 */
@ApplicationScoped
public class AllocateItemUC {

    @Inject
    EntityManager em;

    @Inject
    CallerContext caller;

    /**
     * Closes one open item with one settling line, for an exact amount, or refuses.
     *
     * <p>The amount check happens here rather than in the trigger, which is a deliberate exception
     * to this service's rule that the database is the thing that counts — and it is worth knowing
     * exactly why, because the rule is otherwise absolute.
     *
     * <p>{@code trg_item_allocations_within_open} used to be the whole guard, and it could be
     * raced: it summed prior allocations with a plain {@code SELECT}, and under REPEATABLE READ a
     * plain {@code SELECT} answers from the transaction's snapshot rather than committed state. Six
     * concurrent allocations of 4,000 against a 10,000 item were all accepted. The natural repair
     * is to make that sum a locking read, and MariaDB refuses it: a trigger may not take a locking
     * read on the table it fires for.
     *
     * <p>So the work is split. The trigger still locks the two lines in {@code uuid} order, which
     * is what serialises competing allocations; this method holds those same locks by the time its
     * own locking read runs, so the total it sums is the committed one. {@code SingleWritePathTest}
     * asserts that nothing else writes {@code item_allocations}, which is what makes a check in
     * application code sound rather than hopeful.
     */
    @Transactional
    public ItemAllocation allocate(UUID openLineUuid, UUID settleLineUuid, Money amount) {
        requireSensible(openLineUuid, settleLineUuid, amount);
        lockPair(openLineUuid, settleLineUuid);

        long room = Math.min(roomOn(openLineUuid), roomOn(settleLineUuid));
        if (amount.amountMinor() > room) {
            throw new AllocationRejectedException("Allocating " + amount + " would exceed the "
                    + room + " still open between " + openLineUuid + " and " + settleLineUuid);
        }
        return insert(openLineUuid, settleLineUuid, amount);
    }

    /**
     * Closes as many of an account's oldest open items as a settling line covers.
     *
     * <p>Oldest first, by effective date then posting sequence. FIFO is the right default for a
     * counterparty balance — a payout pays down the funds held longest — and it is the order that
     * makes an aging report mean anything, since any other order leaves the oldest item open while
     * newer ones close and reports debt that is not really there.
     *
     * <p>Unlike {@link #allocate}, this <b>adapts rather than refuses</b>. The candidate list comes
     * from {@code v_open_items}, which is an ordinary read and therefore a snapshot: by the time a
     * candidate is locked, another transaction may have settled some or all of it. Treating that as
     * an error would mean two payouts to the same counterparty could not run at once — one would
     * roll back rather than queue — for a situation that is not a conflict at all. So each
     * candidate is re-measured under its own lock and takes whatever genuinely fits, and one that
     * fits nothing is simply skipped. The plan is a suggestion; the locked read is the decision.
     *
     * <p>Running out of candidates with amount left over is not a failure either: a settling line
     * bigger than everything open stays partly open itself, which is exactly what a payment on
     * account, or a clawback against a counterparty holding nothing, should look like.
     */
    @Transactional
    public List<ItemAllocation> settleOldestFirst(UUID accountUuid, UUID settleLineUuid, Money amount) {
        List<ItemAllocation> made = new ArrayList<>();
        long remaining = amount.amountMinor();
        if (remaining <= 0) {
            return made;
        }

        for (Object[] row : openItemsOldestFirst(accountUuid, settleLineUuid)) {
            if (remaining <= 0) {
                break;
            }
            UUID openLineUuid = (UUID) row[0];
            lockPair(openLineUuid, settleLineUuid);

            long take = Math.min(remaining, Math.min(roomOn(openLineUuid), roomOn(settleLineUuid)));
            if (take <= 0) {
                continue;
            }
            made.add(insert(openLineUuid, settleLineUuid, Money.of(take, amount.currency())));
            remaining -= take;
        }
        return made;
    }

    private void requireSensible(UUID openLineUuid, UUID settleLineUuid, Money amount) {
        if (!amount.isPositive()) {
            throw new AllocationRejectedException(
                    "An allocation must be positive; " + amount + " closes nothing");
        }
        if (openLineUuid.equals(settleLineUuid)) {
            throw new AllocationRejectedException("A line cannot settle itself");
        }
    }

    private ItemAllocation insert(UUID openLineUuid, UUID settleLineUuid, Money amount) {
        ItemAllocation allocation = new ItemAllocation();
        allocation.setTenantCode(caller.tenantCode());
        allocation.setOpenLineUuid(openLineUuid);
        allocation.setSettleLineUuid(settleLineUuid);
        allocation.setAmountMinor(amount.amountMinor());
        allocation.setAllocatedBy(caller.actorId());
        allocation.persist();
        // Forced now rather than at commit so a rejection surfaces here, where the caller can still
        // tell which pair failed.
        em.flush();
        return allocation;
    }

    /**
     * Locks both lines, in {@code uuid} order.
     *
     * <p>The same order {@code trg_item_allocations_within_open} uses, and for the same reason
     * {@code ledger_post_entry()} sorts the accounts it locks: two allocations over the same pair in
     * opposite roles would otherwise take the rows in opposite orders and deadlock. Re-locking a row
     * this transaction already holds is free, which is what lets the loop above call this per
     * candidate without tracking what it has already taken.
     */
    private void lockPair(UUID openLineUuid, UUID settleLineUuid) {
        UUID first = openLineUuid.compareTo(settleLineUuid) <= 0 ? openLineUuid : settleLineUuid;
        UUID second = first.equals(openLineUuid) ? settleLineUuid : openLineUuid;
        lockLine(first);
        lockLine(second);
    }

    /** Takes the row lock. The value is discarded; acquiring the lock is the whole purpose. */
    private void lockLine(UUID lineUuid) {
        em.createNativeQuery("SELECT amount_minor FROM journal_lines WHERE uuid = ?1 FOR UPDATE")
                .setParameter(1, lineUuid)
                .getResultList();
    }

    /**
     * How much of a line is still unallocated, read under lock.
     *
     * <p>Both reads are locking, which is the point: {@link #lockPair} serialises the transactions,
     * but a plain {@code SELECT} would still answer from a snapshot established before the wait and
     * report a total that predates whoever just committed.
     */
    private long roomOn(UUID lineUuid) {
        Object faceValue = em.createNativeQuery(
                        "SELECT amount_minor FROM journal_lines WHERE uuid = ?1 FOR UPDATE")
                .setParameter(1, lineUuid)
                .getResultStream().findFirst().orElse(null);
        if (faceValue == null) {
            throw new AllocationRejectedException("Journal line " + lineUuid + " does not exist");
        }

        List<?> used = em.createNativeQuery(
                        "SELECT amount_minor FROM item_allocations "
                                + "WHERE open_line_uuid = ?1 OR settle_line_uuid = ?1 FOR UPDATE")
                .setParameter(1, lineUuid)
                .getResultList();
        long allocated = used.stream().mapToLong(value -> ((Number) value).longValue()).sum();
        return ((Number) faceValue).longValue() - allocated;
    }

    /**
     * The account's still-open items facing the opposite way to the settling line.
     *
     * <p>Direction is filtered here rather than left to the trigger. The trigger rejects a
     * same-direction pair — correctly, since two debits cannot settle each other — but reaching it
     * would mean this method had chosen an item it should never have offered, and the caller would
     * see a constraint violation instead of an allocation.
     *
     * <p>Pairs already allocated are excluded for a duller reason: {@code uq_item_allocations_pair}
     * makes a line pair unique, so offering one twice would fail on the constraint rather than
     * simply top up.
     */
    @SuppressWarnings("unchecked")
    private List<Object[]> openItemsOldestFirst(UUID accountUuid, UUID settleLineUuid) {
        return em.createNativeQuery("""
                SELECT o.line_uuid, o.open_minor
                  FROM v_open_items o
                  JOIN journal_lines s ON s.uuid = ?2
                 WHERE o.account_uuid = ?1
                   AND o.open_minor > 0
                   AND o.line_uuid <> s.uuid
                   AND o.direction <> s.direction
                   AND NOT EXISTS (
                       SELECT 1 FROM item_allocations a
                        WHERE a.open_line_uuid = o.line_uuid AND a.settle_line_uuid = s.uuid
                   )
                 ORDER BY o.effective_at, o.line_uuid
                """)
                .setParameter(1, accountUuid)
                .setParameter(2, settleLineUuid)
                .getResultList();
    }
}

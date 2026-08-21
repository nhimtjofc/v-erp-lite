package base.l2.ledger;

import base.l0.system.money.Money;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.allocation.application.AllocateItemUC;
import base.l2.ledger.allocation.domain.AllocationRejectedException;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.settlement.api.CaptureRequest;
import base.l2.settlement.intent.application.CapturePaymentUC;
import base.l2.settlement.payout.application.InstructPayoutUC;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing open items, and the aging report that depends on it.
 *
 * <p>The point of every assertion here is the difference between a balance and an open item. An
 * account's balance nets everything; an open item is a specific line that has not been settled by a
 * specific other line. A seller paid in full has a zero balance either way — but only allocation
 * makes their escrow stop being reported as outstanding.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class OpenItemAllocationTest {

    @Inject
    CapturePaymentUC capture;

    @Inject
    InstructPayoutUC instructPayout;

    @Inject
    AllocateItemUC allocate;

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    @Test
    void aPayoutClosesTheEscrowItPaysDown() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");
        LedgerAccount seller = fixture.seller(sellerId, "EUR", 0);

        capture(sellerId, 10_000, 1_000, "ORD-A");
        assertEquals(9_000L, openMinor(seller), "escrow is open until something settles it");

        instructPayout.instruct(UUID.randomUUID(), sellerId, Money.of(9_000, "EUR"), null);

        assertEquals(0L, openMinor(seller), "the payout closed it; nothing is outstanding");
        assertEquals(0L, fixture.balanceOf(seller.getUuid()));
    }

    @Test
    void aPartialPayoutClosesTheOldestFirstAndLeavesTheRest() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");
        LedgerAccount seller = fixture.seller(sellerId, "EUR", 0);

        capture(sellerId, 5_000, 0, "ORD-B1");
        capture(sellerId, 3_000, 0, "ORD-B2");
        assertEquals(8_000L, openMinor(seller));

        // Enough for the first order and part of the second.
        instructPayout.instruct(UUID.randomUUID(), sellerId, Money.of(6_000, "EUR"), null);

        assertEquals(2_000L, openMinor(seller),
                "FIFO: the older item closes completely before the newer one is touched");
        assertEquals(1, openItemCount(seller), "and only one item is left open, not two partial ones");
    }

    @Test
    void anAllocationCannotExceedWhatIsStillOpen() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");
        // Credit limit deliberately generous: the oversized debit below has to be allowed to POST,
        // otherwise the balance guard rejects it first and the allocation guard is never reached.
        LedgerAccount seller = fixture.seller(sellerId, "EUR", 20_000);
        capture(sellerId, 4_000, 0, "ORD-C");

        UUID escrowLine = oldestOpenLine(seller);
        UUID settleLine = UUID.randomUUID();
        postEntry.post(PostingRequest.of("MANUAL_ADJUSTMENT", "alloc-test:" + UUID.randomUUID())
                .line(PostingLine.debit(settleLine, seller.getUuid(), Money.of(9_000, "EUR")))
                .line(PostingLine.credit(fixture.newId(), fixture.psp("EUR").getUuid(), Money.of(9_000, "EUR")))
                .build());

        // The guard is the trigger, not this class — checking it in Java would be a race between
        // two people settling the same item.
        assertThrows(RuntimeException.class,
                () -> allocate.allocate(escrowLine, settleLine, Money.of(9_000, "EUR")),
                "an allocation may not exceed the 4,000 still open on the escrow line");
    }

    @Test
    void aLineCannotSettleItself() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");
        LedgerAccount seller = fixture.seller(sellerId, "EUR", 0);
        capture(sellerId, 1_000, 0, "ORD-D");
        UUID line = oldestOpenLine(seller);

        assertThrows(AllocationRejectedException.class,
                () -> allocate.allocate(line, line, Money.of(100, "EUR")));
    }

    @Test
    void escrowCarriesTheDueDateItsTermsImply() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");
        LedgerAccount seller = fixture.sellerOnTerms(sellerId, "EUR", (short) 14);

        capture(sellerId, 2_000, 0, "ORD-E");

        LocalDate due = dueDateOfOldestOpenLine(seller);
        assertEquals(LocalDate.now(ZoneOffset.UTC).plusDays(14), due,
                "without this every aging bucket collapses into 'current'");
    }

    // ---- helpers ----------------------------------------------------------------------

    private void capture(UUID sellerId, long gross, long commission, String orderNumber) {
        capture.capture(new CaptureRequest(
                UUID.randomUUID(), orderNumber, UUID.randomUUID(), "mock", "ch_" + orderNumber,
                Money.of(gross, "EUR"), null,
                List.of(new CaptureRequest.SellerSplit(sellerId, Money.of(gross, "EUR"),
                        Money.of(commission, "EUR"))),
                "capture:" + UUID.randomUUID()));
    }

    @Transactional
    long openMinor(LedgerAccount account) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(SUM(open_minor), 0) FROM v_open_items WHERE account_uuid = ?1")
                .setParameter(1, account.getUuid())
                .getSingleResult();
        return ((Number) value).longValue();
    }

    @Transactional
    int openItemCount(LedgerAccount account) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COUNT(*) FROM v_open_items WHERE account_uuid = ?1 AND open_minor > 0")
                .setParameter(1, account.getUuid())
                .getSingleResult();
        return ((Number) value).intValue();
    }

    @Transactional
    UUID oldestOpenLine(LedgerAccount account) {
        em.clear();
        List<?> rows = em.createNativeQuery(
                        "SELECT line_uuid FROM v_open_items WHERE account_uuid = ?1 AND open_minor > 0 "
                                + "ORDER BY effective_at, line_uuid")
                .setParameter(1, account.getUuid())
                .getResultList();
        assertTrue(!rows.isEmpty(), "expected an open item");
        return (UUID) rows.getFirst();
    }

    @Transactional
    LocalDate dueDateOfOldestOpenLine(LedgerAccount account) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT due_date FROM v_open_items WHERE account_uuid = ?1 AND open_minor > 0 "
                                + "ORDER BY effective_at, line_uuid LIMIT 1")
                .setParameter(1, account.getUuid())
                .getSingleResult();
        return switch (value) {
            case LocalDate date -> date;
            case java.sql.Date date -> date.toLocalDate();
            case null, default -> null;
        };
    }
}


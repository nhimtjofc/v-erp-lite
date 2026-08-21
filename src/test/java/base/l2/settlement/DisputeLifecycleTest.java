package base.l2.settlement;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.settlement.api.CaptureRequest;
import base.l2.settlement.dispute.application.OpenDisputeUC;
import base.l2.settlement.dispute.application.ResolveDisputeUC;
import base.l2.settlement.dispute.domain.DisputeRejectedException;
import base.l2.settlement.dispute.domain.DisputeShare;
import base.l2.settlement.intent.application.CapturePaymentUC;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A chargeback from the moment it is raised to the moment someone absorbs it.
 *
 * <p>The assertion that matters in every one of these is the clearing balance for the dispute's
 * reconcile group. A dispute is not "correct" because the numbers balance — every entry balances by
 * the posting procedure's own rule — it is correct when the contested amount has stopped being
 * contested, and that is what nets to zero.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class DisputeLifecycleTest {

    @Inject
    CapturePaymentUC capture;

    @Inject
    OpenDisputeUC openDispute;

    @Inject
    ResolveDisputeUC resolveDispute;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    EntityManager em;

    @Test
    void openingADisputeTakesItFromTheProviderAndNobodyElse() {
        UUID partyId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        LedgerAccount psp = fixture.psp("EUR");
        LedgerAccount party = fixture.seller(partyId, "EUR", 0);
        LedgerAccount held = fixture.disputeHeld("EUR");

        capture(partyId, 10_000, "ORD-D1");
        long pspAfterCapture = fixture.balanceOf(psp.getUuid());
        long heldBefore = fixture.balanceOf(held.getUuid());

        openDispute.open(disputeId, "mock", "dp_1", Money.of(10_000, "EUR"), "Buyer says not received");

        assertEquals(pspAfterCapture - 10_000, fixture.balanceOf(psp.getUuid()),
                "the acquirer takes it back the moment the chargeback is raised");
        assertEquals(10_000L, fixture.balanceOf(held.getUuid()) - heldBefore,
                "and it sits as a claim we are contesting, which is a debit — we might get it back");
        assertEquals(-10_000L, fixture.balanceOf(party.getUuid()),
                "the counterparty is untouched; a dispute is not yet a loss");
        assertEquals(10_000L, clearingNet(disputeId), "the lifecycle is open and says so");
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
    }

    @Test
    void winningPutsItBackAndClosesTheGroup() {
        UUID partyId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        LedgerAccount psp = fixture.psp("EUR");
        LedgerAccount party = fixture.seller(partyId, "EUR", 0);

        capture(partyId, 8_000, "ORD-D2");
        long pspAfterCapture = fixture.balanceOf(psp.getUuid());

        openDispute.open(disputeId, "mock", "dp_2", Money.of(8_000, "EUR"), null);
        resolveDispute.won(disputeId, "mock", Money.of(8_000, "EUR"));

        assertEquals(pspAfterCapture, fixture.balanceOf(psp.getUuid()), "the provider holds it again");
        assertEquals(-8_000L, fixture.balanceOf(party.getUuid()),
                "and the counterparty never spent a day appearing to owe us over a charge they won");
        assertEquals(0L, clearingNet(disputeId), "nothing is contested any more");
    }

    @Test
    void losingReducesTheCounterpartyWhoStillHasFundsWithUs() {
        UUID partyId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        fixture.psp("EUR");
        LedgerAccount party = fixture.seller(partyId, "EUR", 0);

        capture(partyId, 6_000, "ORD-D3");
        assertEquals(-6_000L, fixture.balanceOf(party.getUuid()));

        openDispute.open(disputeId, "mock", "dp_3", Money.of(6_000, "EUR"), null);
        resolveDispute.lost(disputeId, Money.of(6_000, "EUR"),
                List.of(new DisputeShare("seller", partyId, Money.of(6_000, "EUR"), 0L)),
                "Issuer upheld the chargeback");

        assertEquals(0L, fixture.balanceOf(party.getUuid()),
                "their held funds absorb it; they owe nothing extra");
        assertEquals(0L, openMinor(party), "and the item it paid down is closed, not left open");
        assertEquals(0L, clearingNet(disputeId));
    }

    @Test
    void losingAfterThePartyWasPaidLeavesARealReceivable() {
        UUID partyId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        fixture.psp("EUR");
        // A credit limit is what makes the clawback postable at all. Without it the balance guard
        // refuses, which is the correct answer for a counterparty we have decided not to extend
        // credit to — the loss then has to be written off instead.
        LedgerAccount party = fixture.seller(partyId, "EUR", 10_000);

        openDispute.open(disputeId, "mock", "dp_4", Money.of(3_000, "EUR"), null);
        resolveDispute.lost(disputeId, Money.of(3_000, "EUR"),
                List.of(new DisputeShare("seller", partyId, Money.of(3_000, "EUR"), 10_000L)), null);

        assertEquals(3_000L, fixture.balanceOf(party.getUuid()),
                "nothing of theirs was held, so this is money they now owe us");
        assertEquals(3_000L, openMinor(party), "and it is an open item somebody has to collect");
        assertEquals(0L, clearingNet(disputeId));
    }

    @Test
    void whatNobodyCarriesIsWrittenOffRatherThanLeftContested() {
        UUID partyId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        fixture.psp("EUR");
        fixture.seller(partyId, "EUR", 0);
        LedgerAccount writeOff = fixture.expense(AccountCode.EXPENSE_WRITE_OFF, "EUR");
        long writeOffBefore = fixture.balanceOf(writeOff.getUuid());

        capture(partyId, 2_000, "ORD-D5");
        openDispute.open(disputeId, "mock", "dp_5", Money.of(5_000, "EUR"), null);

        // Only 2,000 is recoverable from the counterparty; the rest is ours to eat.
        resolveDispute.lost(disputeId, Money.of(5_000, "EUR"),
                List.of(new DisputeShare("seller", partyId, Money.of(2_000, "EUR"), 0L)), null);

        assertEquals(3_000L, fixture.balanceOf(writeOff.getUuid()) - writeOffBefore,
                "a platform loss is named, not left sitting in a clearing account looking unfinished");
        assertEquals(0L, clearingNet(disputeId));
    }

    @Test
    void sharesMayNotExceedTheAmountLost() {
        UUID partyId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        fixture.psp("EUR");
        fixture.seller(partyId, "EUR", 10_000);
        openDispute.open(disputeId, "mock", "dp_6", Money.of(1_000, "EUR"), null);

        assertThrows(DisputeRejectedException.class, () -> resolveDispute.lost(
                disputeId, Money.of(1_000, "EUR"),
                List.of(new DisputeShare("seller", partyId, Money.of(4_000, "EUR"), 10_000L)), null));
    }

    @Test
    void anUnresolvedDisputeKeepsShowingUp() {
        UUID disputeId = UUID.randomUUID();
        fixture.psp("EUR");
        openDispute.open(disputeId, "mock", "dp_7", Money.of(4_400, "EUR"), null);

        assertEquals(4_400L, clearingNet(disputeId),
                "the failure mode of a chargeback is not a wrong number, it is a case nobody answered");
    }

    // ---- helpers ----------------------------------------------------------------------

    private void capture(UUID partyId, long gross, String orderNumber) {
        capture.capture(new CaptureRequest(
                UUID.randomUUID(), orderNumber, UUID.randomUUID(), "mock", "ch_" + orderNumber,
                Money.of(gross, "EUR"), null,
                List.of(new CaptureRequest.SellerSplit(partyId, Money.of(gross, "EUR"), Money.zero("EUR"))),
                "capture:" + UUID.randomUUID()));
    }

    /** What the dispute's reconcile group still has parked in clearing accounts. */
    @Transactional
    long clearingNet(UUID disputeId) {
        em.clear();
        Object value = em.createNativeQuery(
                        "SELECT COALESCE(SUM(clearing_net_minor), 0) FROM v_clearing_group_balance "
                                + "WHERE tenant_code = 'acme' AND reconcile_group = ?1")
                .setParameter(1, OpenDisputeUC.disputeGroup(disputeId))
                .getSingleResult();
        return ((Number) value).longValue();
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
}


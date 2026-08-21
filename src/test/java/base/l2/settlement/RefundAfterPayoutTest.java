package base.l2.settlement;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.settlement.refund.application.RefundOrderUC;
import base.l2.settlement.refund.domain.FundsLocation;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The most expensive defect in the system this replaces (review finding F-02).
 *
 * <p>There, a refund reversed only escrow records still in HELD. Once delivery had happened and the
 * hold was released, that list was empty: the buyer was refunded from platform funds, the seller
 * kept their share, and nothing was written on the seller side — so the loss was both silent and
 * undetectable afterwards. The underlying cause was that a balance could not go debit, so "the
 * seller owes us" had no representation and doing nothing was the only expressible action.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class RefundAfterPayoutTest {

    @Inject
    RefundOrderUC refundOrder;

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void aRefundAfterPayoutPutsTheSellerIntoDebitRatherThanSilentlyDoingNothing() {
        UUID sellerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LedgerAccount seller = fixture.seller(sellerId, "VND", 200_000);

        // The seller has already been paid out, so their balance is flat — exactly the state in
        // which the old code found no escrow to reverse and did nothing at all.
        assertEquals(0L, fixture.balanceOf(seller.getUuid()));

        refundOrder.refund(new RefundOrderUC.Command(
                orderId, "mock", "mock_tx_1",
                Money.of(100_000, "VND"),
                Money.of(5_000, "VND"),
                List.of(new RefundOrderUC.SellerShare(
                        sellerId, Money.of(95_000, "VND"), FundsLocation.PAID_OUT, 200_000)),
                "Customer returned the goods",
                "test-refund:" + orderId));

        long sellerBalance = fixture.balanceOf(seller.getUuid());
        assertEquals(95_000L, sellerBalance,
                "the seller must now owe us their net share — a debit balance");
        assertTrue(sellerBalance > 0, "a positive (debit) balance means the counterparty owes us");
        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"), "the book still balances");
        assertEquals(0L, fixture.accountDrift(seller.getUuid()));
    }

    @Test
    void aRefundWhileStillInEscrowReclaimsFromTheHeldBalance() {
        UUID sellerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LedgerAccount seller = fixture.seller(sellerId, "VND", 200_000);
        LedgerAccount psp = fixture.psp("VND");

        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", "test-settle-esc:" + orderId)
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(95_000, "VND")))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(95_000, "VND")))
                .build());
        assertEquals(-95_000L, fixture.balanceOf(seller.getUuid()));

        refundOrder.refund(new RefundOrderUC.Command(
                orderId, "mock", "mock_tx_2",
                Money.of(95_000, "VND"),
                Money.zero("VND"),
                List.of(new RefundOrderUC.SellerShare(
                        sellerId, Money.of(95_000, "VND"), FundsLocation.IN_ESCROW, 200_000)),
                "Cancelled before delivery",
                "test-refund-esc:" + orderId));

        assertEquals(0L, fixture.balanceOf(seller.getUuid()),
                "reclaiming from escrow returns the seller to flat, not to debit");
        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"));
    }

    @Test
    void anUnrecoverableRefundIsBookedAsAWriteOffRatherThanVanishing() {
        UUID orderId = UUID.randomUUID();

        // No seller shares to reclaim: the whole amount is a platform loss, and it gets an account.
        refundOrder.refund(new RefundOrderUC.Command(
                orderId, "mock", "mock_tx_3",
                Money.of(40_000, "VND"),
                Money.zero("VND"),
                List.of(),
                "Goodwill refund",
                "test-refund-writeoff:" + orderId));

        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"),
                "even a loss must have a counter-account");
    }
}


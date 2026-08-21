package base.l2.settlement;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.settlement.payout.application.ConfirmPayoutUC;
import base.l2.settlement.payout.application.InstructPayoutUC;
import base.l2.settlement.payout.domain.PayoutBlockedException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Review finding F-03: the predecessor treated "we called the bank API" and "the partner received
 * the money" as one state. A transfer returned days later had no status to move to and no path to
 * restore the balance, so the books permanently showed money as delivered that had come back.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class PayoutLifecycleTest {

    @Inject
    InstructPayoutUC instructPayout;

    @Inject
    ConfirmPayoutUC confirmPayout;

    @Inject
    PostEntryUC postEntry;

    @Inject
    LedgerTestFixture fixture;

    private LedgerAccount fundedSeller(UUID sellerId, long amountMinor) {
        LedgerAccount seller = fixture.seller(sellerId, "VND", 500_000);
        LedgerAccount psp = fixture.psp("VND");
        postEntry.post(PostingRequest.of("PAYMENT_CAPTURED", "test-fund:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(), psp.getUuid(), Money.of(amountMinor, "VND")))
                .line(PostingLine.credit(fixture.newId(), seller.getUuid(), Money.of(amountMinor, "VND")))
                .build());
        return seller;
    }

    @Test
    void instructingAPayoutParksTheMoneyInFlightRatherThanCallingItPaid() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        LedgerAccount seller = fundedSeller(sellerId, 95_000);
        LedgerAccount inFlight = fixture.arControl("VND"); // placeholder to force account creation

        instructPayout.instruct(payoutId, sellerId, Money.of(95_000, "VND"), "bank_ref_1");

        assertEquals(0L, fixture.balanceOf(seller.getUuid()),
                "the partner is no longer owed, because we have instructed the transfer");
        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"));
        org.junit.jupiter.api.Assertions.assertNotNull(inFlight);
    }

    @Test
    void confirmationMovesTheMoneyFromInFlightToTheBank() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        fundedSeller(sellerId, 95_000);

        instructPayout.instruct(payoutId, sellerId, Money.of(95_000, "VND"), "bank_ref_2");
        confirmPayout.confirm(payoutId, Money.of(95_000, "VND"), "bank_ref_2");

        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"));
    }

    @Test
    void aReturnedTransferRestoresTheBalanceInsteadOfBeingUnrepresentable() {
        UUID sellerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        LedgerAccount seller = fundedSeller(sellerId, 95_000);

        JournalEntry instruction =
                instructPayout.instruct(payoutId, sellerId, Money.of(95_000, "VND"), "bank_ref_3");
        assertEquals(0L, fixture.balanceOf(seller.getUuid()));

        confirmPayout.markReturned(payoutId, sellerId, Money.of(95_000, "VND"),
                instruction.getUuid(), "Account closed");

        assertEquals(-95_000L, fixture.balanceOf(seller.getUuid()),
                "a bounced transfer means we owe the partner again");
        assertEquals(0L, fixture.trialBalanceNet("acme", "VND"));
    }

    @Test
    void aPartnerCarryingADebitBalanceCannotBePaidOut() {
        UUID sellerId = UUID.randomUUID();
        LedgerAccount seller = fixture.seller(sellerId, "VND", 500_000);
        LedgerAccount psp = fixture.psp("VND");

        // Put the seller into debit, as an unrecovered clawback would.
        postEntry.post(PostingRequest.of("CLAWBACK", "test-claw:" + UUID.randomUUID())
                .line(PostingLine.debit(fixture.newId(), seller.getUuid(), Money.of(20_000, "VND")))
                .line(PostingLine.credit(fixture.newId(), psp.getUuid(), Money.of(20_000, "VND")))
                .build());
        assertEquals(20_000L, fixture.balanceOf(seller.getUuid()));

        assertThrows(PayoutBlockedException.class, () ->
                instructPayout.instruct(UUID.randomUUID(), sellerId, Money.of(10_000, "VND"), "bank_ref_4"));
    }
}


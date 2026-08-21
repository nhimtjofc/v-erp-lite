package base.l2.settlement;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.settlement.api.CaptureRequest;
import base.l2.settlement.api.CaptureResult;
import base.l2.settlement.intent.application.CapturePaymentUC;
import base.l2.settlement.intent.domain.CaptureRejectedException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Capturing a payment, and in particular where the provider's own fee goes.
 *
 * <p>Balances are asserted as deltas: the suite shares one database and the commission and fee
 * accounts are global to a currency, so absolute values would depend on test order.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class CapturePaymentTest {

    @Inject
    CapturePaymentUC capture;

    @Inject
    LedgerTestFixture fixture;

    @Test
    void theProviderIsOnlyOwedWhatItActuallyHolds() {
        UUID sellerId = UUID.randomUUID();
        LedgerAccount psp = fixture.psp("EUR");
        LedgerAccount fees = fixture.expense(AccountCode.EXPENSE_PSP_FEES, "EUR");
        LedgerAccount commission = fixture.commission("EUR");
        LedgerAccount seller = fixture.seller(sellerId, "EUR", 0);

        long pspBefore = fixture.balanceOf(psp.getUuid());
        long feesBefore = fixture.balanceOf(fees.getUuid());
        long commissionBefore = fixture.balanceOf(commission.getUuid());

        // 100.00 charged, the provider keeps 2.90, we keep 10.00, the seller gets 90.00.
        CaptureResult result = capture.capture(new CaptureRequest(
                UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "mock", "ch_1",
                Money.of(10_000, "EUR"), Money.of(290, "EUR"),
                List.of(new CaptureRequest.SellerSplit(sellerId, Money.of(10_000, "EUR"), Money.of(1_000, "EUR"))),
                "capture:" + UUID.randomUUID()));

        assertEquals(9_710L, fixture.balanceOf(psp.getUuid()) - pspBefore,
                "the provider owes us the net; debiting gross would claim money they never send");
        assertEquals(290L, fixture.balanceOf(fees.getUuid()) - feesBefore,
                "their fee is our expense, and it must be nameable");
        assertEquals(-1_000L, fixture.balanceOf(commission.getUuid()) - commissionBefore,
                "our commission is revenue and stays separate from their fee");
        assertEquals(-9_000L, fixture.balanceOf(seller.getUuid()),
                "the seller is owed gross less our commission, untouched by what the provider charged us");

        assertEquals(Money.of(9_710, "EUR"), result.netReceivable());
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
        assertEquals(0L, fixture.accountDrift(psp.getUuid()));
    }

    @Test
    void aProviderThatSettlesGrossPostsNoFeeLine() {
        UUID sellerId = UUID.randomUUID();
        LedgerAccount psp = fixture.psp("EUR");
        LedgerAccount fees = fixture.expense(AccountCode.EXPENSE_PSP_FEES, "EUR");
        long pspBefore = fixture.balanceOf(psp.getUuid());
        long feesBefore = fixture.balanceOf(fees.getUuid());

        // Null rather than zero: some providers invoice their fees monthly instead of netting them.
        capture.capture(new CaptureRequest(
                UUID.randomUUID(), "ORD-2", UUID.randomUUID(), "mock", "ch_2",
                Money.of(5_000, "EUR"), null,
                List.of(new CaptureRequest.SellerSplit(sellerId, Money.of(5_000, "EUR"), Money.of(500, "EUR"))),
                "capture:" + UUID.randomUUID()));

        assertEquals(5_000L, fixture.balanceOf(psp.getUuid()) - pspBefore);
        assertEquals(0L, fixture.balanceOf(fees.getUuid()) - feesBefore,
                "a zero-amount line would be refused by the posting procedure, so none is written");
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
    }

    @Test
    void aFeeLargerThanTheChargeIsRefused() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");

        assertThrows(CaptureRejectedException.class, () -> capture.capture(new CaptureRequest(
                UUID.randomUUID(), "ORD-3", UUID.randomUUID(), "mock", "ch_3",
                Money.of(1_000, "EUR"), Money.of(1_500, "EUR"),
                List.of(new CaptureRequest.SellerSplit(sellerId, Money.of(1_000, "EUR"), Money.zero("EUR"))),
                "capture:" + UUID.randomUUID())));
    }

    @Test
    void aFeeInAnotherCurrencyIsRefusedRatherThanConverted() {
        UUID sellerId = UUID.randomUUID();
        fixture.psp("EUR");

        // There is no rate here, and inventing one is exactly what this service refuses to do.
        assertThrows(CaptureRejectedException.class, () -> capture.capture(new CaptureRequest(
                UUID.randomUUID(), "ORD-4", UUID.randomUUID(), "mock", "ch_4",
                Money.of(10_000, "EUR"), Money.of(290, "USD"),
                List.of(new CaptureRequest.SellerSplit(sellerId, Money.of(10_000, "EUR"), Money.zero("EUR"))),
                "capture:" + UUID.randomUUID())));
    }
}


package base.l0;

import base.l0.system.money.CurrencyExponents;
import base.l0.system.money.CurrencyMismatchException;
import base.l0.system.money.Money;
import base.l0.system.money.UnknownCurrencyException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void usesRealIso4217ExponentsRatherThanTwoForEverythingButVnd() {
        // The predecessor returned 0 for VND and 2 for all else, which silently mis-scaled JPY and
        // KRW by 100 and the three-decimal Gulf currencies by 10.
        assertEquals(0, CurrencyExponents.of("VND"));
        assertEquals(0, CurrencyExponents.of("JPY"));
        assertEquals(0, CurrencyExponents.of("KRW"));
        assertEquals(3, CurrencyExponents.of("KWD"));
        assertEquals(3, CurrencyExponents.of("BHD"));
        assertEquals(2, CurrencyExponents.of("USD"));
    }

    @Test
    void unknownCurrencyIsRejectedRatherThanAssumedToHaveTwoDecimals() {
        assertThrows(UnknownCurrencyException.class, () -> Money.of(100, "XYZ"));
    }

    @Test
    void moneyMayBeNegativeBecauseACounterpartyCanOweUs() {
        // The value object this replaces threw on any non-positive amount, which is the reason a
        // receivable could not be expressed at all. Positivity is a posting rule, not a property
        // of the number.
        Money owed = Money.of(-95_000, "VND");
        assertTrue(owed.isNegative());
        assertEquals(95_000, owed.abs().amountMinor());
        assertEquals(95_000, owed.negate().amountMinor());
    }

    @Test
    void vndCarriesNoPhantomMinorUnits() {
        Money amount = Money.fromDecimal(new BigDecimal("100000"), "VND");
        assertEquals(100_000, amount.amountMinor());
        assertEquals(new BigDecimal("100000"), amount.toDecimal());
    }

    @Test
    void arithmeticAcrossCurrenciesIsRefused() {
        assertThrows(CurrencyMismatchException.class,
                () -> Money.of(100, "VND").add(Money.of(100, "USD")));
    }

    @Test
    void percentageRoundsHalfUpToTheMinorUnit() {
        assertEquals(5_000, Money.of(100_000, "VND").percentage(new BigDecimal("5")).amountMinor());
        // 100,001 * 5% = 5000.05 -> 5000
        assertEquals(5_000, Money.of(100_001, "VND").percentage(new BigDecimal("5")).amountMinor());
        // 100,010 * 5% = 5000.5 -> 5001 (HALF_UP)
        assertEquals(5_001, Money.of(100_010, "VND").percentage(new BigDecimal("5")).amountMinor());
    }

    @Test
    void overflowRaisesRatherThanWrapping() {
        assertThrows(ArithmeticException.class,
                () -> Money.of(Long.MAX_VALUE, "VND").add(Money.of(1, "VND")));
    }
}


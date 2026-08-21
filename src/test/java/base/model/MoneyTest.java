package base.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MoneyTest {

    @Test
    public void createsVndMoney() {
        Money money = Money.of(10_250, "VND");
        assertEquals(10_250, money.getAmountMinor());
        assertEquals("VND", money.getCurrency());
    }

    @Test
    public void convertsVndDecimalUsingZeroExponent() {
        Money roundedUp = Money.fromDecimal(new BigDecimal("50000.5"), "VND");
        Money roundedDown = Money.fromDecimal(new BigDecimal("50000.4"), "VND");

        assertEquals(50_001, roundedUp.getAmountMinor());
        assertEquals(50_000, roundedDown.getAmountMinor());
        assertEquals(new BigDecimal("50001"), roundedUp.toDecimal());
    }

    @Test
    public void rejectsUnsupportedCurrencyConversion() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.fromDecimal(new BigDecimal("10"), "USD"));
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(10, "USD").toDecimal());
    }

    @Test
    public void addsAndSubtractsVnd() {
        Money amount = Money.of(10_000, "VND");

        assertEquals(15_000, amount.add(Money.of(5_000, "VND")).getAmountMinor());
        assertEquals(7_000, amount.subtract(Money.of(3_000, "VND")).getAmountMinor());
    }

    @Test
    public void multipliesVndWithHalfUpRounding() {
        Money commission = Money.of(10_250, "VND").multiply(0.05);
        assertEquals(513, commission.getAmountMinor());
        assertEquals("VND", commission.getCurrency());
    }

    @Test
    public void equalityUsesAmountAndCurrency() {
        assertEquals(Money.of(100, "VND"), Money.of(100, "VND"));
        assertNotEquals(Money.of(100, "VND"), Money.of(200, "VND"));
    }
}
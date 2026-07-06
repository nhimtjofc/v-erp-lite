package base.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class MoneyTest {

    @Test
    public void testCreationAndProperties() {
        Money m = Money.of(1025, "USD");
        assertEquals(1025, m.getAmountMinor());
        assertEquals("USD", m.getCurrency());
    }

    @Test
    public void testFromDecimalUSD() {
        // USD has exponent 2
        Money m1 = Money.fromDecimal(new BigDecimal("10.25"), "USD");
        assertEquals(1025, m1.getAmountMinor());

        // Half up rounding test: 10.255 -> 10.26 -> 1026
        Money m2 = Money.fromDecimal(new BigDecimal("10.255"), "USD");
        assertEquals(1026, m2.getAmountMinor());

        Money m3 = Money.fromDecimal(new BigDecimal("10.254"), "USD");
        assertEquals(1025, m3.getAmountMinor());
    }

    @Test
    public void testFromDecimalVND() {
        // VND has exponent 0
        Money m1 = Money.fromDecimal(new BigDecimal("50000"), "VND");
        assertEquals(50000, m1.getAmountMinor());

        // Half up rounding test: 50000.5 -> 50001
        Money m2 = Money.fromDecimal(new BigDecimal("50000.5"), "VND");
        assertEquals(50001, m2.getAmountMinor());
    }

    @Test
    public void testToDecimal() {
        Money mUsd = Money.of(1025, "USD");
        assertEquals(new BigDecimal("10.25"), mUsd.toDecimal());

        Money mVnd = Money.of(50000, "VND");
        assertEquals(new BigDecimal("50000"), mVnd.toDecimal());
    }

    @Test
    public void testAdd() {
        Money m1 = Money.of(1000, "USD");
        Money m2 = Money.of(500, "USD");
        Money result = m1.add(m2);
        assertEquals(1500, result.getAmountMinor());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    public void testSubtract() {
        Money m1 = Money.of(1000, "USD");
        Money m2 = Money.of(300, "USD");
        Money result = m1.subtract(m2);
        assertEquals(700, result.getAmountMinor());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    public void testMultiply() {
        Money m = Money.of(1025, "USD"); // $10.25
        Money commission = m.multiply(0.05); // 5% commission = $0.5125 -> half-up -> $0.51 -> 51
        assertEquals(51, commission.getAmountMinor());
        assertEquals("USD", commission.getCurrency());
    }

    @Test
    public void testCurrencyMismatchThrowsException() {
        Money usd = Money.of(100, "USD");
        Money vnd = Money.of(100, "VND");
        
        Exception e1 = assertThrows(IllegalArgumentException.class, () -> usd.add(vnd));
        assertTrue(e1.getMessage().contains("Currency mismatch"));
        
        Exception e2 = assertThrows(IllegalArgumentException.class, () -> usd.subtract(vnd));
        assertTrue(e2.getMessage().contains("Currency mismatch"));
    }

    @Test
    public void testEquality() {
        Money m1 = Money.of(100, "USD");
        Money m2 = Money.of(100, "USD");
        Money m3 = Money.of(100, "VND");
        Money m4 = Money.of(200, "USD");

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
        assertNotEquals(m1, m4);
        assertEquals(m1.hashCode(), m2.hashCode());
    }
}

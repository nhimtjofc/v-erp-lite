package base.service;

import base.model.Money;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MoneyServiceTest {

    @Inject
    MoneyService moneyService;

    // ─── Currency Normalization ─────────────────────────────────────

    @Test
    void normalizeCurrencyTrimsAndUppercases() {
        assertEquals("VND", moneyService.normalizeCurrency(" vnd "));
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("USD"));
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("EUR"));
    }

    @Test
    void normalizeCurrencyDefaultsNullOrBlankToVnd() {
        assertEquals("VND", moneyService.normalizeCurrency(null));
        assertEquals("VND", moneyService.normalizeCurrency(""));
        assertEquals("VND", moneyService.normalizeCurrency("  "));
    }

    @Test
    void normalizeCurrencyRejectsWrongLength() {
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("VNDD"));
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("VN"));
        assertThrows(BadRequestException.class, () -> moneyService.normalizeCurrency("V"));
    }

    // ─── Positive Money ─────────────────────────────────────────────

    @Test
    void positiveMoneyAcceptsValidAmount() {
        Money m = moneyService.positiveMoney(100, "VND");
        assertEquals(100, m.getAmountMinor());
        assertEquals("VND", m.getCurrency());

    }

    @Test
    void positiveMoneyRejectsZero() {
        assertThrows(BadRequestException.class, () -> moneyService.positiveMoney(0, "VND"));
    }

    @Test
    void positiveMoneyRejectsNegative() {
        assertThrows(BadRequestException.class, () -> moneyService.positiveMoney(-1, "VND"));
        assertThrows(BadRequestException.class, () -> moneyService.positiveMoney(-100_000, "VND"));
    }

    @Test
    void positiveMoneyNormalizesCurrency() {
        Money m = moneyService.positiveMoney(50_000, " vnd ");
        assertEquals("VND", m.getCurrency());
    }

    // ─── Same Currency ──────────────────────────────────────────────

    @Test
    void requireSameCurrencyAcceptsMatch() {
        assertDoesNotThrow(() -> moneyService.requireSameCurrency("VND", "VND"));
        assertDoesNotThrow(() -> moneyService.requireSameCurrency(" vnd ", "VND"));
    }

    @Test
    void requireSameCurrencyRejectsMismatch() {
        assertThrows(BadRequestException.class, () -> moneyService.requireSameCurrency("VND", "USD"));
        assertThrows(BadRequestException.class, () -> moneyService.requireSameCurrency("USD", "VND"));
    }

    // ─── Money Model ────────────────────────────────────────────────

    @Test
    void moneyFromDecimalRejectsUnsupportedCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.fromDecimal(new BigDecimal("10.99"), "USD"));
    }

    @Test
    void moneyFromDecimalVnd() {
        Money m = Money.fromDecimal(new BigDecimal("50000"), "VND");
        assertEquals(50_000, m.getAmountMinor());
        assertEquals(new BigDecimal("50000"), m.toDecimal());
    }

    @Test
    void moneyFromDecimalHalfUpRounding() {
        Money m = Money.fromDecimal(new BigDecimal("10.5"), "VND");
        assertEquals(11, m.getAmountMinor());
        assertEquals(new BigDecimal("11"), m.toDecimal());

        m = Money.fromDecimal(new BigDecimal("10.4"), "VND");
        assertEquals(10, m.getAmountMinor());
        assertEquals(new BigDecimal("10"), m.toDecimal());
    }

    @Test
    void moneyFromDecimalVndFractionTruncated() {
        Money m = Money.fromDecimal(new BigDecimal("50000.678"), "VND");
        assertEquals(50_001, m.getAmountMinor(), "VND 0 exponent: 50000.678 should round to 50001");
    }

    @Test
    void moneyFromDecimalRejectsNegative() {
        assertThrows(BadRequestException.class, () -> Money.fromDecimal(new BigDecimal("-1"), "VND"));
        assertThrows(BadRequestException.class, () -> Money.fromDecimal(new BigDecimal("-0.01"), "VND"));
    }

    @Test
    void moneyZeroRejectedByFromDecimal() {
        assertThrows(BadRequestException.class, () -> Money.fromDecimal(BigDecimal.ZERO, "VND"));
    }

    // ─── Commission / Multiplication ────────────────────────────────

    @Test
    void commissionRoundingHalfUp() {
        Money amount = Money.of(100_000, "VND");
        Money commission = amount.multiply(0.05);
        assertEquals(5_000, commission.getAmountMinor());

        amount = Money.of(33_333, "VND");
        commission = amount.multiply(0.05);
        assertEquals(1_667, commission.getAmountMinor(), "33_333 * 5% = 1666.65 → HALF_UP = 1667");
    }

    @Test
    void multiplyByZeroThrows() {
        assertThrows(BadRequestException.class,
                () -> Money.of(50_000, "VND").multiply(0));
    }

    @Test
    void multiplyByOne() {
        Money m = Money.of(50_000, "VND").multiply(1);
        assertEquals(50_000, m.getAmountMinor());
    }

    // ─── Arithmetic ─────────────────────────────────────────────────

    @Test
    void moneyAddition() {
        Money a = Money.of(10_000, "VND");
        Money b = Money.of(20_000, "VND");
        Money sum = a.add(b);
        assertEquals(30_000, sum.getAmountMinor());
    }

    @Test
    void moneyAdditionRejectsCurrencyMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(100, "USD").add(Money.of(100, "VND")));
    }

    @Test
    void moneySubtraction() {
        Money a = Money.of(30_000, "VND");
        Money b = Money.of(10_000, "VND");
        Money diff = a.subtract(b);
        assertEquals(20_000, diff.getAmountMinor());
    }

    @Test
    void moneySubtractionRejectsCurrencyMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(100, "USD").subtract(Money.of(100, "VND")));
    }

    // ─── Edge Cases ─────────────────────────────────────────────────

    @Test
    void maxMinorUnitValue() {
        Money m = Money.of(Long.MAX_VALUE, "VND");
        assertEquals(Long.MAX_VALUE, m.getAmountMinor());
    }

    @Test
    void moneyEquality() {
        assertEquals(Money.of(100, "VND"), Money.of(100, "VND"));
        assertNotEquals(Money.of(100, "VND"), Money.of(200, "VND"));
    }

    @Test
    void fromDecimalZeroExponentCurrency() {
        assertEquals(100, Money.fromDecimal(new BigDecimal("100"), "VND").getAmountMinor(),
                "VND has exponent 0, so 100 VND = 100 minor units");
    }
}

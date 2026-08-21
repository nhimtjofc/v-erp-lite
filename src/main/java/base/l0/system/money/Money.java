package base.l0.system.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * An amount in integer minor units, carrying its currency.
 *
 * <p>Two deliberate differences from the ledger this service replaces. First, the currency is
 * validated against {@link CurrencyExponents} on construction, so an unrecognised code cannot enter
 * the system and be scaled as if it had two decimals. Second, <b>a Money may be negative or
 * zero</b>: the previous value object rejected non-positive amounts in its factory, which is a
 * reasonable rule for a custodial balance and the wrong rule for a ledger that has to express a
 * counterparty owing money (Teardown F-04). Positivity is a posting rule, enforced per journal
 * line, not a property of the number itself.
 *
 * <p>No floating point anywhere. Rounding happens only in {@link #fromDecimal} and
 * {@link #allocateProRata}, both HALF_UP and both explicit about it.
 */
public record Money(long amountMinor, String currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        currency = currency.trim().toUpperCase(Locale.ROOT);
        CurrencyExponents.of(currency);
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public static Money fromDecimal(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount");
        int exponent = CurrencyExponents.of(currency);
        return new Money(amount.movePointRight(exponent).setScale(0, RoundingMode.HALF_UP).longValueExact(), currency);
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(amountMinor).movePointLeft(CurrencyExponents.of(currency));
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public Money abs() {
        return amountMinor < 0 ? negate() : this;
    }

    /** Percentage of this amount, rounded HALF_UP to the minor unit. */
    public Money percentage(BigDecimal percent) {
        Objects.requireNonNull(percent, "percent");
        BigDecimal result = BigDecimal.valueOf(amountMinor)
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Money(result.longValueExact(), currency);
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isSameCurrency(Money other) {
        return other != null && currency.equals(other.currency);
    }

    public void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " " + currency;
    }
}


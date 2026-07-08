package base.model;

import jakarta.ws.rs.BadRequestException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class Money {
    private final long amountMinor;
    private final String currency;

    public Money(long amountMinor, String currency) {
        this.amountMinor = amountMinor;
        this.currency = Objects.requireNonNull(currency);
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money fromDecimal(BigDecimal decimalAmount, String currency) {
        Objects.requireNonNull(decimalAmount);
        Objects.requireNonNull(currency);
        if (decimalAmount.signum() <= 0) {
            throw new BadRequestException("Số tiền phải lớn hơn 0");
        }
        int exponent = getExponent(currency);
        BigDecimal minorAmount = decimalAmount.movePointRight(exponent).setScale(0, RoundingMode.HALF_UP);
        return new Money(minorAmount.longValue(), currency);
    }

    public BigDecimal toDecimal() {
        int exponent = getExponent(currency);
        return BigDecimal.valueOf(amountMinor).movePointLeft(exponent);
    }

    public Money add(Money other) {
        checkCurrency(other);
        return new Money(Math.addExact(this.amountMinor, other.amountMinor), this.currency);
    }

    public Money subtract(Money other) {
        checkCurrency(other);
        return new Money(Math.subtractExact(this.amountMinor, other.amountMinor), this.currency);
    }

    public Money multiply(double multiplier) {
        BigDecimal decimalAmount = this.toDecimal().multiply(BigDecimal.valueOf(multiplier));
        return Money.fromDecimal(decimalAmount, this.currency);
    }

    private void checkCurrency(Money other) {
        if (!this.currency.equalsIgnoreCase(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " != " + other.currency);
        }
    }

    private static int getExponent(String currency) {
        if ("VND".equalsIgnoreCase(currency)) {
            return 0;
        }
        return 2; // Default to 2 (USD, etc.)
    }
}

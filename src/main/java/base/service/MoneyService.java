package base.service;

import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.util.Locale;

@ApplicationScoped
public class MoneyService {

    public String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new BadRequestException("Currency must be a 3-letter ISO code");
        }
        return normalized;
    }

    public Money positiveMoney(long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        return Money.of(amountMinor, normalizeCurrency(currency));
    }

    public void requirePositive(Money money) {
        if (money == null) {
            throw new BadRequestException("Amount is required");
        }
        if (money.getAmountMinor() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        normalizeCurrency(money.getCurrency());
    }

    public void requireSameCurrency(String expected, String actual) {
        String normalizedExpected = normalizeCurrency(expected);
        String normalizedActual = normalizeCurrency(actual);
        if (!normalizedExpected.equals(normalizedActual)) {
            throw new BadRequestException("Currency mismatch");
        }
    }
}

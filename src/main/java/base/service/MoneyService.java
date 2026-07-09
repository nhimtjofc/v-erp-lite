package base.service;

import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.util.Locale;

@ApplicationScoped
public class MoneyService {

    public String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Vui lòng nhập mã tiền tệ");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new BadRequestException("Mã tiền tệ phải gồm 3 chữ cái ISO");
        }
        return normalized;
    }

    public Money positiveMoney(long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new BadRequestException("Số tiền phải lớn hơn 0");
        }
        return Money.of(amountMinor, normalizeCurrency(currency));
    }

    public void requirePositive(Money money) {
        if (money == null) {
            throw new BadRequestException("Vui lòng nhập số tiền");
        }
        if (money.getAmountMinor() <= 0) {
            throw new BadRequestException("Số tiền phải lớn hơn 0");
        }
        normalizeCurrency(money.getCurrency());
    }

    public void requireSameCurrency(String expected, String actual) {
        String normalizedExpected = normalizeCurrency(expected);
        String normalizedActual = normalizeCurrency(actual);
        if (!normalizedExpected.equals(normalizedActual)) {
            throw new BadRequestException("Tiền tệ không khớp");
        }
    }
}

package base.l0.system.money;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ISO 4217 minor-unit exponents.
 *
 * <p>The predecessor returned {@code 0} for VND and {@code 2} for everything else, which silently
 * mis-scaled JPY and KRW by 100 and the three-decimal Gulf currencies by 10, and accepted any
 * unrecognised code (Teardown F-25). Here an unknown code is an error: mis-scaling money by a
 * factor of 100 is not a failure mode worth trading for convenience.
 */
public final class CurrencyExponents {

    private static final Map<String, Integer> EXPONENTS = Map.ofEntries(
            Map.entry("VND", 0),
            Map.entry("JPY", 0),
            Map.entry("KRW", 0),
            Map.entry("CLP", 0),
            Map.entry("ISK", 0),
            Map.entry("PYG", 0),
            Map.entry("RWF", 0),
            Map.entry("UGX", 0),
            Map.entry("XAF", 0),
            Map.entry("XOF", 0),
            Map.entry("BHD", 3),
            Map.entry("IQD", 3),
            Map.entry("JOD", 3),
            Map.entry("KWD", 3),
            Map.entry("LYD", 3),
            Map.entry("OMR", 3),
            Map.entry("TND", 3),
            Map.entry("USD", 2),
            Map.entry("EUR", 2),
            Map.entry("GBP", 2),
            Map.entry("AUD", 2),
            Map.entry("CAD", 2),
            Map.entry("CHF", 2),
            Map.entry("CNY", 2),
            Map.entry("HKD", 2),
            Map.entry("SGD", 2),
            Map.entry("THB", 2),
            Map.entry("MYR", 2),
            Map.entry("PHP", 2),
            Map.entry("IDR", 2),
            Map.entry("INR", 2)
    );

    private CurrencyExponents() {
    }

    public static boolean isKnown(String currency) {
        return currency != null && EXPONENTS.containsKey(currency.trim().toUpperCase(Locale.ROOT));
    }

    public static int of(String currency) {
        if (currency == null) {
            throw new UnknownCurrencyException("null");
        }
        Integer exponent = EXPONENTS.get(currency.trim().toUpperCase(Locale.ROOT));
        if (exponent == null) {
            throw new UnknownCurrencyException(currency);
        }
        return exponent;
    }

    public static Set<String> known() {
        return EXPONENTS.keySet();
    }
}


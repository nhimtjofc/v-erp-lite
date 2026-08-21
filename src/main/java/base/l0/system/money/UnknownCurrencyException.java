package base.l0.system.money;

/** Raised for a currency code with no known ISO 4217 minor-unit exponent. */
public class UnknownCurrencyException extends RuntimeException {

    public UnknownCurrencyException(String currency) {
        super("Unknown currency code: " + currency);
    }
}


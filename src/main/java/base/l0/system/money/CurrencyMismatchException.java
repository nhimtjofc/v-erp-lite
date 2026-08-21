package base.l0.system.money;

/** Raised when an arithmetic or posting operation mixes two currencies. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String left, String right) {
        super("Currency mismatch: " + left + " != " + right);
    }
}


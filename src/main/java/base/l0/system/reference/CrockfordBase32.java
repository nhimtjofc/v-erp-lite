package base.l0.system.reference;

import java.util.Locale;

/**
 * Crockford's Base32, with a check symbol that stays inside the alphabet.
 *
 * <p>The alphabet omits {@code I}, {@code L}, {@code O} and {@code U} — the first three because they
 * are read as {@code 1}, {@code 1} and {@code 0} by anyone retyping a code off a bank statement, and
 * the fourth so that no encoding accidentally spells something a customer will screenshot.
 *
 * <p><b>The check symbol is Luhn mod 32, not Crockford's own.</b> The specification's check symbol is
 * mod 37 and its five extra symbols are {@code * ~ $ = U}; four of those are special characters, and
 * VietQR rejects special characters outright while UK Faster Payments disallows a similar set. A
 * check symbol that breaks the format on the rails the format was sized for is not a check symbol.
 * Luhn mod 32 emits a symbol from this same alphabet, catches every single-character error and every
 * adjacent transposition except a pair differing by exactly 16.
 *
 * <p>Decoding accepts the spec's forgiving input rules: lower case, {@code I}/{@code L} read as
 * {@code 1}, {@code O} read as {@code 0}, and hyphens ignored.
 */
public final class CrockfordBase32 {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int BASE = 32;
    private static final int BITS_PER_CHAR = 5;

    private CrockfordBase32() {
    }

    /**
     * Encodes {@code value} into exactly {@code width} symbols.
     *
     * @throws IllegalArgumentException if the value is negative or will not fit — never silently
     *                                  wrapping, because a wrapped time bucket produces a reference
     *                                  that decodes to the wrong century and looks entirely valid
     */
    public static String encode(long value, int width) {
        if (width <= 0 || width > 12) {
            throw new IllegalArgumentException("Width must be 1..12 symbols, got " + width);
        }
        long capacity = capacity(width);
        if (value < 0 || value >= capacity) {
            throw new IllegalArgumentException(
                    "Value " + value + " does not fit in " + width + " symbols (0.." + (capacity - 1) + ")");
        }
        char[] out = new char[width];
        long remaining = value;
        for (int i = width - 1; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (remaining & 0x1F));
            remaining >>>= BITS_PER_CHAR;
        }
        return new String(out);
    }

    /** How many distinct values {@code width} symbols can hold. */
    public static long capacity(int width) {
        return 1L << (BITS_PER_CHAR * width);
    }

    public static long decode(String text) {
        String normalised = normalise(text);
        long value = 0;
        for (int i = 0; i < normalised.length(); i++) {
            int digit = valueOf(normalised.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException(
                        "'" + normalised.charAt(i) + "' is not a Crockford Base32 symbol");
            }
            value = (value << BITS_PER_CHAR) | digit;
        }
        return value;
    }

    /** The Luhn mod 32 check symbol for {@code payload}. */
    public static char checkSymbol(String payload) {
        String normalised = normalise(payload);
        int factor = 2;
        int total = 0;
        for (int i = normalised.length() - 1; i >= 0; i--) {
            int digit = valueOf(normalised.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException(
                        "'" + normalised.charAt(i) + "' is not a Crockford Base32 symbol");
            }
            int addend = factor * digit;
            factor = 3 - factor;
            total += (addend / BASE) + (addend % BASE);
        }
        return ALPHABET.charAt((BASE - total % BASE) % BASE);
    }

    /** Upper-cases, folds the confusable letters onto their digits, and drops hyphens. */
    public static String normalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char raw : text.toUpperCase(Locale.ROOT).toCharArray()) {
            switch (raw) {
                case '-' -> { }
                case 'I', 'L' -> out.append('1');
                case 'O' -> out.append('0');
                default -> out.append(raw);
            }
        }
        return out.toString();
    }

    public static boolean isSymbol(char candidate) {
        return valueOf(candidate) >= 0;
    }

    private static int valueOf(char symbol) {
        return ALPHABET.indexOf(symbol);
    }
}


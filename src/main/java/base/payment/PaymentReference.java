package base.payment;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class PaymentReference {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final LocalDate EPOCH = LocalDate.of(2026, 1, 1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private PaymentReference() {
    }

    public static String issue(LocalDate issuedOn) {
        if (issuedOn == null) {
            throw new IllegalArgumentException("issuedOn is required");
        }

        long dayBucket = ChronoUnit.DAYS.between(EPOCH, issuedOn);
        if (dayBucket < 0 || dayBucket >= (1 << 15)) {
            throw new IllegalArgumentException("issuedOn is outside the supported reference range");
        }

        String value = "P" + encode(dayBucket, 3) + encode(RANDOM.nextLong() & ((1L << 35) - 1), 7);
        return value + ALPHABET.charAt(checksum(value));
    }

    public static boolean isValid(String reference) {
        if (reference == null || reference.length() != 12) {
            return false;
        }

        String normalized = reference.toUpperCase();
        if (normalized.charAt(0) != 'P') {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                return false;
            }
        }
        return ALPHABET.charAt(checksum(normalized.substring(0, 11))) == normalized.charAt(11);
    }

    private static String encode(long value, int width) {
        StringBuilder encoded = new StringBuilder(width);
        for (int i = width - 1; i >= 0; i--) {
            encoded.append(ALPHABET.charAt((int) ((value >>> (i * 5)) & 31)));
        }
        return encoded.toString();
    }

    private static int checksum(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            total += ALPHABET.indexOf(value.charAt(i)) * (i + 3);
        }
        return total % ALPHABET.length();
    }
}

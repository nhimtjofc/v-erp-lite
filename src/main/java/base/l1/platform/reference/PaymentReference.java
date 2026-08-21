package base.l1.platform.reference;

import base.l0.system.reference.CrockfordBase32;

import java.time.Instant;

/**
 * A reference we issued, decoded.
 *
 * <p>{@link #value()} is the authority; the decoded parts are conveniences. In particular the time
 * bucket is <b>not</b> a source of truth for when anything happened — the row this resolves to
 * carries {@code created_at}, and the bucket exists to cluster inserts on the unique index and to
 * separate the entropy space, not to be read back as a date. Nothing should branch on it.
 */
public record PaymentReference(
        String value,
        ReferenceGeneration generation,
        long timeBucket,
        long entropy
) {
    /** Start of the bucket this was issued in. Approximate by construction — see the class note. */
    public Instant bucketStart() {
        return generation.bucketStart(timeBucket);
    }

    /** The payload the check symbol covers: everything but the prefix and the check symbol itself. */
    public String payload() {
        return value.substring(1, value.length() - 1);
    }

    public char checkSymbol() {
        return value.charAt(value.length() - 1);
    }

    @Override
    public String toString() {
        return value;
    }

    static PaymentReference of(ReferenceGeneration generation, long bucket, long entropy) {
        String payload = CrockfordBase32.encode(bucket, generation.timeChars())
                + CrockfordBase32.encode(entropy, generation.randomChars());
        String value = generation.symbol() + payload + CrockfordBase32.checkSymbol(payload);
        return new PaymentReference(value, generation, bucket, entropy);
    }
}


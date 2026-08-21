package base.l1.platform.reference;

import base.l0.system.reference.CrockfordBase32;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One version of the reference format.
 *
 * <p>A reference has to survive being printed on a bank statement we do not control and attached to
 * journal rows that are append-only by design, so a format change that requires rewriting history is
 * a format change we cannot make. The way out is to make every reference say which format it is: the
 * leading symbol identifies the generation, and a generation is never retired in a way that stops its
 * references decoding.
 *
 * <p>That symbol carries the generation rather than the document kind on purpose. It must hold the
 * one thing that <em>cannot</em> be recovered from the row the reference resolves to — the row
 * already knows it is a payout, and it cannot know which set of field widths encoded its own
 * reference. Issue date does not settle it either, because during any rollout two generations are in
 * issue at once.
 *
 * @param symbol      leading symbol; unique per generation and <b>never reused</b>, or old references
 *                    become ambiguous
 * @param epoch       instant the time bucket counts from
 * @param timeUnit    granularity of the time bucket
 * @param timeChars   symbols spent on the bucket — coarse enough to fit, fine enough to cluster
 * @param randomChars symbols spent on entropy
 */
public record ReferenceGeneration(
        char symbol,
        Instant epoch,
        ChronoUnit timeUnit,
        int timeChars,
        int randomChars
) {
    public ReferenceGeneration {
        if (!CrockfordBase32.isSymbol(symbol)) {
            throw new IllegalArgumentException("Generation symbol must be a Crockford symbol: " + symbol);
        }
        if (timeChars < 1 || randomChars < 1) {
            throw new IllegalArgumentException("A generation needs both a time bucket and entropy");
        }
    }

    /** Prefix + bucket + entropy + check symbol. */
    public int length() {
        return 1 + timeChars + randomChars + 1;
    }

    public long timeCapacity() {
        return CrockfordBase32.capacity(timeChars);
    }

    public int entropyBits() {
        return randomChars * 5;
    }

    /** Which bucket {@code at} falls in. Negative or overflowing values are the caller's to reject. */
    public long bucketOf(Instant at) {
        return timeUnit.between(epoch, at);
    }

    public Instant bucketStart(long bucket) {
        return epoch.plus(bucket, timeUnit);
    }

    /** The last instant this generation can express — after which a new generation must take over. */
    public Instant exhaustedAt() {
        return bucketStart(timeCapacity());
    }
}


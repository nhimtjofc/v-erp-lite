package base.l1.platform.reference;

import base.l0.system.reference.CrockfordBase32;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.SecureRandom;

/**
 * The only thing in this service that knows how a reference is shaped.
 *
 * <p>Minting always uses {@link #CURRENT}; parsing dispatches on the leading symbol and so keeps
 * working for every generation ever issued. Adding a generation is one entry in {@link #GENERATIONS}
 * and a new {@link #CURRENT} — no migration, because references are stored as literal strings and
 * nothing anywhere else takes one apart.
 *
 * <p>That chokepoint is the same discipline as the ledger's single write path, for the same reason:
 * a rule enforced in one place survives, and a rule everyone is asked to remember does not.
 *
 * <h2>Why twelve characters</h2>
 *
 * The binding constraint is New Zealand's domestic EFT fields at 12. VietQR allows about 19, UK
 * Faster Payments and Australian BECS 18, ISO 11649 structured references 25, SEPA unstructured 140.
 * A reference sized for the floor fits everywhere; one sized for anything else does not fit New
 * Zealand, and widening is always available while narrowing never is. So 12 is a floor, permanently:
 * anything a reference might later need to carry belongs in a rail's supplementary fields, not in a
 * longer identifier.
 */
public final class ReferenceFormat {

    /**
     * Generation 1. Day buckets over 15 bits reach 2115; 35 bits of entropy is 34 billion values per
     * day, which puts a collision around one insert in 344,000 at 100k references a day — a retry,
     * not a defect.
     */
    public static final ReferenceGeneration GENERATION_1 = new ReferenceGeneration(
            'P',
            LocalDate.of(2026, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            ChronoUnit.DAYS,
            3,
            7);

    public static final ReferenceGeneration CURRENT = GENERATION_1;

    private static final Map<Character, ReferenceGeneration> GENERATIONS = new LinkedHashMap<>();
    private static final List<Pattern> CANDIDATE_PATTERNS = new ArrayList<>();
    private static final RandomGenerator DEFAULT_RANDOM = new SecureRandom();

    static {
        register(GENERATION_1);
    }

    private ReferenceFormat() {
    }

    private static void register(ReferenceGeneration generation) {
        ReferenceGeneration clash = GENERATIONS.put(generation.symbol(), generation);
        if (clash != null) {
            throw new IllegalStateException("Generation symbol " + generation.symbol() + " is already taken");
        }
        // Hyphens are ignorable in Crockford input and people do group long codes with them, so a
        // candidate is N alphanumerics with optional hyphens between, bounded by neither.
        Pattern pattern = Pattern.compile(
                "(?<![0-9A-Za-z-])(?:[0-9A-Za-z]-?){" + generation.length() + "}(?![0-9A-Za-z])");
        if (CANDIDATE_PATTERNS.stream().noneMatch(p -> p.pattern().equals(pattern.pattern()))) {
            CANDIDATE_PATTERNS.add(pattern);
        }
    }

    /** A fresh reference for {@code at}, using the current generation. */
    public static PaymentReference mint(Instant at) {
        return mint(at, DEFAULT_RANDOM);
    }

    public static PaymentReference mint(Instant at, RandomGenerator random) {
        ReferenceGeneration generation = CURRENT;
        long bucket = generation.bucketOf(at);
        if (bucket < 0) {
            throw new InvalidReferenceException(
                    "Cannot issue a reference dated " + at + ", before generation '" + generation.symbol()
                            + "' epoch " + generation.epoch());
        }
        if (bucket >= generation.timeCapacity()) {
            throw new InvalidReferenceException(
                    "Generation '" + generation.symbol() + "' is exhausted at " + generation.exhaustedAt()
                            + "; register a new generation rather than wrapping");
        }
        long entropy = Math.floorMod(random.nextLong(), CrockfordBase32.capacity(generation.randomChars()));
        return PaymentReference.of(generation, bucket, entropy);
    }

    /**
     * Parses and verifies a reference.
     *
     * @throws InvalidReferenceException unknown generation, wrong length, bad symbol or failed check
     */
    public static PaymentReference parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidReferenceException("Reference is blank");
        }
        String value = CrockfordBase32.normalise(raw.trim());
        ReferenceGeneration generation = GENERATIONS.get(value.charAt(0));
        if (generation == null) {
            throw new InvalidReferenceException("No reference generation for prefix '" + value.charAt(0) + "'");
        }
        if (value.length() != generation.length()) {
            throw new InvalidReferenceException("Generation '" + generation.symbol() + "' references are "
                    + generation.length() + " characters, got " + value.length());
        }
        String payload = value.substring(1, value.length() - 1);
        char check = value.charAt(value.length() - 1);
        char expected;
        try {
            expected = CrockfordBase32.checkSymbol(payload);
        } catch (IllegalArgumentException e) {
            throw new InvalidReferenceException(e.getMessage());
        }
        if (check != expected) {
            throw new InvalidReferenceException("Check symbol failed for " + value + "; it was mistyped");
        }
        long bucket = CrockfordBase32.decode(payload.substring(0, generation.timeChars()));
        long entropy = CrockfordBase32.decode(payload.substring(generation.timeChars()));
        return new PaymentReference(value, generation, bucket, entropy);
    }

    public static Optional<PaymentReference> tryParse(String raw) {
        try {
            return Optional.of(parse(raw));
        } catch (InvalidReferenceException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Every valid reference embedded in free text.
     *
     * <p>Bank memos arrive with a payer's name attached, a truncation, and someone's typo, so this
     * searches rather than compares. The check symbol is what keeps the search honest: an arbitrary
     * twelve-character token in a memo passes the shape test and fails the check 31 times out of 32.
     */
    public static List<PaymentReference> scan(String memo) {
        if (memo == null || memo.isBlank()) {
            return List.of();
        }
        List<PaymentReference> found = new ArrayList<>();
        for (Pattern pattern : CANDIDATE_PATTERNS) {
            Matcher matcher = pattern.matcher(memo);
            while (matcher.find()) {
                tryParse(matcher.group().replace("-", ""))
                        .filter(reference -> found.stream().noneMatch(f -> f.value().equals(reference.value())))
                        .ifPresent(found::add);
            }
        }
        return List.copyOf(found);
    }
}


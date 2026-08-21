package base.l1.platform.reference;

import base.l0.system.reference.CrockfordBase32;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The format in isolation — no database, no CDI. */
class ReferenceFormatTest {

    private static final Instant NOON = LocalDate.of(2026, 8, 21).atTime(12, 0).toInstant(ZoneOffset.UTC);

    @Test
    void aMintedReferenceIsTwelveCharactersAndRoundTrips() {
        PaymentReference reference = ReferenceFormat.mint(NOON);

        assertEquals(12, reference.value().length());
        assertEquals('P', reference.value().charAt(0));
        assertEquals(reference, ReferenceFormat.parse(reference.value()));
        assertEquals(232L, reference.timeBucket(), "day 232 of the 2026-01-01 epoch");
    }

    @Test
    void referencesFromTheSameDayShareTheirBucket() {
        // The property the time prefix exists for: same-day inserts land together in the index.
        String today = ReferenceFormat.mint(NOON).value().substring(0, 4);
        for (int i = 0; i < 20; i++) {
            assertEquals(today, ReferenceFormat.mint(NOON).value().substring(0, 4));
        }
        assertFalse(today.equals(ReferenceFormat.mint(NOON.plus(1, ChronoUnit.DAYS)).value().substring(0, 4)));
    }

    @Test
    void aMistypedCharacterIsRejected() {
        String good = ReferenceFormat.mint(NOON).value();
        char original = good.charAt(6);
        char swapped = original == '5' ? '6' : '5';
        String typo = good.substring(0, 6) + swapped + good.substring(7);

        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.parse(typo));
    }

    @Test
    void adjacentCharactersSwappedAreRejected() {
        String good = ReferenceFormat.mint(NOON).value();
        int at = indexOfDifferingNeighbour(good);
        String transposed = good.substring(0, at) + good.charAt(at + 1) + good.charAt(at) + good.substring(at + 2);

        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.parse(transposed));
    }

    @Test
    void theForgivingInputRulesApply() {
        PaymentReference reference = ReferenceFormat.mint(NOON);
        String value = reference.value();

        // Lower case, and the letters a hand-typed code turns into digits.
        assertEquals(reference, ReferenceFormat.parse(value.toLowerCase()));
        assertEquals(reference, ReferenceFormat.parse(value.replace('1', 'I')));
        assertEquals(reference, ReferenceFormat.parse(value.replace('0', 'O')));
        assertEquals(reference, ReferenceFormat.parse(value.substring(0, 6) + "-" + value.substring(6)));
    }

    @Test
    void anUnknownGenerationIsRefusedRatherThanGuessed() {
        String value = ReferenceFormat.mint(NOON).value();

        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.parse("Z" + value.substring(1)));
        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.parse(value + "X"));
        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.parse(""));
    }

    @Test
    void datesOutsideTheGenerationAreRefusedNotWrapped() {
        // A wrapped bucket produces a reference that decodes to the wrong century and looks valid.
        // This is the near failure, not the far one: a backdated import hits it, not the year 2115.
        Instant beforeEpoch = ReferenceFormat.CURRENT.epoch().minus(1, ChronoUnit.DAYS);
        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.mint(beforeEpoch));

        Instant exhausted = ReferenceFormat.CURRENT.exhaustedAt();
        assertThrows(InvalidReferenceException.class, () -> ReferenceFormat.mint(exhausted));

        // The last day the generation can express still works.
        assertEquals(32767L, ReferenceFormat.mint(exhausted.minus(1, ChronoUnit.DAYS)).timeBucket());
    }

    @Test
    void generationOneReachesTwentyOneFifteen() {
        assertEquals(LocalDate.of(2115, 9, 19),
                LocalDate.ofInstant(ReferenceFormat.CURRENT.bucketStart(32767), ZoneOffset.UTC));
        assertEquals(35, ReferenceFormat.CURRENT.entropyBits());
    }

    @Test
    void aMemoIsSearchedNotCompared() {
        String reference = ReferenceFormat.mint(NOON).value();

        // What a real memo looks like by the time a bank is finished with it.
        List<PaymentReference> found = ReferenceFormat.scan("NGUYEN VAN A CHUYEN TIEN " + reference + " CAM ON");
        assertEquals(1, found.size());
        assertEquals(reference, found.getFirst().value());

        assertEquals(1, ReferenceFormat.scan(reference).size());
        assertEquals(0, ReferenceFormat.scan("PAYMENT FOR INVOICE 12345").size(), "no false positive");
        assertEquals(0, ReferenceFormat.scan("X" + reference + "Y").size(), "not embedded in a longer run");
        assertEquals(0, ReferenceFormat.scan(null).size());
    }

    @Test
    void aMemoWithTwoReferencesReturnsBoth() {
        // The caller decides what to do with an ambiguous memo; silently picking one is how a
        // payment gets matched to the wrong document.
        String first = ReferenceFormat.mint(NOON).value();
        String second = ReferenceFormat.mint(NOON).value();

        assertEquals(2, ReferenceFormat.scan(first + " AND " + second).size());
    }

    @Test
    void randomTokensDoNotPassTheCheckSymbol() {
        // 31 of every 32 shape-correct tokens should fail. This is what keeps memo scanning honest.
        RandomGenerator random = RandomGenerator.getDefault();
        int passed = 0;
        for (int i = 0; i < 4_000; i++) {
            StringBuilder token = new StringBuilder("P");
            for (int c = 0; c < 11; c++) {
                token.append(CrockfordBase32.ALPHABET.charAt(random.nextInt(32)));
            }
            if (ReferenceFormat.tryParse(token.toString()).isPresent()) {
                passed++;
            }
        }
        assertTrue(passed < 4_000 / 32 * 3, "far too many arbitrary tokens parsed as valid: " + passed);
    }

    @Test
    void mintingIsSpreadAcrossTheEntropySpace() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            seen.add(ReferenceFormat.mint(NOON).value());
        }
        assertEquals(5_000, seen.size(), "collisions at this volume mean the entropy is not random");
    }

    /**
     * A pair Luhn mod 32 can actually catch. The algorithm's one known blind spot is a transposition
     * of adjacent symbols whose values differ by exactly 16, so a test that picked those at random
     * would fail roughly one run in thirty. Damm mod 32 closes that gap if it ever matters enough to
     * carry the table.
     */
    private static int indexOfDifferingNeighbour(String value) {
        for (int i = 1; i < value.length() - 2; i++) {
            int left = CrockfordBase32.ALPHABET.indexOf(value.charAt(i));
            int right = CrockfordBase32.ALPHABET.indexOf(value.charAt(i + 1));
            if (left != right && Math.abs(left - right) != 16) {
                return i;
            }
        }
        throw new IllegalStateException("no catchable adjacent pair in " + value);
    }
}


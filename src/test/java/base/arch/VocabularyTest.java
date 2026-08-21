package base.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core speaks payments, not commerce.
 *
 * <p>A ledger that learns its client's vocabulary is a ledger that fits one client. This service
 * exists because the payment core of an e-commerce ERP could not be reused or reasoned about on its
 * own terms, and the first symptom was always linguistic: a provider adapter that knew what an
 * order was, an account code named for a marketplace's cut of a sale.
 *
 * <p>{@link ModuleBoundaryTest} cannot catch this. Its rules are about package dependencies, and a
 * leaked {@code orderId} is a {@code UUID} that depends on nothing — structurally invisible,
 * semantically fatal. So this test reads the source as text, the way {@link SingleWritePathTest}
 * does.
 *
 * <p>The rule is about the <em>core</em>: L0, the provider platform, and the ledger itself. The
 * settlement flows above them are allowed to speak the caller's language today; pushing that
 * outward is a separate job. What must not happen is the vocabulary sinking any lower than it
 * already has.
 */
class VocabularyTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /** Packages that must stay free of any one caller's domain language. */
    private static final List<String> CORE = List.of(
            "base/l0",
            "base/l1",
            "base/l2/ledger",
            "base/l2/recon");

    /**
     * Terms belonging to a buy-sell marketplace rather than to payments.
     *
     * <p>"order" is matched only as an identifier ({@code orderId}, {@code order_number}) rather
     * than as a bare word, because the core legitimately discusses lock ordering, time-ordered
     * ids and SQL {@code ORDER BY} — and a rule with false positives gets deleted by the first
     * person it inconveniences. The same boundary keeps {@code paymentOrder}, which is ISO 20022's
     * own term for a payment instruction, from being caught by a rule aimed at shopping carts.
     *
     * <p>"commission" is deliberately absent. It reads like marketplace jargon and is not: "fee and
     * commission income" is a standard financial-statement line item, and in accounting a
     * commission is specifically income earned as an agent facilitating a transaction for another
     * party. {@code revenue:fee} is the better default because it presumes no agency relationship,
     * but an agency business earning both would properly report them as two accounts, and banning
     * the word would prevent naming the second one.
     */
    private static final Pattern COMMERCE = Pattern.compile(
            "\\b(seller|buyer|merchant|escrow|cart|checkout|shipping|order[ _]?(id|no|num|number|uuid|ref|reference)s?)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void theCoreDoesNotSpeakCommerce() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path path : coreSources()) {
            String source = read(path);
            Matcher matcher = COMMERCE.matcher(source);
            while (matcher.find()) {
                offenders.add(SOURCE_ROOT.relativize(path)
                        + ":" + lineOf(source, matcher.start())
                        + " '" + matcher.group().toLowerCase(Locale.ROOT) + "'");
            }
        }

        assertTrue(offenders.isEmpty(), """
                The payment core must not use a caller's commerce vocabulary. Say party, payer, \
                payee, charge, refund, payout, fee, funds held, or an opaque documentType and \
                documentId. Found:
                """ + String.join("\n", offenders));
    }

    /**
     * The provider SPI is the sharpest case, so it gets its own assertion and its own message: an
     * adapter for a card processor should be writable by someone who has never heard of whatever
     * is originating the payment.
     */
    @Test
    void theProviderContractIsFreeOfTheOriginatingSystem() throws IOException {
        String spi = read(Path.of("src/main/java/base/l1/platform/provider/PaymentRequest.java"));

        assertTrue(!COMMERCE.matcher(spi).find(),
                "PaymentRequest is the contract every provider adapter implements against; "
                        + "anything specific to what is being paid for belongs in its metadata map");
    }

    private static List<Path> coreSources() throws IOException {
        List<Path> found = new ArrayList<>();
        for (String pkg : CORE) {
            Path root = SOURCE_ROOT.resolve(pkg);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(found::add);
            }
        }
        return found;
    }

    private static int lineOf(String source, int offset) {
        return (int) source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}


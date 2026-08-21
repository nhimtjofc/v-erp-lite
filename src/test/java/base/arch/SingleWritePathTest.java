package base.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger has exactly one write path, and this asserts it stays that way.
 *
 * <p>The system this replaces relied on every author routing balance changes through one service
 * class. That held until it didn't. Here the database refuses writes from anywhere else, and this
 * test refuses the code that would attempt them — so the rule fails at build time rather than at
 * runtime against a trigger.
 */
class SingleWritePathTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    @Test
    void onlyThePostingUseCaseInvokesTheLedgerProcedure() throws IOException {
        // Matches the invocation, not the name: several classes refer to the procedure in their
        // documentation, and explaining where the guarantee lives is not the same as bypassing it.
        List<String> offenders = sourceFiles()
                .filter(path -> contains(path, "CALL ledger_post_entry"))
                .map(path -> SOURCE_ROOT.relativize(path).toString())
                .filter(name -> !name.endsWith("PostEntryUC.java"))
                .toList();

        assertTrue(offenders.isEmpty(),
                "ledger_post_entry may only be called from PostEntryUC, but was referenced in " + offenders);
    }

    @Test
    void nothingWritesAccountBalancesDirectly() throws IOException {
        List<String> offenders = sourceFiles()
                .filter(path -> contains(path, "setBalanceMinor") || contains(path, "setEntrySeq"))
                .map(path -> SOURCE_ROOT.relativize(path).toString())
                .toList();

        assertTrue(offenders.isEmpty(),
                "Account balances are maintained by the ledger procedure; found setters used in " + offenders);
    }

    @Test
    void theBalanceGuardAndAppendOnlyTriggersAreInstalledByMigration() throws IOException {
        String migrations = Stream.of(
                        Path.of("src/main/resources/db/migration/V50_002__ledger_posting_and_guards.sql"))
                .map(SingleWritePathTest::read)
                .reduce("", String::concat);

        assertTrue(migrations.contains("trg_ledger_accounts_guard"), "balance guard trigger missing");
        assertTrue(migrations.contains("trg_journal_entries_no_update"), "entry immutability trigger missing");
        assertTrue(migrations.contains("trg_journal_lines_no_update"), "line immutability trigger missing");
        assertTrue(migrations.contains("trg_journal_lines_no_delete"), "line delete guard missing");
        assertTrue(migrations.contains("Unbalanced entry"), "balance enforcement missing from the procedure");
    }

    /**
     * Allocations have one writer too, and unusually the reason is not tidiness.
     *
     * <p>Every other rule here exists because the database can enforce the invariant and the code
     * should not be given the chance to route around it. This one is the reverse: MariaDB refuses a
     * locking read on the table a trigger fires for, so {@code trg_item_allocations_within_open}
     * cannot see a committed total and cannot be the authoritative guard against over-allocation.
     * That check lives in {@code AllocateItemUC}, under the row locks the trigger takes — which is
     * only sound while {@code AllocateItemUC} is the sole writer. Hence a build-time rule instead
     * of a database one.
     */
    @Test
    void onlyTheAllocationUseCaseWritesAllocations() throws IOException {
        List<String> offenders = sourceFiles()
                .filter(path -> contains(path, "ItemAllocation()") || contains(path, "INSERT INTO item_allocations"))
                .map(path -> SOURCE_ROOT.relativize(path).toString())
                .filter(name -> !name.endsWith("AllocateItemUC.java"))
                .toList();

        assertTrue(offenders.isEmpty(),
                "item_allocations may only be written by AllocateItemUC, which holds the row locks "
                        + "the over-allocation check depends on; found writes in " + offenders);
    }

    /**
     * Tenant elevation has one caller, and that is what keeps it an elevation.
     *
     * <p>{@code SystemTenantContext.runAs} supplies the tenant discriminator explicitly instead of
     * reading it from a bearer token, which is exactly what inbound dispatch needs — a webhook is
     * authenticated by its signature and only becomes attributable once its reference resolves.
     * Used anywhere else it would be a way to read across tenants on purpose, so the rule is that
     * it is not used anywhere else.
     */
    @Test
    void onlyInboundDispatchElevatesTheTenant() throws IOException {
        List<String> offenders = sourceFiles()
                .filter(path -> contains(path, ".runAs("))
                .map(path -> SOURCE_ROOT.relativize(path).toString())
                .filter(name -> !name.startsWith("base/l2/settlement/inbound/"))
                .toList();

        assertTrue(offenders.isEmpty(),
                "SystemTenantContext.runAs supplies a tenant instead of proving one; it belongs to "
                        + "inbound dispatch alone, but was used in " + offenders);
    }

    @Test
    void everyMigrationIsForwardOnly() throws IOException {
        // A ledger's history is the product. A migration that drops or rewrites journal data would
        // destroy the only evidence of what happened.
        List<String> offenders;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/db/migration"))) {
            offenders = paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String sql = read(path).toUpperCase();
                        return sql.contains("DROP TABLE JOURNAL_")
                                || sql.contains("DELETE FROM JOURNAL_")
                                || sql.contains("TRUNCATE");
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertEquals(List.of(), offenders, "migrations must never destroy journal history");
    }

    private static Stream<Path> sourceFiles() throws IOException {
        return Files.walk(SOURCE_ROOT).filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"));
    }

    private static boolean contains(Path path, String needle) {
        return read(path).contains(needle);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

package base.l2.recon;

import base.l0.system.money.Money;
import base.l2.ledger.LedgerTestFixture;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.AccountNotFoundException;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.domain.DuplicateEntryException;
import base.l2.recon.bank.application.AttestBankBalanceUC;
import base.l2.recon.bank.application.BankReconciliationQuery;
import base.l2.recon.bank.application.ReclassifySuspenseUC;
import base.l2.recon.bank.application.RecordBankTransactionUC;
import base.l2.recon.bank.domain.BankTransaction;
import base.l2.recon.bank.domain.BankTransactionRejectedException;
import base.l2.recon.bank.interfaces.dto.BankAttestationDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Off-platform activity on a bank account the platform otherwise owns.
 *
 * <p>Every test opens its own bank account. The suite shares one database and one suspense account,
 * so anything asserted about a shared balance is asserted as a delta; anything asserted absolutely
 * gets an account nobody else touches.
 */
@QuarkusTest
@TestSecurity(user = "tester", roles = {"admin"})
@JwtSecurity(claims = {@Claim(key = "tenant_code", value = "acme")})
class BankReconciliationTest {

    @Inject
    RecordBankTransactionUC recordTransaction;

    @Inject
    ReclassifySuspenseUC reclassify;

    @Inject
    AttestBankBalanceUC attest;

    @Inject
    BankReconciliationQuery query;

    @Inject
    LedgerTestFixture fixture;

    @Inject
    base.l2.recon.application.LedgerInvariantChecker checker;

    private static String uniqueName() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void anAccountFeeTheServiceNeverSawStillReachesTheBook() {
        String name = uniqueName();
        LedgerAccount bank = fixture.bank(name, "EUR");
        LedgerAccount fees = fixture.expense(AccountCode.EXPENSE_BANK_FEES, "EUR");
        long feesBefore = fixture.balanceOf(fees.getUuid());

        recordTransaction.record(new BankTransaction(
                AccountCode.bank(name), Money.of(-1_250, "EUR"), AccountCode.EXPENSE_BANK_FEES,
                "FEE-" + name, LocalDate.now(), "Monthly account maintenance"));

        // Money out of an asset account is a credit, and the caller never had to say so.
        assertEquals(-1_250L, fixture.balanceOf(bank.getUuid()));
        assertEquals(1_250L, fixture.balanceOf(fees.getUuid()) - feesBefore);
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"), "the book must still net to zero");
        assertEquals(0L, fixture.accountDrift(bank.getUuid()));
    }

    @Test
    void interestArrivingUnannouncedIsBookedTheOtherWay() {
        String name = uniqueName();
        LedgerAccount bank = fixture.bank(name, "EUR");
        LedgerAccount interest = fixture.revenue(AccountCode.REVENUE_INTEREST, "EUR");
        long interestBefore = fixture.balanceOf(interest.getUuid());

        recordTransaction.record(new BankTransaction(
                AccountCode.bank(name), Money.of(430, "EUR"), AccountCode.REVENUE_INTEREST,
                "INT-" + name, LocalDate.now(), null));

        assertEquals(430L, fixture.balanceOf(bank.getUuid()));
        assertEquals(-430L, fixture.balanceOf(interest.getUuid()) - interestBefore,
                "revenue carries a credit balance, which is negative in a debit-positive ledger");
    }

    @Test
    void anUnexplainedMovementIsBookedNowAndNamedLater() {
        String name = uniqueName();
        LedgerAccount bank = fixture.bank(name, "EUR");
        LedgerAccount suspense = fixture.suspense("EUR");
        LedgerAccount fees = fixture.expense(AccountCode.EXPENSE_BANK_FEES, "EUR");
        long suspenseBefore = fixture.balanceOf(suspense.getUuid());
        long feesBefore = fixture.balanceOf(fees.getUuid());
        String ref = "UNK-" + name;

        // Seen on the statement, nobody knows what it is. The bank balance is right immediately.
        recordTransaction.record(new BankTransaction(
                AccountCode.bank(name), Money.of(-9_900, "EUR"), null, ref, LocalDate.now(), null));

        assertEquals(-9_900L, fixture.balanceOf(bank.getUuid()), "the bank position is correct at once");
        assertEquals(9_900L, fixture.balanceOf(suspense.getUuid()) - suspenseBefore,
                "and the amount waits in suspense rather than being guessed at");

        List<?> waiting = query.suspense();
        assertFalse(waiting.isEmpty(), "suspense should report what is sitting in it");

        // Someone finds out what it was. The bank account is untouched: it was already right.
        reclassify.reclassify(ref, AccountCode.EXPENSE_BANK_FEES, Money.of(-9_900, "EUR"),
                "Cross-border transfer charge");

        assertEquals(suspenseBefore, fixture.balanceOf(suspense.getUuid()), "suspense must drain");
        assertEquals(9_900L, fixture.balanceOf(fees.getUuid()) - feesBefore);
        assertEquals(-9_900L, fixture.balanceOf(bank.getUuid()), "the bank position never moved again");
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"));
    }

    @Test
    void theStatementIsWhatTellsUsSomethingIsMissing() {
        String name = uniqueName();
        String source = uniqueName();
        LedgerAccount bank = fixture.bank(name, "EUR");
        fixture.bank(source, "EUR");

        // The bank says it holds 50,000. Our books have never heard of this account.
        attest.attest(AccountCode.bank(name), LocalDate.now(), Money.of(50_000, "EUR"), "STMT-1", null);

        BankAttestationDto before = onlyFor(name);
        assertEquals(50_000L, before.deltaMinor(),
                "every invariant over the book alone stays green; only the bank can report this");
        assertFalse(before.reconciled());
        assertEquals(0L, fixture.trialBalanceNet("acme", "EUR"), "the book is self-consistent and still incomplete");
        assertTrue(breachedOn(AccountCode.bank(name)), "and the invariant run must say so");

        // Book the movement that was missing.
        recordTransaction.record(new BankTransaction(
                AccountCode.bank(name), Money.of(50_000, "EUR"), AccountCode.bank(source),
                "MISSING-" + name, LocalDate.now(), "Transfer in from the operating account"));

        BankAttestationDto after = onlyFor(name);
        assertEquals(0L, after.deltaMinor(), "the delta closes itself; nothing recalculates a stored number");
        assertTrue(after.reconciled());
        assertEquals(50_000L, fixture.balanceOf(bank.getUuid()));
        assertFalse(breachedOn(AccountCode.bank(name)));
    }

    @Test
    void theCorrectionThatClosesADeltaIsAlwaysBackdated() {
        String name = uniqueName();
        String source = uniqueName();
        fixture.bank(name, "EUR");
        fixture.bank(source, "EUR");
        LocalDate close = LocalDate.now().minusDays(10);

        // What the platform did see, inside the statement period.
        transfer(name, source, 90_000, close.minusDays(5), "OPEN-" + name);

        // The bank closes the period 10,000 higher than our book. Something is missing.
        attest.attest(AccountCode.bank(name), close, Money.of(100_000, "EUR"), "STMT", null);
        assertEquals(10_000L, onlyFor(name).deltaMinor());

        // The account keeps being used after the statement closed, which is the normal state of
        // affairs by the time anyone gets round to reconciling. None of it belongs to this period.
        transfer(name, source, 60_000, LocalDate.now(), "AFTER-" + name);
        assertEquals(10_000L, onlyFor(name).deltaMinor(),
                "activity after the statement date cannot change what the statement date closed at");

        // Now the missing item is identified and booked with the value date the bank gave it —
        // before the close, but posted after everything above. The delta must go to zero, and it
        // must do so by being recomputed rather than by anyone adjusting it.
        transfer(name, source, 10_000, close.minusDays(1), "MISSED-" + name);

        assertEquals(0L, onlyFor(name).deltaMinor(),
                "a backdated correction closes the period it is dated into, and drags nothing "
                        + "posted before it across the cutoff");
        assertTrue(onlyFor(name).reconciled());
        assertFalse(breachedOn(AccountCode.bank(name)));
        assertEquals(160_000L, fixture.balanceOf(fixture.bank(name, "EUR").getUuid()),
                "while the account's balance today counts all three, in whatever order they arrived");
    }

    private void transfer(String into, String from, long minor, LocalDate valueDate, String ref) {
        recordTransaction.record(new BankTransaction(
                AccountCode.bank(into), Money.of(minor, "EUR"), AccountCode.bank(from),
                ref, valueDate, null));
    }

    @Test
    void aCorrectedStatementReplacesTheOneItCorrects() {
        String name = uniqueName();
        fixture.bank(name, "EUR");

        attest.attest(AccountCode.bank(name), LocalDate.now(), Money.of(1_000, "EUR"), "STMT-1", null);
        attest.attest(AccountCode.bank(name), LocalDate.now(), Money.of(2_000, "EUR"), "STMT-1-REV", "reissued");

        List<BankAttestationDto> found = query.attestations(AccountCode.bank(name));
        assertEquals(1, found.size(), "an attestation is an observation, not a journal entry");
        assertEquals(2_000L, found.getFirst().closingBalanceMinor());
    }

    @Test
    void theSameStatementLineCannotBeBookedTwice() {
        String name = uniqueName();
        fixture.bank(name, "EUR");
        fixture.expense(AccountCode.EXPENSE_BANK_FEES, "EUR");
        BankTransaction tx = new BankTransaction(
                AccountCode.bank(name), Money.of(-500, "EUR"), AccountCode.EXPENSE_BANK_FEES,
                "DUP-" + name, LocalDate.now(), null);

        recordTransaction.record(tx);

        // Two people working the same reconciliation is the normal failure of a manual process.
        assertThrows(DuplicateEntryException.class, () -> recordTransaction.record(tx));
    }

    @Test
    void aManualPostingCannotInventAnAccount() {
        String name = uniqueName();
        fixture.bank(name, "EUR");

        assertThrows(AccountNotFoundException.class, () -> recordTransaction.record(new BankTransaction(
                AccountCode.bank(name), Money.of(-100, "EUR"), "expense:whatever_this_is",
                "GHOST-" + name, LocalDate.now(), null)));
    }

    @Test
    void aTransactionWithoutTheBanksReferenceIsRefused() {
        String name = uniqueName();
        fixture.bank(name, "EUR");
        fixture.expense(AccountCode.EXPENSE_BANK_FEES, "EUR");

        assertThrows(BankTransactionRejectedException.class, () -> recordTransaction.record(new BankTransaction(
                AccountCode.bank(name), Money.of(-100, "EUR"), AccountCode.EXPENSE_BANK_FEES,
                "  ", LocalDate.now(), null)));
    }

    /** Runs every invariant, which is also what proves the two new queries are valid SQL. */
    private boolean breachedOn(String accountCode) {
        return checker.runAll().stream()
                .anyMatch(b -> "BANK_AGREES_WITH_BOOK".equals(b.invariant()) && accountCode.equals(b.subject()));
    }

    private BankAttestationDto onlyFor(String name) {
        List<BankAttestationDto> found = query.attestations(AccountCode.bank(name));
        assertEquals(1, found.size());
        return found.getFirst();
    }
}


package base.l2.recon.bank.application;

import base.l0.system.money.Money;
import base.l0.system.util.UuidUtil;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.recon.bank.domain.BankReconciliation;
import base.l2.recon.bank.domain.BankTransaction;
import base.l2.recon.bank.domain.BankTransactionRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Books a bank movement this service did not originate.
 *
 * <p>A dedicated bank sub-account still sees the occasional item that came from nowhere near a
 * payment: the account fee, interest, a tax debit, a manual transfer. The temptation is to leave
 * those out on the grounds that they are not platform activity. That is the one choice that breaks
 * reconciliation permanently, because the bank control account is the only account whose correct
 * value is set externally — the moment some bank lines are booked and others are not, its balance
 * stops being a claim about anything the bank would confirm, and an unbooked transaction becomes
 * indistinguishable from a bug.
 *
 * <p>So the rule this enforces is: <b>everything that touches the bank gets a bank line.</b> What
 * varies is the counter-account, never whether the movement is recorded.
 *
 * <p>This is not a second write path. It posts through {@link PostEntryUC} like everything else and
 * inherits every guarantee the procedure enforces; what it adds is a shape a person can fill in
 * from a statement without having to think in debits.
 */
@ApplicationScoped
public class RecordBankTransactionUC {

    @Inject
    PostEntryUC postEntry;

    /**
     * Posts the movement against its counter-account, or against suspense when the counter-account
     * is not yet known.
     *
     * @throws BankTransactionRejectedException the amount is zero, or the reference is missing
     * @throws base.l2.ledger.account.domain.AccountNotFoundException an account named here is not open
     */
    @Transactional
    public JournalEntry record(BankTransaction tx) {
        if (tx.bankRef() == null || tx.bankRef().isBlank()) {
            throw new BankTransactionRejectedException(
                    "A bank transaction needs the bank's own reference; it is the idempotency key");
        }
        if (tx.amount().amountMinor() == 0) {
            throw new BankTransactionRejectedException("A bank transaction of zero moves nothing");
        }

        String currency = tx.amount().currency();
        LedgerAccount bank = LedgerAccount.require(tx.bankAccountCode(), currency);
        String counterCode = tx.counterAccountCode() == null
                ? AccountCode.CONTROL_SUSPENSE
                : tx.counterAccountCode();
        LedgerAccount counter = LedgerAccount.require(counterCode, currency);

        Money magnitude = tx.amount().abs();
        LocalDate valueDate = tx.valueDate() == null ? LocalDate.now() : tx.valueDate();
        boolean unexplained = tx.counterAccountCode() == null;

        // The statement's sign decides the direction, so nobody has to translate it by hand.
        PostingLine bankLine = tx.isIntoBank()
                ? PostingLine.debit(UuidUtil.v7(), bank.getUuid(), magnitude)
                : PostingLine.credit(UuidUtil.v7(), bank.getUuid(), magnitude);
        PostingLine counterLine = tx.isIntoBank()
                ? PostingLine.credit(UuidUtil.v7(), counter.getUuid(), magnitude)
                : PostingLine.debit(UuidUtil.v7(), counter.getUuid(), magnitude);

        return postEntry.post(
                PostingRequest.of("BANK_TRANSACTION_RECORDED", idempotencyKey(tx))
                        // Indexed alongside provider settlement references, so a bank reference is
                        // searchable by the same query that finds a provider's.
                        .provider("bank", tx.bankRef())
                        .effectiveAt(valueDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                        .reconcileGroup(BankReconciliation.groupOf(tx.bankRef()))
                        // Distinguishes a hand-entered movement from a machine-posted one forever.
                        // Worth knowing later: manual entries are where the mistakes are.
                        .meta("source", "manual_bank")
                        .meta("bankRef", tx.bankRef())
                        .meta("unexplained", unexplained)
                        .memo(memo(tx, unexplained))
                        .line(bankLine)
                        .line(counterLine)
                        .build());
    }

    /**
     * Scoped by account as well as reference, because bank references are unique within an account
     * and nothing guarantees two banks will not issue the same string.
     */
    private static String idempotencyKey(BankTransaction tx) {
        return "bank-tx:" + tx.bankAccountCode() + ":" + tx.bankRef();
    }

    private static String memo(BankTransaction tx, boolean unexplained) {
        if (tx.memo() != null && !tx.memo().isBlank()) {
            return tx.memo();
        }
        return unexplained
                ? "Bank movement " + tx.bankRef() + ", not yet classified"
                : "Bank movement " + tx.bankRef();
    }
}


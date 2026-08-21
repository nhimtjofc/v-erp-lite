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
import base.l2.recon.bank.domain.BankTransactionRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * The second half of the manual flow: naming what a suspense item actually was.
 *
 * <p>Splitting recording from classifying is the point. They run at different speeds and are done
 * by different people — recording is mechanical and must happen the moment the item is seen, while
 * classifying may need someone to go and ask. Holding the posting back until the answer arrives
 * would leave the bank balance wrong for precisely the interval the question takes, which is the
 * interval during which somebody is most likely to read it.
 *
 * <p>This never edits the original entry. It posts an opposing pair that moves the amount out of
 * suspense into the account it belongs in, carrying the same reconcile group, so the item's whole
 * history stays legible: what arrived, when, and what it turned out to be.
 */
@ApplicationScoped
public class ReclassifySuspenseUC {

    @Inject
    PostEntryUC postEntry;

    /**
     * Moves {@code amount} out of suspense and into {@code counterAccountCode}.
     *
     * @param amount signed as the original bank movement was — positive if money came in. The
     *               caller restates it rather than having it looked up, because a partial
     *               reclassification is normal: one deposit can turn out to be three things.
     */
    @Transactional
    public JournalEntry reclassify(String bankRef, String counterAccountCode, Money amount, String memo) {
        if (bankRef == null || bankRef.isBlank()) {
            throw new BankTransactionRejectedException("Reclassification needs the bank reference it belongs to");
        }
        if (counterAccountCode == null || counterAccountCode.isBlank()) {
            throw new BankTransactionRejectedException(
                    "Reclassification needs the account the item belongs in; leaving it null is what suspense already means");
        }
        if (amount.amountMinor() == 0) {
            throw new BankTransactionRejectedException("A reclassification of zero moves nothing");
        }

        String currency = amount.currency();
        LedgerAccount suspense = LedgerAccount.require(AccountCode.CONTROL_SUSPENSE, currency);
        LedgerAccount counter = LedgerAccount.require(counterAccountCode, currency);

        Money magnitude = amount.abs();
        boolean cameIn = amount.amountMinor() > 0;

        // Money in was credited to suspense, so clearing it debits suspense. Money out is the
        // mirror. Either way the bank account is untouched: it was already right.
        PostingLine suspenseLine = cameIn
                ? PostingLine.debit(UuidUtil.v7(), suspense.getUuid(), magnitude)
                : PostingLine.credit(UuidUtil.v7(), suspense.getUuid(), magnitude);
        PostingLine counterLine = cameIn
                ? PostingLine.credit(UuidUtil.v7(), counter.getUuid(), magnitude)
                : PostingLine.debit(UuidUtil.v7(), counter.getUuid(), magnitude);

        return postEntry.post(
                PostingRequest.of("BANK_ITEM_RECLASSIFIED", idempotencyKey(bankRef, counterAccountCode))
                        .provider("bank", bankRef)
                        .reconcileGroup(BankReconciliation.groupOf(bankRef))
                        .meta("source", "manual_bank")
                        .meta("bankRef", bankRef)
                        .memo(memo != null && !memo.isBlank()
                                ? memo
                                : "Bank item " + bankRef + " classified as " + counterAccountCode)
                        .line(suspenseLine)
                        .line(counterLine)
                        .build());
    }

    /**
     * Keyed by reference and destination, so one item may be split across several accounts but the
     * same split cannot be applied twice. Splitting one item into two amounts on the <em>same</em>
     * account is the case this refuses; do it as one line for their total.
     */
    private static String idempotencyKey(String bankRef, String counterAccountCode) {
        return "bank-reclass:" + bankRef + ":" + counterAccountCode;
    }
}


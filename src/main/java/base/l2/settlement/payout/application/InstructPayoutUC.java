package base.l2.settlement.payout.application;

import base.l0.system.money.Money;
import base.l1.platform.reference.IssuedReference;
import base.l1.platform.reference.ReferenceIssuer;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.allocation.application.AllocateItemUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l0.system.util.UuidUtil;
import base.l2.settlement.payout.domain.PayoutBlockedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * First half of a payout: the instruction.
 *
 * <p>Debits the partner and parks the money in {@code clearing:payout:in_flight} — not in the bank
 * account, because it is not at the bank yet. The in-flight balance is then a real number with a
 * real meaning: everything we have told the bank to send and have not been told landed. Its age is
 * the earliest signal a bank integration is quietly failing.
 */
@ApplicationScoped
public class InstructPayoutUC {

    @Inject
    PostEntryUC postEntry;

    @Inject
    OpenAccountUC openAccount;

    @Inject
    ReferenceIssuer references;

    @Inject
    AllocateItemUC allocate;

    /**
     * @param bankRef the bank's own reference for the transfer, if the caller already has one. Ours
     *                is issued here and is what travels in the transfer's reference field.
     */
    @Transactional
    public JournalEntry instruct(UUID payoutId, UUID sellerId, Money amount, String bankRef) {
        String currency = amount.currency();

        LedgerAccount arControl = openAccount.open(
                OpenAccountUC.Spec.control(AccountCode.CONTROL_COUNTERPARTIES, currency));
        LedgerAccount seller = openAccount.open(OpenAccountUC.Spec.partner(
                AccountCode.subledger("seller", sellerId), "SELLER", sellerId, currency,
                arControl.getUuid(), 0L, null));

        // A partner carrying a debit balance owes us; sending them more money before that clears
        // is how a clawback becomes uncollectable.
        if (seller.getBalanceMinor() > 0) {
            throw new PayoutBlockedException(
                    "Seller " + sellerId + " carries a debit balance of "
                            + Money.of(seller.getBalanceMinor(), currency) + "; payout is blocked until it clears");
        }
        if (seller.getBalanceMinor() + amount.amountMinor() > 0) {
            throw new PayoutBlockedException(
                    "Payout of " + amount + " exceeds the seller's available balance");
        }

        LedgerAccount inFlight = openAccount.open(
                OpenAccountUC.Spec.clearingLiability(AccountCode.CONTROL_PAYOUT_IN_FLIGHT, currency, null, (short) 2));

        // Issued before the posting so the entry carries it: this is the string that goes in the
        // transfer's reference field and comes back on the confirmation, the return and the
        // statement line. Idempotent, so a retried instruction reuses the same one.
        IssuedReference reference = references.issue("PAYOUT", payoutId);

        // Held so the debit can be matched against what it pays down. A payout that does not
        // close the escrow it settles leaves those credits open forever, and every aging and
        // open-item report built on them reads the seller as still owed money already sent.
        UUID sellerLineUuid = UuidUtil.v7();

        JournalEntry entry = postEntry.post(
                PostingRequest.of("PAYOUT_INSTRUCTED", "payout-instruct:" + payoutId)
                        .document("PAYOUT", payoutId)
                        .provider("bank", reference.getReference())
                        .reconcileGroup("payout:" + payoutId)
                        .memo("Payout instructed to bank as " + reference.getReference()
                                + (bankRef == null ? "" : ", bank ref " + bankRef))
                        .line(PostingLine.debit(sellerLineUuid, seller.getUuid(), amount))
                        .line(PostingLine.credit(UuidUtil.v7(), inFlight.getUuid(), amount))
                        .build());

        allocate.settleOldestFirst(seller.getUuid(), sellerLineUuid, amount);
        return entry;
    }
}


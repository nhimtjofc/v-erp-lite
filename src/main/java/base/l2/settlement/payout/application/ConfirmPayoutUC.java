package base.l2.settlement.payout.application;

import base.l0.system.money.Money;
import base.l1.platform.reference.IssuedReference;
import base.l1.platform.reference.ReferenceIssuer;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l0.system.util.UuidUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Second half of a payout: the bank confirmed, or the transfer came back.
 *
 * <p>Confirmation moves the money from in-flight to the bank position. A return reverses the
 * original instruction outright, so the partner's balance is restored and the funds become payable
 * again — the path the predecessor had no state for.
 */
@ApplicationScoped
public class ConfirmPayoutUC {

    @Inject
    PostEntryUC postEntry;

    @Inject
    OpenAccountUC openAccount;

    @Inject
    ReferenceIssuer references;

    @Transactional
    public JournalEntry confirm(UUID payoutId, Money amount, String bankRef) {
        String currency = amount.currency();
        LedgerAccount inFlight = openAccount.open(
                OpenAccountUC.Spec.clearingLiability(AccountCode.CONTROL_PAYOUT_IN_FLIGHT, currency, null, (short) 2));
        LedgerAccount bank = openAccount.open(
                OpenAccountUC.Spec.asset(AccountCode.CONTROL_BANK, currency, "bank:operating"));

        // The same reference the instruction carried — issue() returns the existing one. Without it
        // on this entry the confirmation is tied to the instruction only by a payout id nobody
        // outside this service can see.
        IssuedReference reference = references.issue("PAYOUT", payoutId);

        return postEntry.post(PostingRequest.of("PAYOUT_CONFIRMED", "payout-confirm:" + payoutId)
                .document("PAYOUT", payoutId)
                .provider("bank", reference.getReference())
                .reconcileGroup("payout:" + payoutId)
                .memo("Bank confirmed payout " + reference.getReference()
                        + (bankRef == null ? "" : ", bank ref " + bankRef))
                .line(PostingLine.debit(UuidUtil.v7(), inFlight.getUuid(), amount))
                .line(PostingLine.credit(UuidUtil.v7(), bank.getUuid(), amount))
                .build());
    }

    /**
     * The transfer bounced. Reverses the instruction so the partner is owed again, and links the
     * reversal to the entry it undoes so the chain is auditable.
     */
    @Transactional
    public JournalEntry markReturned(UUID payoutId, UUID sellerId, Money amount,
                                     UUID instructionEntryUuid, String reason) {
        String currency = amount.currency();
        LedgerAccount arControl = openAccount.open(
                OpenAccountUC.Spec.control(AccountCode.CONTROL_COUNTERPARTIES, currency));
        LedgerAccount seller = openAccount.open(OpenAccountUC.Spec.partner(
                AccountCode.subledger("seller", sellerId), "SELLER", sellerId, currency,
                arControl.getUuid(), 0L, null));
        LedgerAccount inFlight = openAccount.open(
                OpenAccountUC.Spec.clearingLiability(AccountCode.CONTROL_PAYOUT_IN_FLIGHT, currency, null, (short) 2));

        IssuedReference reference = references.issue("PAYOUT", payoutId);

        return postEntry.post(PostingRequest.of("PAYOUT_RETURNED", "payout-return:" + payoutId)
                .document("PAYOUT", payoutId)
                .provider("bank", reference.getReference())
                .reverses(instructionEntryUuid)
                .reconcileGroup("payout:" + payoutId)
                .memo("Payout " + reference.getReference() + " returned by bank: " + reason)
                .line(PostingLine.debit(UuidUtil.v7(), inFlight.getUuid(), amount))
                .line(PostingLine.credit(UuidUtil.v7(), seller.getUuid(), amount))
                .build());
    }
}


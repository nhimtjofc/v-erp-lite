package base.l2.settlement.dispute.application;

import base.l0.system.money.Money;
import base.l0.system.util.UuidUtil;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.settlement.dispute.domain.DisputeRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * A payer has charged back, and the money is gone until somebody decides who keeps it.
 *
 * <p>The acquirer debits our position the moment a chargeback is raised, so that is what this
 * records: the provider holds less, and the difference sits in {@code clearing:dispute:held} as a
 * claim we are contesting. Posting it now rather than at resolution is the same rule bank
 * reconciliation follows — the position must be right immediately, and the classification catches
 * up.
 *
 * <p>Nothing is taken from the counterparty here, deliberately. A dispute is not yet a loss, and
 * debiting them on the strength of a chargeback that is frequently reversed would mean holding
 * their money against an outcome that has not happened. {@link ResolveDisputeUC#lost} is where
 * anyone's balance moves.
 *
 * <p>The reconcile group is the lifecycle's name. {@code clearing:dispute:held} is a clearing
 * account, so {@code v_clearing_group_balance} reports any dispute still holding a balance past its
 * tolerance — which for a chargeback is the useful alarm, since the failure mode is not a wrong
 * number but a case nobody ever answered.
 */
@ApplicationScoped
public class OpenDisputeUC {

    /**
     * Card scheme representment windows run to 45 days and beyond, so a dispute open for a month is
     * ordinary. This is set to flag the ones that have been forgotten, not the ones in progress.
     */
    private static final short TOLERANCE_DAYS = 75;

    @Inject
    PostEntryUC postEntry;

    @Inject
    OpenAccountUC openAccount;

    @Transactional
    public JournalEntry open(UUID disputeId, String provider, String providerRef, Money amount,
                             String reason) {
        if (!amount.isPositive()) {
            throw new DisputeRejectedException(
                    "A dispute must be for a positive amount; " + amount + " contests nothing");
        }
        String currency = amount.currency();

        LedgerAccount psp = openAccount.open(
                OpenAccountUC.Spec.asset(AccountCode.psp(provider), currency, provider));
        LedgerAccount held = openAccount.open(OpenAccountUC.Spec.clearingAsset(
                AccountCode.CONTROL_DISPUTE_HELD, currency, null, TOLERANCE_DAYS));

        return postEntry.post(PostingRequest.of("DISPUTE_OPENED", "dispute-open:" + disputeId)
                .document("DISPUTE", disputeId)
                .provider(provider, providerRef)
                .reconcileGroup(disputeGroup(disputeId))
                .memo(reason == null ? "Chargeback raised" : reason)
                .line(PostingLine.debit(UuidUtil.v7(), held.getUuid(), amount)
                        .describedAs("Contested amount held pending the outcome"))
                .line(PostingLine.credit(UuidUtil.v7(), psp.getUuid(), amount)
                        .describedAs("Withheld by " + provider + " on chargeback"))
                .build());
    }

    /** The name both halves of the lifecycle post under, and the key the clearing check nets on. */
    public static String disputeGroup(UUID disputeId) {
        return "dispute:" + disputeId;
    }
}


package base.l2.settlement.dispute.application;

import base.l0.system.money.Money;
import base.l0.system.util.UuidUtil;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.allocation.application.AllocateItemUC;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l2.settlement.dispute.domain.DisputeRejectedException;
import base.l2.settlement.dispute.domain.DisputeShare;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a dispute ends. Both ways empty {@code clearing:dispute:held}, which is the point of it.
 *
 * <p>Two outcomes and one account. Whichever way a case goes, the contested amount stops being
 * contested, so the clearing balance for that reconcile group returns to zero and
 * {@code v_clearing_group_balance} stops reporting it. A dispute that ends without one of these
 * being called stays visible forever, which is the correct behaviour: an unanswered chargeback is
 * money nobody decided about.
 *
 * <p>Two methods rather than two classes, following {@code ConfirmPayoutUC}: won and lost are the
 * branches of one lifecycle, not separate pieces of business, and splitting them would put the
 * shared knowledge of which account the money returns to in two places.
 */
@ApplicationScoped
public class ResolveDisputeUC {

    @Inject
    PostEntryUC postEntry;

    @Inject
    OpenAccountUC openAccount;

    @Inject
    AllocateItemUC allocate;

    /**
     * We won: the acquirer puts the money back.
     *
     * <p>The exact reverse of opening it, and nobody else's balance ever moved, which is why
     * declining to touch the counterparty at open time matters. Had the loss been provisioned
     * against them, winning would require unwinding it, and the counterparty would have spent the
     * intervening weeks apparently owing us money over a charge they turned out to be right about.
     */
    @Transactional
    public JournalEntry won(UUID disputeId, String provider, Money amount) {
        String currency = requirePositive(amount).currency();

        LedgerAccount psp = openAccount.open(
                OpenAccountUC.Spec.asset(AccountCode.psp(provider), currency, provider));
        LedgerAccount held = heldAccount(currency);

        return postEntry.post(PostingRequest.of("DISPUTE_WON", "dispute-won:" + disputeId)
                .document("DISPUTE", disputeId)
                .provider(provider, null)
                .reconcileGroup(OpenDisputeUC.disputeGroup(disputeId))
                .memo("Chargeback reversed in our favour")
                .line(PostingLine.debit(UuidUtil.v7(), psp.getUuid(), amount)
                        .describedAs("Returned by " + provider + " on a won dispute"))
                .line(PostingLine.credit(UuidUtil.v7(), held.getUuid(), amount)
                        .describedAs("Contested amount released"))
                .build());
    }

    /**
     * We lost: the money is gone and somebody absorbs it.
     *
     * <p>Each share debits a counterparty — they had the value and now owe it back. Whatever the
     * shares do not cover is a platform loss and is posted to {@code expense:write_off} under its
     * own name, rather than being quietly left in the clearing account where it would masquerade as
     * a case still in progress.
     *
     * <p>Shares are matched against whatever that counterparty still has open, so a party with
     * funds not yet paid out simply has them reduced, and a party already paid out ends up carrying
     * a genuine receivable. That difference is not decided here; it falls out of what is open,
     * exactly as it does for a refund.
     */
    @Transactional
    public JournalEntry lost(UUID disputeId, Money amount, List<DisputeShare> shares, String reason) {
        String currency = requirePositive(amount).currency();
        LedgerAccount held = heldAccount(currency);
        LedgerAccount arControl = openAccount.open(
                OpenAccountUC.Spec.control(AccountCode.CONTROL_COUNTERPARTIES, currency));

        List<PostingLine> lines = new ArrayList<>();
        Map<UUID, UUID> lineToAccount = new LinkedHashMap<>();
        Map<UUID, Money> lineToAmount = new LinkedHashMap<>();
        Money borne = Money.zero(currency);

        for (DisputeShare share : shares == null ? List.<DisputeShare>of() : shares) {
            if (!share.amount().isPositive()) {
                continue;
            }
            if (!share.amount().currency().equals(currency)) {
                throw new DisputeRejectedException("Share for " + share.partyId() + " is in "
                        + share.amount().currency() + " but the dispute is in " + currency);
            }
            LedgerAccount party = openAccount.open(OpenAccountUC.Spec.partner(
                    AccountCode.subledger(share.ownerType(), share.partyId()),
                    share.ownerType().toUpperCase(java.util.Locale.ROOT), share.partyId(),
                    currency, arControl.getUuid(), share.creditLimitMinor(), null));

            UUID lineUuid = UuidUtil.v7();
            lines.add(PostingLine.debit(lineUuid, party.getUuid(), share.amount())
                    .describedAs("Share of lost dispute " + disputeId));
            lineToAccount.put(lineUuid, party.getUuid());
            lineToAmount.put(lineUuid, share.amount());
            borne = borne.add(share.amount());
        }

        Money shortfall = amount.subtract(borne);
        if (shortfall.isNegative()) {
            throw new DisputeRejectedException(
                    "Shares total " + borne + ", more than the " + amount + " lost");
        }
        if (shortfall.isPositive()) {
            LedgerAccount writeOff = openAccount.open(
                    OpenAccountUC.Spec.expense(AccountCode.EXPENSE_WRITE_OFF, currency));
            lines.add(PostingLine.debit(UuidUtil.v7(), writeOff.getUuid(), shortfall)
                    .describedAs("Unrecovered portion of lost dispute " + disputeId));
        }

        lines.add(PostingLine.credit(UuidUtil.v7(), held.getUuid(), amount)
                .describedAs("Contested amount written out on a lost dispute"));

        PostingRequest.Builder builder = PostingRequest.of("DISPUTE_LOST", "dispute-lost:" + disputeId)
                .document("DISPUTE", disputeId)
                .reconcileGroup(OpenDisputeUC.disputeGroup(disputeId))
                .memo(reason == null ? "Chargeback upheld against us" : reason);
        lines.forEach(builder::line);

        JournalEntry entry = postEntry.post(builder.build());

        lineToAmount.forEach((lineUuid, share) ->
                allocate.settleOldestFirst(lineToAccount.get(lineUuid), lineUuid, share));
        return entry;
    }

    private LedgerAccount heldAccount(String currency) {
        return openAccount.open(OpenAccountUC.Spec.clearingAsset(
                AccountCode.CONTROL_DISPUTE_HELD, currency, null, (short) 75));
    }

    private static Money requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new DisputeRejectedException("A dispute resolution needs a positive amount");
        }
        return amount;
    }
}


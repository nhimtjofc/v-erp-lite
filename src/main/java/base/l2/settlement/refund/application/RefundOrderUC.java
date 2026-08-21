package base.l2.settlement.refund.application;

import base.l0.system.money.Money;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.allocation.application.AllocateItemUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.Direction;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l0.system.util.UuidUtil;
import base.l2.settlement.refund.domain.FundsLocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Refunds a buyer, taking the money back from whoever is actually holding it.
 *
 * <p>This is the fix for the most expensive defect in the system this replaces. There, a refund
 * called the provider for the full amount and then reversed only escrow records still in HELD — so
 * once delivery had happened and the hold had been released, the reversal found nothing, the buyer
 * was paid from platform funds, and the seller kept their share. Nothing was written on the seller
 * side, so the loss was silent and undetectable afterwards.
 *
 * <p>The root cause was not the loop. It was that the ledger had no way to say a seller owed money
 * back, so the only expressible action was to do nothing. With signed balances the third case
 * finally has a posting: the seller's account goes debit and stays there until it clears, and the
 * payout guard refuses to send money to an account carrying one.
 */
@ApplicationScoped
public class RefundOrderUC {

    @Inject
    PostEntryUC postEntry;

    @Inject
    OpenAccountUC openAccount;

    @Inject
    AllocateItemUC allocate;

    @Transactional
    public JournalEntry refund(Command command) {
        Money amount = command.amount();
        String currency = amount.currency();

        LedgerAccount psp = openAccount.open(
                OpenAccountUC.Spec.asset(AccountCode.psp(command.provider()), currency, command.provider()));
        LedgerAccount arControl = openAccount.open(
                OpenAccountUC.Spec.control(AccountCode.CONTROL_COUNTERPARTIES, currency));

        List<PostingLine> lines = new ArrayList<>();
        Money reclaimed = Money.zero(currency);

        // Seller debit lines, kept so they can be matched afterwards against whatever escrow of
        // that seller's is still open.
        Map<UUID, Money> sellerReclaims = new LinkedHashMap<>();
        Map<UUID, UUID> sellerAccounts = new LinkedHashMap<>();

        for (SellerShare share : command.sellerShares()) {
            LedgerAccount seller = openAccount.open(OpenAccountUC.Spec.partner(
                    AccountCode.subledger("seller", share.sellerId()), "SELLER", share.sellerId(),
                    currency, arControl.getUuid(), share.creditLimitMinor(), null));

            UUID lineUuid = UuidUtil.v7();
            // In every case the seller's obligation to us increases by their net share; what
            // differs is which account gives the value back.
            switch (share.location()) {
                case IN_ESCROW, RELEASED -> lines.add(
                        PostingLine.debit(lineUuid, seller.getUuid(), share.net())
                                .describedAs("Refund reclaim (" + share.location() + ") for order " + command.orderId()));
                case PAID_OUT -> lines.add(
                        PostingLine.debit(lineUuid, seller.getUuid(), share.net())
                                .describedAs("Refund clawback after payout for order " + command.orderId()));
            }
            sellerReclaims.put(lineUuid, share.net());
            sellerAccounts.put(lineUuid, seller.getUuid());
            reclaimed = reclaimed.add(share.net());
        }

        if (command.commission().isPositive()) {
            LedgerAccount commission = openAccount.open(
                    OpenAccountUC.Spec.revenue(AccountCode.REVENUE_FEE, currency));
            lines.add(PostingLine.debit(UuidUtil.v7(), commission.getUuid(), command.commission())
                    .describedAs("Commission reversed on refund of order " + command.orderId()));
            reclaimed = reclaimed.add(command.commission());
        }

        // Anything the sellers and commission do not cover is a genuine platform loss, and is
        // named as one instead of silently reducing a balance somewhere.
        Money shortfall = amount.subtract(reclaimed);
        if (shortfall.isPositive()) {
            LedgerAccount writeOff = openAccount.open(
                    OpenAccountUC.Spec.expense(AccountCode.EXPENSE_WRITE_OFF, currency));
            lines.add(PostingLine.debit(UuidUtil.v7(), writeOff.getUuid(), shortfall)
                    .describedAs("Unrecovered portion of refund for order " + command.orderId()));
        } else if (shortfall.isNegative()) {
            LedgerAccount rounding = openAccount.open(
                    OpenAccountUC.Spec.expense(AccountCode.EXPENSE_ROUNDING, currency));
            lines.add(new PostingLine(UuidUtil.v7(), rounding.getUuid(), Direction.CR,
                    shortfall.abs(), null, "Over-recovery on refund of order " + command.orderId()));
        }

        // The buyer is paid back out of our position at the provider.
        lines.add(PostingLine.credit(UuidUtil.v7(), psp.getUuid(), amount)
                .describedAs("Refunded to buyer via " + command.provider()));

        PostingRequest.Builder builder = PostingRequest.of("REFUND_ISSUED", command.idempotencyKey())
                .document("ORDER", command.orderId())
                .provider(command.provider(), command.providerRef())
                .reconcileGroup("order:" + command.orderId())
                .memo(command.reason());
        lines.forEach(builder::line);

        JournalEntry entry = postEntry.post(builder.build());

        // Matching is what separates the two refund cases in the reports, without either branch
        // above having to know it: money still in escrow has open credits for this debit to close,
        // so the seller's position simply reduces. Money already paid out has none — the payout
        // closed them — so the debit stays open and shows up as what it is, a receivable we now
        // have to collect. Neither outcome is decided here; it falls out of what is still open.
        sellerReclaims.forEach((lineUuid, net) ->
                allocate.settleOldestFirst(sellerAccounts.get(lineUuid), lineUuid, net));

        return entry;
    }

    public record SellerShare(UUID sellerId, Money net, FundsLocation location, long creditLimitMinor) {
    }

    public record Command(
            UUID orderId,
            String provider,
            String providerRef,
            Money amount,
            Money commission,
            List<SellerShare> sellerShares,
            String reason,
            String idempotencyKey
    ) {
    }
}


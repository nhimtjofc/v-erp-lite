package base.l2.settlement.intent.application;

import base.l0.system.money.Money;
import base.l2.ledger.account.application.OpenAccountUC;
import base.l2.ledger.account.domain.AccountCode;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.fee.application.FeeScheduleQuery;
import base.l2.ledger.fee.domain.FeeSchedule;
import base.l2.ledger.journal.application.PostEntryUC;
import base.l2.ledger.journal.domain.JournalEntry;
import base.l2.ledger.journal.domain.PostingLine;
import base.l2.ledger.journal.domain.PostingRequest;
import base.l0.system.util.UuidUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import base.l2.settlement.api.CaptureRequest;
import base.l2.settlement.api.CaptureResult;
import base.l2.settlement.intent.domain.CaptureRejectedException;

/**
 * Records a captured payment as one balanced entry.
 *
 * <p>"Captured", not "settled". In the card industry settlement is a specific later event: the
 * funding movement between acquirer, network and issuer that lands at T+1 or T+2 and produces a
 * settlement file. This use case fires when the money reaches the provider, which is capture. Using
 * one word for both is how a reconciliation process ends up comparing two different events and
 * concluding the books are wrong.
 *
 * <p>Money arrives at the provider and is owed onward, so the entry is: debit what the provider now
 * holds for us, credit each seller's escrow obligation, credit our commission. Every seller's share
 * has a counter-account, which is what makes the total tie out — the predecessor credited sellers
 * and commission with nothing on the other side, so there was no number for what the provider owed
 * and no way to reconcile against a settlement file.
 *
 * <p>Rounding residue is posted to an explicit rounding account rather than being absorbed into the
 * last seller's slice. If proportional splitting leaves a remainder, that remainder is a real
 * amount that belongs somewhere nameable.
 *
 * <p>The provider's fee is debited to {@code expense:psp_fees} and the provider control account is
 * debited only the net. This matters more than it looks: a provider settles you gross minus its
 * fee, so debiting the control account the full gross makes it claim the provider owes us money it
 * was never going to send. The difference is invisible per transaction and permanent in aggregate —
 * the account drifts by the whole fee, every capture, forever, and no invariant here can see it
 * because the entry balances perfectly and {@code BANK_AGREES_WITH_BOOK} only covers accounts with
 * an attestation. Splitting it also keeps the two fees apart: theirs is an expense, ours is
 * revenue, and a single net number can answer neither "what did processing cost us" nor "what did
 * we earn".
 */
@ApplicationScoped
public class CapturePaymentUC {

    @Inject
    PostEntryUC postEntry;

    @Inject
    OpenAccountUC openAccount;

    @Inject
    FeeScheduleQuery feeSchedules;

    @Transactional
    public CaptureResult capture(CaptureRequest request) {
        Money gross = request.grossCharged();
        String currency = gross.currency();

        Money providerFee = request.providerFee() == null ? Money.zero(currency) : request.providerFee();
        if (providerFee.isNegative()) {
            throw new CaptureRejectedException(
                    "Provider fee " + providerFee + " is negative; a fee the provider pays us is revenue, not a fee");
        }
        if (!providerFee.currency().equals(currency)) {
            throw new CaptureRejectedException(
                    "Provider fee is in " + providerFee.currency() + " but the capture is in " + currency);
        }
        Money netFromProvider = gross.subtract(providerFee);
        if (netFromProvider.isNegative()) {
            throw new CaptureRejectedException(
                    "Provider fee " + providerFee + " exceeds the " + gross + " charged");
        }

        LedgerAccount psp = openAccount.open(
                OpenAccountUC.Spec.asset(AccountCode.psp(request.provider()), currency, request.provider()));
        LedgerAccount commissionAccount = openAccount.open(
                OpenAccountUC.Spec.revenue(AccountCode.REVENUE_FEE, currency));
        LedgerAccount arControl = openAccount.open(
                OpenAccountUC.Spec.control(AccountCode.CONTROL_COUNTERPARTIES, currency));

        List<PostingLine> lines = new ArrayList<>();
        // The provider holds the net, not the gross. Debiting gross here is what would make this
        // account permanently overstate what they owe us.
        if (netFromProvider.isPositive()) {
            lines.add(PostingLine.debit(UuidUtil.v7(), psp.getUuid(), netFromProvider)
                    .describedAs("Charged via " + request.provider() + " for order " + request.orderNumber()));
        }
        if (providerFee.isPositive()) {
            LedgerAccount pspFees = openAccount.open(
                    OpenAccountUC.Spec.expense(AccountCode.EXPENSE_PSP_FEES, currency));
            lines.add(PostingLine.debit(UuidUtil.v7(), pspFees.getUuid(), providerFee)
                    .describedAs(request.provider() + " processing fee on order " + request.orderNumber()));
        }

        Money commissionTotal = Money.zero(currency);
        Money allocated = Money.zero(currency);
        // Schedules named by the splits, so the entry can say which version priced it. A set
        // rather than one value: nothing stops two parties on one order being on different
        // arrangements, and recording only the first would be a plausible-looking lie.
        java.util.Set<String> appliedSchedules = new java.util.LinkedHashSet<>();
        Instant pricedAt = Instant.now();

        for (CaptureRequest.SellerSplit split : request.splits()) {
            LedgerAccount seller = openAccount.open(OpenAccountUC.Spec.partner(
                    AccountCode.subledger("seller", split.sellerId()), "SELLER", split.sellerId(),
                    currency, arControl.getUuid(), 0L, null));

            if (split.feeScheduleCode() != null && !split.feeScheduleCode().isBlank()) {
                FeeSchedule schedule = feeSchedules.require(split.feeScheduleCode(), pricedAt);
                Money expected = schedule.feeOn(split.gross());
                if (!expected.equals(split.commission())) {
                    throw new CaptureRejectedException("Fee schedule " + schedule.describe()
                            + " makes the fee on " + split.gross() + " " + expected
                            + ", but the capture says " + split.commission());
                }
                appliedSchedules.add(schedule.describe());
            }

            Money net = split.net();
            if (net.isPositive()) {
                // The due date is what turns aging from a column of zeroes into a report: with it
                // null on every line, v_aging can only ever bucket everything as current.
                lines.add(PostingLine.credit(UuidUtil.v7(), seller.getUuid(), net)
                        .dueOn(dueDate(seller))
                        .describedAs("Escrow for order " + request.orderNumber()));
                allocated = allocated.add(net);
            }
            commissionTotal = commissionTotal.add(split.commission());
        }

        if (commissionTotal.isPositive()) {
            lines.add(PostingLine.credit(UuidUtil.v7(), commissionAccount.getUuid(), commissionTotal)
                    .describedAs("Platform commission on order " + request.orderNumber()));
            allocated = allocated.add(commissionTotal);
        }

        // Whatever proportional splitting could not place lands in a named account rather than
        // being quietly folded into somebody's balance.
        Money residue = gross.subtract(allocated);
        if (!residue.isZero()) {
            LedgerAccount rounding = openAccount.open(
                    OpenAccountUC.Spec.expense(AccountCode.EXPENSE_ROUNDING, currency));
            lines.add(new PostingLine(UuidUtil.v7(), rounding.getUuid(),
                    residue.isPositive() ? base.l2.ledger.journal.domain.Direction.CR
                                         : base.l2.ledger.journal.domain.Direction.DR,
                    residue.abs(), null, "Rounding residue on order " + request.orderNumber()));
        }

        PostingRequest.Builder builder = PostingRequest.of("PAYMENT_CAPTURED", request.idempotencyKey())
                .document("ORDER", request.orderId())
                .provider(request.provider(), request.providerRef())
                .reconcileGroup("order:" + request.orderId())
                .meta("orderNumber", request.orderNumber())
                .memo("Settlement of order " + request.orderNumber());
        if (!appliedSchedules.isEmpty()) {
            // On the entry, not merely checked at the door: the value of a versioned rate is being
            // able to answer "why this number" from the posting alone, years later, without
            // depending on what the schedule table happens to contain by then.
            builder.meta("feeSchedules", String.join("; ", appliedSchedules));
        }
        lines.forEach(builder::line);

        JournalEntry entry = postEntry.post(builder.build());
        return new CaptureResult(entry.getUuid(), gross, providerFee, commissionTotal, request.splits().size());
    }

    /** Terms-based due date for an open item, when the account carries payment terms. */
    static LocalDate dueDate(LedgerAccount account) {
        Short terms = account.getPaymentTermsDays();
        return terms == null ? null : LocalDate.now().plusDays(terms);
    }
}


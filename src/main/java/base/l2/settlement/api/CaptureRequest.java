package base.l2.settlement.api;

import base.l0.system.money.Money;

import java.util.List;
import java.util.UUID;

/**
 * A settled order to record: what the buyer paid, and how it splits across sellers.
 *
 * <p>The caller supplies the split rather than this service deriving it from order items. That is
 * the module boundary: the ERP knows what an order line is; the ledger knows what a posting is.
 */
/**
 * @param grossCharged what the buyer was charged
 * @param providerFee  what the provider keeps out of that — its processing fee, not ours. May be
 *                     null when the provider settles gross and bills separately. Distinct from
 *                     {@link SellerSplit#commission}, which is what <em>we</em> charge the seller:
 *                     one is an expense and the other is revenue, and netting them into a single
 *                     number loses both.
 */
public record CaptureRequest(
        UUID orderId,
        String orderNumber,
        UUID buyerId,
        String provider,
        String providerRef,
        Money grossCharged,
        Money providerFee,
        List<SellerSplit> splits,
        String idempotencyKey
) {
    /**
     * @param feeScheduleCode the arrangement {@code commission} was priced under, if the caller
     *                        quotes one. Optional, and that is a migration path rather than a
     *                        loophole: a caller that names a schedule has its number recomputed and
     *                        refused if it disagrees, and the version that priced it is stamped on
     *                        the entry. A caller that names none is trusted exactly as before. The
     *                        alternative — making it mandatory on day one — would have meant this
     *                        service refusing every capture until the ERP shipped a matching
     *                        release.
     */
    public record SellerSplit(UUID sellerId, Money gross, Money commission, String feeScheduleCode) {

        public SellerSplit(UUID sellerId, Money gross, Money commission) {
            this(sellerId, gross, commission, null);
        }

        public Money net() {
            return gross.subtract(commission);
        }
    }
}


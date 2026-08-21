package base.l1.platform.provider;

import base.l0.system.money.Money;

import java.util.Map;
import java.util.UUID;

/**
 * What a provider needs in order to open a charge.
 *
 * <p>Deliberately free of any vocabulary from the system that originated the payment. A card
 * processor has no concept of an order, an invoice or a subscription, and an adapter that had to
 * know about one would be coupled to whatever happens to be calling this month. Anything the
 * caller wants echoed back — a document number, a description for the payer — travels in
 * {@code metadata} and is opaque to everything in this package.
 */
public record PaymentRequest(
        UUID intentId,
        Money amount,
        String returnUrl,
        String idempotencyKey,
        Map<String, String> metadata
) {
}


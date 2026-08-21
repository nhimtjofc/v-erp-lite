package base.l1.platform.provider;

import java.time.Instant;
import java.util.Map;

/** What the provider gave back when a charge was opened: where to send the payer, and its reference. */
public record PaymentHandle(
        String provider,
        String providerRef,
        String redirectUrl,
        String qrPayload,
        Instant expiresAt,
        Map<String, String> metadata
) {
}


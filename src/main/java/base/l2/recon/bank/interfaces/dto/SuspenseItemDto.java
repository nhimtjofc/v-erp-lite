package base.l2.recon.bank.interfaces.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Money sitting in suspense, with the date it started sitting there.
 *
 * <p>{@code openSince} is the age of the oldest item posted since the account was last at zero,
 * which is the number that matters: a suspense balance is not a problem because it is large, it is
 * a problem because it is old.
 */
public record SuspenseItemDto(
        UUID accountUuid,
        String code,
        String currency,
        long balanceMinor,
        Instant openSince,
        long daysOpen
) {
}


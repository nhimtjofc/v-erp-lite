package base.l2.ledger.reporting.interfaces.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An account balance, presented both signed (debit-positive, the ledger's own convention) and as
 * the account's natural side, because "the balance" means different things to an accountant and to
 * the partner reading their statement.
 */
public record AccountBalanceDto(
        UUID accountUuid,
        String code,
        String accountClass,
        String accountRole,
        String ownerType,
        UUID ownerId,
        String currency,
        long signedBalanceMinor,
        BigDecimal signedBalance,
        String naturalSide,
        long naturalBalanceMinor,
        boolean owesUs,
        long creditLimitMinor,
        long entrySeq
) {
}


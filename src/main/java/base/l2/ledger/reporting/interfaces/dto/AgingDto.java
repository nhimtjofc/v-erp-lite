package base.l2.ledger.reporting.interfaces.dto;

import java.util.UUID;

public record AgingDto(
        UUID accountUuid,
        String accountCode,
        String ownerType,
        UUID ownerId,
        String currency,
        long currentMinor,
        long overdue1to30Minor,
        long overdue31to60Minor,
        long overdue61to90Minor,
        long overdue90PlusMinor,
        long totalMinor
) {
}


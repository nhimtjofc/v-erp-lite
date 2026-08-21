package base.l2.ledger.reporting.interfaces.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementLineDto(
        UUID lineUuid,
        UUID entryUuid,
        String eventType,
        String documentType,
        UUID documentId,
        String direction,
        long amountMinor,
        String currency,
        LocalDate dueDate,
        long balanceAfterMinor,
        long sequenceNo,
        Instant effectiveAt,
        String memo
) {
}


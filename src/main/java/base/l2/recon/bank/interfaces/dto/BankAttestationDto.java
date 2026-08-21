package base.l2.recon.bank.interfaces.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A statement's closing balance beside our own for the same date, and the gap between them.
 *
 * <p>{@code deltaMinor} is what the bank holds minus what we think it holds. Non-zero means real
 * activity we have not booked: positive, money arrived that we do not know about; negative, money
 * left. Both are computed at read time, so the number goes to zero on its own once the missing
 * transactions are recorded.
 */
public record BankAttestationDto(
        UUID attestationUuid,
        UUID accountUuid,
        String accountCode,
        String currency,
        LocalDate statementDate,
        String statementRef,
        long closingBalanceMinor,
        long bookBalanceMinor,
        long deltaMinor,
        boolean reconciled
) {
}


package base.service;

import base.entity.PayoutRequest;
import base.entity.Wallet;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PayoutService {

    @Inject
    MoneyService moneyService;

    @Inject
    WalletService walletService;

    @Inject
    BankTransferService bankTransferService;

    /** Compatibility seam for legacy callers. New UI and jobs do not expose manual payout creation. */
    @Transactional
    public PayoutRequest createPayout(String tenantId, UUID walletId, long amountMinor, String currency,
                                      String bankName, String bankAccountNumber, String bankAccountHolder,
                                      UUID actorId) {
        Wallet wallet = walletService.getWallet(tenantId, walletId);
        Money amount = moneyService.positiveMoney(amountMinor, currency);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        validateBankDetails(bankName, bankAccountNumber, bankAccountHolder);
        return createRequest(
                wallet,
                amountMinor,
                "FLAGGED",
                false,
                "Legacy manual payout request",
                "legacy-manual:" + UUID.randomUUID(),
                bankName,
                bankAccountNumber,
                bankAccountHolder,
                actorId);
    }

    public PayoutRequest createAutomatedRequest(Wallet wallet, String status, boolean autoApproved,
                                                String flagReason, String idempotencyKey) {
        return createRequest(
                wallet,
                wallet.getAvailableBalanceMinor(),
                status,
                autoApproved,
                flagReason,
                idempotencyKey,
                wallet.getBankName(),
                wallet.getBankAccountNumber(),
                wallet.getBankAccountHolder(),
                null);
    }

    public List<PayoutRequest> listPayouts(String tenantId, UUID walletId, String status) {
        if (walletId != null && status != null && !status.isBlank()) {
            return PayoutRequest.list(
                    "tenantId = ?1 and wallet.id = ?2 and status = ?3 order by createdAt desc",
                    tenantId, walletId, status.trim().toUpperCase());
        }
        if (walletId != null) {
            return PayoutRequest.list(
                    "tenantId = ?1 and wallet.id = ?2 order by createdAt desc",
                    tenantId, walletId);
        }
        if (status != null && !status.isBlank()) {
            return PayoutRequest.list(
                    "tenantId = ?1 and status = ?2 order by createdAt desc",
                    tenantId, status.trim().toUpperCase());
        }
        return PayoutRequest.list("tenantId = ?1 order by createdAt desc", tenantId);
    }

    public List<PayoutRequest> listFlagged(String tenantId) {
        return PayoutRequest.list("tenantId = ?1 and status = 'FLAGGED' order by createdAt asc", tenantId);
    }

    public PayoutRequest getPayout(String tenantId, UUID payoutId) {
        PayoutRequest payout = PayoutRequest.find("id = ?1 and tenantId = ?2", payoutId, tenantId).firstResult();
        if (payout == null) {
            throw new NotFoundException("Payout request not found");
        }
        return payout;
    }

    @Transactional
    public PayoutRequest approvePayout(String tenantId, UUID payoutId, UUID actorId) {
        PayoutRequest payout = payoutForUpdate(tenantId, payoutId);
        if ("PAID".equals(payout.getStatus())) {
            return payout;
        }
        if (!"FLAGGED".equals(payout.getStatus())) {
            throw new BadRequestException("Only FLAGGED payouts can be reviewed by an administrator");
        }
        payout.setStatus("APPROVED");
        payout.setUpdatedBy(actorId);
        executeTransfer(payout, actorId);
        return payout;
    }

    @Transactional
    public PayoutRequest rejectPayout(String tenantId, UUID payoutId, UUID actorId) {
        return rejectPayout(tenantId, payoutId, null, actorId);
    }

    @Transactional
    public PayoutRequest rejectPayout(String tenantId, UUID payoutId, String reason, UUID actorId) {
        PayoutRequest payout = payoutForUpdate(tenantId, payoutId);
        if ("REJECTED".equals(payout.getStatus())) {
            return payout;
        }
        if (!"FLAGGED".equals(payout.getStatus())) {
            throw new BadRequestException("Only FLAGGED payouts can be rejected");
        }
        payout.setStatus("REJECTED");
        if (reason != null && !reason.isBlank()) {
            payout.setFlagReason(payout.getFlagReason() + " | Admin rejection: " + reason.trim());
        }
        payout.setUpdatedBy(actorId);
        return payout;
    }

    public void executeTransfer(PayoutRequest payout, UUID actorId) {
        if ("PAID".equals(payout.getStatus())) {
            return;
        }
        validateBankDetails(payout.getBankName(), payout.getBankAccountNumber(), payout.getBankAccountHolder());
        String transferReference = bankTransferService.transfer(
                payout.getIdempotencyKey(),
                payout.getAmountMinor(),
                payout.getCurrency(),
                payout.getBankName(),
                payout.getBankAccountNumber(),
                payout.getBankAccountHolder());
        walletService.debitAvailable(
                payout.getTenantId(),
                payout.getWallet().getId(),
                Money.of(payout.getAmountMinor(), payout.getCurrency()),
                "PAYOUT",
                "PAYOUT_REQUEST",
                payout.getId(),
                actorId,
                "Automated bank payout");
        payout.setTransferReference(transferReference);
        payout.setPaidAt(Instant.now());
        payout.setStatus("PAID");
        payout.setUpdatedBy(actorId);
    }

    public PayoutRequest lastPaid(String tenantId, UUID walletId) {
        return PayoutRequest.find(
                "tenantId = ?1 and wallet.id = ?2 and status = 'PAID' order by paidAt desc",
                tenantId, walletId).firstResult();
    }

    public List<PayoutRequest> listForExport(String tenantId, String status) {
        String statusFilter = status == null || status.isBlank() ? "FLAGGED" : status.trim().toUpperCase();
        return PayoutRequest.list(
                "tenantId = ?1 and status = ?2 order by createdAt asc",
                tenantId, statusFilter);
    }

    @Transactional
    public int batchApprove(String tenantId, List<UUID> payoutIds, UUID actorId) {
        return batch(payoutIds, id -> approvePayout(tenantId, id, actorId));
    }

    @Transactional
    public int batchReject(String tenantId, List<UUID> payoutIds, UUID actorId) {
        return batch(payoutIds, id -> rejectPayout(tenantId, id, actorId));
    }

    public String generateCsv(String tenantId, String status) {
        List<PayoutRequest> payouts = listForExport(tenantId, status);
        String header = "No,Id,Bank,AccountNumber,AccountHolder,Amount,Currency,Status,FlagReason";
        String rows = payouts.stream()
                .map(p -> toCsvRow(p, payouts.indexOf(p) + 1))
                .collect(Collectors.joining("\n"));
        return header + "\n" + rows;
    }

    private PayoutRequest createRequest(Wallet wallet, long amountMinor, String status, boolean autoApproved,
                                        String flagReason, String idempotencyKey, String bankName,
                                        String bankAccountNumber, String bankAccountHolder, UUID actorId) {
        PayoutRequest existing = PayoutRequest.find(
                "tenantId = ?1 and idempotencyKey = ?2", wallet.getTenantId(), idempotencyKey).firstResult();
        if (existing != null) {
            return existing;
        }
        PayoutRequest payout = new PayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setTenantId(wallet.getTenantId());
        payout.setWallet(wallet);
        payout.setAmountMinor(amountMinor);
        payout.setCurrency(wallet.getCurrency());
        payout.setStatus(status);
        payout.setAutoApproved(autoApproved);
        payout.setFlagReason(flagReason);
        payout.setBankName(trimOrEmpty(bankName));
        payout.setBankAccountNumber(trimOrEmpty(bankAccountNumber));
        payout.setBankAccountHolder(trimOrEmpty(bankAccountHolder));
        payout.setIdempotencyKey(idempotencyKey);
        payout.setCreatedBy(actorId);
        payout.persist();
        return payout;
    }

    private int batch(List<UUID> payoutIds, PayoutAction action) {
        if (payoutIds == null || payoutIds.isEmpty()) {
            throw new BadRequestException("Select at least one payout");
        }
        int count = 0;
        for (UUID payoutId : payoutIds) {
            try {
                action.apply(payoutId);
                count++;
            } catch (RuntimeException ignored) {
                // Continue; the caller receives the number actually processed.
            }
        }
        return count;
    }

    private String toCsvRow(PayoutRequest payout, int index) {
        int fractionDigits;
        try {
            fractionDigits = Currency.getInstance(payout.getCurrency()).getDefaultFractionDigits();
        } catch (IllegalArgumentException ignored) {
            fractionDigits = 2;
        }
        if (fractionDigits < 0) {
            fractionDigits = 2;
        }
        BigDecimal amount = BigDecimal.valueOf(payout.getAmountMinor(), fractionDigits);
        return String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s",
                index,
                escapeCsv(payout.getId().toString()),
                escapeCsv(payout.getBankName()),
                escapeCsv(payout.getBankAccountNumber()),
                escapeCsv(payout.getBankAccountHolder()),
                amount.toPlainString(),
                escapeCsv(payout.getCurrency()),
                escapeCsv(payout.getStatus()),
                escapeCsv(payout.getFlagReason()));
    }

    private PayoutRequest payoutForUpdate(String tenantId, UUID payoutId) {
        PayoutRequest payout = PayoutRequest.find("id = ?1 and tenantId = ?2", payoutId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (payout == null) {
            throw new NotFoundException("Payout request not found");
        }
        return payout;
    }

    private void validateBankDetails(String bankName, String bankAccountNumber, String bankAccountHolder) {
        if (bankName == null || bankName.isBlank()
                || bankAccountNumber == null || bankAccountNumber.isBlank()
                || bankAccountHolder == null || bankAccountHolder.isBlank()) {
            throw new BadRequestException("Complete bank details are required before a payout can be paid");
        }
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @FunctionalInterface
    private interface PayoutAction {
        void apply(UUID payoutId);
    }
}

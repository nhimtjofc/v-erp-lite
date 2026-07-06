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

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PayoutService {

    @Inject
    MoneyService moneyService;

    @Inject
    WalletService walletService;

    @Transactional
    public PayoutRequest createPayout(String tenantId, UUID walletId, long amountMinor, String currency,
                                      String bankName, String bankAccountNumber, String bankAccountHolder,
                                      UUID actorId) {
        Money amount = moneyService.positiveMoney(amountMinor, currency);
        Wallet wallet = walletService.getWallet(tenantId, walletId);
        moneyService.requireSameCurrency(wallet.getCurrency(), amount.getCurrency());
        validateBankDetails(bankName, bankAccountNumber, bankAccountHolder);

        PayoutRequest payout = new PayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setTenantId(tenantId);
        payout.setWallet(wallet);
        payout.setAmountMinor(amount.getAmountMinor());
        payout.setCurrency(amount.getCurrency());
        payout.setStatus("PENDING");
        payout.setBankName(bankName.trim());
        payout.setBankAccountNumber(bankAccountNumber.trim());
        payout.setBankAccountHolder(bankAccountHolder.trim());
        payout.setCreatedBy(actorId);
        payout.persist();
        return payout;
    }

    public List<PayoutRequest> listPayouts(String tenantId, UUID walletId, String status) {
        if (walletId != null && status != null && !status.isBlank()) {
            return PayoutRequest.list(
                    "tenantId = ?1 and wallet.id = ?2 and status = ?3 order by createdAt desc",
                    tenantId,
                    walletId,
                    status.trim().toUpperCase()
            );
        }
        if (walletId != null) {
            return PayoutRequest.list(
                    "tenantId = ?1 and wallet.id = ?2 order by createdAt desc",
                    tenantId,
                    walletId
            );
        }
        if (status != null && !status.isBlank()) {
            return PayoutRequest.list(
                    "tenantId = ?1 and status = ?2 order by createdAt desc",
                    tenantId,
                    status.trim().toUpperCase()
            );
        }
        return PayoutRequest.list("tenantId = ?1 order by createdAt desc", tenantId);
    }

    public PayoutRequest getPayout(String tenantId, UUID payoutId) {
        PayoutRequest payout = PayoutRequest.find("id = ?1 and tenantId = ?2", payoutId, tenantId).firstResult();
        if (payout == null) {
            throw new NotFoundException("Không tìm thấy yêu cầu rút tiền");
        }
        return payout;
    }

    @Transactional
    public PayoutRequest approvePayout(String tenantId, UUID payoutId, UUID actorId) {
        PayoutRequest payout = payoutForUpdate(tenantId, payoutId);
        if ("APPROVED".equals(payout.getStatus())) {
            return payout;
        }
        if (!"PENDING".equals(payout.getStatus())) {
            throw new BadRequestException("Chỉ duyệt yêu cầu rút tiền đang chờ xử lý");
        }

        walletService.debitAvailable(
                tenantId,
                payout.getWallet().getId(),
                Money.of(payout.getAmountMinor(), payout.getCurrency()),
                "PAYOUT",
                "PAYOUT_REQUEST",
                payout.getId(),
                actorId,
                "Yêu cầu rút tiền đã được duyệt"
        );
        payout.setStatus("APPROVED");
        payout.setUpdatedBy(actorId);
        return payout;
    }

    @Transactional
    public PayoutRequest rejectPayout(String tenantId, UUID payoutId, UUID actorId) {
        PayoutRequest payout = payoutForUpdate(tenantId, payoutId);
        if ("REJECTED".equals(payout.getStatus())) {
            return payout;
        }
        if (!"PENDING".equals(payout.getStatus())) {
            throw new BadRequestException("Chỉ từ chối yêu cầu rút tiền đang chờ xử lý");
        }
        payout.setStatus("REJECTED");
        payout.setUpdatedBy(actorId);
        return payout;
    }

    private PayoutRequest payoutForUpdate(String tenantId, UUID payoutId) {
        if (payoutId == null) {
            throw new BadRequestException("Vui lòng nhập id yêu cầu rút tiền");
        }
        PayoutRequest payout = PayoutRequest.find("id = ?1 and tenantId = ?2", payoutId, tenantId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (payout == null) {
            throw new NotFoundException("Không tìm thấy yêu cầu rút tiền");
        }
        return payout;
    }

    private void validateBankDetails(String bankName, String bankAccountNumber, String bankAccountHolder) {
        if (bankName == null || bankName.isBlank()) {
            throw new BadRequestException("Vui lòng nhập tên ngân hàng");
        }
        if (bankAccountNumber == null || bankAccountNumber.isBlank()) {
            throw new BadRequestException("Vui lòng nhập số tài khoản ngân hàng");
        }
        if (bankAccountHolder == null || bankAccountHolder.isBlank()) {
            throw new BadRequestException("Vui lòng nhập tên chủ tài khoản");
        }
    }
}

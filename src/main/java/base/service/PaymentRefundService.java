package base.service;

import base.entity.EscrowRecord;
import base.entity.PaymentIntent;
import base.entity.PaymentRefund;
import base.entity.PaymentTransaction;
import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaymentRefundService {

    private static final UUID PLATFORM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Inject
    PaymentGatewayService paymentGatewayService;

    @Inject
    EscrowService escrowService;

    @Inject
    WalletService walletService;

    @Transactional
    public PaymentRefund refundOrder(String tenantId, UUID orderId, String reason, UUID actorId) {
        PaymentRefund existing = PaymentRefund.find("tenantId = ?1 and orderId = ?2", tenantId, orderId).firstResult();
        if (existing != null) {
            return existing;
        }

        PaymentIntent intent = PaymentIntent.find(
                "tenantId = ?1 and orderId = ?2 and status = 'SUCCEEDED' order by updatedAt desc",
                tenantId,
                orderId).firstResult();
        if (intent == null) {
            // Fallback for Wallet payments that don't use PaymentIntent
            WalletTransaction wt = WalletTransaction.find("referenceType = 'ORDER' and referenceId = ?1 and transactionType = 'PAYMENT' and type = 'DEBIT' and tenantId = ?2", orderId, tenantId).firstResult();
            if (wt != null) {
                List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, orderId);
                long sellerEscrowTotal = escrows.stream().mapToLong(EscrowRecord::getAmountMinor).sum();
                long platformCommission = Math.max(0, wt.getAmountMinor() - sellerEscrowTotal);

                escrowService.refundOrderEscrow(tenantId, orderId, actorId);
                if (platformCommission > 0) {
                    Wallet platformWallet = walletService.getOrCreateWallet(
                            tenantId, "PLATFORM", PLATFORM_OWNER_ID, wt.getWallet().getCurrency(), actorId);
                    walletService.debitAvailable(
                            tenantId,
                            platformWallet.getId(),
                            Money.of(platformCommission, wt.getWallet().getCurrency()),
                            "COMMISSION_REFUND",
                            "ORDER",
                            orderId,
                            actorId,
                            "Reverse platform commission for refunded order");
                }

                walletService.creditAvailable(
                        tenantId,
                        wt.getWallet().getId(),
                        Money.of(wt.getAmountMinor(), wt.getWallet().getCurrency()),
                        "REFUND",
                        "ORDER",
                        orderId,
                        actorId,
                        reason != null ? reason : "Order refunded"
                );
                return null; // Wallet refunds don't create PaymentRefund records
            }
            throw new BadRequestException("No succeeded payment exists for this order");
        }
        PaymentTransaction payment = PaymentTransaction.find(
                "tenantId = ?1 and paymentIntent.id = ?2 and status = 'SUCCESS' order by createdAt desc",
                tenantId,
                intent.getId()).firstResult();
        if (payment == null) {
            throw new BadRequestException("The original payment method could not be resolved");
        }

        List<EscrowRecord> escrows = escrowService.listByOrder(tenantId, orderId);
        long sellerEscrowTotal = escrows.stream().mapToLong(EscrowRecord::getAmountMinor).sum();
        long platformCommission = Math.max(0, intent.getAmountMinor() - sellerEscrowTotal);

        String idempotencyKey = "order-refund:" + orderId;
        String gatewayRefundId = paymentGatewayService.refund(
                payment.getGateway(),
                payment.getGatewayTransactionId(),
                intent.getAmountMinor(),
                intent.getCurrency(),
                idempotencyKey);

        escrowService.refundOrderEscrow(tenantId, orderId, actorId);
        if (platformCommission > 0) {
            Wallet platformWallet = walletService.getOrCreateWallet(
                    tenantId, "PLATFORM", PLATFORM_OWNER_ID, intent.getCurrency(), actorId);
            walletService.debitAvailable(
                    tenantId,
                    platformWallet.getId(),
                    Money.of(platformCommission, intent.getCurrency()),
                    "COMMISSION_REFUND",
                    "ORDER",
                    orderId,
                    actorId,
                    "Reverse platform commission for refunded order");
        }

        PaymentRefund refund = new PaymentRefund();
        refund.setId(UUID.randomUUID());
        refund.setTenantId(tenantId);
        refund.setOrderId(orderId);
        refund.setPaymentIntent(intent);
        refund.setGateway(payment.getGateway());
        refund.setGatewayRefundId(gatewayRefundId);
        refund.setAmountMinor(intent.getAmountMinor());
        refund.setCurrency(intent.getCurrency());
        refund.setStatus("COMPLETED");
        refund.setReason(reason);
        refund.setCreatedBy(actorId);
        refund.persist();
        return refund;
    }
}

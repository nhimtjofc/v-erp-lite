package base.rest;

import base.entity.Business;
import base.entity.PayoutRequest;
import base.entity.User;
import base.entity.Wallet;
import base.security.CallerContext;
import base.service.PayoutAutomationService;
import base.service.PayoutScheduler;
import base.service.PayoutService;
import base.service.WalletService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/payouts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "businessOwner"})
public class PayoutResource {

    @Inject
    CallerContext caller;

    @Inject
    PayoutService payoutService;

    @Inject
    WalletService walletService;

    @Inject
    PayoutScheduler payoutScheduler;

    @Inject
    PayoutAutomationService payoutAutomationService;

    public static class ActionRequest {
        public String reason;
    }

    public static class ScreenPayoutRequest {
        public UUID walletId;
    }

    public static class PayoutResponse {
        public UUID id;
        public UUID walletId;
        public long amountMinor;
        public String currency;
        public String status;
        public boolean autoApproved;
        public String flagReason;
        public String bankName;
        public String bankAccountNumber;
        public String bankAccountHolder;
        public String transferReference;
        public String paymentReference;
        public Instant paidAt;
        public Instant createdAt;
        public Instant updatedAt;

        static PayoutResponse from(PayoutRequest payout) {
            PayoutResponse response = new PayoutResponse();
            response.id = payout.getId();
            response.walletId = payout.getWallet().getId();
            response.amountMinor = payout.getAmountMinor();
            response.currency = payout.getCurrency();
            response.status = payout.getStatus();
            response.autoApproved = payout.isAutoApproved();
            response.flagReason = payout.getFlagReason();
            response.bankName = payout.getBankName();
            response.bankAccountNumber = payout.getBankAccountNumber();
            response.bankAccountHolder = payout.getBankAccountHolder();
            response.transferReference = payout.getTransferReference();
            response.paymentReference = payout.getPaymentReference();
            response.paidAt = payout.getPaidAt();
            response.createdAt = payout.getCreatedAt();
            response.updatedAt = payout.getUpdatedAt();
            return response;
        }
    }

    public static class PayoutStatusResponse {
        public Instant lastPayoutAt;
        public Instant nextScheduledRunAt;
        public long minimumThresholdMinor;
        public String lastStatus;
    }

    /** Admins only receive the exception queue; owners receive history for their own wallet. */
    @GET
    public List<PayoutResponse> list(@QueryParam("walletId") UUID walletId) {
        if (caller.hasRole("admin")) {
            return payoutService.listFlagged(caller.tenantCode()).stream().map(PayoutResponse::from).toList();
        }
        requireOwnWallet(walletId);
        return payoutService.listPayouts(caller.tenantCode(), walletId, null)
                .stream().map(PayoutResponse::from).toList();
    }

    @GET
    @Path("/status")
    public PayoutStatusResponse status(@QueryParam("walletId") UUID walletId) {
        requireOwnWallet(walletId);
        List<PayoutRequest> history = payoutService.listPayouts(caller.tenantCode(), walletId, null);
        PayoutRequest lastPaid = payoutService.lastPaid(caller.tenantCode(), walletId);
        PayoutStatusResponse response = new PayoutStatusResponse();
        response.lastPayoutAt = lastPaid == null ? null : lastPaid.getPaidAt();
        response.nextScheduledRunAt = payoutScheduler.nextRunAt();
        response.minimumThresholdMinor = payoutAutomationService.minimumThresholdMinor();
        response.lastStatus = history.isEmpty() ? null : history.getFirst().getStatus();
        return response;
    }

    @POST
    @Path("/screen")
    @RolesAllowed("businessOwner")
    @Transactional
    public PayoutResponse screenMyPayout(ScreenPayoutRequest request) {
        if (request == null || request.walletId == null) {
            throw new BadRequestException("walletId is required");
        }
        Wallet wallet = requireOwnWallet(request.walletId);
        if (wallet.getAvailableBalanceMinor() < payoutAutomationService.minimumThresholdMinor()) {
            throw new BadRequestException("Available balance is below the automated payout threshold");
        }
        PayoutRequest payout = payoutAutomationService.processWallet(wallet.getId());
        if (payout == null) {
            throw new BadRequestException("Wallet is not currently eligible for payout screening");
        }
        return PayoutResponse.from(payout);
    }

    @GET
    @Path("/{id}")
    public PayoutResponse getPayout(@PathParam("id") UUID id) {
        PayoutRequest payout = payoutService.getPayout(caller.tenantCode(), id);
        if (!caller.hasRole("admin")) {
            requireOwnWallet(payout.getWallet().getId());
        }
        return PayoutResponse.from(payout);
    }

    @POST
    @Path("/{id}/approve")
    @RolesAllowed({"admin"})
    @Transactional
    public PayoutResponse approve(@PathParam("id") UUID id) {
        return PayoutResponse.from(payoutService.approvePayout(caller.tenantCode(), id, caller.userId()));
    }

    @POST
    @Path("/{id}/reject")
    @RolesAllowed({"admin"})
    @Transactional
    public PayoutResponse reject(@PathParam("id") UUID id, ActionRequest request) {
        String reason = request == null ? null : request.reason;
        return PayoutResponse.from(payoutService.rejectPayout(caller.tenantCode(), id, reason, caller.userId()));
    }

    @POST
    @Path("/batch-approve")
    @RolesAllowed({"admin"})
    @Transactional
    public Response batchApprove(Map<String, List<UUID>> body) {
        int count = payoutService.batchApprove(caller.tenantCode(), body.get("ids"), caller.userId());
        return Response.ok(Map.of("approved", count)).build();
    }

    @POST
    @Path("/batch-reject")
    @RolesAllowed({"admin"})
    @Transactional
    public Response batchReject(Map<String, List<UUID>> body) {
        int count = payoutService.batchReject(caller.tenantCode(), body.get("ids"), caller.userId());
        return Response.ok(Map.of("rejected", count)).build();
    }

    @GET
    @Path("/export")
    @RolesAllowed({"admin"})
    @Produces("text/csv")
    public Response export() {
        String csv = payoutService.generateCsv(caller.tenantCode(), "FLAGGED");
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"flagged-payouts.csv\"")
                .build();
    }

    private Wallet requireOwnWallet(UUID walletId) {
        if (walletId == null) {
            throw new BadRequestException("walletId is required");
        }
        Wallet wallet = walletService.getWallet(caller.tenantCode(), walletId);
        if (caller.hasRole("admin")) {
            return wallet;
        }
        User user = caller.resolveUser();
        if (wallet.getUser() != null && user.getId().equals(wallet.getUser().getId())
                && "SELLER".equals(wallet.getOwnerType())) {
            return wallet;
        }
        Business business = Business.find(
                "tenantId = ?1 and createdBy = ?2", caller.tenantCode(), user.getId()).firstResult();
        if (business == null || !"SELLER".equals(wallet.getOwnerType())
                || !business.getId().equals(wallet.getOwnerId())) {
            throw new ForbiddenException("You are not allowed to access this payout history");
        }
        return wallet;
    }
}

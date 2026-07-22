package base.rest;

import base.entity.Business;
import base.entity.User;
import base.entity.Wallet;
import base.entity.WalletTopUp;
import base.entity.WalletTransaction;
import base.model.Money;
import base.security.CallerContext;
import base.service.WalletService;
import base.service.VietQRService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/wallets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner", "customer"})
public class WalletResource {

    @Inject
    CallerContext caller;

    @Inject
    WalletService walletService;

    @Inject
    VietQRService vietQRService;

    public static class EnsureWalletRequest {
        public String ownerType;
        public UUID ownerId;
        public String currency;
    }

    public static class WalletAdjustmentRequest {
        public long amountMinor;
        public String currency;
        public String transactionType;
        public String referenceType;
        public UUID referenceId;
        public String description;
    }

    public static class BankDetailsRequest {
        public UUID bankId;
        public String bankAccountNumber;
        public String bankAccountHolder;
    }

    public static class WalletTopUpRequest {
        public long amountMinor;
        public String currency;
    }

    public static class WalletTopUpResponse {
        public UUID id;
        public UUID walletId;
        public long amountMinor;
        public String currency;
        public String status;
        public String paymentQrCodeBase64;
        public Instant createdAt;

        static WalletTopUpResponse from(WalletTopUp topUp) {
            WalletTopUpResponse response = new WalletTopUpResponse();
            response.id = topUp.getId();
            response.walletId = topUp.getWallet().getId();
            response.amountMinor = topUp.getAmountMinor();
            response.currency = topUp.getCurrency();
            response.status = topUp.getStatus();
            response.paymentQrCodeBase64 = topUp.getPaymentQrCodeBase64();
            response.createdAt = topUp.getCreatedAt();
            return response;
        }
    }

    public static class WalletResponse {
        public UUID id;
        public String tenantId;
        public String ownerType;
        public UUID ownerId;
        public UUID userId;
        public long availableBalanceMinor;
        public long pendingBalanceMinor;
        public String currency;
        public UUID bankId;
        public String bankCode;
        public String bankName;
        public String bankAccountNumber;
        public String bankAccountHolder;
        public Instant bankDetailsUpdatedAt;
        public Instant createdAt;
        public Instant updatedAt;

        static WalletResponse from(Wallet wallet) {
            WalletResponse response = new WalletResponse();
            response.id = wallet.getId();
            response.tenantId = wallet.getTenantId();
            response.ownerType = wallet.getOwnerType();
            response.ownerId = wallet.getOwnerId();
            response.userId = wallet.getUser() == null ? null : wallet.getUser().getId();
            response.availableBalanceMinor = wallet.getAvailableBalanceMinor();
            response.pendingBalanceMinor = wallet.getPendingBalanceMinor();
            response.currency = wallet.getCurrency();
            response.bankId = wallet.getBank() == null ? null : wallet.getBank().getId();
            response.bankCode = wallet.getBank() == null ? null : wallet.getBank().getCode();
            response.bankName = wallet.getBankName();
            response.bankAccountNumber = wallet.getBankAccountNumber();
            response.bankAccountHolder = wallet.getBankAccountHolder();
            response.bankDetailsUpdatedAt = wallet.getBankDetailsUpdatedAt();
            response.createdAt = wallet.getCreatedAt();
            response.updatedAt = wallet.getUpdatedAt();
            return response;
        }
    }

    public static class WalletTransactionResponse {
        public UUID id;
        public UUID walletId;
        public long amountMinor;
        public String type;
        public String transactionType;
        public String referenceType;
        public UUID referenceId;
        public long sequenceNo;
        public String description;
        public Instant createdAt;

        static WalletTransactionResponse from(WalletTransaction transaction) {
            WalletTransactionResponse response = new WalletTransactionResponse();
            response.id = transaction.getId();
            response.walletId = transaction.getWallet().getId();
            response.amountMinor = transaction.getAmountMinor();
            response.type = transaction.getType();
            response.transactionType = transaction.getTransactionType();
            response.referenceType = transaction.getReferenceType();
            response.referenceId = transaction.getReferenceId();
            response.sequenceNo = transaction.getSequenceNo();
            response.description = transaction.getDescription();
            response.createdAt = transaction.getCreatedAt();
            return response;
        }
    }

    @GET
    @RolesAllowed({"admin"})
    public List<WalletResponse> listWallets(@QueryParam("ownerType") String ownerType,
                                     @QueryParam("ownerId") UUID ownerId,
                                     @QueryParam("currency") String currency) {
        return walletService.listWallets(caller.tenantCode(), ownerType, ownerId, currency)
                .stream()
                .map(WalletResponse::from)
                .toList();
    }

    /**
     * The wallet of the currently logged-in user.
     *
     * A business owner is linked to the existing SELLER wallet for their business,
     * preserving order and payout balances. Other users receive a USER wallet.
     * The VND wallet is created on demand if provisioning has not created it yet.
     */
    @GET
    @Path("/me")
    @Transactional
    public WalletResponse myWallet(@QueryParam("currency") @DefaultValue("VND") String currency) {
        User user = caller.resolveUser();
        if (user == null) {
            return null;
        }
        Wallet wallet = walletService.getOrCreateUserWallet(
                caller.tenantCode(), user.getId(), currency, caller.userId());
        return WalletResponse.from(wallet);
    }

    @POST
    @Path("/ensure")
    @RolesAllowed({"admin"})
    public Response ensure(EnsureWalletRequest request) {
        Wallet wallet = walletService.getOrCreateWallet(
                caller.tenantCode(),
                request.ownerType,
                request.ownerId,
                request.currency,
                caller.userId()
        );
        return Response.created(URI.create("/api/wallets/" + wallet.getId()))
                .entity(WalletResponse.from(wallet))
                .build();
    }

    @GET
    @Path("/{id}")
    public WalletResponse getWallet(@PathParam("id") UUID id) {
        return WalletResponse.from(requireWalletAccess(id));
    }

    @GET
    @Path("/{id}/transactions")
    public List<WalletTransactionResponse> transactions(@PathParam("id") UUID id,
                                                         @QueryParam("limit") @DefaultValue("20") int limit,
                                                         @QueryParam("offset") @DefaultValue("0") int offset) {
        requireWalletAccess(id);
        return walletService.listTransactions(caller.tenantCode(), id, limit, offset)
                .stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    @PUT
    @Path("/me/bank-details")
    @RolesAllowed({"businessOwner"})
    @Transactional
    public WalletResponse updateMyBankDetails(BankDetailsRequest request,
                                              @QueryParam("currency") @DefaultValue("VND") String currency) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("Request body is required");
        }
        User user = caller.resolveUser();
        Wallet wallet = walletService.getOrCreateUserWallet(
                caller.tenantCode(), user.getId(), currency, caller.userId());
        return WalletResponse.from(walletService.updateBankDetails(
                caller.tenantCode(),
                wallet.getId(),
                request.bankId,
                request.bankAccountNumber,
                request.bankAccountHolder,
                caller.userId()));
    }

    @POST
    @Path("/me/top-up")
    @Transactional
    public WalletTopUpResponse topUpMyWallet(WalletTopUpRequest request) {
        User user = caller.resolveUser();
        String currency = request.currency != null ? request.currency : "VND";
        Wallet wallet = walletService.getOrCreateUserWallet(
                caller.tenantCode(), user.getId(), currency, caller.userId());
        
        WalletTopUp topUp = new WalletTopUp();
        topUp.setId(UUID.randomUUID());
        topUp.setTenantId(caller.tenantCode());
        topUp.setCreatedBy(caller.userId());
        topUp.setUpdatedBy(caller.userId());
        topUp.setWallet(wallet);
        topUp.setAmountMinor(request.amountMinor);
        topUp.setCurrency(currency);
        topUp.setStatus("PENDING");
        
        // Mock bank account for platform receiving top-ups
        String qrContent = vietQRService.generateQrBase64("MB", "0123456789", request.amountMinor, "Nap tien " + topUp.getId().toString().substring(0, 8).toUpperCase());
        topUp.setPaymentQrCodeBase64(qrContent);
        
        topUp.persist();
        return WalletTopUpResponse.from(topUp);
    }
    
    @POST
    @Path("/top-up/{id}/complete")
    @Transactional
    public Response completeTopUp(@PathParam("id") UUID id) {
        WalletTopUp topUp = WalletTopUp.findById(id);
        if (topUp == null) {
            throw new jakarta.ws.rs.NotFoundException("Top-up request not found");
        }
        if ("COMPLETED".equals(topUp.getStatus())) {
            return Response.ok(Map.of("status", "ALREADY_PROCESSED")).build();
        }
        
        walletService.completeTopUp(id, caller.userId());
        return Response.ok(Map.of("status", "SUCCESS")).build();
    }

    @GET
    @Path("/me/top-up")
    public List<WalletTopUpResponse> getMyTopUps(@QueryParam("limit") @DefaultValue("20") int limit,
                                                 @QueryParam("offset") @DefaultValue("0") int offset) {
        User user = caller.resolveUser();
        Wallet wallet = walletService.getOrCreateUserWallet(
                caller.tenantCode(), user.getId(), "VND", caller.userId());
        
        return WalletTopUp.find("wallet = ?1 order by createdAt desc", wallet)
                .page(offset / limit, limit)
                .list()
                .stream()
                .map(t -> WalletTopUpResponse.from((WalletTopUp) t))
                .toList();
    }

    @POST
    @Path("/{id}/credit")
    @RolesAllowed({"admin"})
    public WalletResponse credit(@PathParam("id") UUID id, WalletAdjustmentRequest request) {
        WalletService.WalletMutation mutation = walletService.creditAvailable(
                caller.tenantCode(),
                id,
                Money.of(request.amountMinor, request.currency),
                defaultTransactionType(request.transactionType, "DEPOSIT"),
                request.referenceType,
                request.referenceId,
                caller.userId(),
                request.description
        );
        return WalletResponse.from(mutation.wallet());
    }

    @POST
    @Path("/{id}/debit")
    @RolesAllowed({"admin"})
    public WalletResponse debit(@PathParam("id") UUID id, WalletAdjustmentRequest request) {
        WalletService.WalletMutation mutation = walletService.debitAvailable(
                caller.tenantCode(),
                id,
                Money.of(request.amountMinor, request.currency),
                defaultTransactionType(request.transactionType, "WITHDRAWAL"),
                request.referenceType,
                request.referenceId,
                caller.userId(),
                request.description
        );
        return WalletResponse.from(mutation.wallet());
    }

    @POST
    @Path("/{id}/adjust")
    @RolesAllowed({"admin"})
    @Transactional
    public Response adjust(@PathParam("id") UUID id, Map<String, Object> body) {
        long adjustmentMinor = body.get("adjustmentMinor") instanceof Number n ? n.longValue() : 0;
        String reason = body.getOrDefault("reason", "").toString();
        Wallet wallet = walletService.adjustBalance(caller.tenantCode(), id, adjustmentMinor, reason, caller.userId());
        return Response.ok(WalletResponse.from(wallet)).build();
    }

    private String defaultTransactionType(String transactionType, String fallback) {
        return transactionType == null || transactionType.isBlank() ? fallback : transactionType;
    }

    private Wallet requireWalletAccess(UUID walletId) {
        Wallet wallet = walletService.getWallet(caller.tenantCode(), walletId);
        if (caller.hasRole("admin")) {
            return wallet;
        }
        User user = caller.resolveUser();
        if (wallet.getUser() != null && user.getId().equals(wallet.getUser().getId())) {
            return wallet;
        }
        Business business = Business.find(
                "tenantId = ?1 and createdBy = ?2", caller.tenantCode(), user.getId()).firstResult();
        if (business == null || !"SELLER".equals(wallet.getOwnerType())
                || !business.getId().equals(wallet.getOwnerId())) {
            throw new ForbiddenException("You are not allowed to access this wallet");
        }
        return wallet;
    }
}
 


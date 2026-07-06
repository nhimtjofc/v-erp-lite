package base.rest;

import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import base.security.CallerContext;
import base.service.WalletService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/wallets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
public class WalletResource {

    @Inject
    CallerContext caller;

    @Inject
    WalletService walletService;

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

    public static class WalletResponse {
        public UUID id;
        public String tenantId;
        public String ownerType;
        public UUID ownerId;
        public long availableBalanceMinor;
        public long pendingBalanceMinor;
        public String currency;
        public Instant createdAt;
        public Instant updatedAt;

        static WalletResponse from(Wallet wallet) {
            WalletResponse response = new WalletResponse();
            response.id = wallet.getId();
            response.tenantId = wallet.getTenantId();
            response.ownerType = wallet.getOwnerType();
            response.ownerId = wallet.getOwnerId();
            response.availableBalanceMinor = wallet.getAvailableBalanceMinor();
            response.pendingBalanceMinor = wallet.getPendingBalanceMinor();
            response.currency = wallet.getCurrency();
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
    public List<WalletResponse> list(@QueryParam("ownerType") String ownerType,
                                     @QueryParam("ownerId") UUID ownerId,
                                     @QueryParam("currency") String currency) {
        return walletService.listWallets(caller.tenantCode(), ownerType, ownerId, currency)
                .stream()
                .map(WalletResponse::from)
                .toList();
    }

    @POST
    @Path("/ensure")
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
    public WalletResponse get(@PathParam("id") UUID id) {
        return WalletResponse.from(walletService.getWallet(caller.tenantCode(), id));
    }

    @GET
    @Path("/{id}/transactions")
    public List<WalletTransactionResponse> transactions(@PathParam("id") UUID id) {
        return walletService.listTransactions(caller.tenantCode(), id)
                .stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    @POST
    @Path("/{id}/credit")
    @RolesAllowed({"admin", "operator"})
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
    @RolesAllowed({"admin", "operator"})
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

    private String defaultTransactionType(String transactionType, String fallback) {
        return transactionType == null || transactionType.isBlank() ? fallback : transactionType;
    }
}

package base.rest;

import base.entity.Wallet;
import base.entity.WalletTransaction;
import base.model.Money;
import base.security.CallerContext;
import base.service.WalletService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/wallets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WalletResource {

    @Inject
    CallerContext caller;

    @Inject
    WalletService walletService;

    @GET
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public List<Wallet> list(@QueryParam("ownerType") String ownerType,
                             @QueryParam("ownerId") UUID ownerId) {
        return walletService.listWallets(caller.tenantCode(), ownerType, ownerId);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public Wallet get(@PathParam("id") UUID id) {
        return walletService.getWallet(caller.tenantCode(), id);
    }

    @GET
    @Path("/{id}/transactions")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public List<WalletTransaction> transactions(@PathParam("id") UUID id,
                                                 @QueryParam("limit") @DefaultValue("20") int limit,
                                                 @QueryParam("offset") @DefaultValue("0") int offset) {
        return walletService.listTransactions(caller.tenantCode(), id, limit, offset);
    }

    @POST
    @Path("/{id}/adjust")
    @RolesAllowed({"admin"})
    @Transactional
    public Response adjust(@PathParam("id") UUID id, Map<String, Object> body) {
        long adjustmentMinor = body.get("adjustmentMinor") instanceof Number n ? n.longValue() : 0;
        String reason = body.getOrDefault("reason", "").toString();
        Wallet wallet = walletService.adjustBalance(caller.tenantCode(), id, adjustmentMinor, reason, caller.userId());
        return Response.ok(wallet).build();
    }

    public static class CreateWalletRequest {
        public String ownerType;
        public UUID ownerId;
        public String currency;
    }

    @POST
    @RolesAllowed({"admin", "operator"})
    @Transactional
    public Response create(CreateWalletRequest request) {
        Wallet wallet = walletService.getOrCreateWallet(
                caller.tenantCode(),
                request.ownerType,
                request.ownerId,
                request.currency,
                caller.userId()
        );
        return Response.created(URI.create("/api/wallets/" + wallet.getId())).entity(wallet).build();
    }
}

package base.rest;

import base.entity.PayoutRequest;
import base.security.CallerContext;
import base.service.PayoutService;
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

@Path("/payouts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PayoutResource {

    @Inject
    CallerContext caller;

    @Inject
    PayoutService payoutService;

    public static class CreatePayoutRequest {
        public UUID walletId;
        public long amountMinor;
        public String currency;
        public String bankName;
        public String bankAccountNumber;
        public String bankAccountHolder;
    }

    @POST
    @RolesAllowed({"admin", "operator", "businessOwner"})
    @Transactional
    public Response create(CreatePayoutRequest request) {
        PayoutRequest payout = payoutService.createPayout(
                caller.tenantCode(),
                request.walletId,
                request.amountMinor,
                request.currency,
                request.bankName,
                request.bankAccountNumber,
                request.bankAccountHolder,
                caller.userId()
        );
        return Response.created(URI.create("/api/payouts/" + payout.getId())).entity(payout).build();
    }

    @GET
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public List<PayoutRequest> list(@QueryParam("walletId") UUID walletId,
                                    @QueryParam("status") String status) {
        return payoutService.listPayouts(caller.tenantCode(), walletId, status);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "operator", "businessOwner"})
    public PayoutRequest get(@PathParam("id") UUID id) {
        return payoutService.getPayout(caller.tenantCode(), id);
    }

    @POST
    @Path("/{id}/approve")
    @RolesAllowed({"admin"})
    @Transactional
    public PayoutRequest approve(@PathParam("id") UUID id) {
        return payoutService.approvePayout(caller.tenantCode(), id, caller.userId());
    }

    @POST
    @Path("/{id}/reject")
    @RolesAllowed({"admin"})
    @Transactional
    public PayoutRequest reject(@PathParam("id") UUID id) {
        return payoutService.rejectPayout(caller.tenantCode(), id, caller.userId());
    }

    @POST
    @Path("/batch-approve")
    @RolesAllowed({"admin"})
    @Transactional
    public Response batchApprove(Map<String, List<UUID>> body) {
        List<UUID> ids = body.get("ids");
        int count = payoutService.batchApprove(caller.tenantCode(), ids, caller.userId());
        return Response.ok(Map.of("approved", count)).build();
    }

    @POST
    @Path("/batch-reject")
    @RolesAllowed({"admin"})
    @Transactional
    public Response batchReject(Map<String, List<UUID>> body) {
        List<UUID> ids = body.get("ids");
        int count = payoutService.batchReject(caller.tenantCode(), ids, caller.userId());
        return Response.ok(Map.of("rejected", count)).build();
    }

    @GET
    @Path("/export")
    @RolesAllowed({"admin", "operator"})
    @Produces("text/csv")
    public Response export(@QueryParam("status") String status) {
        String csv = payoutService.generateCsv(caller.tenantCode(), status);
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"payouts.csv\"")
                .build();
    }
}

package base.rest;

import base.entity.PayoutRequest;
import base.security.CallerContext;
import base.service.PayoutService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    public static class CreatePayoutRequest {
        public UUID walletId;
        public long amountMinor;
        public String currency;
        public String bankName;
        public String bankAccountNumber;
        public String bankAccountHolder;
    }

    public static class PayoutResponse {
        public UUID id;
        public UUID walletId;
        public long amountMinor;
        public String currency;
        public String status;
        public String bankName;
        public String bankAccountNumber;
        public String bankAccountHolder;
        public Instant createdAt;
        public Instant updatedAt;

        static PayoutResponse from(PayoutRequest payout) {
            PayoutResponse response = new PayoutResponse();
            response.id = payout.getId();
            response.walletId = payout.getWallet().getId();
            response.amountMinor = payout.getAmountMinor();
            response.currency = payout.getCurrency();
            response.status = payout.getStatus();
            response.bankName = payout.getBankName();
            response.bankAccountNumber = payout.getBankAccountNumber();
            response.bankAccountHolder = payout.getBankAccountHolder();
            response.createdAt = payout.getCreatedAt();
            response.updatedAt = payout.getUpdatedAt();
            return response;
        }
    }

    @GET
    public List<PayoutResponse> list(@QueryParam("walletId") UUID walletId,
                                     @QueryParam("status") String status) {
        return payoutService.listPayouts(caller.tenantCode(), walletId, status)
                .stream()
                .map(PayoutResponse::from)
                .toList();
    }

    @POST
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
        return Response.created(URI.create("/api/payouts/" + payout.getId()))
                .entity(PayoutResponse.from(payout))
                .build();
    }

    @GET
    @Path("/{id}")
    public PayoutResponse getPayout(@PathParam("id") UUID id) {
        return PayoutResponse.from(payoutService.getPayout(caller.tenantCode(), id));
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
    public PayoutResponse reject(@PathParam("id") UUID id) {
        return PayoutResponse.from(payoutService.rejectPayout(caller.tenantCode(), id, caller.userId()));
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
    @RolesAllowed({"admin"})
    @Produces("text/csv")
    public Response export(@QueryParam("status") String status) {
        String csv = payoutService.generateCsv(caller.tenantCode(), status);
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"payouts.csv\"")
                .build();
    }
}

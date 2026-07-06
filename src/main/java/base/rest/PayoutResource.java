package base.rest;

import base.entity.PayoutRequest;
import base.security.CallerContext;
import base.service.PayoutService;
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

@Path("/payouts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "operator", "businessOwner"})
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
    public PayoutResponse get(@PathParam("id") UUID id) {
        return PayoutResponse.from(payoutService.getPayout(caller.tenantCode(), id));
    }

    @POST
    @Path("/{id}/approve")
    @RolesAllowed({"admin", "operator"})
    public PayoutResponse approve(@PathParam("id") UUID id) {
        return PayoutResponse.from(payoutService.approvePayout(caller.tenantCode(), id, caller.userId()));
    }

    @POST
    @Path("/{id}/reject")
    @RolesAllowed({"admin", "operator"})
    public PayoutResponse reject(@PathParam("id") UUID id) {
        return PayoutResponse.from(payoutService.rejectPayout(caller.tenantCode(), id, caller.userId()));
    }
}

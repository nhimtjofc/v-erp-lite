package base.rest;

import base.entity.SubscriptionPayment;
import base.security.CallerContext;
import base.service.SubscriptionPaymentService;
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

import java.util.List;
import java.util.UUID;

@Path("/admin/subscription-payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminSubscriptionPaymentResource {

    public record RejectSubscriptionPaymentRequest(String rejectionReason) {}

    @Inject
    CallerContext caller;

    @Inject
    SubscriptionPaymentService subscriptionPaymentService;

    @GET
    public List<SubscriptionPayment> listPayments(@QueryParam("status") String status) {
        return subscriptionPaymentService.listPayments(status);
    }

    @POST
    @Path("/{id}/approve")
    public SubscriptionPayment approvePayment(@PathParam("id") UUID id) {
        return subscriptionPaymentService.approvePayment(id, caller.userId());
    }

    @POST
    @Path("/{id}/reject")
    public SubscriptionPayment rejectPayment(@PathParam("id") UUID id, RejectSubscriptionPaymentRequest request) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("Rejection request is required");
        }
        return subscriptionPaymentService.rejectPayment(id, request.rejectionReason(), caller.userId());
    }
}

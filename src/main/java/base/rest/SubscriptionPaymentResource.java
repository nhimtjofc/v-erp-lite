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
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Path("/subscriptions/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("businessOwner")
public class SubscriptionPaymentResource {

    public record CreateSubscriptionPaymentRequest(String planCode, String billingPeriod, String idempotencyKey) {}

    public record MockWebhookRequest(String gateway, String status) {}

    @Inject
    CallerContext caller;

    @Inject
    SubscriptionPaymentService subscriptionPaymentService;

    @GET
    public List<SubscriptionPayment> listMyPayments() {
        return subscriptionPaymentService.listTenantPayments(caller.tenantCode());
    }

    @POST
    public SubscriptionPayment createPayment(CreateSubscriptionPaymentRequest request) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("Subscription payment request is required");
        }
        return subscriptionPaymentService.createPayment(
                caller.tenantCode(), request.planCode(), request.billingPeriod(), request.idempotencyKey(), caller.userId()
        );
    }

    @POST
    @Path("/{id}/mock-webhook")
    public SubscriptionPayment receiveMockWebhook(@PathParam("id") UUID id, MockWebhookRequest request) {
        return subscriptionPaymentService.processMockWebhook(
                caller.tenantCode(), id, request != null ? request.gateway() : null, request != null ? request.status() : null
        );
    }
}
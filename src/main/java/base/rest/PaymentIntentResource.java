package base.rest;

import base.entity.Order;
import base.entity.PaymentIntent;
import base.entity.PaymentTransaction;
import base.security.CallerContext;
import base.service.PaymentIntentService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/payment-intents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentIntentResource {

    @Inject
    CallerContext caller;

    @Inject
    PaymentIntentService paymentIntentService;

    public static class CreateIntentRequest {
        public UUID orderId;
        public String idempotencyKey;
    }

    @POST
    @RolesAllowed({"admin", "businessOwner", "customer"})
    @Transactional
    public Response createIntent(CreateIntentRequest request) {
        if (request == null || request.orderId == null) {
            throw new BadRequestException("orderId is required");
        }
        if (request.idempotencyKey == null || request.idempotencyKey.isBlank()) {
            throw new BadRequestException("idempotencyKey is required");
        }

        String tenantCode = caller.tenantCode();
        Order order = Order.find("id = ?1 and tenantId = ?2 and deletedAt IS NULL", request.orderId, tenantCode).firstResult();
        if (order == null) {
            throw new NotFoundException("Order not found");
        }

        if (order.getCustomer() == null) {
            throw new BadRequestException("Order does not have a customer associated");
        }

        if (!"PENDING".equals(order.getStatus())) {
            throw new BadRequestException("Only PENDING orders can be paid for");
        }

        PaymentIntent intent = paymentIntentService.createIntent(
                tenantCode,
                order.getId(),
                order.getCustomer().getId(),
                order.getFinalAmountMinor(),
                order.getCurrency(),
                request.idempotencyKey,
                caller.userId()
        );

        return Response.created(URI.create("/api/payment-intents/" + intent.getId())).entity(intent).build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "businessOwner", "customer"})
    public PaymentIntent get(@PathParam("id") UUID id) {
        return paymentIntentService.getIntent(caller.tenantCode(), id);
    }

    @GET
    @Path("/{id}/transactions")
    @RolesAllowed({"admin", "businessOwner", "customer"})
    public List<PaymentTransaction> listTransactions(@PathParam("id") UUID id) {
        return paymentIntentService.listTransactions(caller.tenantCode(), id);
    }
}

package base.rest;

import base.rest.dto.ProviderOperationResult;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/mock-bank")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@UnlessBuildProfile("prod")
public class MockBankResource {

    // 1. Initiate Charge (Pay-in)
    @POST
    @Path("/charge")
    public ProviderOperationResult initiateCharge(Map<String, Object> payload) {
        // Return a pending state, wait for user to simulate the transfer
        return new ProviderOperationResult(
                "PROVIDER_SUBMITTED",
                "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                payload.toString(),
                Instant.now(),
                false
        );
    }

    // 2. Initiate Refund (Pay-out to buyer)
    @POST
    @Path("/refund")
    public ProviderOperationResult initiateRefund(Map<String, Object> payload) {
        return new ProviderOperationResult(
                "PROVIDER_SUBMITTED",
                "REF_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                payload.toString(),
                Instant.now(),
                false
        );
    }

    // 3. Initiate Payout (Pay-out to seller bank)
    @POST
    @Path("/payout")
    public ProviderOperationResult initiatePayout(Map<String, Object> payload) {
        return new ProviderOperationResult(
                "PROVIDER_SUBMITTED",
                "POUT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                payload.toString(),
                Instant.now(),
                false
        );
    }

    // 4. Simulate Transferred (Only for QA/Demo to trigger Webhook manually)
    @POST
    @Path("/simulate-webhook")
    public Response simulateWebhook(Map<String, Object> payload, @ConfigProperty(name = "mockbank.secret", defaultValue = "mock_secret") String secret) {
        // This endpoint will be used by the frontend Mock Bank UI to trigger the actual PaymentWebhookResource.
        // It acts as the "Bank Server" sending a POST request to our own Webhook URL.
        // In reality, Dev B will write an HTTP Client here to fire the webhook with HMAC signature.
        return Response.ok(Map.of("message", "Simulated webhook fired successfully")).build();
    }
}

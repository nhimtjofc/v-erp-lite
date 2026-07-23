package base.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class HmacSignatureFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "mockbank.secret", defaultValue = "mock_secret")
    String mockBankSecret;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("/api/webhooks/mock-bank")) {
            String signature = requestContext.getHeaderString("X-Signature");
            if (signature == null || signature.isBlank()) {
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"Missing signature\"}")
                        .build());
                return;
            }

            // Read the raw body
            InputStream inputStream = requestContext.getEntityStream();
            byte[] rawBytes = inputStream.readAllBytes();
            
            // Put it back so Jackson can deserialize it later
            requestContext.setEntityStream(new ByteArrayInputStream(rawBytes));

            // Verify signature using constant-time comparison
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(mockBankSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] expectedHash = mac.doFinal(rawBytes);
                String expectedSignature = Base64.getEncoder().encodeToString(expectedHash);

                if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                    requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"error\":\"Invalid signature\"}")
                            .build());
                }
            } catch (Exception e) {
                requestContext.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("{\"error\":\"Failed to verify signature\"}")
                        .build());
            }
        }
    }
}

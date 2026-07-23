package base.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Provider
@HmacSecure
@Priority(Priorities.AUTHENTICATION)
public class HmacSignatureFilter implements ContainerRequestFilter {

    private static final String MOCK_SECRET = "mock_secret_v_erp_lite_2025";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String signature = requestContext.getHeaderString("X-Webhook-Signature");
        if (signature == null || signature.isBlank()) {
            signature = requestContext.getHeaderString("Signature");
        }

        if (signature == null || signature.isBlank()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Missing webhook signature header"))
                    .build());
            return;
        }

        byte[] bodyBytes = requestContext.getEntityStream().readAllBytes();
        // Reset the body stream for downstream processing
        requestContext.setEntityStream(new ByteArrayInputStream(bodyBytes));

        String bodyString = new String(bodyBytes, StandardCharsets.UTF_8);
        String expectedSignature = hmacSha256(bodyString, MOCK_SECRET);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Invalid webhook signature"))
                    .build());
        }
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HmacSHA256 algorithm or key is invalid", e);
        }
    }
}

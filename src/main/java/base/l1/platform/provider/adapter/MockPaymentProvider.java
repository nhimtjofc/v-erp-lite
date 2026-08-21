package base.l1.platform.provider.adapter;

import base.l0.system.money.Money;
import base.l1.platform.provider.PaymentHandle;
import base.l1.platform.provider.PaymentRequest;
import base.l1.platform.provider.PaymentProvider;
import base.l1.platform.provider.ProviderCapabilities;
import base.l1.platform.provider.ProviderEvent;
import base.l1.platform.provider.RefundHandle;
import base.l1.platform.provider.RefundRequest;
import base.l1.platform.provider.WebhookSigner;
import base.l1.platform.provider.WebhookVerificationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * A local stand-in for a real provider, used in development and tests.
 *
 * <p>It is registered only when {@code payment.provider.mock.enabled=true}, which is not the case in
 * a production profile. It verifies signatures with the same code path a real adapter uses, so the
 * webhook plumbing is genuinely exercised rather than bypassed.
 */
@ApplicationScoped
@IfBuildProperty(name = "payment.provider.mock.enabled", stringValue = "true")
public class MockPaymentProvider implements PaymentProvider {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    WebhookSigner signer;

    @ConfigProperty(name = "payment.provider.mock.webhook-secret")
    String webhookSecret;

    @Override
    public String id() {
        return "mock";
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, true, true, false);
    }

    @Override
    public PaymentHandle initiatePayment(PaymentRequest request) {
        String ref = "mock_" + UUID.nameUUIDFromBytes(
                request.idempotencyKey().getBytes(StandardCharsets.UTF_8));
        return new PaymentHandle(
                id(),
                ref,
                "https://mock-gateway.local/pay?ref=" + ref,
                "mock://pay/" + ref + "?amount=" + request.amount().amountMinor(),
                Instant.now().plus(30, ChronoUnit.MINUTES),
                request.metadata() == null ? Map.of() : request.metadata());
    }

    @Override
    public RefundHandle refund(RefundRequest request) {
        String ref = "mockref_" + UUID.nameUUIDFromBytes(
                request.idempotencyKey().getBytes(StandardCharsets.UTF_8));
        return new RefundHandle(id(), ref, true);
    }

    @Override
    public ProviderEvent parseWebhook(String rawBody, Map<String, String> headers) {
        signer.verify(webhookSecret, headers.get("x-mock-signature"), headers.get("x-mock-timestamp"), rawBody);
        try {
            JsonNode node = JSON.readTree(rawBody);
            Money amount = node.hasNonNull("amountMinor") && node.hasNonNull("currency")
                    ? Money.of(node.get("amountMinor").asLong(), node.get("currency").asText())
                    : null;
            return new ProviderEvent(
                    id(),
                    node.path("eventId").asText(null),
                    node.path("providerRef").asText(null),
                    signalOf(node.path("status").asText("")),
                    amount,
                    node.hasNonNull("occurredAt") ? Instant.parse(node.get("occurredAt").asText()) : Instant.now(),
                    rawBody);
        } catch (WebhookVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookVerificationException("Webhook body is not valid JSON: " + e.getMessage());
        }
    }

    private ProviderEvent.Signal signalOf(String status) {
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED" -> ProviderEvent.Signal.PAYMENT_SUCCEEDED;
            case "FAILED"               -> ProviderEvent.Signal.PAYMENT_FAILED;
            case "EXPIRED"              -> ProviderEvent.Signal.PAYMENT_EXPIRED;
            case "REFUNDED"             -> ProviderEvent.Signal.REFUND_SETTLED;
            case "CHARGEBACK"           -> ProviderEvent.Signal.CHARGEBACK_OPENED;
            case "PAYOUT_CONFIRMED"     -> ProviderEvent.Signal.PAYOUT_CONFIRMED;
            case "PAYOUT_RETURNED"      -> ProviderEvent.Signal.PAYOUT_RETURNED;
            default                     -> ProviderEvent.Signal.UNKNOWN;
        };
    }
}


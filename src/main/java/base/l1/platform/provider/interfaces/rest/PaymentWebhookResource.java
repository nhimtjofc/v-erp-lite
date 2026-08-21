package base.l1.platform.provider.interfaces.rest;

import base.l0.system.api.ApiResp;
import base.l1.platform.provider.PaymentProviderRegistry;
import base.l1.platform.provider.ProviderEvent;
import base.l1.platform.provider.inbox.ProviderEventRecord;
import base.l1.platform.provider.inbox.RecordProviderEventUC;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where providers post their notifications.
 *
 * <p>Two things about this endpoint are load-bearing.
 *
 * <p><b>It takes bytes, not an object.</b> The body parameter is a {@code byte[]} decoded here
 * rather than a DTO Jackson has parsed, because a webhook signature covers the exact bytes the
 * provider sent. The moment the framework deserialises the body for us, the only thing left to hash
 * is a re-encoding — different key order, different whitespace, different number formatting — and
 * the check silently becomes "did we produce the same JSON we just parsed", which is always true
 * and proves nothing. localstripe pretty-prints with sorted keys and two-space indentation, so the
 * difference is not subtle; it is every byte after the first brace.
 *
 * <p><b>It is unauthenticated, deliberately.</b> A provider has no bearer token from us, so the
 * signature is the authentication, and it is a stronger one than a shared bearer would be: it
 * covers the payload and a timestamp rather than merely asserting the caller once knew a secret.
 * {@code PaymentProviderRegistry.require} is what makes that safe to expose — an unknown provider
 * id has no verifier and no fallback, so an unsigned request to a made-up provider name is refused
 * before any body is read.
 */
@Path("/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class PaymentWebhookResource {

    private static final Logger log = Logger.getLogger(PaymentWebhookResource.class);

    @Inject
    PaymentProviderRegistry providers;

    @Inject
    RecordProviderEventUC inbox;

    @POST
    @Path("/{providerId}")
    @Consumes(MediaType.WILDCARD)
    public ApiResp<Received> receive(@PathParam("providerId") String providerId,
                                     @Context HttpHeaders httpHeaders,
                                     byte[] body) {
        String rawBody = new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
        ProviderEvent event = providers.require(providerId).parseWebhook(rawBody, headersOf(httpHeaders));

        ProviderEventRecord record = inbox.record(event);
        log.infof("Webhook accepted [%s] provider=%s event=%s ref=%s",
                event.signal(), event.provider(), event.providerEventId(), event.providerRef());

        // 200 on a redelivery too. A provider retries until it gets one, and having already stored
        // the event is the definition of success, not a conflict.
        return ApiResp.fromData(new Received(
                record.getUuid().toString(), event.signal().name(), record.getProcessedAt() != null));
    }

    /**
     * Headers lower-cased into a plain map, because HTTP header names are case-insensitive and an
     * adapter should not have to guess whether this provider sends {@code Stripe-Signature} or
     * {@code stripe-signature}. Adapters look them up in lower case.
     */
    private static Map<String, String> headersOf(HttpHeaders httpHeaders) {
        Map<String, String> headers = new HashMap<>();
        httpHeaders.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name.toLowerCase(Locale.ROOT), values.getFirst());
            }
        });
        return headers;
    }

    /** What the provider gets back: enough to correlate in their logs, nothing about our tenants. */
    public record Received(String eventUuid, String signal, boolean processed) {
    }
}


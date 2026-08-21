package base.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class EcommerceIntegrationService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    @Inject
    EcommerceSecretResolver secretResolver;

    @Inject
    EcommerceChannelStore channelStore;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "ecommerce.health.timeout", defaultValue = "PT3S")
    Duration configuredTimeout;

    @ConfigProperty(name = "ecommerce.health.allow-private-hosts", defaultValue = "false")
    boolean allowPrivateHosts;

    private Duration timeout;
    private HttpClient httpClient;

    @PostConstruct
    void initializeHttpClient() {
        timeout = configuredTimeout == null || configuredTimeout.isNegative() || configuredTimeout.isZero()
                ? DEFAULT_TIMEOUT
                : configuredTimeout;
        httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public HealthSnapshot checkHealth(String tenantId, UUID businessId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("Tenant is required");
        }

        EcommerceChannelStore.ChannelSnapshot channel = channelStore.resolve(tenantId, businessId);
        if (channel == null) {
            return new HealthSnapshot(
                    null,
                    businessId,
                    null,
                    "NOT_CONFIGURED",
                    "UNKNOWN",
                    false,
                    0,
                    0,
                    Instant.now(),
                    null,
                    "No Ecommerce sales channel is configured for this tenant."
            );
        }

        String publicStoreUrl = publicStoreUrl(channel.storeUrl());
        boolean secretConfigured = secretResolver.isConfigured(channel.secretReference());
        if (!channel.active() || "DISABLED".equals(channel.status())) {
            return new HealthSnapshot(
                    channel.id(),
                    channel.businessId(),
                    publicStoreUrl,
                    "DISABLED",
                    channel.healthStatus(),
                    secretConfigured,
                    channel.mappedEntityCount(),
                    channel.webhookEventCount(),
                    channel.lastHealthCheckedAt(),
                    channel.lastConnectedAt(),
                    "This Ecommerce sales channel is disabled."
            );
        }

        Instant checkedAt = Instant.now();
        ProbeResult probe = probe(channel.storeUrl());
        String connectionStatus;
        String message = probe.message();

        if ("UP".equals(probe.healthStatus()) && secretConfigured) {
            boolean connectorPreviouslyConfirmed = "CONNECTED".equals(channel.status())
                    && channel.lastConnectedAt() != null;
            connectionStatus = connectorPreviouslyConfirmed ? "CONNECTED" : "CONFIGURED";
            message = connectorPreviouslyConfirmed
                    ? "The Ecommerce connection is healthy."
                    : "Ecommerce is reachable and the server-side secret is configured; "
                            + "connector handshake is pending.";
        } else if ("UP".equals(probe.healthStatus())) {
            connectionStatus = "DISCONNECTED";
            message = "Ecommerce is reachable, but the server-side webhook secret is not configured.";
        } else {
            connectionStatus = "ERROR";
        }

        channelStore.recordHealth(
                tenantId,
                channel.id(),
                connectionStatus,
                probe.healthStatus(),
                checkedAt,
                "UP".equals(probe.healthStatus()) ? null : message
        );

        return new HealthSnapshot(
                channel.id(),
                channel.businessId(),
                publicStoreUrl,
                connectionStatus,
                probe.healthStatus(),
                secretConfigured,
                channel.mappedEntityCount(),
                channel.webhookEventCount(),
                checkedAt,
                channel.lastConnectedAt(),
                message
        );
    }

    private ProbeResult probe(String storeUrl) {
        URI healthUri;
        try {
            URI baseUri = toPublicBaseUri(storeUrl);
            validateDestination(baseUri.getHost());
            String basePath = baseUri.getPath();
            if (basePath == null || basePath.isBlank()) {
                basePath = "/";
            } else if (!basePath.endsWith("/")) {
                basePath += "/";
            }
            healthUri = new URI(
                    baseUri.getScheme(),
                    null,
                    baseUri.getHost(),
                    baseUri.getPort(),
                    basePath,
                    "rest_route=/",
                    null
            );
        } catch (IllegalArgumentException | URISyntaxException e) {
            return new ProbeResult("DOWN", "The configured Ecommerce store URL is invalid.");
        }

        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "V-ERP-Lite-Ecommerce-Health/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ProbeResult(
                        "DOWN",
                        "The Ecommerce store returned HTTP " + response.statusCode() + "."
                );
            }
            if (!hasEcommerceApi(response.body())) {
                return new ProbeResult(
                        "DEGRADED",
                        "WordPress is reachable, but the Ecommerce REST API is unavailable."
                );
            }
            return new ProbeResult("UP", "Ecommerce is reachable.");
        } catch (HttpTimeoutException e) {
            return new ProbeResult("DOWN", "The Ecommerce health check timed out.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProbeResult("DOWN", "The Ecommerce health check was interrupted.");
        } catch (IOException e) {
            return new ProbeResult("DOWN", "The Ecommerce store is unreachable.");
        }
    }

    private boolean hasEcommerceApi(String responseBody) {
        try {
            JsonNode namespaces = objectMapper.readTree(responseBody).path("namespaces");
            if (!namespaces.isArray()) {
                return false;
            }
            for (JsonNode namespace : namespaces) {
                if ("wc/v3".equals(namespace.asText())) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private String publicStoreUrl(String storeUrl) {
        try {
            return toPublicBaseUri(storeUrl).toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }

    private URI toPublicBaseUri(String storeUrl) throws URISyntaxException {
        if (storeUrl == null || storeUrl.isBlank()) {
            throw new IllegalArgumentException("Store URL is required");
        }

        URI input = new URI(storeUrl.trim());
        String scheme = input.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Store URL scheme is required");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || input.getHost() == null
                || input.getUserInfo() != null
                || input.getQuery() != null
                || input.getFragment() != null) {
            throw new IllegalArgumentException("Store URL must be a public HTTP(S) base URL");
        }

        String path = input.getPath();
        if (path == null || "/".equals(path)) {
            path = "";
        } else {
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        }

        return new URI(scheme, null, input.getHost(), input.getPort(), path, null, null);
    }

    private void validateDestination(String host) {
        if (allowPrivateHosts) {
            return;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Private store hosts are not allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Store host could not be resolved", e);
        }
    }

    public record HealthSnapshot(
            UUID channelId,
            UUID businessId,
            String storeUrl,
            String connectionStatus,
            String healthStatus,
            boolean secretConfigured,
            long mappedEntityCount,
            long webhookEventCount,
            Instant lastCheckedAt,
            Instant lastConnectedAt,
            String message
    ) {}

    private record ProbeResult(String healthStatus, String message) {}
}

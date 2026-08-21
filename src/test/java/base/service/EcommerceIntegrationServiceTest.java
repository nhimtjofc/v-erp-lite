package base.service;

import base.entity.Business;
import base.entity.ExternalEntityMapping;
import base.entity.SalesChannel;
import base.entity.Tenant;
import base.entity.WebhookEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(EcommerceIntegrationServiceTest.EcommerceTestProfile.class)
class EcommerceIntegrationServiceTest {

    private static final String TEST_SECRET = "test-secret-that-must-never-be-serialized";

    @Inject
    EcommerceIntegrationService service;

    @Inject
    ObjectMapper objectMapper;

    @Test
    @TestTransaction
    void healthLookupIsTenantScopedAndDoesNotSerializeSecret() throws JsonProcessingException {
        ChannelFixture tenantA = createDisabledChannel("a");
        ChannelFixture tenantB = createDisabledChannel("b");

        EcommerceIntegrationService.HealthSnapshot result = service.checkHealth(
                tenantA.tenantId(),
                tenantA.businessId()
        );

        assertEquals(tenantA.channelId(), result.channelId());
        assertEquals(tenantA.businessId(), result.businessId());
        assertEquals("http://store-a.example", result.storeUrl());
        assertEquals("DISABLED", result.connectionStatus());
        assertTrue(result.secretConfigured());
        assertEquals(1, result.mappedEntityCount());
        assertEquals(1, result.webhookEventCount());
        assertFalse(objectMapper.writeValueAsString(result).contains(TEST_SECRET));

        EcommerceIntegrationService.HealthSnapshot crossTenantResult = service.checkHealth(
                tenantA.tenantId(),
                tenantB.businessId()
        );
        assertEquals("NOT_CONFIGURED", crossTenantResult.connectionStatus());
        assertNull(crossTenantResult.channelId());
        assertNull(crossTenantResult.storeUrl());
        assertEquals(0, crossTenantResult.mappedEntityCount());
        assertEquals(0, crossTenantResult.webhookEventCount());
        assertFalse(crossTenantResult.secretConfigured());
    }

    @Test
    @TestTransaction
    void rejectsDuplicateErpMappingOnRetry() {
        ChannelFixture fixture = createDisabledChannel("duplicate-erp");

        ExternalEntityMapping duplicate = newMapping(
                fixture,
                fixture.erpEntityId(),
                "different-external-id"
        );

        assertThrows(PersistenceException.class, duplicate::persistAndFlush);
    }

    @Test
    @TestTransaction
    void rejectsDuplicateExternalMappingOnRetry() {
        ChannelFixture fixture = createDisabledChannel("duplicate-external");

        ExternalEntityMapping duplicate = newMapping(
                fixture,
                UUID.randomUUID(),
                fixture.mappingExternalId()
        );

        assertThrows(PersistenceException.class, duplicate::persistAndFlush);
    }

    @Test
    @TestTransaction
    void rejectsDuplicateWebhookEventButAcceptsDistinctEvent() {
        ChannelFixture fixture = createDisabledChannel("duplicate-webhook");

        WebhookEvent distinct = newWebhookEvent(fixture, "distinct-event-id");
        distinct.persistAndFlush();
        assertEquals(
                2,
                WebhookEvent.count(
                        "tenantId = ?1 and salesChannelId = ?2",
                        fixture.tenantId(),
                        fixture.channelId()
                )
        );

        WebhookEvent duplicate = newWebhookEvent(fixture, fixture.webhookExternalEventId());
        assertThrows(PersistenceException.class, duplicate::persistAndFlush);
    }

    @Test
    @TestTransaction
    void reportsReachableConfiguredStoreWithoutClaimingConnectorHandshake() throws IOException {
        byte[] responseBody = "{\"namespaces\":[\"wc/v3\"]}".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();

        try {
            ChannelFixture fixture = createDisabledChannel("reachable");
            SalesChannel channel = SalesChannel.find(
                    "id = ?1 and tenantId = ?2",
                    fixture.channelId(),
                    fixture.tenantId()
            ).firstResult();
            channel.setStoreUrl("http://127.0.0.1:" + server.getAddress().getPort());
            channel.setStatus("DISCONNECTED");
            channel.setActive(true);

            EcommerceIntegrationService.HealthSnapshot result = service.checkHealth(
                    fixture.tenantId(),
                    fixture.businessId()
            );

            assertEquals("UP", result.healthStatus());
            assertEquals("CONFIGURED", result.connectionStatus());
            assertTrue(result.secretConfigured());
            assertNull(result.lastConnectedAt());
        } finally {
            server.stop(0);
        }
    }

    private ChannelFixture createDisabledChannel(String suffix) {
        String tenantId = "ecommerce-test-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        UUID businessId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setCode(tenantId);
        tenant.setName("Ecommerce test tenant " + suffix);
        tenant.setActive(true);
        tenant.persist();

        Business business = new Business();
        business.setId(businessId);
        business.setTenantId(tenantId);
        business.setName("Ecommerce test business " + suffix);
        business.setActive(true);
        business.persist();

        SalesChannel channel = new SalesChannel();
        channel.setId(channelId);
        channel.setTenantId(tenantId);
        channel.setBusinessId(businessId);
        channel.setStoreUrl("http://store-" + suffix + ".example");
        channel.setStatus("DISABLED");
        channel.setHealthStatus("UNKNOWN");
        channel.setSecretReference("test-channel");
        channel.setActive(false);
        channel.persist();

        UUID erpEntityId = UUID.randomUUID();
        String mappingExternalId = "ecommerce-product-" + suffix;
        String webhookExternalEventId = "ecommerce-event-" + suffix;

        ChannelFixture fixture = new ChannelFixture(
                tenantId,
                businessId,
                channelId,
                erpEntityId,
                mappingExternalId,
                webhookExternalEventId
        );

        ExternalEntityMapping mapping = newMapping(fixture, erpEntityId, mappingExternalId);
        mapping.persist();

        WebhookEvent webhookEvent = newWebhookEvent(fixture, webhookExternalEventId);
        webhookEvent.persist();

        return fixture;
    }

    private ExternalEntityMapping newMapping(
            ChannelFixture fixture,
            UUID erpEntityId,
            String externalEntityId
    ) {
        ExternalEntityMapping mapping = new ExternalEntityMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setTenantId(fixture.tenantId());
        mapping.setSalesChannelId(fixture.channelId());
        mapping.setEntityType("PRODUCT");
        mapping.setErpEntityId(erpEntityId);
        mapping.setExternalEntityId(externalEntityId);
        mapping.setSyncStatus("SYNCED");
        return mapping;
    }

    private WebhookEvent newWebhookEvent(ChannelFixture fixture, String externalEventId) {
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setId(UUID.randomUUID());
        webhookEvent.setTenantId(fixture.tenantId());
        webhookEvent.setGateway("WOOCOMMERCE");
        webhookEvent.setEventType("order.created");
        webhookEvent.setSalesChannelId(fixture.channelId());
        webhookEvent.setExternalEventId(externalEventId);
        webhookEvent.setRawPayload("{}");
        webhookEvent.setStatus("PROCESSED");
        return webhookEvent;
    }

    public static class EcommerceTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.keycloak.admin-client.server-url", "http://127.0.0.1:1",
                    "ecommerce.webhook.secrets.test-channel", TEST_SECRET,
                    "ecommerce.health.allow-private-hosts", "true",
                    "ecommerce.bootstrap.enabled", "false"
            );
        }
    }

    private record ChannelFixture(
            String tenantId,
            UUID businessId,
            UUID channelId,
            UUID erpEntityId,
            String mappingExternalId,
            String webhookExternalEventId
    ) {}
}

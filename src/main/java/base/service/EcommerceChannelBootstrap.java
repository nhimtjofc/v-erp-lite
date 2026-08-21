package base.service;

import base.entity.Business;
import base.entity.SalesChannel;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EcommerceChannelBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EcommerceChannelBootstrap.class);

    @ConfigProperty(name = "ecommerce.bootstrap.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "ecommerce.bootstrap.tenant-id")
    Optional<String> configuredTenantId;

    @ConfigProperty(name = "ecommerce.bootstrap.business-id")
    Optional<String> configuredBusinessId;

    @ConfigProperty(name = "ecommerce.bootstrap.store-url")
    Optional<String> configuredStoreUrl;

    @ConfigProperty(name = "ecommerce.bootstrap.secret-reference", defaultValue = "local")
    String secretReference;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        if (configuredTenantId.isEmpty()
                || configuredBusinessId.isEmpty()
                || configuredStoreUrl.isEmpty()) {
            log.warn("Ecommerce dev bootstrap skipped because channel configuration is incomplete");
            return;
        }

        String tenantId = configuredTenantId.get().trim();
        String businessId = configuredBusinessId.get().trim();
        String storeUrl = configuredStoreUrl.get().trim();
        if (tenantId.isBlank() || businessId.isBlank() || storeUrl.isBlank()) {
            log.warn("Ecommerce dev bootstrap skipped because channel configuration is incomplete");
            return;
        }

        UUID parsedBusinessId;
        try {
            parsedBusinessId = UUID.fromString(businessId);
        } catch (IllegalArgumentException e) {
            log.warn("Ecommerce dev bootstrap skipped because business ID is invalid");
            return;
        }

        Business business = Business.find(
                "id = ?1 and tenantId = ?2",
                parsedBusinessId,
                tenantId
        ).firstResult();
        if (business == null) {
            log.warn("Ecommerce dev bootstrap skipped because the tenant business was not found");
            return;
        }

        SalesChannel existing = SalesChannel.findEcommerce(tenantId, parsedBusinessId);
        if (existing != null) {
            String normalizedSecretReference = secretReference.isBlank() ? null : secretReference.trim();
            existing.setStoreUrl(storeUrl);
            existing.setSecretReference(normalizedSecretReference);
            existing.setActive(true);
            log.info("Updated the local Ecommerce sales channel configuration for tenant {}", tenantId);
            return;
        }

        SalesChannel channel = new SalesChannel();
        channel.setId(UUID.randomUUID());
        channel.setTenantId(tenantId);
        channel.setBusinessId(parsedBusinessId);
        channel.setChannelType(SalesChannel.ECOMMERCE);
        channel.setStoreUrl(storeUrl.trim());
        channel.setStatus("DISCONNECTED");
        channel.setHealthStatus("UNKNOWN");
        channel.setSecretReference(secretReference.isBlank() ? null : secretReference.trim());
        channel.setActive(true);
        channel.persist();
        log.info("Created the local Ecommerce sales channel for tenant {}", tenantId);
    }
}

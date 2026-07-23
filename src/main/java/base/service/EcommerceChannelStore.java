package base.service;

import base.entity.ExternalEntityMapping;
import base.entity.SalesChannel;
import base.entity.WebhookEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EcommerceChannelStore {

    @Transactional
    public ChannelSnapshot resolve(String tenantId, UUID businessId) {
        SalesChannel channel;
        if (businessId != null) {
            channel = SalesChannel.findEcommerce(tenantId, businessId);
        } else {
            List<SalesChannel> channels = SalesChannel.listEcommerce(tenantId);
            channel = channels.isEmpty() ? null : channels.getFirst();
        }

        if (channel == null) {
            return null;
        }

        long mappedEntityCount = ExternalEntityMapping.count(
                "tenantId = ?1 and salesChannelId = ?2",
                tenantId,
                channel.getId()
        );
        long webhookEventCount = WebhookEvent.count(
                "tenantId = ?1 and salesChannelId = ?2 and externalEventId is not null",
                tenantId,
                channel.getId()
        );

        return new ChannelSnapshot(
                channel.getId(),
                channel.getBusinessId(),
                channel.getStoreUrl(),
                channel.getStatus(),
                channel.getHealthStatus(),
                channel.getSecretReference(),
                channel.getLastHealthCheckedAt(),
                channel.getLastConnectedAt(),
                channel.isActive(),
                mappedEntityCount,
                webhookEventCount
        );
    }

    @Transactional
    public void recordHealth(
            String tenantId,
            UUID channelId,
            String connectionStatus,
            String healthStatus,
            Instant checkedAt,
            String errorMessage
    ) {
        SalesChannel channel = SalesChannel.find(
                "id = ?1 and tenantId = ?2 and channelType = ?3",
                channelId,
                tenantId,
                SalesChannel.ECOMMERCE
        ).firstResult();
        if (channel == null) {
            return;
        }

        channel.setStatus(connectionStatus);
        channel.setHealthStatus(healthStatus);
        channel.setLastHealthCheckedAt(checkedAt);
        channel.setLastErrorMessage(errorMessage);
    }

    public record ChannelSnapshot(
            UUID id,
            UUID businessId,
            String storeUrl,
            String status,
            String healthStatus,
            String secretReference,
            Instant lastHealthCheckedAt,
            Instant lastConnectedAt,
            boolean active,
            long mappedEntityCount,
            long webhookEventCount
    ) {}
}

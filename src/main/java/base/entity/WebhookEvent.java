package base.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_webhook_events_channel_event",
                        columnNames = {"tenant_id", "sales_channel_id", "external_event_id"}
                )
        }
)
@Getter
@Setter
public class WebhookEvent extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String gateway;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "sales_channel_id")
    private UUID salesChannelId;

    @Column(name = "external_event_id")
    // Stable connector idempotency key. Keep Woo's transport delivery ID in channelMetadata.
    private String externalEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_metadata", columnDefinition = "json")
    private Map<String, Object> channelMetadata = new HashMap<>();

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private long version;
}

package base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "external_entity_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_external_mappings_erp",
                        columnNames = {"tenant_id", "sales_channel_id", "entity_type", "erp_entity_id"}
                ),
                @UniqueConstraint(
                        name = "uq_external_mappings_external",
                        columnNames = {"tenant_id", "sales_channel_id", "entity_type", "external_entity_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_external_mappings_tenant_channel_status",
                        columnList = "tenant_id,sales_channel_id,sync_status"
                )
        }
)
@Getter
@Setter
public class ExternalEntityMapping extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "sales_channel_id", nullable = false)
    private UUID salesChannelId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "erp_entity_id", nullable = false)
    private UUID erpEntityId;

    @Column(name = "external_entity_id", nullable = false)
    private String externalEntityId;

    @Column(name = "external_parent_id")
    private String externalParentId;

    @Column(name = "sync_status", nullable = false, length = 32)
    private String syncStatus = "PENDING";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata = new HashMap<>();

    public static ExternalEntityMapping findByErpId(
            String tenantId,
            UUID salesChannelId,
            String entityType,
            UUID erpEntityId
    ) {
        return find(
                "tenantId = ?1 and salesChannelId = ?2 and entityType = ?3 and erpEntityId = ?4",
                tenantId,
                salesChannelId,
                entityType,
                erpEntityId
        ).firstResult();
    }

    public static ExternalEntityMapping findByExternalId(
            String tenantId,
            UUID salesChannelId,
            String entityType,
            String externalEntityId
    ) {
        return find(
                "tenantId = ?1 and salesChannelId = ?2 and entityType = ?3 and externalEntityId = ?4",
                tenantId,
                salesChannelId,
                entityType,
                externalEntityId
        ).firstResult();
    }
}

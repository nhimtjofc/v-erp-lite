package base.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "sales_channels",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_sales_channels_tenant_business_type",
                        columnNames = {"tenant_id", "business_id", "channel_type"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_sales_channels_tenant_type_status",
                        columnList = "tenant_id,channel_type,status"
                )
        }
)
@Getter
@Setter
public class SalesChannel extends BaseEntity {

    public static final String WOOCOMMERCE = "WOOCOMMERCE";

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType = WOOCOMMERCE;

    @Column(name = "store_url", nullable = false, length = 512)
    private String storeUrl;

    @Column(nullable = false, length = 32)
    private String status = "DISCONNECTED";

    @Column(name = "health_status", nullable = false, length = 32)
    private String healthStatus = "UNKNOWN";

    @JsonIgnore
    @Column(name = "secret_reference", length = 128)
    private String secretReference;

    @Column(name = "last_health_checked_at")
    private Instant lastHealthCheckedAt;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static List<SalesChannel> listWooCommerce(String tenantId) {
        return list(
                "tenantId = ?1 and channelType = ?2 order by active desc, createdAt, id",
                tenantId,
                WOOCOMMERCE
        );
    }

    public static SalesChannel findWooCommerce(String tenantId, UUID businessId) {
        return find(
                "tenantId = ?1 and businessId = ?2 and channelType = ?3",
                tenantId,
                businessId,
                WOOCOMMERCE
        ).firstResult();
    }
}

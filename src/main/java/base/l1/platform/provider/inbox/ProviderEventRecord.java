package base.l1.platform.provider.inbox;

import base.l0.system.util.UuidUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A verified inbound provider event, stored before anything acts on it.
 *
 * <p>Deliberately not a {@link base.l0.system.persistance.BaseEntity}. Every other entity here
 * carries a {@code @TenantId} discriminator that Hibernate appends to every query, which is exactly
 * right for data that belongs to a tenant. A webhook does not, yet: it arrives with no bearer token,
 * because its signature is its authentication, and the tenant only becomes knowable once
 * {@code provider_ref} is resolved against {@code issued_references}. Extending {@code BaseEntity}
 * would have made Hibernate silently stamp every arriving event with the configured default tenant
 * — which is how a payment notification ends up filed under the wrong company.
 */
@Entity
@Table(name = "provider_events")
@Getter
@Setter
public class ProviderEventRecord extends PanacheEntityBase {

    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 160)
    private String providerEventId;

    @Column(name = "event_signal", nullable = false, length = 32)
    private String signal;

    @Column(name = "provider_ref", length = 160)
    private String providerRef;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    /** Null until a dispatcher resolves it from {@link #providerRef}. See the class comment. */
    @Column(name = "tenant_code", length = 64)
    private String tenantCode;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** Why this has not been acted on, when that is a decision rather than a backlog. */
    @Column(name = "dispatch_note")
    private String dispatchNote;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    @PrePersist
    void assignUuid() {
        if (uuid == null) {
            uuid = UuidUtil.v7();
        }
    }
}


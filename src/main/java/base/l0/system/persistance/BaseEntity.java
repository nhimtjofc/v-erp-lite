package base.l0.system.persistance;

import base.l0.system.util.UuidUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared base for tenant-scoped entities. The tenant is a Hibernate discriminator rather than a
 * column every query has to remember to filter on.
 *
 * <p>That choice is the direct answer to a defect in the system this replaces, where tenant
 * isolation depended on each author writing {@code and tenantId = ?} into every query — and where
 * a review found five money-touching queries that had forgotten to. A discriminator cannot be
 * forgotten: Hibernate appends it to every select, update and delete for these entities.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity extends PanacheEntityBase {

    @Column(name = "tenant_code")
    private String tenantCode;

    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    void assignUuid() {
        if (uuid == null) {
            uuid = UuidUtil.v7();
        }
    }

    /** Entities whose rows carry audit and optimistic-lock columns extend this instead. */
    @MappedSuperclass
    @Getter
    @Setter
    public abstract static class Mutable extends BaseEntity {

        @UpdateTimestamp
        @ColumnDefault("CURRENT_TIMESTAMP")
        @Column(name = "updated_at")
        private Instant updatedAt;

        @Column(name = "updated_by")
        private UUID updatedBy;

        @Version
        @Column(name = "version")
        private long version;
    }
}

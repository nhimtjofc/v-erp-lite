package base.l2.ledger.period.domain;

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

/** A statement that we are finished with everything up to {@code closedThrough}. */
@Entity
@Table(name = "closed_periods")
@Getter
@Setter
public class ClosedPeriod extends PanacheEntityBase {

    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "tenant_code")
    private String tenantCode;

    @Column(name = "closed_through", nullable = false)
    private Instant closedThrough;

    @Column(name = "note")
    private String note;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopened_by")
    private UUID reopenedBy;

    @Column(name = "reopen_reason")
    private String reopenReason;

    @PrePersist
    void assignUuid() {
        if (uuid == null) {
            uuid = UuidUtil.v7();
        }
        if (closedAt == null) {
            closedAt = Instant.now();
        }
    }
}

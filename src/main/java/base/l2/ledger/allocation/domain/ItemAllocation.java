package base.l2.ledger.allocation.domain;

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
 * "This payment closed that invoice", for whatever part of each it covers.
 *
 * <p>Open-item accounting kept out of the journal. A line's outstanding amount is never written
 * back onto the line — {@code journal_lines} is append-only and a mutable "remaining" column would
 * be a second copy of a number derivable from these rows. {@code v_open_items} does the derivation.
 *
 * <p>Not a {@link base.l0.system.persistance.BaseEntity} because the table predates it and carries
 * {@code allocated_at} / {@code allocated_by} rather than the standard audit columns. The tenant
 * discriminator is declared explicitly so it is still appended to every query.
 */
@Entity
@Table(name = "item_allocations")
@Getter
@Setter
public class ItemAllocation extends PanacheEntityBase {

    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "tenant_code")
    private String tenantCode;

    /** The line being settled — funds held that a payout is closing, an invoice a payment covers. */
    @Column(name = "open_line_uuid", nullable = false)
    private UUID openLineUuid;

    /** The line doing the settling. Always the opposite direction; the database refuses otherwise. */
    @Column(name = "settle_line_uuid", nullable = false)
    private UUID settleLineUuid;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @Column(name = "allocated_by")
    private UUID allocatedBy;

    @PrePersist
    void assignUuid() {
        if (uuid == null) {
            uuid = UuidUtil.v7();
        }
        if (allocatedAt == null) {
            allocatedAt = Instant.now();
        }
    }
}

package base.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_voucher",
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "voucher_id"}))
@Getter
@Setter
public class CustomerVoucher extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "voucher_id", nullable = false)
    private UUID voucherId;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt = Instant.now();

    @Column(name = "is_used", nullable = false)
    private boolean isUsed = false;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
}

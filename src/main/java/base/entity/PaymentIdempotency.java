package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "payment_idempotency", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"})
})
@Getter
@Setter
public class PaymentIdempotency extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String status; // PROCESSING, SUCCESS, FAILED

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
}

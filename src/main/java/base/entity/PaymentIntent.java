package base.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"})
})
@Getter
@Setter
public class PaymentIntent extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status; // REQUIRES_PAYMENT_METHOD, PROCESSING, SUCCEEDED, FAILED, CANCELLED

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

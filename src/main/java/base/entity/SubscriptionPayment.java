package base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
public class SubscriptionPayment extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Column(name = "billing_period", nullable = false, length = 16)
    private String billingPeriod;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}

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
@Table(name = "tenant_subscriptions")
@Getter
@Setter
public class TenantSubscription extends BaseEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "billing_period", nullable = false, length = 16)
    private String billingPeriod;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "current_period_ends_at")
    private Instant currentPeriodEndsAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;
}

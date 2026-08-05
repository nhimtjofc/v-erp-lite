CREATE TABLE subscription_payments (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    plan_code           VARCHAR(32) NOT NULL,
    billing_period      VARCHAR(16) NOT NULL,
    amount_minor        BIGINT NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'VND',
    status              VARCHAR(16) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    payment_reference   VARCHAR(128) NULL,
    submitted_at        TIMESTAMP NULL,
    reviewed_at         TIMESTAMP NULL,
    reviewed_by         UUID NULL,
    rejection_reason    VARCHAR(500) NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID NULL,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          UUID NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_subscription_payment_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_subscription_payment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(code),
    CONSTRAINT fk_subscription_payment_plan FOREIGN KEY (plan_code) REFERENCES subscription_plans(code),
    CONSTRAINT chk_subscription_payment_period CHECK (billing_period IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT chk_subscription_payment_status CHECK (status IN ('PENDING', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_subscription_payments_review ON subscription_payments(status, submitted_at);
CREATE INDEX idx_subscription_payments_tenant ON subscription_payments(tenant_id, created_at);

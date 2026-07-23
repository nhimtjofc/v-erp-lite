CREATE TABLE revenue_adjustments (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    business_id UUID NULL,
    entry_date DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_revenue_adjustments_business FOREIGN KEY (business_id) REFERENCES business(id),
    CONSTRAINT chk_revenue_adjustments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT chk_revenue_adjustments_direction CHECK (direction IN ('INCOME', 'EXPENSE'))
);

CREATE INDEX idx_revenue_adjustments_period
    ON revenue_adjustments (tenant_id, business_id, entry_date, currency);

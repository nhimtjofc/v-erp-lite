CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    customer_id UUID NOT NULL,
    order_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notifications_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    INDEX idx_notifications_customer (tenant_id, customer_id, read_at)
);

CREATE TABLE IF NOT EXISTS payment_refunds (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    payment_intent_id UUID NOT NULL,
    gateway VARCHAR(50) NOT NULL,
    gateway_refund_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_refunds_intent FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(id),
    UNIQUE (tenant_id, order_id)
);

CREATE TABLE IF NOT EXISTS vendor_risk_events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_vendor_risk_window (tenant_id, owner_id, event_type, occurred_at, resolved_at)
);

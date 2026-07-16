-- Webhook events log for auditing and replay
-- tenant_id is nullable because webhooks arrive before tenant resolution
CREATE TABLE IF NOT EXISTS webhook_events (
    id                      UUID PRIMARY KEY,
    tenant_id               VARCHAR(64),
    gateway                 VARCHAR(50) NOT NULL,
    event_type              VARCHAR(50) NOT NULL,
    payment_intent_id       UUID,
    gateway_transaction_id  VARCHAR(255),
    raw_payload             TEXT,
    status                  VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    error_message           TEXT,
    processed_at            TIMESTAMP NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              UUID,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_tenant_gateway ON webhook_events(tenant_id, gateway);
CREATE INDEX IF NOT EXISTS idx_webhook_events_payment_intent ON webhook_events(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_status ON webhook_events(status);

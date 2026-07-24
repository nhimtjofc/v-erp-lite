-- Inbound event log to prevent replay attacks
CREATE TABLE IF NOT EXISTS inbound_event_logs (
    id                      UUID PRIMARY KEY,
    gateway                 VARCHAR(50) NOT NULL,
    event_id                VARCHAR(255) NOT NULL,
    status                  VARCHAR(50) NOT NULL,
    processed_at            TIMESTAMP NOT NULL,
    CONSTRAINT uq_gateway_event UNIQUE (gateway, event_id)
);

CREATE INDEX IF NOT EXISTS idx_inbound_event_logs_gateway_event ON inbound_event_logs(gateway, event_id);

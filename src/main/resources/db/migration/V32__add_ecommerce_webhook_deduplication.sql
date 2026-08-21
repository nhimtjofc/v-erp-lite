-- external_event_id is the connector's stable semantic event/idempotency key.
-- X-WC-Webhook-Delivery-ID is transport metadata and must be stored only in
-- channel_metadata; it is not sufficient for exactly-once order creation.
-- channel_metadata is reserved for safe routing/audit metadata; never store an
-- HMAC signature, shared secret, Authorization header, or raw credentials in it.
ALTER TABLE webhook_events
    ADD COLUMN sales_channel_id UUID NULL,
    ADD COLUMN external_event_id VARCHAR(255) NULL
        COMMENT 'Stable connector event/idempotency key; not the Woo delivery header',
    ADD COLUMN channel_metadata JSON NULL,
    ADD CONSTRAINT fk_webhook_events_channel_tenant
        FOREIGN KEY (tenant_id, sales_channel_id)
        REFERENCES sales_channels(tenant_id, id),
    ADD CONSTRAINT uq_webhook_events_channel_event
        UNIQUE (tenant_id, sales_channel_id, external_event_id),
    ADD CONSTRAINT chk_webhook_events_ecommerce_identity
        CHECK (
            UPPER(gateway) <> 'WOOCOMMERCE'
            OR (
                tenant_id IS NOT NULL
                AND sales_channel_id IS NOT NULL
                AND external_event_id IS NOT NULL
                AND CHAR_LENGTH(TRIM(external_event_id)) > 0
            )
        );

CREATE INDEX idx_webhook_events_tenant_channel_status_created
    ON webhook_events(tenant_id, sales_channel_id, status, created_at);

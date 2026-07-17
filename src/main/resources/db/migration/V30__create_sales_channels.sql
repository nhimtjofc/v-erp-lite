-- Sales channels connect one tenant business to an external commerce channel.
-- The non-secret reference may point at a server-side environment/secret-store entry;
-- the actual shared secret must never be stored in this table.
ALTER TABLE business
    ADD CONSTRAINT uq_business_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE sales_channels (
    id                      UUID PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    business_id             UUID NOT NULL,
    channel_type            VARCHAR(32) NOT NULL DEFAULT 'WOOCOMMERCE',
    store_url               VARCHAR(512) NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    health_status           VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    secret_reference        VARCHAR(128),
    last_health_checked_at  TIMESTAMP NULL,
    last_connected_at       TIMESTAMP NULL,
    last_error_message      TEXT,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              UUID,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sales_channels_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(code),
    CONSTRAINT fk_sales_channels_business_tenant
        FOREIGN KEY (tenant_id, business_id) REFERENCES business(tenant_id, id),
    CONSTRAINT uq_sales_channels_tenant_id
        UNIQUE (tenant_id, id),
    CONSTRAINT uq_sales_channels_tenant_business_type
        UNIQUE (tenant_id, business_id, channel_type)
);

CREATE INDEX idx_sales_channels_tenant_type_status
    ON sales_channels(tenant_id, channel_type, status);

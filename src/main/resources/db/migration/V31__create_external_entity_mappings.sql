-- Bidirectional ERP <-> external channel identity mapping.
-- Both unique constraints are required so concurrent retries cannot create
-- duplicate remote entities or attach one remote entity to multiple ERP rows.
CREATE TABLE external_entity_mappings (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    sales_channel_id    UUID NOT NULL,
    entity_type         VARCHAR(50) NOT NULL,
    erp_entity_id       UUID NOT NULL,
    external_entity_id  VARCHAR(255) NOT NULL,
    external_parent_id  VARCHAR(255),
    sync_status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_synced_at      TIMESTAMP NULL,
    last_attempted_at   TIMESTAMP NULL,
    last_error_message  TEXT,
    metadata            JSON NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_external_mappings_channel_tenant
        FOREIGN KEY (tenant_id, sales_channel_id)
        REFERENCES sales_channels(tenant_id, id),
    CONSTRAINT uq_external_mappings_erp
        UNIQUE (tenant_id, sales_channel_id, entity_type, erp_entity_id),
    CONSTRAINT uq_external_mappings_external
        UNIQUE (tenant_id, sales_channel_id, entity_type, external_entity_id)
);

CREATE INDEX idx_external_mappings_tenant_channel_status
    ON external_entity_mappings(tenant_id, sales_channel_id, sync_status);

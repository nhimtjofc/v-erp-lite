-- Tenant-wide configuration (JSON for flexible settings)
CREATE TABLE tenant_config (
    tenant_id   VARCHAR(64) PRIMARY KEY,
    settings    JSON NOT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    CONSTRAINT fk_tenant_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(code)
);

-- User-level preference overrides on top of tenant config
CREATE TABLE user_settings (
    user_id     UUID PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    settings    JSON NOT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Index for tenant lookups on user_settings
CREATE INDEX idx_user_settings_tenant ON user_settings(tenant_id);

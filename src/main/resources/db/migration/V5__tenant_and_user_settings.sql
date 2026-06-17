-- Tenant-wide configuration (JSONB for flexible settings)
CREATE TABLE tenant_config (
    tenant_id   VARCHAR(64) PRIMARY KEY REFERENCES tenant(code),
    settings    JSONB NOT NULL DEFAULT '{}',
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by  UUID
);

-- User-level preference overrides on top of tenant config
CREATE TABLE user_settings (
    user_id     UUID PRIMARY KEY REFERENCES users(id),
    tenant_id   VARCHAR(64) NOT NULL,
    settings    JSONB NOT NULL DEFAULT '{}',
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for tenant lookups on user_settings
CREATE INDEX idx_user_settings_tenant ON user_settings(tenant_id);

CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id   UUID,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id),
    CONSTRAINT uq_categories_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_categories_tenant ON categories(tenant_id);
CREATE INDEX idx_categories_parent ON categories(parent_id);

CREATE TABLE products (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    category_id     UUID,
    sku             VARCHAR(128) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    image_url       VARCHAR(512),
    base_price      DECIMAL(19, 2) NOT NULL,
    sale_price      DECIMAL(19, 2),
    sale_starts_at  TIMESTAMP NULL DEFAULT NULL,
    sale_ends_at    TIMESTAMP NULL DEFAULT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT chk_products_base_price_nonnegative CHECK (base_price >= 0),
    CONSTRAINT chk_products_sale_price_nonnegative CHECK (sale_price IS NULL OR sale_price >= 0)
);

CREATE INDEX idx_products_tenant ON products(tenant_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_tenant_active ON products(tenant_id, is_active);

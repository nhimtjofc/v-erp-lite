ALTER TABLE products ADD COLUMN stock_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN options JSON NULL;

CREATE TABLE tags (
    id          UUID PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tags_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_tags_tenant ON tags(tenant_id);

CREATE TABLE product_tags (
    product_id  UUID NOT NULL,
    tag_id      UUID NOT NULL,
    PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_tags_tag ON product_tags(tag_id);

CREATE TABLE product_variants (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    product_id      UUID NOT NULL,
    sku             VARCHAR(128) NOT NULL,
    price           DECIMAL(19, 2) NOT NULL,
    stock_quantity  INT NOT NULL DEFAULT 0,
    option_values   JSON NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uq_variants_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT chk_variants_price_nonnegative CHECK (price >= 0)
);

CREATE INDEX idx_product_variants_tenant ON product_variants(tenant_id);
CREATE INDEX idx_product_variants_product ON product_variants(product_id);

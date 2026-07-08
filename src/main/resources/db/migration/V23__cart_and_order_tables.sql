CREATE TABLE carts (
    id          UUID PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    customer_id UUID NOT NULL,
    status      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    notes       TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_carts_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT uq_carts_tenant_customer_active UNIQUE (tenant_id, customer_id, status),
    CONSTRAINT chk_carts_status CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED'))
);

CREATE INDEX idx_carts_tenant ON carts(tenant_id);
CREATE INDEX idx_carts_customer ON carts(customer_id);
CREATE INDEX idx_carts_tenant_status ON carts(tenant_id, status);

CREATE TABLE cart_items (
    id                 UUID PRIMARY KEY,
    cart_id            UUID NOT NULL,
    product_id         UUID NOT NULL,
    product_variant_id UUID,
    quantity           INTEGER NOT NULL DEFAULT 1,
    unit_price_minor   BIGINT NOT NULL DEFAULT 0,
    currency           CHAR(3) NOT NULL DEFAULT 'USD',
    tenant_id          VARCHAR(64) NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id, product_variant_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

-- Add stock_quantity to products if not present (missing from V9)
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER NOT NULL DEFAULT 5;

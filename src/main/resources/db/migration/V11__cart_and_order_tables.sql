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
    id                UUID PRIMARY KEY,
    cart_id           UUID NOT NULL,
    product_id        UUID NOT NULL,
    product_variant_id UUID,
    quantity          INTEGER NOT NULL DEFAULT 1,
    unit_price        DECIMAL(19, 2) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id, product_variant_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    customer_id     UUID NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_amount    DECIMAL(19, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    shipping_fee    DECIMAL(19, 2) NOT NULL DEFAULT 0,
    final_amount    DECIMAL(19, 2) NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_orders_tenant ON orders(tenant_id);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);

CREATE TABLE order_items (
    id                UUID PRIMARY KEY,
    order_id          UUID NOT NULL,
    product_id        UUID NOT NULL,
    product_variant_id UUID,
    quantity          INTEGER NOT NULL,
    unit_price        DECIMAL(19, 2) NOT NULL,
    subtotal          DECIMAL(19, 2) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Add stock_quantity to products if not present (missing from V9)
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER NOT NULL DEFAULT 5;

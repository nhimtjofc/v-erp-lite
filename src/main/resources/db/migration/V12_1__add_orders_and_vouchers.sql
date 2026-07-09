CREATE TABLE IF NOT EXISTS vouchers (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    discount_type       VARCHAR(50) NOT NULL,
    discount_value      DECIMAL(19, 2) NOT NULL,
    min_order_value     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    max_discount_amount DECIMAL(19, 2),
    start_time          TIMESTAMP NULL DEFAULT NULL,
    end_time            TIMESTAMP NULL DEFAULT NULL,
    usage_limit         INT,
    used_count          INT NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_vouchers_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_vouchers_tenant ON vouchers(tenant_id);

CREATE TABLE IF NOT EXISTS orders (
    id                UUID PRIMARY KEY,
    tenant_id         VARCHAR(64) NOT NULL,
    customer_id       UUID NOT NULL,
    voucher_id        UUID,
    order_number      VARCHAR(50),
    order_date        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    shipping_name     VARCHAR(255),
    shipping_phone    VARCHAR(50),
    shipping_address  TEXT,
    shipping_note     TEXT,
    subtotal_amount   DECIMAL(19, 2),
    discount_amount   DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    shipping_fee      DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    tax_amount        DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    final_amount      DECIMAL(19, 2),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT uq_orders_tenant_number UNIQUE (tenant_id, order_number)
);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_number VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_name VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_phone VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_address TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_note TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(19, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_fee DECIMAL(19, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS final_amount DECIMAL(19, 2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_orders_tenant ON orders(tenant_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);

CREATE TABLE IF NOT EXISTS order_items (
    id                 UUID PRIMARY KEY,
    order_id           UUID NOT NULL,
    product_id         UUID NOT NULL,
    product_variant_id UUID,
    quantity           INT NOT NULL DEFAULT 1,
    tenant_id          VARCHAR(64),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         UUID,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         UUID,
    version            BIGINT NOT NULL DEFAULT 0,
    subtotal_amount    DECIMAL(19, 2),
    unit_price         DECIMAL(19, 2),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id)
);

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(19, 2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS unit_price DECIMAL(19, 2);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);

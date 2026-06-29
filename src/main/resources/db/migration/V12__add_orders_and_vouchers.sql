CREATE TABLE vouchers (
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

CREATE INDEX idx_vouchers_tenant ON vouchers(tenant_id);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    customer_id         UUID NOT NULL,
    voucher_id          UUID,
    order_number        VARCHAR(50) NOT NULL,
    order_date          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status              VARCHAR(50) NOT NULL,
    shipping_name       VARCHAR(255) NOT NULL,
    shipping_phone      VARCHAR(50) NOT NULL,
    shipping_address    TEXT NOT NULL,
    shipping_note       TEXT,
    subtotal_amount     DECIMAL(19, 2) NOT NULL,
    discount_amount     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    shipping_fee        DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    tax_amount          DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    final_amount        DECIMAL(19, 2) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT uq_orders_tenant_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_orders_tenant ON orders(tenant_id);
CREATE INDEX idx_orders_customer ON orders(customer_id);

CREATE TABLE order_items (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    order_id            UUID NOT NULL,
    product_id          UUID NOT NULL,
    product_variant_id  UUID,
    quantity            INT NOT NULL,
    unit_price          DECIMAL(19, 2) NOT NULL,
    subtotal_amount     DECIMAL(19, 2) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          UUID,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

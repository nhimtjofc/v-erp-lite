ALTER TABLE products
    ADD COLUMN cost_price_minor BIGINT NOT NULL DEFAULT 0;

ALTER TABLE product_variants
    ADD COLUMN cost_price_minor BIGINT NULL;

ALTER TABLE order_items
    ADD COLUMN unit_cost_minor BIGINT NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD CONSTRAINT chk_products_cost_price_nonnegative CHECK (cost_price_minor >= 0);

ALTER TABLE product_variants
    ADD CONSTRAINT chk_variants_cost_price_nonnegative
        CHECK (cost_price_minor IS NULL OR cost_price_minor >= 0);

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_unit_cost_nonnegative CHECK (unit_cost_minor >= 0);

UPDATE order_items oi
JOIN products p ON p.id = oi.product_id
LEFT JOIN product_variants pv ON pv.id = oi.product_variant_id
SET oi.unit_cost_minor = COALESCE(pv.cost_price_minor, p.cost_price_minor, 0);

-- Product
ALTER TABLE products RENAME COLUMN base_price TO base_price_minor;
ALTER TABLE products ALTER COLUMN base_price_minor TYPE BIGINT USING (base_price_minor * 100)::BIGINT;
ALTER TABLE products RENAME COLUMN sale_price TO sale_price_minor;
ALTER TABLE products ALTER COLUMN sale_price_minor TYPE BIGINT USING (sale_price_minor * 100)::BIGINT;
ALTER TABLE products ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

-- ProductVariant
ALTER TABLE product_variants RENAME COLUMN price TO price_minor;
ALTER TABLE product_variants ALTER COLUMN price_minor TYPE BIGINT USING (price_minor * 100)::BIGINT;
ALTER TABLE product_variants ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

-- CartItem
ALTER TABLE cart_items RENAME COLUMN unit_price TO unit_price_minor;
ALTER TABLE cart_items ALTER COLUMN unit_price_minor TYPE BIGINT USING (unit_price_minor * 100)::BIGINT;
ALTER TABLE cart_items ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

-- OrderItem
ALTER TABLE order_items RENAME COLUMN unit_price TO unit_price_minor;
ALTER TABLE order_items ALTER COLUMN unit_price_minor TYPE BIGINT USING (unit_price_minor * 100)::BIGINT;
ALTER TABLE order_items RENAME COLUMN subtotal_amount TO subtotal_amount_minor;
ALTER TABLE order_items ALTER COLUMN subtotal_amount_minor TYPE BIGINT USING (subtotal_amount_minor * 100)::BIGINT;
ALTER TABLE order_items ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Order
ALTER TABLE orders RENAME COLUMN subtotal_amount TO subtotal_amount_minor;
ALTER TABLE orders ALTER COLUMN subtotal_amount_minor TYPE BIGINT USING (subtotal_amount_minor * 100)::BIGINT;
ALTER TABLE orders RENAME COLUMN discount_amount TO discount_amount_minor;
ALTER TABLE orders ALTER COLUMN discount_amount_minor TYPE BIGINT USING (discount_amount_minor * 100)::BIGINT;
ALTER TABLE orders RENAME COLUMN tax_amount TO tax_amount_minor;
ALTER TABLE orders ALTER COLUMN tax_amount_minor TYPE BIGINT USING (tax_amount_minor * 100)::BIGINT;
ALTER TABLE orders RENAME COLUMN shipping_fee TO shipping_fee_minor;
ALTER TABLE orders ALTER COLUMN shipping_fee_minor TYPE BIGINT USING (shipping_fee_minor * 100)::BIGINT;
ALTER TABLE orders RENAME COLUMN final_amount TO final_amount_minor;
ALTER TABLE orders ALTER COLUMN final_amount_minor TYPE BIGINT USING (final_amount_minor * 100)::BIGINT;
ALTER TABLE orders ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Customer
ALTER TABLE customer RENAME COLUMN total_spend TO total_spend_minor;
ALTER TABLE customer ALTER COLUMN total_spend_minor TYPE BIGINT USING (total_spend_minor * 100)::BIGINT;

-- Voucher
ALTER TABLE vouchers ALTER COLUMN discount_value TYPE BIGINT USING (discount_value)::BIGINT;
ALTER TABLE vouchers RENAME COLUMN min_order_value TO min_order_value_minor;
ALTER TABLE vouchers ALTER COLUMN min_order_value_minor TYPE BIGINT USING (min_order_value_minor * 100)::BIGINT;
ALTER TABLE vouchers RENAME COLUMN max_discount_amount TO max_discount_amount_minor;
ALTER TABLE vouchers ALTER COLUMN max_discount_amount_minor TYPE BIGINT USING (max_discount_amount_minor * 100)::BIGINT;
ALTER TABLE vouchers ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'USD';

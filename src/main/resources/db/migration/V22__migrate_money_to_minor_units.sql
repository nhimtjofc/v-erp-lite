<<<<<<< HEAD
-- Product
ALTER TABLE products CHANGE COLUMN base_price base_price_minor DECIMAL(19, 2);
UPDATE products SET base_price_minor = base_price_minor * 100;
ALTER TABLE products MODIFY COLUMN base_price_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products CHANGE COLUMN sale_price sale_price_minor DECIMAL(19, 2);
UPDATE products SET sale_price_minor = sale_price_minor * 100;
ALTER TABLE products MODIFY COLUMN sale_price_minor BIGINT NULL;
ALTER TABLE products ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- ProductVariant
ALTER TABLE product_variants CHANGE COLUMN price price_minor DECIMAL(19, 2);
UPDATE product_variants SET price_minor = price_minor * 100;
ALTER TABLE product_variants MODIFY COLUMN price_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- CartItem
ALTER TABLE cart_items CHANGE COLUMN unit_price unit_price_minor DECIMAL(19, 2);
UPDATE cart_items SET unit_price_minor = unit_price_minor * 100;
ALTER TABLE cart_items MODIFY COLUMN unit_price_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- OrderItem
ALTER TABLE order_items CHANGE COLUMN unit_price unit_price_minor DECIMAL(19, 2);
UPDATE order_items SET unit_price_minor = unit_price_minor * 100;
ALTER TABLE order_items MODIFY COLUMN unit_price_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_items CHANGE COLUMN subtotal_amount subtotal_amount_minor DECIMAL(19, 2);
UPDATE order_items SET subtotal_amount_minor = subtotal_amount_minor * 100;
ALTER TABLE order_items MODIFY COLUMN subtotal_amount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Order
ALTER TABLE orders CHANGE COLUMN subtotal_amount subtotal_amount_minor DECIMAL(19, 2);
UPDATE orders SET subtotal_amount_minor = subtotal_amount_minor * 100;
ALTER TABLE orders MODIFY COLUMN subtotal_amount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders CHANGE COLUMN discount_amount discount_amount_minor DECIMAL(19, 2);
UPDATE orders SET discount_amount_minor = discount_amount_minor * 100;
ALTER TABLE orders MODIFY COLUMN discount_amount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders CHANGE COLUMN tax_amount tax_amount_minor DECIMAL(19, 2);
UPDATE orders SET tax_amount_minor = tax_amount_minor * 100;
ALTER TABLE orders MODIFY COLUMN tax_amount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders CHANGE COLUMN shipping_fee shipping_fee_minor DECIMAL(19, 2);
UPDATE orders SET shipping_fee_minor = shipping_fee_minor * 100;
ALTER TABLE orders MODIFY COLUMN shipping_fee_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders CHANGE COLUMN final_amount final_amount_minor DECIMAL(19, 2);
UPDATE orders SET final_amount_minor = final_amount_minor * 100;
ALTER TABLE orders MODIFY COLUMN final_amount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Customer
ALTER TABLE customer CHANGE COLUMN total_spend total_spend_minor DECIMAL(19, 2);
UPDATE customer SET total_spend_minor = total_spend_minor * 100;
ALTER TABLE customer MODIFY COLUMN total_spend_minor BIGINT NOT NULL DEFAULT 0;

-- Voucher
ALTER TABLE vouchers MODIFY COLUMN discount_value BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vouchers CHANGE COLUMN min_order_value min_order_value_minor DECIMAL(19, 2);
UPDATE vouchers SET min_order_value_minor = min_order_value_minor * 100;
ALTER TABLE vouchers MODIFY COLUMN min_order_value_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vouchers CHANGE COLUMN max_discount_amount max_discount_amount_minor DECIMAL(19, 2);
UPDATE vouchers SET max_discount_amount_minor = max_discount_amount_minor * 100;
ALTER TABLE vouchers MODIFY COLUMN max_discount_amount_minor BIGINT NULL;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';
=======
-- Product: already renamed by previous partial run, skip
-- ProductVariant: check and only rename if old column still exists
ALTER TABLE product_variants CHANGE COLUMN IF EXISTS price price_minor BIGINT;
ALTER TABLE product_variants ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- OrderItem
UPDATE order_items SET unit_price = unit_price * 100 WHERE unit_price IS NOT NULL;
ALTER TABLE order_items CHANGE COLUMN unit_price unit_price_minor BIGINT;

UPDATE order_items SET subtotal_amount = subtotal_amount * 100 WHERE subtotal_amount IS NOT NULL;
ALTER TABLE order_items CHANGE COLUMN subtotal_amount subtotal_amount_minor BIGINT;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Order
UPDATE orders SET subtotal_amount = subtotal_amount * 100 WHERE subtotal_amount IS NOT NULL;
ALTER TABLE orders CHANGE COLUMN subtotal_amount subtotal_amount_minor BIGINT;

UPDATE orders SET discount_amount = discount_amount * 100 WHERE discount_amount IS NOT NULL;
ALTER TABLE orders CHANGE COLUMN discount_amount discount_amount_minor BIGINT;

UPDATE orders SET tax_amount = tax_amount * 100 WHERE tax_amount IS NOT NULL;
ALTER TABLE orders CHANGE COLUMN tax_amount tax_amount_minor BIGINT;

UPDATE orders SET shipping_fee = shipping_fee * 100 WHERE shipping_fee IS NOT NULL;
ALTER TABLE orders CHANGE COLUMN shipping_fee shipping_fee_minor BIGINT;

UPDATE orders SET final_amount = final_amount * 100 WHERE final_amount IS NOT NULL;
ALTER TABLE orders CHANGE COLUMN final_amount final_amount_minor BIGINT;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Customer
UPDATE customer SET total_spend = total_spend * 100 WHERE total_spend IS NOT NULL;
ALTER TABLE customer CHANGE COLUMN total_spend total_spend_minor BIGINT;

-- Voucher
ALTER TABLE vouchers MODIFY COLUMN discount_value BIGINT;

UPDATE vouchers SET min_order_value = min_order_value * 100 WHERE min_order_value IS NOT NULL;
ALTER TABLE vouchers CHANGE COLUMN min_order_value min_order_value_minor BIGINT;

UPDATE vouchers SET max_discount_amount = max_discount_amount * 100 WHERE max_discount_amount IS NOT NULL;
ALTER TABLE vouchers CHANGE COLUMN max_discount_amount max_discount_amount_minor BIGINT;

ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';

-- Products: ensure columns renamed (idempotent - already done)
ALTER TABLE products CHANGE COLUMN IF EXISTS base_price base_price_minor BIGINT;
ALTER TABLE products CHANGE COLUMN IF EXISTS sale_price sale_price_minor BIGINT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS currency CHAR(3) NOT NULL DEFAULT 'USD';
>>>>>>> 8a9702588ecd1b6bc3585995b89b00cd491f3c58

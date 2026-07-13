-- Drop orphan column 'subtotal' from order_items (original V12 used 'subtotal',
-- V12_1 added 'subtotal_amount', V22 renamed that to 'subtotal_amount_minor',
-- but 'subtotal' was left behind as a NOT NULL column with no default)
ALTER TABLE order_items DROP COLUMN IF EXISTS subtotal;

-- Drop orphan column 'total_amount' from orders (not mapped in the entity)
ALTER TABLE orders DROP COLUMN IF EXISTS total_amount;

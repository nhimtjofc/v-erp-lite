ALTER TABLE products ADD COLUMN IF NOT EXISTS business_id UUID;
ALTER TABLE products ADD CONSTRAINT IF NOT EXISTS fk_products_business FOREIGN KEY (business_id) REFERENCES business (id);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS business_id UUID;
ALTER TABLE orders ADD CONSTRAINT IF NOT EXISTS fk_orders_business FOREIGN KEY (business_id) REFERENCES business (id);

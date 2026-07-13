ALTER TABLE products ADD COLUMN IF NOT EXISTS business_id UUID;
ALTER TABLE products ADD CONSTRAINT fk_products_business FOREIGN KEY (business_id) REFERENCES business (id);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS business_id UUID;
ALTER TABLE orders ADD CONSTRAINT fk_orders_business FOREIGN KEY (business_id) REFERENCES business (id);

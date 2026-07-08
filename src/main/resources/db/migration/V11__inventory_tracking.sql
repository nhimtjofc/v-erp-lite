ALTER TABLE products ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;

ALTER TABLE product_variants ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 0;
ALTER TABLE product_variants ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0;

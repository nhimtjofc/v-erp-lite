-- Add approval_status column to products table if it does not exist
ALTER TABLE products ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

-- Make all existing products APPROVED
UPDATE products SET approval_status = 'APPROVED';

-- Rename the business 'b1000000-0000-0000-0000-000000000001' to 'cửa hàng owner'
UPDATE business SET name = 'cửa hàng owner' WHERE id = 'b1000000-0000-0000-0000-000000000001';

-- Deactivate existing products for this business so they won't be shown in the store
UPDATE products SET is_active = FALSE WHERE business_id = 'b1000000-0000-0000-0000-000000000001';

-- Insert new sample products for 'cửa hàng owner' (business_id: 'b1000000-0000-0000-0000-000000000001') using INSERT IGNORE
INSERT IGNORE INTO products (id, tenant_id, business_id, category_id, sku, name, description, image_url, base_price_minor, sale_price_minor, is_active, version, stock_quantity, options, low_stock_threshold, reserved_quantity, currency, approval_status) VALUES
('30000000-0000-0000-0000-000000000501', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'OWNER-RAINCOAT', 'Áo mưa', 'Áo mưa bộ cao cấp, chống thấm nước tuyệt đối', 'https://images.unsplash.com/photo-1548883354-7622d03aca27?w=500', 1500, NULL, TRUE, 0, 100, '[]', 5, 0, 'USD', 'APPROVED'),
('30000000-0000-0000-0000-000000000502', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'OWNER-HELMET', 'Nón bảo hiểm', 'Nón bảo hiểm nửa đầu đạt chuẩn chất lượng', 'https://images.unsplash.com/photo-1599819811279-d5ad9cccf838?w=500', 2500, NULL, TRUE, 0, 80, '[]', 5, 0, 'USD', 'APPROVED'),
('30000000-0000-0000-0000-000000000503', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'OWNER-KNIFE', 'Dao bếp', 'Dao bếp bằng thép không gỉ sắc bén', 'https://images.unsplash.com/photo-1593642632823-8f785ba67e45?w=500', 1200, NULL, TRUE, 0, 50, '[]', 5, 0, 'USD', 'APPROVED'),
('30000000-0000-0000-0000-000000000504', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'OWNER-BOOKSHELF', 'Kệ sách', 'Kệ sách gỗ 3 tầng thiết kế hiện đại', 'https://images.unsplash.com/photo-1544644181-1484b3fdfc62?w=500', 4500, NULL, TRUE, 0, 30, '[]', 5, 0, 'USD', 'APPROVED'),
('30000000-0000-0000-0000-000000000505', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000007', 'OWNER-CUTTINGBOARD', 'Tấm thớt', 'Thớt gỗ xà cừ tự nhiên kháng khuẩn', 'https://images.unsplash.com/photo-1590794056226-79ef3a8147e1?w=500', 900, NULL, TRUE, 0, 40, '[]', 5, 0, 'USD', 'PENDING');

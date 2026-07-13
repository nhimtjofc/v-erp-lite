-- Seed Categories
INSERT IGNORE INTO categories (id, tenant_id, name, description, parent_id, is_active, version) VALUES
('10000000-0000-0000-0000-000000000006', 'v-erp-app', 'Board Games', 'Trò chơi bàn cờ trí tuệ, giải trí nhóm', NULL, true, 0),
('10000000-0000-0000-0000-000000000007', 'v-erp-app', 'Đồ thủ công', 'Sản phẩm handmade, quà tặng thủ công nghệ thuật', NULL, true, 0),
('10000000-0000-0000-0000-000000000008', 'v-erp-app', 'Điện nước', 'Thiết bị điện gia dụng, vật tư ngành nước', NULL, true, 0);

-- Seed Businesses (Stores)
INSERT IGNORE INTO business (id, tenant_id, name, address, phone, email, tax_id, logo_url, is_active, version, created_at, updated_at) VALUES
('b1000000-0000-0000-0000-000000000001', 'v-erp-app', 'Cửa hàng Board Game', '45 Nguyễn Chí Thanh, Hà Nội', '0981112223', 'boardgame@v-erp.com', 'TAX-BG-001', 'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?w=100', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('b1000000-0000-0000-0000-000000000002', 'v-erp-app', 'Cửa hàng Đồ thủ công', '88 Phố Cổ, Hà Nội', '0984445556', 'handicraft@v-erp.com', 'TAX-HC-002', 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?w=100', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('b1000000-0000-0000-0000-000000000003', 'v-erp-app', 'Cửa hàng Đồ điện nước', '120 Đường Láng, Hà Nội', '0987778889', 'hardware@v-erp.com', 'TAX-HW-003', 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=100', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Seed Products for Board Game Store
INSERT IGNORE INTO products (id, tenant_id, business_id, category_id, sku, name, description, image_url, base_price_minor, sale_price_minor, is_active, version, stock_quantity, options, low_stock_threshold, reserved_quantity, currency) VALUES
('30000000-0000-0000-0000-000000000101', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000006', 'BG-CATAN', 'Cờ Tỷ Phú Catan', 'Board game giao thương xây dựng thuộc địa Catan phiên bản tiêu chuẩn', 'https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?w=500', 3500, NULL, true, 0, 50, '[]', 5, 0, 'USD'),
('30000000-0000-0000-0000-000000000102', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000006', 'BG-WEREWOLF', 'Bài Ma Sói Ultimate', 'Trò chơi Ma sói Ultimate Deluxe Việt hóa 78 lá bài', 'https://images.unsplash.com/photo-1585504198199-20277593b94f?w=500', 1500, 1200, true, 0, 100, '[]', 10, 0, 'USD'),
('30000000-0000-0000-0000-000000000103', 'v-erp-app', 'b1000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000006', 'BG-EXPLODING', 'Bài Mèo Nổ Exploding Kittens', 'Trò chơi thẻ bài Mèo Nổ phiên bản mở rộng đầy kịch tính', 'https://images.unsplash.com/photo-1606167668584-78701c57f13d?w=500', 1800, NULL, true, 0, 80, '[]', 8, 0, 'USD');

-- Seed Products for Handicraft Store
INSERT IGNORE INTO products (id, tenant_id, business_id, category_id, sku, name, description, image_url, base_price_minor, sale_price_minor, is_active, version, stock_quantity, options, low_stock_threshold, reserved_quantity, currency) VALUES
('30000000-0000-0000-0000-000000000201', 'v-erp-app', 'b1000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000007', 'HC-CERAMIC-VASE', 'Bình Gốm Bát Tràng Vẽ Tay', 'Bình hoa gốm sứ Bát Tràng đắp nổi thủ công họa tiết hoa sen tinh xảo', 'https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?w=500', 4500, 3900, true, 0, 20, '[]', 2, 0, 'USD'),
('30000000-0000-0000-0000-000000000202', 'v-erp-app', 'b1000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000007', 'HC-LEATHER-WALLET', 'Ví Da Bò Handmade', 'Ví da nam khâu tay thủ công từ da bò sáp nhập khẩu bền bỉ', 'https://images.unsplash.com/photo-1627124765138-0c3702a5c9e4?w=500', 2900, NULL, true, 0, 45, '[]', 5, 0, 'USD'),
('30000000-0000-0000-0000-000000000203', 'v-erp-app', 'b1000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000007', 'HC-EMBROIDERY-PIC', 'Tranh Thêu Tay Quê Hương', 'Tranh thêu tay truyền thống phong cảnh đồng quê Việt Nam khổ 40x60cm', 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?w=500', 7500, NULL, true, 0, 15, '[]', 3, 0, 'USD');

-- Seed Products for Electrical & Plumbing Store
INSERT IGNORE INTO products (id, tenant_id, business_id, category_id, sku, name, description, image_url, base_price_minor, sale_price_minor, is_active, version, stock_quantity, options, low_stock_threshold, reserved_quantity, currency) VALUES
('30000000-0000-0000-0000-000000000301', 'v-erp-app', 'b1000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000008', 'HW-LED-BULB', 'Bóng Đèn LED Điện Quang 15W', 'Bóng đèn LED siêu sáng tiết kiệm điện năng tiêu chuẩn Điện Quang', 'https://images.unsplash.com/photo-1550985543-f47f38aee65f?w=500', 500, NULL, true, 0, 150, '[]', 15, 0, 'USD'),
('30000000-0000-0000-0000-000000000302', 'v-erp-app', 'b1000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000008', 'HW-SOCKET', 'Ổ Cắm Điện Đa Năng 5 Cổng', 'Ổ cắm điện chịu nhiệt cao có công tắc nguồn an toàn tiện dụng', 'https://images.unsplash.com/photo-1595264550426-bbd6fa33c2a6?w=500', 1200, 1000, true, 0, 90, '[]', 8, 0, 'USD'),
('30000000-0000-0000-0000-000000000303', 'v-erp-app', 'b1000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000008', 'HW-PLIER', 'Kìm Điện Đa Năng Cách Điện', 'Kìm cơ khí cắt tuốt dây điện thương hiệu cao cấp tay cầm cách điện', 'https://images.unsplash.com/photo-1534224039826-c7a0dea0e66a?w=500', 850, NULL, true, 0, 60, '[]', 5, 0, 'USD');

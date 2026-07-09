-- Seed Categories
INSERT IGNORE INTO categories (id, tenant_id, name, description, parent_id, is_active, version) VALUES
('10000000-0000-0000-0000-000000000001', 'v-erp-app', 'Electronics', 'Smartphones, laptops, accessories and more', NULL, true, 0),
('10000000-0000-0000-0000-000000000002', 'v-erp-app', 'Fashion', 'Apparel, shoes, and clothing accessories', NULL, true, 0),
('10000000-0000-0000-0000-000000000003', 'v-erp-app', 'Home & Living', 'Furniture, lighting, kitchen appliances', NULL, true, 0),
('10000000-0000-0000-0000-000000000004', 'v-erp-app', 'Books', 'Educational, novels, and professional literature', NULL, true, 0),
('10000000-0000-0000-0000-000000000005', 'v-erp-app', 'Sports & Outdoors', 'Fitness equipment, apparel, and outdoor gear', NULL, true, 0);

-- Seed Tags
INSERT IGNORE INTO tags (id, tenant_id, name, is_active, version) VALUES
('20000000-0000-0000-0000-000000000001', 'v-erp-app', 'Best Seller', true, 0),
('20000000-0000-0000-0000-000000000002', 'v-erp-app', 'New Arrival', true, 0),
('20000000-0000-0000-0000-000000000003', 'v-erp-app', 'Discounted', true, 0),
('20000000-0000-0000-0000-000000000004', 'v-erp-app', 'Limited Edition', true, 0),
('20000000-0000-0000-0000-000000000005', 'v-erp-app', 'Eco Friendly', true, 0);

-- Seed Products
INSERT IGNORE INTO products (id, tenant_id, category_id, sku, name, description, image_url, base_price, sale_price, sale_starts_at, sale_ends_at, is_active, version, stock_quantity, options, low_stock_threshold, reserved_quantity) VALUES
-- Electronics
('30000000-0000-0000-0000-000000000001', 'v-erp-app', '10000000-0000-0000-0000-000000000001', 'PROD-PHONE-12', 'V-Phone 12 Pro', 'Flagship smartphone with high resolution camera and OLED display', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=500', 999.00, 899.00, '2026-06-01 00:00:00', '2026-07-31 23:59:59', true, 0, 50, '[{"name": "Storage", "values": ["128GB", "256GB"]}, {"name": "Color", "values": ["Space Gray", "Silver"]}]', 5, 0),
('30000000-0000-0000-0000-000000000002', 'v-erp-app', '10000000-0000-0000-0000-000000000001', 'PROD-LAPTOP-X', 'V-Book Pro X', '15-inch professional developer laptop, 32GB RAM, 1TB SSD', 'https://images.unsplash.com/photo-1496181130204-755241524eab?w=500', 1899.00, NULL, NULL, NULL, true, 0, 25, '[]', 3, 0),
('30000000-0000-0000-0000-000000000003', 'v-erp-app', '10000000-0000-0000-0000-000000000001', 'PROD-EARBUDS-3', 'V-Earbuds 3', 'Wireless active noise cancelling earbuds with fast charging case', 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=500', 149.00, 129.00, '2026-06-15 00:00:00', '2026-07-15 23:59:59', true, 0, 120, '[]', 10, 0),

-- Fashion
('30000000-0000-0000-0000-000000000004', 'v-erp-app', '10000000-0000-0000-0000-000000000002', 'PROD-TSHIRT-COTTON', 'Premium Cotton T-Shirt', '100% organic soft cotton crewneck t-shirt', 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=500', 25.00, NULL, NULL, NULL, true, 0, 300, '[{"name": "Color", "values": ["Red", "Blue"]}, {"name": "Size", "values": ["S", "M", "L"]}]', 20, 0),
('30000000-0000-0000-0000-000000000005', 'v-erp-app', '10000000-0000-0000-0000-000000000002', 'PROD-DRESS-SUMMER', 'Floral Summer Dress', 'Lightweight, breathable floral dress perfect for warm weather', 'https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=500', 45.00, 39.99, '2026-06-01 00:00:00', '2026-08-31 23:59:59', true, 0, 75, '[]', 5, 0),
('30000000-0000-0000-0000-000000000006', 'v-erp-app', '10000000-0000-0000-0000-000000000002', 'PROD-SHOES-RUN', 'Aero Running Shoes', 'Mesh running shoes with high cushion midsole', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=500', 110.00, 95.00, '2026-06-20 00:00:00', '2026-07-20 23:59:59', true, 0, 90, '[{"name": "Size", "values": ["40", "41", "42"]}]', 8, 0),

-- Home & Living
('30000000-0000-0000-0000-000000000007', 'v-erp-app', '10000000-0000-0000-0000-000000000003', 'PROD-COFFEE-M1', 'Espresso Master Coffee Maker', '15-bar pump espresso machine with milk frother wand', 'https://images.unsplash.com/photo-1517256064527-09c53b2d0c6f?w=500', 299.00, 249.00, '2026-05-01 00:00:00', '2026-07-31 23:59:59', true, 0, 40, '[]', 4, 0),
('30000000-0000-0000-0000-000000000008', 'v-erp-app', '10000000-0000-0000-0000-000000000003', 'PROD-LIGHT-BULB', 'Smart LED Bulb Pack', 'RGB color changing smart LED bulbs, compatible with smart assistants', 'https://images.unsplash.com/photo-1550985543-f47f38aee65f?w=500', 35.00, NULL, NULL, NULL, true, 0, 150, '[]', 10, 0),

-- Books
('30000000-0000-0000-0000-000000000009', 'v-erp-app', '10000000-0000-0000-0000-000000000004', 'BOOK-JAVA-25', 'Mastering Java 25 & Quarkus', 'A comprehensive guide to backend engineering with Java 25 and modern Quarkus', 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500', 59.99, NULL, NULL, NULL, true, 0, 200, '[]', 15, 0),
('30000000-0000-0000-0000-000000000010', 'v-erp-app', '10000000-0000-0000-0000-000000000004', 'BOOK-SCIFI-NEBULA', 'Echoes of the Nebula', 'Award-winning science fiction epic exploring deep space anomalies', 'https://images.unsplash.com/photo-1532012197267-da84d127e765?w=500', 14.99, 12.99, '2026-06-01 00:00:00', '2026-08-31 23:59:59', true, 0, 80, '[]', 5, 0),

-- Sports & Outdoors
('30000000-0000-0000-0000-000000000011', 'v-erp-app', '10000000-0000-0000-0000-000000000005', 'PROD-YOGA-MAT', 'Eco-Grip Yoga Mat', 'Non-slip natural rubber yoga mat, 6mm thickness, eco-friendly material', 'https://images.unsplash.com/photo-1592432678016-e910b452f9a2?w=500', 49.00, NULL, NULL, NULL, true, 0, 100, '[]', 10, 0),
('30000000-0000-0000-0000-000000000012', 'v-erp-app', '10000000-0000-0000-0000-000000000005', 'PROD-WATER-BOTTLE', 'Thermal Sports Water Bottle', 'Double-walled vacuum insulated stainless steel water bottle, 1L', 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=500', 25.00, 19.99, '2026-06-01 00:00:00', '2026-07-31 23:59:59', true, 0, 250, '[]', 20, 0);

-- Seed Product-Tag Associations
INSERT IGNORE INTO product_tags (product_id, tag_id) VALUES
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001'), -- Phone -> Best Seller
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000003'), -- Phone -> Discounted
('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001'), -- Laptop -> Best Seller
('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002'), -- T-Shirt -> New Arrival
('30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001'), -- Running Shoes -> Best Seller
('30000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000003'), -- Running Shoes -> Discounted
('30000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000003'), -- Coffee Maker -> Discounted
('30000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000002'), -- Java Book -> New Arrival
('30000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000005'), -- Yoga Mat -> Eco Friendly
('30000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000005'); -- Water Bottle -> Eco Friendly

-- Seed Product Variants
INSERT IGNORE INTO product_variants (id, tenant_id, product_id, sku, price, stock_quantity, option_values, version, low_stock_threshold, reserved_quantity) VALUES
-- Variants for V-Phone 12 Pro (base price 999.00 / sale 899.00)
('40000000-0000-0000-0000-000000000010', 'v-erp-app', '30000000-0000-0000-0000-000000000001', 'VAR-PHONE-12-128-GRAY', 899.00, 15, '{"Storage": "128GB", "Color": "Space Gray"}', 0, 2, 0),
('40000000-0000-0000-0000-000000000011', 'v-erp-app', '30000000-0000-0000-0000-000000000001', 'VAR-PHONE-12-256-GRAY', 999.00, 10, '{"Storage": "256GB", "Color": "Space Gray"}', 0, 2, 0),
('40000000-0000-0000-0000-000000000012', 'v-erp-app', '30000000-0000-0000-0000-000000000001', 'VAR-PHONE-12-128-SILV', 899.00, 12, '{"Storage": "128GB", "Color": "Silver"}', 0, 2, 0),
('40000000-0000-0000-0000-000000000013', 'v-erp-app', '30000000-0000-0000-0000-000000000001', 'VAR-PHONE-12-256-SILV', 999.00, 13, '{"Storage": "256GB", "Color": "Silver"}', 0, 2, 0),

-- Variants for Premium Cotton T-Shirt (base price 25.00)
('40000000-0000-0000-0000-000000000001', 'v-erp-app', '30000000-0000-0000-0000-000000000004', 'VAR-TSHIRT-RED-S', 25.00, 50, '{"Color": "Red", "Size": "S"}', 0, 5, 0),
('40000000-0000-0000-0000-000000000002', 'v-erp-app', '30000000-0000-0000-0000-000000000004', 'VAR-TSHIRT-RED-M', 25.00, 60, '{"Color": "Red", "Size": "M"}', 0, 5, 0),
('40000000-0000-0000-0000-000000000003', 'v-erp-app', '30000000-0000-0000-0000-000000000004', 'VAR-TSHIRT-RED-L', 25.00, 40, '{"Color": "Red", "Size": "L"}', 0, 5, 0),
('40000000-0000-0000-0000-000000000004', 'v-erp-app', '30000000-0000-0000-0000-000000000004', 'VAR-TSHIRT-BLUE-S', 25.00, 55, '{"Color": "Blue", "Size": "S"}', 0, 5, 0),
('40000000-0000-0000-0000-000000000005', 'v-erp-app', '30000000-0000-0000-0000-000000000004', 'VAR-TSHIRT-BLUE-M', 25.00, 50, '{"Color": "Blue", "Size": "M"}', 0, 5, 0),
('40000000-0000-0000-0000-000000000006', 'v-erp-app', '30000000-0000-0000-0000-000000000004', 'VAR-TSHIRT-BLUE-L', 25.00, 45, '{"Color": "Blue", "Size": "L"}', 0, 5, 0),

-- Variants for Aero Running Shoes (base price 110.00 / sale 95.00)
('40000000-0000-0000-0000-000000000007', 'v-erp-app', '30000000-0000-0000-0000-000000000006', 'VAR-SHOES-RUN-40', 95.00, 30, '{"Size": "40"}', 0, 3, 0),
('40000000-0000-0000-0000-000000000008', 'v-erp-app', '30000000-0000-0000-0000-000000000006', 'VAR-SHOES-RUN-41', 95.00, 40, '{"Size": "41"}', 0, 3, 0),
('40000000-0000-0000-0000-000000000009', 'v-erp-app', '30000000-0000-0000-0000-000000000006', 'VAR-SHOES-RUN-42', 95.00, 20, '{"Size": "42"}', 0, 3, 0);

-- Seed Customers (Vietnamese settings)
INSERT IGNORE INTO customer (id, tenant_id, first_name, last_name, email, phone, address, birth_date, notes, is_active, version) VALUES
('50000000-0000-0000-0000-000000000001', 'v-erp-app', 'An', 'Nguyễn Văn', 'nguyen.an@gmail.com', '0901234567', '123 Đường Lê Lợi, Quận 1, TP. Hồ Chí Minh', '1990-05-15', 'VIP customer, prefers call before delivery', true, 0),
('50000000-0000-0000-0000-000000000002', 'v-erp-app', 'Bình', 'Trần Thị', 'tran.binh@yahoo.com', '0912345678', '456 Đường Nguyễn Huệ, Quận 3, TP. Hồ Chí Minh', '1992-08-20', NULL, true, 0),
('50000000-0000-0000-0000-000000000003', 'v-erp-app', 'Cường', 'Lê Văn', 'le.cuong@outlook.com', '0923456789', '789 Đường Láng, Đống Đa, Hà Nội', '1988-12-10', 'Loyal tech buyer', true, 0),
('50000000-0000-0000-0000-000000000004', 'v-erp-app', 'Dung', 'Phạm Thị', 'pham.dung@gmail.com', '0934567890', '12 Đường Trần Hưng Đạo, Quận Sơn Trà, Đà Nẵng', '1995-03-25', NULL, true, 0),
('50000000-0000-0000-0000-000000000005', 'v-erp-app', 'Em', 'Hoàng Văn', 'hoang.em@gmail.com', '0945678901', '78 Đường Hai Bà Trưng, Quận Ninh Kiều, Cần Thơ', '1991-07-30', NULL, true, 0),
('50000000-0000-0000-0000-000000000006', 'v-erp-app', 'Hương', 'Vũ Thị', 'vu.huong@gmail.com', '0956789012', '90 Đường Hùng Vương, Hồng Bàng, Hải Phòng', '1985-02-18', 'Always uses voucher code', true, 0),
('50000000-0000-0000-0000-000000000007', 'v-erp-app', 'Giang', 'Đoàn Văn', 'doan.giang@yahoo.com', '0967890123', '34 Đường Lê Duẩn, Hải Châu, Đà Nẵng', '1994-09-05', NULL, true, 0),
('50000000-0000-0000-0000-000000000008', 'v-erp-app', 'Hạnh', 'Ngô Thị', 'ngo.hanh@gmail.com', '0978901234', '56 Đường Quang Trung, Quy Nhơn, Bình Định', '1987-11-22', NULL, true, 0),
('50000000-0000-0000-0000-000000000009', 'v-erp-app', 'Hải', 'Bùi Văn', 'bui.hai@gmail.com', '0989012345', '78 Đường Cách Mạng Tháng Tám, Biên Hòa, Đồng Nai', '1993-04-14', NULL, true, 0),
('50000000-0000-0000-0000-000000000010', 'v-erp-app', 'Khánh', 'Lý Thị', 'ly.khanh@gmail.com', '0990123456', '99 Đường 30 Tháng 4, Vũng Tàu, Bà Rịa - Vũng Tàu', '1996-01-05', 'Prefers home delivery during weekends', true, 0);

-- Seed Vouchers
INSERT IGNORE INTO vouchers (id, tenant_id, code, name, discount_type, discount_value, min_order_value, max_discount_amount, start_time, end_time, usage_limit, used_count, is_active, version) VALUES
('60000000-0000-0000-0000-000000000001', 'v-erp-app', 'WELCOME10', 'Welcome Discount 10%', 'PERCENTAGE', 10.00, 50.00, 20.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1000, 3, true, 0),
('60000000-0000-0000-0000-000000000002', 'v-erp-app', 'FREESHIP', 'Free Shipping for orders over $100', 'FIXED_AMOUNT', 5.00, 100.00, 5.00, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 5000, 2, true, 0),
('60000000-0000-0000-0000-000000000003', 'v-erp-app', 'SUMMER25', 'Summer Sale 25% Off', 'PERCENTAGE', 25.00, 150.00, 50.00, '2026-06-01 00:00:00', '2026-08-31 23:59:59', 500, 0, true, 0),
('60000000-0000-0000-0000-000000000004', 'v-erp-app', 'FLASH50', 'Extreme Flash Sale $50 Off', 'FIXED_AMOUNT', 50.00, 200.00, 50.00, '2026-06-25 00:00:00', '2026-07-05 23:59:59', 100, 0, true, 0);

-- Seed Orders
INSERT IGNORE INTO orders (id, tenant_id, customer_id, voucher_id, order_number, order_date, status, shipping_name, shipping_phone, shipping_address, shipping_note, subtotal_amount, discount_amount, shipping_fee, tax_amount, final_amount, version) VALUES
-- Order 1: Completed, used WELCOME10 voucher. Bought V-Phone 12 Pro Space Gray (128GB)
('70000000-0000-0000-0000-000000000001', 'v-erp-app', '50000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001', 'ORD-2026-0001', '2026-06-20 10:30:00', 'COMPLETED', 'Nguyễn Văn An', '0901234567', '123 Đường Lê Lợi, Quận 1, TP. Hồ Chí Minh', 'Giao giờ hành chính', 899.00, 20.00, 5.00, 0.00, 884.00, 0),

-- Order 2: Shipped, used FREESHIP. Bought Book & Coffee Maker
('70000000-0000-0000-0000-000000000002', 'v-erp-app', '50000000-0000-0000-0000-000000000003', '60000000-0000-0000-0000-000000000002', 'ORD-2026-0002', '2026-06-25 14:15:00', 'SHIPPED', 'Lê Văn Cường', '0923456789', '789 Đường Láng, Đống Đa, Hà Nội', 'Gọi điện trước khi giao', 308.99, 5.00, 5.00, 0.00, 308.99, 0),

-- Order 3: Pending, no voucher. Bought Cotton T-Shirt Blue M, Yoga Mat
('70000000-0000-0000-0000-000000000003', 'v-erp-app', '50000000-0000-0000-0000-000000000004', NULL, 'ORD-2026-0003', '2026-06-28 09:00:00', 'PENDING', 'Phạm Thị Dung', '0934567890', '12 Đường Trần Hưng Đạo, Quận Sơn Trà, Đà Nẵng', NULL, 74.00, 0.00, 3.00, 0.00, 77.00, 0),

-- Order 4: Cancelled, no voucher. Bought V-Book Pro X
('70000000-0000-0000-0000-000000000004', 'v-erp-app', '50000000-0000-0000-0000-000000000002', NULL, 'ORD-2026-0004', '2026-06-10 16:45:00', 'CANCELLED', 'Trần Thị Bình', '0912345678', '456 Đường Nguyễn Huệ, Quận 3, TP. Hồ Chí Minh', NULL, 1899.00, 0.00, 10.00, 0.00, 1909.00, 0),

-- Order 5: Completed, used WELCOME10. Bought running shoes size 41
('70000000-0000-0000-0000-000000000005', 'v-erp-app', '50000000-0000-0000-0000-000000000006', '60000000-0000-0000-0000-000000000001', 'ORD-2026-0005', '2026-06-18 11:20:00', 'COMPLETED', 'Vũ Thị Hương', '0956789012', '90 Đường Hùng Vương, Hồng Bàng, Hải Phòng', NULL, 95.00, 9.50, 4.00, 0.00, 89.50, 0),

-- Order 6: Pending, no voucher. Bought V-Earbuds 3 & Smart LED Bulb Pack
('70000000-0000-0000-0000-000000000006', 'v-erp-app', '50000000-0000-0000-0000-000000000008', NULL, 'ORD-2026-0006', '2026-06-29 08:30:00', 'PENDING', 'Ngô Thị Hạnh', '0978901234', '56 Đường Quang Trung, Quy Nhơn, Bình Định', 'Giao buổi chiều', 164.00, 0.00, 5.00, 0.00, 169.00, 0),

-- Order 7: Confirmed, used FREESHIP. Bought V-Phone 12 Pro Silver (256GB)
('70000000-0000-0000-0000-000000000007', 'v-erp-app', '50000000-0000-0000-0000-000000000010', '60000000-0000-0000-0000-000000000002', 'ORD-2026-0007', '2026-06-29 11:00:00', 'CONFIRMED', 'Lý Thị Khánh', '0990123456', '99 Đường 30 Tháng 4, Vũng Tàu, Bà Rịa - Vũng Tàu', 'Giao cuối tuần', 999.00, 5.00, 5.00, 0.00, 999.00, 0),

-- Order 8: Completed, used WELCOME10. Bought 2x Thermal Water Bottles
('70000000-0000-0000-0000-000000000008', 'v-erp-app', '50000000-0000-0000-0000-000000000009', '60000000-0000-0000-0000-000000000001', 'ORD-2026-0008', '2026-06-15 13:40:00', 'COMPLETED', 'Bùi Văn Hải', '0989012345', '78 Đường Cách Mạng Tháng Tám, Biên Hòa, Đồng Nai', NULL, 39.98, 4.00, 3.00, 0.00, 38.98, 0);

-- Seed Order Items
INSERT IGNORE INTO order_items (id, tenant_id, order_id, product_id, product_variant_id, quantity, unit_price, subtotal_amount, version) VALUES
-- Order 1: V-Phone 12 Pro Space Gray (128GB)
('80000000-0000-0000-0000-000000000001', 'v-erp-app', '70000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000010', 1, 899.00, 899.00, 0),

-- Order 2: Espresso Master Coffee Maker (249.00) & Mastering Java 25 & Quarkus (59.99)
('80000000-0000-0000-0000-000000000002', 'v-erp-app', '70000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000007', NULL, 1, 249.00, 249.00, 0),
('80000000-0000-0000-0000-000000000003', 'v-erp-app', '70000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000009', NULL, 1, 59.99, 59.99, 0),

-- Order 3: Cotton T-Shirt Blue M (25.00) & Eco-Grip Yoga Mat (49.00)
('80000000-0000-0000-0000-000000000004', 'v-erp-app', '70000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000005', 1, 25.00, 25.00, 0),
('80000000-0000-0000-0000-000000000005', 'v-erp-app', '70000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000011', NULL, 1, 49.00, 49.00, 0),

-- Order 4: V-Book Pro X (1899.00)
('80000000-0000-0000-0000-000000000006', 'v-erp-app', '70000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000002', NULL, 1, 1899.00, 1899.00, 0),

-- Order 5: Aero Running Shoes size 41 (95.00)
('80000000-0000-0000-0000-000000000007', 'v-erp-app', '70000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000006', '40000000-0000-0000-0000-000000000008', 1, 95.00, 95.00, 0),

-- Order 6: V-Earbuds 3 (129.00) & Smart LED Bulb Pack (35.00)
('80000000-0000-0000-0000-000000000008', 'v-erp-app', '70000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000003', NULL, 1, 129.00, 129.00, 0),
('80000000-0000-0000-0000-000000000009', 'v-erp-app', '70000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000008', NULL, 1, 35.00, 35.00, 0),

-- Order 7: V-Phone 12 Pro Silver (256GB)
('80000000-0000-0000-0000-000000000010', 'v-erp-app', '70000000-0000-0000-0000-000000000007', '30000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000013', 1, 999.00, 999.00, 0),

-- Order 8: Thermal Sports Water Bottle (19.99 * 2)
('80000000-0000-0000-0000-000000000011', 'v-erp-app', '70000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000012', NULL, 2, 19.99, 39.98, 0);

-- Seed Order History (Timeline tracking)
INSERT IGNORE INTO order_history (id, order_id, tenant_id, old_status, new_status, note, created_at, created_by) VALUES
-- Order 1 History
('90000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000001', 'v-erp-app', NULL, 'PENDING', 'Đơn hàng được tạo thành công bởi khách hàng.', '2026-06-20 10:30:00', NULL),
('90000000-0000-0000-0000-000000000002', '70000000-0000-0000-0000-000000000001', 'v-erp-app', 'PENDING', 'CONFIRMED', 'Đã xác nhận thanh toán và thông tin giao nhận.', '2026-06-20 11:00:00', NULL),
('90000000-0000-0000-0000-000000000003', '70000000-0000-0000-0000-000000000001', 'v-erp-app', 'CONFIRMED', 'SHIPPED', 'Đơn hàng đã bàn giao cho đơn vị vận chuyển.', '2026-06-21 09:00:00', NULL),
('90000000-0000-0000-0000-000000000004', '70000000-0000-0000-0000-000000000001', 'v-erp-app', 'SHIPPED', 'COMPLETED', 'Khách hàng nhận hàng thành công.', '2026-06-23 15:30:00', NULL),

-- Order 2 History
('90000000-0000-0000-0000-000000000005', '70000000-0000-0000-0000-000000000002', 'v-erp-app', NULL, 'PENDING', 'Đơn hàng được tạo thành công.', '2026-06-25 14:15:00', NULL),
('90000000-0000-0000-0000-000000000006', '70000000-0000-0000-0000-000000000002', 'v-erp-app', 'PENDING', 'CONFIRMED', 'Hệ thống tự động xác nhận đơn hàng.', '2026-06-25 14:30:00', NULL),
('90000000-0000-0000-0000-000000000007', '70000000-0000-0000-0000-000000000002', 'v-erp-app', 'CONFIRMED', 'SHIPPED', 'Đang vận chuyển từ kho Hà Nội.', '2026-06-26 10:00:00', NULL),

-- Order 3 History
('90000000-0000-0000-0000-000000000008', '70000000-0000-0000-0000-000000000003', 'v-erp-app', NULL, 'PENDING', 'Khách hàng đặt hàng qua website.', '2026-06-28 09:00:00', NULL),

-- Order 4 History
('90000000-0000-0000-0000-000000000009', '70000000-0000-0000-0000-000000000004', 'v-erp-app', NULL, 'PENDING', 'Đơn hàng nháp khởi tạo bởi hệ thống.', '2026-06-10 16:45:00', NULL),
('90000000-0000-0000-0000-000000000010', '70000000-0000-0000-0000-000000000004', 'v-erp-app', 'PENDING', 'CANCELLED', 'Hủy bởi người bán do hết hàng trong kho.', '2026-06-11 10:00:00', NULL),

-- Order 5 History
('90000000-0000-0000-0000-000000000011', '70000000-0000-0000-0000-000000000005', 'v-erp-app', NULL, 'PENDING', 'Đơn hàng được khởi tạo.', '2026-06-18 11:20:00', NULL),
('90000000-0000-0000-0000-000000000012', '70000000-0000-0000-0000-000000000005', 'v-erp-app', 'PENDING', 'CONFIRMED', 'Đã xác nhận đơn hàng.', '2026-06-18 11:45:00', NULL),
('90000000-0000-0000-0000-000000000013', '70000000-0000-0000-0000-000000000005', 'v-erp-app', 'CONFIRMED', 'SHIPPED', 'Hàng đang được chuyển đi.', '2026-06-18 14:00:00', NULL),
('90000000-0000-0000-0000-000000000014', '70000000-0000-0000-0000-000000000005', 'v-erp-app', 'SHIPPED', 'COMPLETED', 'Khách đã thanh toán COD và nhận hàng.', '2026-06-19 16:30:00', NULL);

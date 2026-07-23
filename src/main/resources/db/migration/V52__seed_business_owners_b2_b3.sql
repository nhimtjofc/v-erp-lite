-- V52: Tạo tài khoản businessOwner cho Cửa hàng Đồ thủ công (b2) và Đồ điện nước (b3).
-- Mục đích: mỗi owner chỉ thấy sản phẩm/đơn hàng của shop mình (BusinessAccess scoping).
-- keycloak_id=NULL -> KeycloakSyncService sẽ tự tạo tài khoản Keycloak khi app khởi động.

INSERT IGNORE INTO users (id, tenant_id, email, first_name, last_name, keycloak_id, role_id, is_active, business_id)
VALUES
    ('c2000000-cafe-4000-a000-000000000002', 'v-erp-app', 'owner2@v-erp.com', 'Chu', 'ThuCong',
     NULL,
     (SELECT id FROM role WHERE name = 'businessOwner'), true,
     'b1000000-0000-0000-0000-000000000002'),
    ('c2000000-cafe-4000-a000-000000000003', 'v-erp-app', 'owner3@v-erp.com', 'Chu', 'DienNuoc',
     NULL,
     (SELECT id FROM role WHERE name = 'businessOwner'), true,
     'b1000000-0000-0000-0000-000000000003');

-- Link business b2, b3 sang chủ tương ứng
UPDATE business
SET created_by = 'c2000000-cafe-4000-a000-000000000002'
WHERE id = 'b1000000-0000-0000-0000-000000000002'
  AND tenant_id = 'v-erp-app'
  AND (created_by IS NULL);

UPDATE business
SET created_by = 'c2000000-cafe-4000-a000-000000000003'
WHERE id = 'b1000000-0000-0000-0000-000000000003'
  AND tenant_id = 'v-erp-app'
  AND (created_by IS NULL);

INSERT IGNORE INTO tenant (id, code, name, is_active)
VALUES (UUID(), 'v-erp-app', 'V-ERP App', true);

INSERT IGNORE INTO users (id, tenant_id, email, first_name, last_name, keycloak_id, role_id, is_active)
VALUES
    (UUID(), 'v-erp-app', 'admin@v-erp.com', 'Admin', 'User',
     NULL,
     (SELECT id FROM role WHERE name = 'admin'), true),
    (UUID(), 'v-erp-app', 'operator@v-erp.com', 'Operator', 'User',
     NULL,
     (SELECT id FROM role WHERE name = 'operator'), true),
    (UUID(), 'v-erp-app', 'owner@v-erp.com', 'Owner', 'Business',
     NULL,
     (SELECT id FROM role WHERE name = 'businessOwner'), true);

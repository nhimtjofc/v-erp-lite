INSERT INTO tenant (id, code, name, is_active)
VALUES (gen_random_uuid(), 'v-erp-app', 'V-ERP App', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO users (id, tenant_id, email, first_name, last_name, keycloak_id, role_id, is_active)
VALUES
    (gen_random_uuid(), 'v-erp-app', 'admin@v-erp.com', 'Admin', 'User',
     NULL,
     (SELECT id FROM role WHERE name = 'admin'), true),
    (gen_random_uuid(), 'v-erp-app', 'operator@v-erp.com', 'Operator', 'User',
     NULL,
     (SELECT id FROM role WHERE name = 'operator'), true),
    (gen_random_uuid(), 'v-erp-app', 'owner@v-erp.com', 'Owner', 'Business',
     NULL,
     (SELECT id FROM role WHERE name = 'businessOwner'), true)
ON CONFLICT (tenant_id, email) DO NOTHING;

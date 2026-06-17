-- Reset hardcoded keycloak_id values from the previous V4__seed_users.sql.
-- Seed users now start with keycloak_id = NULL; the startup
-- KeycloakSyncService creates KC accounts and fills in the IDs.
UPDATE users SET keycloak_id = NULL
WHERE tenant_id = 'v-erp-app'
  AND email IN ('admin@v-erp.com', 'operator@v-erp.com', 'owner@v-erp.com');

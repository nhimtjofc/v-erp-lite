-- Allow users to exist in the app DB before they have a Keycloak account.
-- The sync service (KeycloakSyncService) fills in the keycloak_id on startup
-- or when triggered via the /api/sync/keycloak endpoint.
ALTER TABLE users MODIFY keycloak_id VARCHAR(255) NULL;

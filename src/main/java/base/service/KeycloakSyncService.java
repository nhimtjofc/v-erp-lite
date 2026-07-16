package base.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import base.entity.Role;
import base.entity.Tenant;
import base.entity.User;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Synchronises app-DB users with Keycloak so local dev iteration does not
 * require hand-editing the realm JSON or the admin console.
 * <p>
 * Runs on {@link StartupEvent} (after Flyway) and can be triggered manually
 * via {@code POST /api/sync/keycloak}.
 * <p>
 * <b>What it does:</b>
 * <ol>
 *   <li>Ensures the {@code v-erp-app} client has a protocol mapper that reads
 *       {@code tenant_code} from the user's Keycloak attributes.</li>
 *   <li>Ensures {@code tenant_code} is declared in the Keycloak User Profile
 *       (Keycloak 26+ requires this).</li>
 *   <li>For each user whose {@code keycloak_id} is {@code null}: creates a
 *       Keycloak account with password and stores the Keycloak user ID.</li>
 *   <li>For each existing Keycloak user: updates their {@code tenant_code} and
 *       profile fields from the DB (email, name, etc.).</li>
 * </ol>
 */
@ApplicationScoped
public class KeycloakSyncService {

    private static final String REALM = "v-erp-lite";
    private static final String DEV_PASSWORD = "password";
    private static final String CLIENT_ID = "v-erp-app";
    private static final String MAPPER_NAME = "tenant-code-mapper";
    private static final String USER_ATTRIBUTE_MAPPER = "oidc-usermodel-attribute-mapper";

    @Inject
    Keycloak keycloak;

    @Inject
    @ConfigProperty(name = "quarkus.keycloak.admin-client.server-url", defaultValue = "http://localhost:8181")
    String keycloakServerUrl;

    private static final Logger log = LoggerFactory.getLogger(KeycloakSyncService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── lifecycle hook ────────────────────────────────────────────────

    void onStart(@Observes StartupEvent event) {
        log.info("KeycloakSyncService: startup sync triggered");
        try {
            SyncResult result = syncAll();
            log.info("KeycloakSyncService: done — created={}, updated={}, failed={}",
                    result.created, result.updated, result.failed);
            if (!result.errors.isEmpty()) {
                log.warn("KeycloakSyncService: {} error(s): {}",
                        result.errors.size(), String.join("; ", result.errors));
            }
        } catch (Exception e) {
            log.warn("Keycloak sync failed on startup (Keycloak may not be ready yet): {}", e.getMessage());
            log.warn("Trigger sync manually via POST /api/sync/keycloak after Keycloak is available.");
        }
    }

    // ── public API ────────────────────────────────────────────────────

    public SyncResult syncAll() {
        SyncResult result = new SyncResult();
        ensureRealmSettings(result);
        ensureProtocolMapper(result);
        ensureUserProfile(result);
        ensureRolesAndDefaults(result);
        syncOrganizations(result);
        syncUsers(result);
        syncOrgMemberships(result);
        return result;
    }

    public SyncResult syncTenant(String tenantCode) {
        SyncResult result = new SyncResult();
        ensureRealmSettings(result);
        ensureProtocolMapper(result);
        ensureUserProfile(result);
        ensureRolesAndDefaults(result);
        List<User> users = User.list("tenantId", tenantCode);
        for (User user : users) {
            syncUser(user, result);
        }
        return result;
    }

    // ── realm level settings ──────────────────────────────────────────

    void ensureRealmSettings(SyncResult result) {
        try {
            RealmRepresentation realmRep = keycloak.realm(REALM).toRepresentation();
            if (realmRep != null && !Boolean.TRUE.equals(realmRep.isResetPasswordAllowed())) {
                realmRep.setResetPasswordAllowed(true);
                keycloak.realm(REALM).update(realmRep);
                log.info("Enabled 'resetPasswordAllowed' on realm '{}' dynamically", REALM);
            }
        } catch (Exception e) {
            result.errors.add("Failed to ensure realm resetPasswordAllowed setting: " + e.getMessage());
            log.warn("Failed to update realm settings", e);
        }
    }

    // ── protocol mapper on the v-erp-app client ──────────────────────

    void ensureProtocolMapper(SyncResult result) {
        try {
            var clients = keycloak.realm(REALM).clients()
                    .findByClientId(CLIENT_ID);
            if (clients.isEmpty()) {
                result.errors.add("Client '" + CLIENT_ID + "' not found in realm");
                return;
            }
            String clientUuid = clients.get(0).getId();

            var mappers = keycloak.realm(REALM).clients()
                    .get(clientUuid)
                    .getProtocolMappers()
                    .getMappers();

            // Already exists and is correct
            var existing = mappers.stream()
                    .filter(m -> MAPPER_NAME.equals(m.getName()))
                    .findFirst();
            if (existing.isPresent() && USER_ATTRIBUTE_MAPPER.equals(existing.get().getProtocolMapper())) {
                log.info("Protocol mapper '{}' already configured on client '{}'", MAPPER_NAME, CLIENT_ID);
                return;
            }

            // Delete any old mapper with this name (e.g. hardcoded type)
            existing.ifPresent(m -> keycloak.realm(REALM).clients()
                    .get(clientUuid)
                    .getProtocolMappers()
                    .delete(m.getId()));

            // Create the user-attribute mapper
            ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
            mapper.setName(MAPPER_NAME);
            mapper.setProtocol("openid-connect");
            mapper.setProtocolMapper(USER_ATTRIBUTE_MAPPER);
            Map<String, String> config = new HashMap<>();
            config.put("user.attribute", "tenant_code");
            config.put("claim.name", "tenant_code");
            config.put("jsonType.label", "String");
            config.put("access.token.claim", "true");
            config.put("id.token.claim", "true");
            config.put("userinfo.token.claim", "true");
            mapper.setConfig(config);

            keycloak.realm(REALM).clients()
                    .get(clientUuid)
                    .getProtocolMappers()
                    .createMapper(mapper);

            result.mapperMigrated = true;
            log.info("Created user-attribute protocol mapper '{}' on client '{}'", MAPPER_NAME, CLIENT_ID);

        } catch (Exception e) {
            result.errors.add("Protocol mapper setup failed: " + e.getMessage());
            log.warn("Protocol mapper setup failed", e);
        }
    }

    // ── Keycloak 26 User Profile declaration ──────────────────────────

    /**
     * Keycloak 26+ requires custom user attributes to be declared in the
     * User Profile configuration.  This method uses a direct REST call
     * (since the Admin Client's {@code UserProfileResource} API returns
     * types not available in the client jar) to add {@code tenant_code}
     * to the profile if it is missing.
     * <p>
     * Requires {@code manage-clients} or {@code manage-realm} role.
     */
    void ensureUserProfile(SyncResult result) {
        try {
            // Obtain an access token for the service account
            String token = keycloak.tokenManager().getAccessToken().getToken();

            HttpClient http = HttpClient.newHttpClient();

            // GET current profile
            var getReq = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakServerUrl + "/admin/realms/" + REALM + "/users/profile"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            var getResp = http.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (getResp.statusCode() != 200) {
                result.errors.add("User profile GET returned " + getResp.statusCode());
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> profile = KeycloakSyncService.<Map<String, Object>>jsonDecode(getResp.body());
            if (profile == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attrs = (List<Map<String, Object>>) profile.get("attributes");
            if (attrs == null) {
                attrs = new ArrayList<>();
                profile.put("attributes", attrs);
            }

            boolean exists = attrs.stream()
                    .anyMatch(a -> "tenant_code".equals(a.get("name")));
            if (exists) return;

            // Add tenant_code attribute
            Map<String, Object> attrDef = new HashMap<>();
            attrDef.put("name", "tenant_code");
            attrDef.put("displayName", "Tenant Code");
            attrDef.put("multivalued", false);
            Map<String, Object> perms = new HashMap<>();
            perms.put("view", List.of("admin", "user"));
            perms.put("edit", List.of("admin", "user"));
            attrDef.put("permissions", perms);
            attrs.add(attrDef);

            // PUT updated profile
            String body = jsonEncode(profile);
            var putReq = HttpRequest.newBuilder()
                    .uri(URI.create(keycloakServerUrl + "/admin/realms/" + REALM + "/users/profile"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var putResp = http.send(putReq, HttpResponse.BodyHandlers.ofString());
            if (putResp.statusCode() == 200) {
                result.profileUpdated = true;
                log.info("Added 'tenant_code' to Keycloak user profile");
            } else {
                result.errors.add("User profile PUT returned " + putResp.statusCode() + ": " + putResp.body());
            }

        } catch (Exception e) {
            result.errors.add("User profile update failed: " + e.getMessage());
            log.warn("User profile update failed", e);
        }
    }

    void ensureRolesAndDefaults(SyncResult result) {
        try {
            // 1. Ensure all database roles exist in Keycloak
            List<Role> dbRoles = Role.listAll();
            for (Role dbRole : dbRoles) {
                try {
                    keycloak.realm(REALM).roles().get(dbRole.getName()).toRepresentation();
                } catch (NotFoundException e) {
                    RoleRepresentation r = new RoleRepresentation();
                    r.setName(dbRole.getName());
                    r.setDescription(dbRole.getDescription());
                    keycloak.realm(REALM).roles().create(r);
                    log.info("Created missing realm role '{}' in Keycloak", dbRole.getName());
                }
            }

            // 2. Ensure "customer" is in the default roles composite (default-roles-v-erp-lite)
            try {
                var defaultRoleResource = keycloak.realm(REALM).roles().get("default-roles-v-erp-lite");
                RoleRepresentation defaultRole = defaultRoleResource.toRepresentation();
                if (Boolean.TRUE.equals(defaultRole.isComposite())) {
                    var composites = defaultRoleResource.getRoleComposites();
                    boolean hasCustomer = composites.stream().anyMatch(r -> "customer".equals(r.getName()));
                    if (!hasCustomer) {
                        RoleRepresentation customerRole = keycloak.realm(REALM).roles().get("customer").toRepresentation();
                        defaultRoleResource.addComposites(List.of(customerRole));
                        log.info("Added 'customer' role as a composite of default-roles-v-erp-lite in Keycloak");
                    }
                }
            } catch (NotFoundException e) {
                log.warn("Default role 'default-roles-v-erp-lite' not found in Keycloak");
            }
        } catch (Exception e) {
            result.errors.add("Failed to ensure default roles in Keycloak: " + e.getMessage());
            log.warn("Failed to ensure default roles in Keycloak", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonDecode(String json) {
        try {
            return JSON.readValue(json, HashMap.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonEncode(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ── user sync ─────────────────────────────────────────────────────

    void syncUsers(SyncResult result) {
        // Materialize the list before iterating: syncUser makes Keycloak REST
        // calls and opens a REQUIRES_NEW transaction to persist keycloak_id, which
        // interleaves DB writes/reads on the connection. Streaming an open
        // ResultSet across those calls closes the cursor mid-iteration
        // ("ResultSet is closed"). listAll() reads everything up front — the same
        // pattern syncOrgMemberships already uses.
        List<User> users = User.listAll();
        for (User user : users) {
            syncUser(user, result);
        }
    }

    void syncUser(User user, SyncResult result) {
        String existingId = user.getKeycloakId();

        try {
            if (existingId != null) {
                // Check if Keycloak user still exists
                try {
                    keycloak.realm(REALM).users().get(existingId).toRepresentation();
                } catch (NotFoundException e) {
                    existingId = null;
                }
            }

            if (existingId != null) {
                // Update existing user — build full representation from DB
                UserRepresentation kcUser = keycloak.realm(REALM).users()
                        .get(existingId).toRepresentation();
                applyAppUserFields(kcUser, user, false);
                keycloak.realm(REALM).users().get(existingId).update(kcUser);
                result.updated++;
            } else {
                // Create new Keycloak user (HTTP calls — no transaction)
                String kcId = createKeycloakUser(user);
                if (kcId != null) {
                    doPersistUserSync(user.getId(), kcId);
                    result.created++;
                } else {
                    result.failed++;
                }
            }
        } catch (Exception e) {
            String reason = describeKeycloakException(e);
            result.errors.add("Failed to sync user " + user.getEmail() + ": " + reason);
            result.failed++;
            log.warn("Failed to sync user {}: {}", user.getEmail(), reason, e);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void doPersistUserSync(UUID userId, String kcId) {
        User managedUser = User.findById(userId);
        if (managedUser != null) {
            managedUser.setKeycloakId(kcId);
        }
    }

    // ── Keycloak user creation ────────────────────────────────────────

    String createKeycloakUser(User user) {
        UserRepresentation kcUser = userToRepresentation(user);

        // Assign realm role in the creation payload
        if (user.getRole() != null && user.getRole().getName() != null) {
            kcUser.setRealmRoles(List.of(user.getRole().getName()));
        }

        Response response;
        try {
            response = keycloak.realm(REALM).users().create(kcUser);
        } catch (Exception e) {
            log.warn("Keycloak user creation failed for {}: {}", user.getEmail(), describeKeycloakException(e));
            return null;
        }

        if (response.getStatus() != 201) {
            log.warn("Keycloak user creation returned {} for {}", response.getStatus(), user.getEmail());
            return null;
        }

        // Extract the new user ID from the Location header
        URI location = response.getLocation();
        if (location == null) {
            log.warn("Keycloak did not return a Location header for {}", user.getEmail());
            return null;
        }
        String path = location.getPath();
        String createdId = path.substring(path.lastIndexOf('/') + 1);

        // Set a default dev password
        setPassword(createdId);

        // Assign realm role (creation payload may be ignored for role assignments)
        if (user.getRole() != null && user.getRole().getName() != null) {
            assignRealmRole(createdId, user.getRole().getName());
        }

        return createdId;
    }

    // ── app → Keycloak (called from AdminResource on create/update) ──

    /**
     * Creates a Keycloak user from an app DB user, ensures the tenant's
     * organization exists in Keycloak, and adds the user as a member.
     * <p>
     * Intended to be called <b>after</b> the user is persisted in the app DB
     * (e.g. from {@code AdminResource.createTenantUser()}).
     *
     * @param user       the app DB user (must have {@code id}, {@code email},
     *                   {@code role}, and {@code tenantId} set)
     * @return the Keycloak user ID, or {@code null} on failure
     */
    public String createUserInKeycloak(User user) {
        // 1. Create KC user + set password + assign realm role
        String kcId = createKeycloakUser(user);
        if (kcId == null) return null;

        // 2. Ensure KC org exists for this tenant
        Tenant tenant = Tenant.find("code", user.getTenantId()).firstResult();
        if (tenant == null) {
            log.warn("No app tenant found for code={} — skipping org membership", user.getTenantId());
            return kcId;
        }
        SyncResult sr = new SyncResult();
        syncTenantOrg(tenant, sr);
        if (!sr.errors.isEmpty()) {
            log.error("Org sync failed for {}: {}", tenant.getCode(), sr.errors);
        }

        // 3. Add user to the tenant's KC organization
        String orgId = findOrgIdByAlias(tenant.getCode());
        if (orgId != null) {
            addOrgMember(orgId, kcId);
        } else {
            log.warn("No KC org found for alias={} after creation", tenant.getCode());
        }

        return kcId;
    }

    /**
     * Updates the Keycloak user's realm role to match the app DB role.
     * Removes all existing realm roles first, then assigns the current one.
     */
    public void updateKeycloakUserRole(String keycloakId, String roleName) {
        try {
            // Get current roles
            var roleMappings = keycloak.realm(REALM).users().get(keycloakId)
                    .roles().realmLevel().listAll();
            if (!roleMappings.isEmpty()) {
                keycloak.realm(REALM).users().get(keycloakId)
                        .roles().realmLevel().remove(roleMappings);
            }
            assignRealmRole(keycloakId, roleName);
        } catch (Exception e) {
            log.warn("Failed to update role in Keycloak for user {}: {}", keycloakId, describeKeycloakException(e));
        }
    }

    /**
     * Disables a Keycloak user (does not delete — sets enabled=false).
     */
    public void disableKeycloakUser(String keycloakId) {
        try {
            UserRepresentation kcUser = keycloak.realm(REALM).users()
                    .get(keycloakId).toRepresentation();
            kcUser.setEnabled(false);
            keycloak.realm(REALM).users().get(keycloakId).update(kcUser);
        } catch (Exception e) {
            log.warn("Failed to disable Keycloak user {}: {}", keycloakId, describeKeycloakException(e));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────

    /**
     * Builds a full {@link UserRepresentation} from the app DB entity,
     * setting every field we care about so a PUT does not accidentally
     * clear omitted fields (Keycloak replaces the user on PUT).
     */
    private UserRepresentation userToRepresentation(User user) {
        UserRepresentation kcUser = new UserRepresentation();
        applyAppUserFields(kcUser, user, true);
        return kcUser;
    }

    private void applyAppUserFields(UserRepresentation kcUser, User user, boolean updateUsername) {
        if (updateUsername) {
            kcUser.setUsername(user.getEmail());
        }
        kcUser.setEmail(user.getEmail());
        kcUser.setFirstName(user.getFirstName());
        kcUser.setLastName(user.getLastName());
        kcUser.setEnabled(true);
        kcUser.setEmailVerified(true);

        Map<String, List<String>> attrs = new HashMap<>();
        if (kcUser.getAttributes() != null) {
            attrs.putAll(kcUser.getAttributes());
        }
        attrs.put("tenant_code", List.of(user.getTenantId()));
        kcUser.setAttributes(attrs);
    }

    private String describeKeycloakException(Exception e) {
        if (e instanceof WebApplicationException wae && wae.getResponse() != null) {
            Response response = wae.getResponse();
            String body = null;
            try {
                if (response.hasEntity()) {
                    body = response.readEntity(String.class);
                }
            } catch (Exception ignored) {
                // Response entities can be consumed once; status + message is
                // still more useful than hiding the original exception.
            }

            String status = response.getStatus() + " " + response.getStatusInfo().getReasonPhrase();
            if (body != null && !body.isBlank()) {
                return status + " - " + body;
            }
            return status + " - " + e.getMessage();
        }
        return e.getMessage();
    }

    private void setPassword(String userId) {
        try {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(DEV_PASSWORD);
            cred.setTemporary(false);
            keycloak.realm(REALM).users().get(userId).resetPassword(cred);
        } catch (Exception e) {
            log.warn("Failed to set password for user {}: {}", userId, e.getMessage());
        }
    }

    public void setPassword(String userId, String password) {
        try {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(password);
            cred.setTemporary(false);
            keycloak.realm(REALM).users().get(userId).resetPassword(cred);
        } catch (Exception e) {
            log.warn("Failed to set password for user {}: {}", userId, e.getMessage());
        }
    }

    private void assignRealmRole(String userId, String roleName) {
        try {
            RoleRepresentation role = keycloak.realm(REALM).roles()
                    .get(roleName).toRepresentation();
            keycloak.realm(REALM).users().get(userId)
                    .roles().realmLevel().add(List.of(role));
        } catch (Exception e) {
            log.warn("Failed to assign role '{}' to {}: {}", roleName, userId, e.getMessage());
        }
    }

    // ── organization sync ─────────────────────────────────────────────

    /**
     * Creates a Keycloak organization for each tenant (alias = tenant code)
     * if one does not already exist.
     */
    void syncOrganizations(SyncResult result) {
        List<Tenant> tenants = Tenant.listAll();
        for (Tenant tenant : tenants) {
            syncTenantOrg(tenant, result);
        }
        log.info("KC org sync: processed {} tenants", tenants.size());
    }

    /**
     * Ensures a KC organization exists for the given tenant.
     */
    void syncTenantOrg(Tenant tenant, SyncResult result) {
        try {
            // Check if org already exists by alias (tenant code)
            String existingId = findOrgIdByAlias(tenant.getCode());
            if (existingId != null) {
                log.debug("KC org already exists for tenant {} (id={})", tenant.getCode(), existingId);
                return;
            }

            OrganizationRepresentation org = new OrganizationRepresentation();
            org.setName(tenant.getName());
            org.setAlias(tenant.getCode());
            org.setEnabled(tenant.isActive());

            try (Response resp = keycloak.realm(REALM).organizations().create(org)) {
                if (resp.getStatus() == 201) {
                    log.info("Created KC org '{}' for tenant {}", tenant.getCode(), tenant.getId());
                } else {
                    result.errors.add("KC org creation for " + tenant.getCode()
                            + " returned HTTP " + resp.getStatus());
                }
            }
        } catch (Exception e) {
            result.errors.add("Org sync failed for " + tenant.getCode() + ": " + e.getMessage());
            log.warn("Org sync failed for tenant {}", tenant.getCode(), e);
        }
    }

    /**
     * Resolves the KC-assigned organization id by its alias (tenant code).
     * Returns {@code null} if not found.
     */
    String findOrgIdByAlias(String alias) {
        try {
            return keycloak.realm(REALM).organizations().getAll().stream()
                    .filter(o -> alias.equals(o.getAlias()))
                    .map(OrganizationRepresentation::getId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("KC findOrgIdByAlias({}) failed: {}", alias, e.getMessage());
            return null;
        }
    }

    /**
     * Iterates all users and adds each to its tenant's KC organization.
     * Idempotent — Keycloak returns 204 if the member already exists.
     */
    void syncOrgMemberships(SyncResult result) {
        // Build alias → org-id map (single KC call)
        Map<String, String> orgIdByAlias = new HashMap<>();
        try {
            keycloak.realm(REALM).organizations().getAll()
                    .forEach(o -> orgIdByAlias.put(o.getAlias(), o.getId()));
        } catch (Exception e) {
            result.errors.add("Failed to list KC organizations: " + e.getMessage());
            return;
        }

        if (orgIdByAlias.isEmpty()) {
            log.warn("No KC organizations found — skipping membership sync");
            return;
        }

        List<User> users = User.listAll();
        int added = 0;
        for (User user : users) {
            String kcId = user.getKeycloakId();
            if (kcId == null) {
                log.debug("Skipping user {} (no keycloak_id yet)", user.getEmail());
                continue;
            }
            String orgId = orgIdByAlias.get(user.getTenantId());
            if (orgId == null) {
                result.errors.add("No KC org found for tenant " + user.getTenantId() + " (user " + user.getEmail() + ")");
                continue;
            }
            if (addOrgMember(orgId, kcId)) {
                added++;
            }
        }
        if (added > 0) {
            log.info("KC: added {} users to organizations", added);
        }
    }

    /**
     * Adds a user (by KC user id) as a member of an organization (by KC org id).
     * Returns {@code true} if the member was added (or already existed).
     */
    boolean addOrgMember(String orgId, String kcUserId) {
        try (Response resp = keycloak.realm(REALM).organizations().get(orgId)
                .members().addMember(kcUserId)) {
            int status = resp.getStatus();
            if (status == 201 || status == 204) {
                return true;
            }
            log.warn("KC addMember user={} org={} returned HTTP {}", kcUserId, orgId, status);
            return false;
        } catch (Exception e) {
            log.warn("KC addMember failed user={} org={}: {}", kcUserId, orgId, e.getMessage());
            return false;
        }
    }

    // ── result type ───────────────────────────────────────────────────

    public static class SyncResult {
        public int created;
        public int updated;
        public int failed;
        public boolean mapperMigrated;
        public boolean profileUpdated;
        public List<String> errors = new ArrayList<>();
    }
}

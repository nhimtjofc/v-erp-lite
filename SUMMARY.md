# V-ERP Lite — Kiến trúc & Tính năng

---

## Backend Architecture

### Entities

| Entity | Extends | Tenant-scoped | `@Version` | Table |
|--------|---------|:---:|:---:|-------|
| `BaseEntity` (abstract) | `PanacheEntityBase` | — | Yes | `@MappedSuperclass` |
| `User` | BaseEntity | (inherited) | `users` |
| `Customer` | BaseEntity  | `customer` |
| `Business` | BaseEntity  | `business` |
| `Tenant` | `PanacheEntityBase` (direct) |  (system)  | `tenant` |
| `Role` | `PanacheEntityBase` (direct) | (system) | `role` |
| `TenantConfig` | `PanacheEntityBase` (direct) | (PK=tenantId) | `tenant_config` |
| `UserSettings` | `PanacheEntityBase` (direct) | `user_settings` |

**Không có Repository layer** — tất cả truy vấn DB dùng Panache static methods trực tiếp từ resources/services.

---

## REST Resources (10 endpoints + 1 ExceptionMapper)

| Resource | Path | Roles | Inject | Chức năng |
|----------|------|-------|--------|-----------|
| `HealthResource` | `/health` | Public | — | Health check `{"status":"UP"}` |
| `I18nResource` | `/i18n` | Public | — | Translation JSON files với SHA-256 ETags |
| `TenantResource` | `/tenants` | admin | — | CRUD tenants (system entity) |
| `AdminResource` | `/admin` | admin | KeycloakSyncService | Quản lý user theo tenant, sync Keycloak |
| `SyncResource` | `/sync` | admin | KeycloakSyncService | Manual sync Keycloak (full/per tenant) |
| `TenantConfigResource` | `/tenant-settings` | Mixed | CallerContext, TenantConfigService | Settings 3 lớp (default→tenant→user) |
| `UserResource` | `/users` | all auth | CallerContext | CRUD users, profile `/me` |
| `CustomerResource` | `/customers` | all auth | CallerContext | CRUD + search customers |
| `BusinessResource` | `/businesses` | all auth | CallerContext | CRUD businesses |
| `AuthResource` | `/auth` | all auth | CallerContext, Keycloak | Đổi mật khẩu, MFA (setup/verify/disable/status) |
| `ConstraintViolationExceptionMapper` | — | — | — | DB constraint violation → HTTP 409 |

---

## Security Layer

### Luồng xác thực

```
JWT Bearer Token (Keycloak :8181/realms/v-erp-lite)
  → Quarkus OIDC: validate signature, expiry, audience (client: v-erp-app)
  → SecurityIdentity + JsonWebToken available
  → CallerContext (@RequestScoped): đọc tenant từ JWT claims
  → @RolesAllowed kiểm tra realm_access/roles
```

### CallerContext — thành phần trung tâm

| Method | Nguồn | Mục đích |
|--------|-------|----------|
| `tenantCode()` | `organization[0]` → `tenant_code` claim → `azp` → `"default"` | Tenant isolation |
| `userId()` | JWT `sub` | Xác định user hiện tại |
| `userEmail()` | JWT `email` | |
| `resolveUser()` | `User.find("email=?1 and tenantId=?2")` | Lấy entity User |
| `hasRole(role)` | `SecurityIdentity.hasRole()` | Kiểm tra role |

---

## Services

### KeycloakSyncService (`@ApplicationScoped`)

- `@Startup` → `syncAll()` tự động sau Flyway
- Đồng bộ 2 chiều: App DB ↔ Keycloak
- Tạo protocol mapper, user profile, organizations, users
- **Bắt lỗi ở WARN level** — user có thể tồn tại trong DB mà không có tài khoản KC
- Phục hồi thủ công: `POST /api/sync/keycloak`

**Flow đồng bộ chi tiết:**

```
syncAll():
  1. ensureProtocolMapper()  — tạo mapper attribute→JWT claim (tenant_code)
  2. ensureUserProfile()     — khai báo tenant_code trong User Profile KC
  3. syncOrganizations()     — tạo Organization cho mỗi Tenant
  4. syncUsers()             — tạo/Cập nhật tài khoản KC cho DB users
  5. syncOrgMemberships()    — gán user vào Organization
```

**AdminResource (app→KC):**
- `createUserInKeycloak()`: tạo user KC + org + member
- `updateKeycloakUserRole()`: cập nhật realm role
- `disableKeycloakUser()`: set `enabled=false`

### TenantConfigService (`@ApplicationScoped`)

**Settings 3 lớp (deep-merge):**

```
Hardcoded defaults (trong code)
  → TenantConfig (JSONB, admin quản lý)
    → UserSettings (JSONB, user tự set)
      → getEffectiveSettings() trả về merged result
```

Các nhóm settings: `regional`, `currency`, `security`, `features`, `communication`.

## Luồng request điển hình

```
HTTP GET /api/customers/{id}
  → Quarkus OIDC: validate JWT
  → JAX-RS: kiểm tra @RolesAllowed
  → CustomerResource.get(id)
    → caller.tenantCode() đọc từ JWT
    → Customer.find("id=?1 and tenantId=?2", id, tenantCode)
  → Jackson serialize entity → JSON
  → HTTP 200
```

## MFA (Multi-Factor Authentication)

```
GET /api/auth/mfa/setup
  → caller.resolveUser()
  → TotpUtil.generateSecret() — base32 secret 160-bit
  → Lưu secret vào user (mfaEnabled=false)
  → Trả về secret + provisioning URI (otpauth://)

POST /api/auth/mfa/verify { code }
  → TotpUtil.verify(code, secret) — ±1 time window (90s)
  → Nếu đúng: mfaEnabled=true

POST /api/auth/mfa/disable
  → Xóa secret + disable

GET /api/auth/mfa/status
  → { mfaEnabled, hasSecret }
```
## Quy tắc kiến trúc quan trọng

1. **Không DTO** — entity được serialize trực tiếp
2. **Không Repository** — Panache static methods từ resources/services
3. **Không Hibernate multi-tenancy** — filter tenant bằng tay
4. **Không Redis, MinIO, messaging, WebSockets** — "lite" project
5. **Không hand-written fetch** — chỉ dùng generated hey-api SDK
6. **`persist()` chỉ gọi trên entity mới** — entity managed (từ find()) tự flush
7. **Bidirectional sync app↔Keycloak** — DB là source of truth

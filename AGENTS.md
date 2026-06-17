# AGENTS.md — V-ERP Lite

## Startup Order

Infrastructure before backend, backend before frontend:

```bash
docker compose up -d                    # PostgreSQL + Keycloak
./gradlew quarkusDev                    # Backend dev mode at :8081
# then (separate terminal):
cd frontend && npm run dev              # Vite at :5174, proxies /api → :8081
```

Keycloak realm (`keycloak/realm-v-erp-lite.json`) auto-imports on first start.
Seeded user: `admin@v-erp.com` / `password`. Keycloak admin console: `admin` / `admin` at `localhost:8181`.

## Commands

Backend (Gradle, Quarkus 3.33.1, Java 25):
```bash
./gradlew quarkusDev          # Dev mode with live reload
./gradlew build               # Build
./gradlew test                # All tests (none written yet)
./gradlew compileJava         # Compile only (fast feedback)
```

Frontend (`frontend/`, separate Vite React app):
```bash
npm run dev           # Vite dev server (proxies /api → localhost:8081)
npm run build         # Typecheck + bundle (tsc -b && vite build) — THE verification gate
npm run generate-api  # Regenerate the hey-api SDK from openapi-spec.yaml
```

No frontend test runner. No backend lint tasks.

## Ports

| Service   | Port | Notes                |
|-----------|------|----------------------|
| Backend   | 8081 | Quarkus default 8080 |
| Frontend  | 5174 | Vite default 5173    |
| Keycloak  | 8181 |                      |
| PG (ext)  | 5433 |                      |

## Architecture

Single-module Quarkus (Gradle) app, flat `base.*` package structure:

- `base.entity` — JPA entities (Tenant, User, Customer, Business, Role, BaseEntity, TenantConfig, UserSettings)
- `base.security` — Keycloak OIDC auth: CallerContext, TenantResolver, TotpUtil
- `base.rest` — JAX-RS resources (Health, Tenant, User, Customer, Business, Auth, Admin, Sync, I18n, TenantConfig) + ConstraintViolationExceptionMapper
- `base.service` — KeycloakSyncService, TenantConfigService

No DTOs — entities are returned directly. No Hibernate multi-tenancy — `tenant_id` is a regular column filtered manually. Backend root path is `/api/`.

### Naming

`ThingResource` (JAX-RS endpoint), `ThingService` (business logic), `ThingRepository` (Panache).

## Backend Patterns

- **Persistence** — entities extend `PanacheEntityBase`. Schema changes through Flyway migrations in `src/main/resources/db/migration/V{version}__{description}.sql`.
- **Security** — Keycloak OIDC bearer token via `quarkus-oidc`. `@RolesAllowed` on endpoints. `CallerContext` (request-scoped) reads `tenant_code` from JWT claim.
- **Tenant isolation** — EVERY `get(id)`, `update(id)`, `delete(id)` MUST add `AND tenantId = ?2` to the query. Pattern: `Entity.find("id = ?1 and tenantId = ?2", id, caller.tenantCode()).firstResult()`. The `list()` methods do this already; the single-entity endpoints were historically missing it.
- **`CallerContext.resolveUser()`** — shared method for resolving the authenticated user entity (`email + tenantId` lookup). Use this instead of duplicating `User.find("email = ?1 and tenantId = ?2", ...)` in resources.
- **`resolveRole()` throws** — if the frontend sends an unresolvable role, `AdminResource.resolveRole()` throws `BadRequestException`. The old behavior (returning null silently) was a bug.
- **`@Version`** — BaseEntity has a dedicated `long version` field with `@Version` for optimistic locking. There's a Flyway migration (V8) to add this column. `updatedAt` uses `@UpdateTimestamp` only; the two are NOT on the same column.
- **Search escaping** — CustomerResource.search escapes LIKE wildcards (`%`, `_`, `\`) before passing to the query, with `ESCAPE '\\'` in each LIKE clause.
- **KeycloakSyncService** — injects `@ConfigProperty` for the Keycloak server URL, NOT `System.getProperty`.
- **App→Keycloak sync** — runs on `@Startup`, exposed via `SyncResource` at `POST /api/sync`. Creates Keycloak accounts for DB users with `null keycloak_id`. `AdminResource` user create/update/delete syncs bidirectionally.
- **OpenAPI** — `operationIdStrategy=METHOD` so JAX-RS method name maps to SDK function name.
- **JSONB columns** — use `@JdbcTypeCode(SqlTypes.JSON)`, NOT `@Convert`. Set `quarkus.hibernate-orm.mapping.format.global=ignore`. See `TenantConfig.java` / `UserSettings.java`.

## Flyway Migrations

| File | Purpose |
|------|---------|
| `V1__init.sql` | Table structure |
| `V2__user_profile_fields.sql` | Profile columns |
| `V3__user_mfa.sql` | MFA fields |
| `V3_1__keycloak_id_nullable.sql` | Makes `keycloak_id` nullable (BEFORE V4 seed) |
| `V4__seed_users.sql` | Seeds admin/operator/owner with `keycloak_id=NULL` |
| `V5__tenant_and_user_settings.sql` | Settings tables |
| `V7__clear_hardcoded_keycloak_ids.sql` | One-time cleanup (no-op on fresh) |
| `V8__add_version_columns.sql` | `version BIGINT` column for optimistic locking |

Flyway uses numeric ordering — `V3_1` runs between `V3` and `V4`. Use underscore versioning for interleaved migrations. V6 is intentionally skipped (deleted migration).

## Frontend

Separate React 19 + TypeScript 5.9 + Vite 7 app; NOT built by Gradle.

- **Routes** — file-based in `src/routes/` (TanStack Router). Page components in `src/pages/`.
- **UI kit** — `src/shared/ui/` (Base UI v1.5+ primitives + Tailwind v4). **No Radix packages**.
- **Shared hooks/utils** — `useTheme` at `src/shared/hooks/use-theme.ts` (includes `matchMedia` listener for system theme changes). `getInitials` at `src/shared/utils/user.ts`.
- **Auth** — Keycloak OIDC PKCE (S256) via `oidc-client-ts` (`src/shared/auth/index.ts`). `automaticSilentRenew: false` — the app uses its own proactive timer-based refresh in `interceptors.ts`. Tokens mirrored to `localStorage` keys `access_token` and `refresh_token` via `userLoaded`/`userUnloaded`.
- **KC URL default** — both `auth/index.ts` and `interceptors.ts` default to `http://localhost:8181`. A mismatch between these was a root cause of the auth redirect loop.
- **401 interceptor** (`src/shared/api/interceptors.ts`, auto-imported in `main.tsx`) — coalesced silent refresh + retry. `failAuth()` clears all sessions and redirects to Keycloak SSO.
- **API client** — all calls use generated hey-api SDK + React Query hooks. **No hand-written `fetch`**. Config at `src/shared/api/client-config.ts`.
- **SDK regeneration** — `curl -s http://localhost:8081/api/q/openapi.yaml -o frontend/openapi-spec.yaml && cd frontend && npm run generate-api`. `openapi-spec.yaml` is checked in. `openapi-ts.config.ts` has `enums: false`.
- **Theme** — dark/light toggle in user avatar dropdown. `localStorage key='theme'`, `.dark` class on `<html>`. Default: `prefers-color-scheme: dark`. Success color is theme-aware via `text-success` (CSS variable `--success`), NOT hardcoded `text-green-600`.
- **CardTitle** renders as `<h3>`, `Badge` renders as `<span>` — safe to nest Badge inside `<p>`.
- **i18n** — i18next + react-i18next + i18next-http-backend. Translation files served by backend at `/api/i18n/{locale}/{namespace}` with SHA-256 ETags (cached in `ConcurrentHashMap`). Language switch needs BOTH `localStorage.setItem('language', lang)` AND `i18n.changeLanguage(lang)`.
- **Tailwind CSS v4** — config in `src/index.css` (`@theme` block), no `tailwind.config.js`.
- **Env vars** in `frontend/.env`: `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`. `VITE_API_BASE_URL` is optional (defaults to `''`).

### Business paths

All CRUD paths use plural: `/api/customers`, `/api/users`, `/api/tenants`, `/api/businesses`.
The one exception is `BusinessResource` which was migrated from `/business` → `/businesses`. If you see `/business` anywhere, it should be `/businesses`.

### Entity hierarchy

| Entity | Extends | Has versioning | Tenant-scoped |
|--------|---------|----------------|---------------|
| User | BaseEntity | ✅ (optimistic lock) | ✅ |
| Customer | BaseEntity | ✅ | ✅ |
| Business | BaseEntity | ✅ | ✅ |
| Tenant | PanacheEntityBase | ❌ | ❌ (system) |
| Role | PanacheEntityBase | ❌ | ❌ (system) |
| TenantConfig | PanacheEntityBase | ❌ | ✅ |
| UserSettings | PanacheEntityBase | ❌ | ✅ |

`Tenant` and `Role` don't extend `BaseEntity` because they're system-level lookup tables shared across tenants.

### Test directory

`src/test/java/base/` exists but is **empty** — no tests have been written yet. Any intern adding a feature should add tests alongside it. The Gradle `./gradlew test` command works with Quarkus test infrastructure when test classes are added under this directory.

## Common Backend Gotchas

- **CORS** — `quarkus.http.cors.origins=*` allows any origin. Lock down for production.
- **Flyway seed data** — V1 creates tables only. V4 seeds 3 users with `keycloak_id=NULL`. On first startup, `KeycloakSyncService` creates Keycloak accounts for all null-`keycloak_id` users. If new seed users are needed, add a new V migration.
- **`SyncResource.syncTenant()`** must have `@Transactional` — it calls `user.persist()` internally.
- **`persist()` on managed entities** — only call `persist()` on new entities. Managed entities (found by query) auto-save on transaction flush. Calling `persist()` on a managed entity is a JPA spec violation. See `TenantConfigService`.
- **`KeycloakSyncService` swallows exceptions** — failures log at WARN and continue. A user may be created in the DB without a Keycloak account. Trigger sync manually via `POST /api/sync/keycloak` to recover.
- **TestResource isolation** — When debugging 404s, create a minimal `@Path("/test")` resource with no `@RolesAllowed` to isolate routing from security.
- **`ConstraintViolationExceptionMapper`** — handles Hibernate DB-level constraint violations (duplicate key, FK violation), NOT Jakarta Bean Validation. Bean validation errors (e.g. `@NotNull`, `@Size`) are returned automatically by JAX-RS as 400 responses.
- **Tenant/Role are system entities** — `Tenant` and `Role` extend `PanacheEntityBase` directly (not `BaseEntity`) because they're system-wide lookup tables not scoped to a tenant. They don't have optimistic locking or audit fields.
- **`frontend/.env`** — has `VITE_API_BASE_URL=http://localhost:8081` set. This means the hey-api SDK and i18n backend call the backend directly at port 8081 (bypassing the Vite proxy), relying on CORS (`*` in dev). If you remove this env var, the Vite proxy handles `/api/*` at port 5174 → 8081.

## Common Frontend Gotchas

- **Vite stale cache** — After adding/removing dependencies, delete `node_modules/.vite` and restart Vite. Symptom: "Failed to fetch dynamically imported module" 504 errors.
- **OIDC auth redirect loop** — App shakes between /dashboard and /login. The `oidc.user:*` localStorage key has a stale session. `failAuth()` must clear the full authority+clientId key AND redirect to Keycloak SSO. Ensure the KC URL default is 8181 in BOTH `auth/index.ts` and `interceptors.ts`.
- **Settings API body shape** — `PUT /api/tenant-settings` and `PUT /api/user-settings` SDK expects `{ settings: { ... } }`, NOT the flat map. Always wrap: `{ body: { settings: { regional } } }`.
- **`setupMfa` is a GET** — no generated mutation hook exists. Wrap manually: `useMutation({ mutationFn: async () => { const resp = await setupMfa(); return resp.data as unknown as { secret: string; provisioningUri: string }; } })`.
- **Query invalidation** — after any create/update/delete mutation, invalidate the corresponding list query: `queryClient.invalidateQueries({ queryKey: ['list2'] })`. Without this, data is stale for up to `staleTime: 30s`.
- **Customer search** — debounced (300ms) via `useEffect` before passing to `useList2Query`. The debounced value goes to the backend which has LIKE wildcard escaping.
- **`i18n/config.ts`** — guards `localStorage` access with `typeof window !== 'undefined'` for non-browser environments.

## Style

- 4-space indentation, braces on same line
- One top-level public type per file
- Explicit imports; remove unused imports when editing
- Prefer `interface` over `type` for object shapes
- Use Lombok where already present (`@Getter`, `@Setter`)
- Match surrounding file style; don't reformat unrelated code

## What to Avoid

- No Redis, MinIO, messaging, WebSockets, billing, CRM, async jobs — "lite" project
- No custom JWT auth — only Keycloak OIDC (oidc-client-ts)
- No hand-written `fetch` — use generated hey-api SDK only
- No Radix packages — all primitives are Base UI v1.5+
- No bypassing Flyway for persistent schema changes
- No weakening Keycloak OIDC validation
- No `tenant_id` via Hibernate multi-tenancy — manual filtering only
- No refactoring unrelated files for style consistency
- No MapStruct (removed), no `JacksonJsonConverter` (deleted), no `zod`/`zustand` (removed from frontend)

# Develop Branch Engineering Audit

**Reviewed revision:** `origin/develop` at `b24a54a72bcf8fa36b02df897b33985cf75c6d7a`  
**Comparison base:** `origin/main` at `11dd2788b5b137c4a1ad22b519436e1edd5a3c2e`  
**Review date:** 2026-07-22  
**Remote re-check:** `origin/develop` was fetched again on 2026-07-22 and still resolved to the reviewed revision  
**Review type:** Static code review, architecture review, financial transition tracing, migration review, and partial build/test verification

## Executive summary

The branch is an ambitious prototype with some strong service-level engineering, particularly pessimistic wallet locking, tenant-scoped lookups, idempotent business references, append-only ledger triggers, and concurrency-oriented tests. It is not safe to merge into a production-bound branch or use with real money.

The most serious problems are at integration boundaries rather than inside isolated methods:

- Business owners and operators can mint wallet funds by completing unverified top-ups; the customer top-up UI instead reaches a role-protected 403 path.
- Customers can directly complete mock payments in every runtime profile.
- Webhook signatures do not authenticate payment status, allowing a valid signature to be reused with `SUCCESS`.
- Multiple payment intents can succeed for one order, creating a double-charge condition.
- Seller-side roles can mark an order complete and release their own escrow without buyer confirmation.
- Keycloak synchronization provisions accounts with the known permanent password `password`, and the local MFA flag does not enforce MFA in Keycloak.
- Product, order, payment, wallet, migration, and frontend currency semantics disagree.
- Wallet-paid orders have no working refund path.
- Gateway refunds are fabricated locally but persisted as `COMPLETED`.
- Payouts can be marked paid and debited while using a mock transfer provider.
- Reconciliation is neither independent nor operationally effective.
- The ledger is append-only, but it is not a double-entry ledger and lacks important database invariants.
- Business-level authorization is missing from user, customer, and WooCommerce administration paths.
- Credential-shaped secrets, debug endpoints, generated artifacts, logs, bytecode, and a large installer are committed.

### Overall assessment

| Area | Assessment |
|---|---:|
| Engineering ambition | 8/10 |
| Core service design | 7/10 |
| Test-writing effort | 7/10 |
| Security and financial correctness | 2/10 |
| Release readiness | 2/10 |
| Payment/ledger release readiness | 3/10 |

This is capable intern-level prototype work attempting senior-level financial and integration scope without sufficient threat modeling, accounting controls, migration safety, or release review.

## Severity definitions

- **P0 — Critical:** Direct security compromise, money creation/loss, double charge, exposed production credential, or systemic financial corruption. Blocks merge and deployment.
- **P1 — High:** Authorization bypass, tenant leak, broken refund/payout/reconciliation, unsafe migration, or likely production incident. Blocks production release.
- **P2 — Medium:** Material reliability, maintainability, CI, API-contract, or operational weakness.
- **P3 — Low:** Cleanup, canonical naming, or minor consistency issue.

---

# Branch-wide findings

## P0 — Credential-shaped secrets are committed

`ecommerce-chatbot/.env` contains Gemini and Pinecone keys in live credential formats. They were not validated against the providers during this review, but any committed secret must be treated as compromised.

Required response:

1. Rotate/revoke both credentials.
2. Remove the file from the current tree.
3. Purge the values from Git history.
4. Add an example environment file containing placeholders only.
5. Enable secret scanning in CI and repository protection.

Evidence: `ecommerce-chatbot/.env`.

## P0 — Currency handling is systemically inconsistent

The inconsistency is not a display-only defect:

- `Product.currency` defaults to `VND`.
- `Order.currency` defaults to `USD`.
- Checkout copies raw product minor amounts into the order without setting order currency.
- Payment intent creation multiplies USD orders by `254` and changes the payment currency to VND.
- V39 migrated historical values using `250`.
- Bank transfer QR generation also uses `254`.
- `TenantConfigService` still contains an USD default.
- Numerous frontend pages use `formatUSD` for values that now represent VND.

A `100,000 VND` product can become a `100,000` USD-minor-unit order and then a `25,400,000 VND` payment request.

Evidence:

- `src/main/java/base/entity/Product.java:69-70`
- `src/main/java/base/entity/Order.java:107-108`
- `src/main/java/base/rest/OrderResource.java:453-592`
- `src/main/java/base/rest/PaymentIntentResource.java:72-78`
- `src/main/resources/db/migration/V39__convert_usd_to_vnd.sql`

## P0 — Keycloak synchronization uses a known permanent password

`KeycloakSyncService` runs at application startup without a development-profile guard. For every database user it creates in Keycloak, it sends the literal password `password` with `temporary=false`. The same creation path is used by manual sync and admin user creation.

Public registration calls this creation path first and then tries to replace the password. The replacement method catches and suppresses Keycloak errors, so registration can report success while the account retains the known default password.

Required changes:

- Stop assigning a shared default password in every runtime profile.
- Use a random one-time credential plus a required password-update action, or an invitation/reset flow.
- Make identity-provider failures observable and compensate local/external partial creation.
- Reset credentials for every account created through the affected path.

Evidence:

- `src/main/java/base/service/KeycloakSyncService.java:57-90`
- `src/main/java/base/service/KeycloakSyncService.java:393-429`
- `src/main/java/base/service/KeycloakSyncService.java:561-582`
- `src/main/java/base/rest/AuthResource.java:236-249`

## P1 — Voucher ownership and reuse can be bypassed

Voucher endpoints accept an arbitrary `customerId` without consistently proving that it belongs to the caller. Collecting an existing voucher can reset its used flag, allowing repeated use. Checkout also applies any valid voucher code even when the customer never collected it; the missing `CustomerVoucher` row is treated as optional.

Required changes:

- Resolve the authenticated customer on the server.
- Never trust a client-supplied customer identity for ownership.
- Make collection idempotent without resetting redemption state.
- Lock and constrain usage counters during checkout.

Evidence:

- `src/main/java/base/rest/VoucherResource.java:99-150`
- `src/main/java/base/rest/OrderResource.java:535-556`

## P1 — MFA seeds and identity-provider IDs are serialized

Resources return the `User` entity directly. `mfaSecret` and `keycloakId` are ordinary serializable fields without `@JsonIgnore`.

Consequences:

- `/users/me` can return the caller's reusable MFA seed.
- Owners listing tenant users can receive other users' MFA seeds.
- Keycloak internal identifiers are unnecessarily disclosed.

Suggested improvements:

- Replace entity responses with explicit `CurrentUserResponse` and `ManagedUserResponse` DTOs containing an allowlist of fields.
- Add `@JsonIgnore` to `mfaSecret` and `keycloakId` as defense in depth while entity responses are being removed.
- Clear local TOTP secrets after migration to Keycloak; until then, encrypt them at rest and expose them only once during enrollment.
- Add serialization tests asserting that secrets and identity-provider IDs never appear in JSON.

Evidence:

- `src/main/java/base/entity/User.java:29-30`
- `src/main/java/base/entity/User.java:64-68`
- `src/main/java/base/rest/UserResource.java:41-84`

## P1 — The application MFA setting does not enforce MFA at login

The MFA endpoints generate and verify a local TOTP secret and then toggle `User.mfaEnabled` in MariaDB. They do not create a Keycloak OTP credential, add a Keycloak required action, alter the Keycloak authentication flow, or gate API access on the flag. A user who enables this setting continues to authenticate with the same OIDC flow and password-only bearer token.

This is a security-control representation failure: the UI states that the account is protected by two-factor authentication when authentication behavior has not changed.

Required changes:

- Implement OTP enrollment and enforcement in Keycloak, which is the authentication authority.
- Derive MFA status from the identity provider rather than a disconnected application flag.
- Remove local TOTP seeds after migrating or invalidating them.

Evidence:

- `src/main/java/base/rest/AuthResource.java:76-163`
- `src/main/java/base/entity/User.java:64-68`
- `frontend/src/shared/auth/index.ts`
- `keycloak/realm-v-erp-lite.json`

## P1 — Tenant resolution fails open to the OIDC client ID

When neither the organization claim nor `tenant_code` is present, `CallerContext.tenantCode()` accepts the token's `azp` claim as a tenant and ultimately falls back to the literal tenant `default`. `azp` identifies the OAuth client, not an organization or tenant. `resolveUser()` can then auto-provision the authenticated subject into that inferred tenant.

A role-bearing Keycloak account without a valid organization claim can therefore be mapped into the client-named tenant instead of being rejected. Tenant context must be an explicit, validated claim tied to membership.

Suggested improvements:

- Remove the `azp` and `default` fallbacks and return 403 when the explicit tenant claim is absent or ambiguous.
- Validate the resolved code against an active `Tenant` and the subject's organization membership before any repository query.
- Do not auto-provision users until tenant membership has been established independently of caller-controlled claims.
- Add tests for missing, multiple, unknown, and mismatched organization/tenant claims.

Evidence:

- `src/main/java/base/security/CallerContext.java:45-71`
- `src/main/java/base/security/CallerContext.java:111-134`
- `src/main/java/base/service/UserProvisioningService.java:21-70`

## P1 — Business owners can create an eventual Keycloak administrator

`UserResource` allows `businessOwner` to create or update users and accepts an arbitrary role ID. A new user with a database admin role can later be created in Keycloak by startup/manual sync, which assigns the database role as a realm role.

Required changes:

- Define which roles each caller may assign.
- Prevent business owners from assigning admin or cross-business roles.
- Validate role existence and reject null resolution.
- Keep database and Keycloak role changes in a deliberate, auditable workflow.

Evidence:

- `src/main/java/base/rest/UserResource.java:87-125`
- `src/main/java/base/service/KeycloakSyncService.java:345-429`

## P1 — Business owners can administer users from other businesses

The business-owner user APIs list, read, update, and delete every user in the tenant. They do not apply `BusinessAccess` to the target user. In addition to the admin-role escalation above, an owner can modify or remove another business's owner/operator and can submit an operator assignment for any business in the tenant.

Required changes:

- Scope owner-managed user queries to the caller's business.
- Enforce a caller-to-target role and business assignment matrix.
- Protect self-deletion, the last owner, and cross-business reassignment.

Evidence:

- `src/main/java/base/rest/UserResource.java:69-136`
- `src/main/java/base/security/BusinessAccess.java:47-80`

## P1 — Business staff receive tenant-wide customer PII and write access

`CustomerResource` is tenant-scoped but not business-scoped. Every business owner and operator can list, read, create, edit, and delete every customer profile in a multi-business tenant, including email, phone, address, birth date, and internal notes. Order and product APIs already recognize business-level isolation, so the customer API is inconsistent with the same vendor boundary.

Suggested improvements:

- Give admins the tenant-wide view, but derive staff-visible customers from orders belonging to `BusinessAccess.requireCurrentBusinessId()` or add an explicit business/customer association.
- Return a limited business-facing customer DTO; omit birth date, internal notes, and unrelated addresses unless the workflow requires them.
- Remove operator create/update/delete permission and enforce business scope on every single-entity query.
- Record auditable access for exports or bulk reads of customer PII.

Evidence: `src/main/java/base/rest/CustomerResource.java:16-114`.

## P0 — Seller-side roles can release their own escrow

The dedicated receipt-confirmation endpoint correctly permits only the buyer, but the generic order-status endpoint permits `businessOwner` and `operator`. Those seller-side roles can move an accepted order through `FULFILLING -> DELIVERED -> COMPLETED`. Entering `COMPLETED` calls the same `completeOrder()` helper and releases escrow to the seller.

The buyer confirmation control is therefore bypassable through a second public state-transition path. A seller can unilaterally declare delivery complete and unlock its own funds.

Required changes:

- Remove seller-side `DELIVERED -> COMPLETED` authority.
- Model buyer confirmation, administrative dispute resolution, and timed auto-release as distinct audited commands.
- Enforce escrow-release authorization inside the payment/escrow service, not only in a resource method.

Evidence:

- `src/main/java/base/rest/OrderResource.java:653-693`
- `src/main/java/base/rest/OrderResource.java:696-802`
- `src/main/java/base/rest/OrderResource.java:917-938`
- `src/main/java/base/rest/OrderResource.java:964-992`

## P1 — Paid and settled order totals remain mutable

`PUT /orders/{id}` lets admin, business-owner, and operator callers change discount, VAT, shipping fee, and `finalAmountMinor` without restricting order status. It can therefore rewrite a paid, completed, or refunded order after the payment intent, transaction, escrow, commission, wallet ledger, and revenue records were calculated.

Suggested improvements:

- Permit total edits only before a payment intent is confirmed, using a locked status predicate in the same transaction.
- Snapshot the authorized order amount/currency and reject settlement when it differs from the current immutable order snapshot.
- Represent later corrections as explicit adjustment, refund, or credit-note records linked to the original order.
- Add tests proving paid, completed, cancelled, and refunded orders cannot have financial fields changed.

Evidence: `src/main/java/base/rest/OrderResource.java:822-876`.

## P1 — Chatbot endpoints violate tenant isolation

The chatbot uses global `Product.count()` and looks up orders by order number without tenant filtering. It also uses a shared API key stored directly in application configuration.

Suggested improvements:

- Authenticate chatbot requests with the existing OIDC identity or a rotated service credential mapped to one explicit tenant.
- Derive tenant context server-side and include it in every product/order query; never accept a tenant override from chatbot input.
- Replace the shared literal key with a secret-store value and add key rotation, rate limiting, and audit logging.
- Add cross-tenant tests for product statistics and duplicate order numbers.

Evidence:

- `src/main/java/base/rest/ChatbotResource.java:27-59`
- `src/main/resources/application.properties:76`

## P1 — WooCommerce webhook identity drops legitimate updates

The webhook ID for updates is derived from `orderId + status`. Two separate WooCommerce deliveries with the same status but changed address, items, or metadata resolve to the same already-processed event and the later payload is discarded.

The transport delivery ID is stored in metadata but not used to distinguish deliveries.

Suggested improvements:

- Retain `X-WC-Webhook-Delivery-ID` as immutable transport identity/audit metadata and follow WooCommerce's documented retry semantics for transport deduplication.
- Apply order changes idempotently using the external order ID plus WooCommerce modification version/timestamp or a canonical payload hash, not status alone.
- Keep an immutable event history and let a newer successful retry supersede older failed attempts in the summary projection.
- Add tests for two same-status deliveries with different address, item, and metadata payloads.

Evidence:

- `src/main/java/base/rest/WooCommerceOrderResource.java:261-276`
- `src/main/java/base/service/WooCommerceOrderService.java:85-114`

The status summary deterministically prefers the oldest failed or processing event: events are loaded newest-first and then scanned in reverse. A later successful retry can therefore leave the order displayed as failed.

## P1 — WooCommerce staff APIs are not scoped to the caller's business

WooCommerce catalog, health, order-list, and reconcile APIs accept a caller-selected `businessId` or `salesChannelId` but never call `BusinessAccess.requireAccess()`. A business owner or operator can read another business's sync/customer data and retry its webhook events when the identifiers are known.

The catalog export has a second scope defect: after resolving one business's WooCommerce channel, it queries every active product in the tenant because the product query omits `businessId`. Mapping upserts also accept arbitrary entity types and ERP IDs without proving that the target entity belongs to the tenant and business.

Suggested improvements:

- Resolve the selected channel first and call `BusinessAccess.requireAccess(channel.getBusiness().getId())` for every staff endpoint.
- Include the resolved business ID in catalog product queries and summary counts.
- Allowlist mapping entity types and verify each ERP entity belongs to the same tenant/business before upsert.
- Add database constraints where possible and negative tests using another business's channel, product, mapping, and webhook event IDs.

Evidence:

- `src/main/java/base/rest/WooCommerceCatalogResource.java:25-63`
- `src/main/java/base/service/WooCommerceCatalogService.java:32-130`
- `src/main/java/base/rest/WooCommerceOrderResource.java:42-206`
- `src/main/java/base/rest/WooCommerceOrderResource.java:328-426`
- `src/main/java/base/rest/WooCommerceIntegrationResource.java:25-31`

## P1 — Customer-facing entities expose internal cost prices

Products and orders are returned as persistence entities. `Product.costPriceMinor`, variant cost price, and `OrderItem.unitCostMinor` are ordinary serializable properties. Customers can receive seller cost and margin data through product and order responses; `GET /products/{id}` also has no security annotation.

Suggested improvements:

- Introduce customer-facing product/order DTOs that omit cost, audit, internal business, and identity fields by construction.
- Require an explicit customer/staff role on `GET /products/{id}` and apply the same publication/business rules as the list endpoint.
- Keep cost data in separate staff-only DTOs or endpoints rather than conditionally hiding entity properties.
- Add API contract tests that scan customer JSON for every known internal-cost field.

Evidence:

- `src/main/java/base/entity/Product.java:60-70`
- `src/main/java/base/entity/ProductVariant.java:41-45`
- `src/main/java/base/entity/OrderItem.java:43-53`
- `src/main/java/base/rest/ProductResource.java:37-95`
- `src/main/java/base/rest/OrderResource.java:293-315`

## P1 — Anonymous upload can exhaust local disk

`POST /images/upload` is `@PermitAll`, accepts up to 5 MB per request, trusts client-provided MIME information, and writes to the local uploads directory without authentication, quota, rate limit, or lifecycle management.

Random filenames limit traversal risk, but anonymous repeated uploads can exhaust disk.

Evidence: `src/main/java/base/rest/ImageResource.java:37-79`.

## P1 — Production debug endpoints and exception details remain enabled

The branch contains:

- Anonymous `/users/test-error`.
- `/debug-db/caller`, which exposes JWT claims and resolved database-user details.
- A catch-all exception mapper that prints stack traces and returns internal exception messages.

Evidence:

- `src/main/java/base/rest/UserResource.java:33-39`
- `src/main/java/base/rest/DebugDbResource.java:16-55`
- `src/main/java/base/rest/DebugExceptionMapper.java:7-24`

## P1 — Operator mutation permissions broadly exceed the stated role model

The project role model describes operators as read-only, but backend operator permissions include product, category, tag, and voucher writes; order status and financial-total changes; checkout/payment actions; and WooCommerce reconciliation. Frontend route guards also expose product creation/editing to operators.

This is not limited to one resource and cannot be fixed only in the UI. Define a server-side permission matrix and remove operator write access from every command endpoint, including the escrow-release path described above.

Suggested improvements:

- Define named backend permissions such as `catalog:read`, `catalog:write`, and `orders:fulfill` instead of repeating broad realm-role arrays.
- Map `operator` only to approved read permissions and narrowly defined non-financial fulfillment commands.
- Remove write routes from operator navigation after backend enforcement is corrected.
- Add one authorization-matrix test per command resource, including explicit 403 tests for operators.

Evidence includes:

- `src/main/java/base/rest/ProductResource.java:98-218`
- `src/main/java/base/rest/CustomerResource.java:16-114`
- `src/main/java/base/rest/CategoryResource.java:47-91`
- `src/main/java/base/rest/TagResource.java:47-72`
- `src/main/java/base/rest/VoucherResource.java:153-214`
- `src/main/java/base/rest/OrderResource.java:696-876`
- `src/main/java/base/rest/WooCommerceOrderResource.java:328-426`

## P2 — Hand-written frontend requests bypass the API client

Raw `fetch` remains in frontend modules, including order and cart code. These requests bypass generated SDK typing, base URL configuration, token refresh, request coalescing, and the centralized 401 interceptor.

Suggested improvements:

- Add stable backend operation IDs, regenerate the SDK, and replace order/cart requests with generated functions and React Query hooks.
- Route chatbot traffic through a typed backend endpoint or a dedicated centralized client with the same authentication/error policy.
- Remove the legacy cart module if its backend contract no longer exists.
- Add a CI check that rejects new direct `fetch(` calls outside an explicitly allowlisted transport module.

Evidence includes:

- `frontend/src/shared/api/cart.ts`
- `frontend/src/pages/orders-page.tsx`
- `frontend/src/pages/order-detail-page.tsx`
- `frontend/src/shared/ui/chat-widget.tsx`

The cart API also appears to reference removed backend cart resources and may be dead code.

## P2 — Personal settings use the Keycloak subject as a database user ID

`CallerContext.userId()` returns the Keycloak JWT subject UUID. `TenantConfigResource` passes that value to the user-settings service, but `user_settings.user_id` is a foreign key to the independently generated application `users.id`.

As a result, reads normally miss the user's settings and the first write normally violates the foreign key. These endpoints should resolve the application user with `caller.resolveUser()` and use that entity's ID.

Suggested improvements:

- Resolve the application `User` once in the resource and pass `user.getId()` to all effective/get/put settings calls.
- Make `UserSettings.findByUserId` tenant-aware or accept the resolved `User`, avoiding ID-only access patterns.
- Add a forward cleanup migration for any orphaned settings rows created in environments without the foreign key.
- Test with deliberately different Keycloak subject and application-user UUIDs.

Evidence:

- `src/main/java/base/security/CallerContext.java:74-81`
- `src/main/java/base/rest/TenantConfigResource.java:55-87`
- `src/main/resources/db/migration/V5__tenant_and_user_settings.sql:10-17`

## P2 — Inactive products remain visible and purchasable

The customer product list does not filter `active`, and checkout resolves a product by tenant and ID without checking publication state. A hidden or deactivated product can therefore still be ordered at its stored price when selected directly or retained in stale client state.

Suggested improvements:

- Add `active = true` to customer catalog queries while preserving an explicit staff/admin inventory view.
- Revalidate product publication state, current price, and stock under the checkout transaction immediately before reservation.
- Remove or flag inactive items in existing carts and return a domain-specific conflict response.
- Add tests for direct-ID checkout and deactivation between cart load and checkout.

Evidence:

- `src/main/java/base/rest/ProductResource.java:37-80`
- `src/main/java/base/rest/OrderResource.java:467-516`

## P2 — CI runs only a small subset of the tests

The repository contains roughly 150 test methods, but `payment-ci.yml` runs only four test classes, approximately 16 tests:

- `WalletServiceTest`
- `PaymentTenantIsolationTest`
- `PaymentModuleInvariantTest`
- `MigrationValidationTest`

CI does not run the required frontend `npm run build` gate. Most payment, webhook, refund, payout, security, load, and concurrency tests are excluded.

Evidence: `.github/workflows/payment-ci.yml:43-44`.

## P2 — OpenAPI operation IDs collide

Build output reports duplicate operation IDs:

- `getStats` in `OrderResource` and `ChatbotResource`
- `getOrderStatus` in `WooCommerceOrderResource` and `ChatbotResource`

The generated SDK works around this with names such as `getStats2` and `getOrderStatus2`, which makes the client API unstable and order-dependent.

## P2 — Java configuration disagrees with project and CI requirements

`build.gradle` targets Java 24, while project instructions and CI provision Java 25. This is an unexplained compatibility regression from main.

Evidence: `build.gradle:67-68` and `.github/workflows/payment-ci.yml:18-22`.

## P2 — Developer instructions are materially stale

`AGENTS.md` says Docker starts PostgreSQL on port 5433 and that the backend test directory is empty. The reviewed branch actually uses MariaDB on port 3307 and contains 147 tests across 28 Java files. Its architecture inventory also omits the payment, wallet, payout, revenue, notification, and WooCommerce modules that dominate this branch.

This drift makes the prescribed startup/debug workflow unreliable and hides the real review surface from contributors.

Suggested improvements:

- Update `AGENTS.md` to name MariaDB, port 3307, the current Java target decision, and the actual verification gates.
- Generate test/module inventory summaries in CI or remove volatile counts from documentation.
- Add a lightweight documentation check that validates documented compose service names and ports against `docker-compose.yml`.
- Review developer instructions whenever infrastructure or major modules change.

Evidence:

- `AGENTS.md:5-36`
- `docker-compose.yml:1-42`
- `src/test/java`

## P2 — Repository hygiene is poor

Committed generated or local artifacts include:

- `ecommerce-chatbot/python-installer.exe` at approximately 26 MB
- Python `__pycache__` directories and `.pyc` files
- Multiple Python-version bytecode variants
- One-off `test_*.py` scripts
- Logs and error output files
- `frontend/openapi-ts-error-*.log`
- `frontend/vite-dev.log`
- `token.json`
- `error.html` and `error_output.html`
- Uploaded image files and duplicates
- `ecommerce-chatbot/.env`

The reviewed diff contained 391 files, 47,542 additions, and 1,544 deletions across 186 commits. There were 51 merge commits and many vague commit subjects. The scale and noise materially obscured integration defects.

## P3 — Singular and plural business routes coexist

Both `/business` and `/businesses` frontend routes exist, and navigation still points to `/business`. The documented canonical contract is `/businesses`.

Evidence:

- `frontend/src/app/nav-registry.ts:65`
- `frontend/src/app/layouts/app-layout.tsx:549`

---

# Payment and ledger deep audit

## Financial flow map

| Flow | Intended balance movement | Actual assessment |
|---|---|---|
| Top-up | User available `+gross` after verified receipt | Unverified and self-completable |
| Wallet payment | Buyer available `-gross`; seller pending `+net`; platform available `+commission` | Conserved on success; refund is missing |
| Gateway payment | Seller pending `+net`; platform available `+commission`; external cash received | Payment authenticity is broken |
| Escrow release | Seller pending `-net`; seller available `+net` | Correct in the normal serialized path |
| Gateway refund | Seller pending `-net`; platform available `-commission`; external refund | No provider refund occurs; a fabricated reference is marked complete |
| Wallet refund | Seller/platform reversal and buyer wallet credit | Not implemented |
| Payout | Seller available `-amount`; bank transfer confirmed | Mock transfer can still mark `PAID` and debit ledger |
| Reconciliation | Independently rebuild balances from immutable entries | Starts from copied wallet state and often proves nothing |

## P0 — Wallet top-up is unrestricted money creation

`WalletResource.topUpMyWallet()` creates a pending top-up using a hardcoded receiving account. `completeTopUp()` is available to every class-level role (`admin`, `operator`, and `businessOwner`) and allows the caller to complete any top-up ID. A business owner or operator can therefore create and self-complete unverified funds.

The class-level role annotation excludes `customer`, so the customer-facing `/user-wallet` simulation UI cannot normally exploit this endpoint; it is instead a broken 403 path. The critical money-creation defect remains reachable by staff roles.

Problems:

- No payment-provider callback or bank reconciliation.
- No top-up ownership check.
- No tenant filter on top-up lookup.
- No privileged completion role.
- No development-profile restriction.
- Amount and currency are not validated before persistence.
- The frontend explicitly calls completion from “simulate transferred” UI.

Suggested improvements:

- Remove the caller-accessible completion endpoint from production; complete top-ups only from a verified provider callback or reconciled bank statement.
- Scope every top-up lookup by tenant and wallet owner, lock the row during transition, and allow only `PENDING -> COMPLETED` once.
- Validate supported currency, positive amount, maximum amount, receiving account, and a unique provider transaction reference before crediting.
- Put simulation endpoints behind a backend build-profile guard with isolated demo wallets, and align the customer UI with the permitted API role.

Evidence:

- `src/main/java/base/rest/WalletResource.java:251-291`
- `src/main/java/base/service/WalletService.java:139-161`
- `frontend/src/pages/user-wallet-page.tsx:107-165`

## P0 — Mock payments are enabled in all backend profiles

`MockPaymentResource` has no build-profile guard and allows `admin`, `businessOwner`, `operator`, and `customer`. A customer with access to their intent can call completion and settle the order.

Frontend environment flags are not security controls.

Evidence: `src/main/java/base/rest/MockPaymentResource.java:22-106`.

## P0 — Webhook authenticity is structurally broken

The signature is calculated over:

```text
paymentIntentId : gatewayTransactionId : amountMinor
```

It omits:

- payment status
- currency
- gateway identity
- event identifier/timestamp
- raw request body

The initiate endpoint returns this signature in `mockSecret`/metadata. A customer can keep the signed fields unchanged, change status to `SUCCESS`, and post the public webhook.

Additional defects:

- One shared hardcoded mock secret signs all gateways.
- Unknown gateway names fall back to mock behavior.
- Callback amount and currency are not compared with the stored intent.
- Any non-success status is treated as permanent failure.
- Signature comparison is not constant-time.
- Webhook schema is reconstructed with `String.format` rather than verifying the exact raw bytes received.
- Successful webhooks can settle an expired intent because `markSucceeded()` does not call the expiry check used by confirmation.

Suggested improvements:

- Implement a separate verifier per supported provider and reject unknown gateway names.
- Verify the exact raw body with the provider's secret and constant-time comparison; never return signing material to API clients.
- Bind status, amount, currency, gateway, intent/order reference, provider event ID, and timestamp to verification and stored-state comparison.
- Enforce a replay window and unique provider event/transaction constraints, then reject or quarantine expired-intent captures.
- Preserve raw immutable webhook events and process them through an idempotent state machine with retryable failure states.

Evidence:

- `src/main/java/base/service/PaymentGatewayService.java:23`
- `src/main/java/base/service/PaymentGatewayService.java:96-109`
- `src/main/java/base/service/PaymentGatewayService.java:176-178`
- `src/main/java/base/rest/PaymentWebhookResource.java:45-128`

## P0 — Multiple successful intents can double-charge one order

The database permits unlimited intents per order as long as their idempotency keys differ. Two intents can be created while an order is awaiting payment.

After the first settles, the second can still settle because `PaymentService` accepts `PAID_ESCROW`. If escrow already exists, it returns the existing escrow while the second intent and payment transaction remain `SUCCEEDED`.

Outcome:

- The provider can charge twice.
- Two payment records report success.
- Seller escrow and commission are recorded only once.
- No automatic refund is created for the duplicate charge.

Evidence:

- `src/main/java/base/service/PaymentIntentService.java:30-64`
- `src/main/java/base/service/PaymentIntentService.java:108-122`
- `src/main/java/base/service/PaymentService.java:65-84`

Required controls:

- Lock the order during successful settlement.
- Store an order payment state independent of individual attempts.
- Enforce one captured/successful payment per order in the database.
- Detect and automatically reverse unexpected duplicate captures.

## P1 — Gateway transaction reuse is not bound to an intent

When a gateway transaction already exists, `appendGatewayTransaction()` returns without proving that it belongs to the current payment intent or has the same amount, currency, gateway, and result.

The caller then continues to mark the current intent successful and settle it.

Evidence: `src/main/java/base/service/PaymentIntentService.java:182-210`.

## P1 — Idempotency keys are not bound to request payloads

Payment-intent creation returns an existing record based only on `(tenant, idempotencyKey)`. It does not compare:

- order ID
- customer ID
- amount
- currency
- caller identity

Reusing a key for a different order silently returns the previous intent. The resource does not re-run access control against the returned existing intent before serializing it.

Wallet reference idempotency has the same semantic problem: a duplicate reference with a changed amount returns the previous transaction rather than rejecting a payload mismatch.

Evidence:

- `src/main/java/base/service/PaymentIntentService.java:42-49`
- `src/main/java/base/service/WalletService.java:426-438`

Idempotency should store and compare a canonical request fingerprint.

## P1 — Failed webhook idempotency records cannot recover cleanly

`checkAndCreate()` returns an existing `FAILED` record and allows processing to continue. If that retry succeeds, `complete()` refuses to update the record because it only updates `PROCESSING` records. The operation succeeds while the idempotency record remains permanently failed.

`PROCESSING` records also have no lease, expiry, or takeover mechanism.

Evidence: `src/main/java/base/service/IdempotencyService.java:16-63`.

## P1 — Wallet-paid orders cannot be refunded

Wallet checkout directly debits the buyer wallet and creates escrow, but it creates no `PaymentIntent` or `PaymentTransaction`.

Owner decline calls `PaymentRefundService.refundOrder()`, which requires a succeeded payment intent. The refund throws, rolling back the decline.

The missing wallet refund should atomically:

1. Debit/refund seller pending escrow.
2. Reverse platform commission.
3. Credit the exact originating buyer wallet.
4. Record linked reversal journal entries.
5. Mark refund and order state durably.

Evidence:

- `src/main/java/base/service/PaymentService.java:129-200`
- `src/main/java/base/service/OrderFulfillmentService.java:70-78`
- `src/main/java/base/service/PaymentRefundService.java:38-50`

## P1 — Refunds after escrow release do not recover seller funds

`PaymentRefundService` obtains a refund reference and then calls `refundOrderEscrow()`, which only selects `HELD` escrow records. If escrow is already `RELEASED`, seller funds are not debited, yet the refund is still recorded complete. With a real provider adapter, the buyer could be refunded externally while the seller retains the released funds.

Current public status transitions reduce reachability, but the service itself does not enforce the accounting invariant and is unsafe for future admin/dispute/chargeback flows.

Suggested improvements:

- Lock the order, escrow, seller wallet, and original payment before deciding which reversal path applies.
- For `RELEASED` escrow, debit seller available funds; if insufficient, create an explicit seller receivable/negative reserve and block payout rather than silently succeeding.
- Record every reversal against the original escrow/journal entry and require the full refund amount to be accounted for before completion.
- Add tests for held, released, partially available, already-refunded, and concurrent payout/refund cases.

Evidence:

- `src/main/java/base/service/PaymentRefundService.java:53-92`
- `src/main/java/base/service/EscrowService.java:97-112`

## P0 — Gateway refunds are fabricated but recorded as completed

The current refund adapter does not call Stripe, VNPay, a bank, or any other provider. For every gateway it returns a deterministic `REF_<UUID>` string. `PaymentRefundService` treats that string as proof of refund, reverses internal seller/platform balances where possible, and persists `PaymentRefund.status = COMPLETED`.

For an externally paid order, the buyer receives no money even though the API and database say the refund completed. This is the refund equivalent of the mock-payout defect and is direct customer loss if used with real payments.

Suggested improvements:

- Disable gateway refunds outside a demo profile until a real provider adapter is configured.
- Replace the string-returning adapter contract with a structured result containing provider state, reference, timestamps, and retryability.
- Persist `REQUESTED` before provider submission and advance to `COMPLETED` only after provider confirmation and balanced ledger posting.
- Reconcile provider refund status independently and alert on provider/local mismatches.

Evidence:

- `src/main/java/base/service/PaymentGatewayService.java:112-127`
- `src/main/java/base/service/PaymentRefundService.java:57-93`

## P1 — A real refund adapter would run before durable accounting

There is no external refund side effect in the current adapter. However, the orchestration calls the adapter before seller escrow and platform commission are reversed and before the refund row is committed. Replacing the mock with a real provider without redesigning this flow would immediately create a transaction-boundary defect.

If the provider succeeds but any subsequent database operation fails, the database transaction rolls back while the external refund remains. Provider idempotency prevents a second refund, but does not repair local state.

Required design:

```text
REQUESTED -> PROVIDER_SUBMITTED -> PROVIDER_CONFIRMED -> LEDGER_POSTED
```

Use a durable outbox/state machine and a reconciliation worker.

## P1 — Payouts are not reserved

A flagged payout snapshots the entire available balance but does not move it into a reserved balance. Before admin approval, the seller wallet can change or be spent through wallet checkout.

Approval then calls the bank adapter before attempting the wallet debit. If balance is insufficient, the debit and payout row roll back after the provider may already have paid.

Evidence:

- `src/main/java/base/service/PayoutService.java:196-219`
- `src/main/java/base/service/PayoutService.java:137-161`

## P1 — Payout lock order can deadlock after provider submission

Bank-detail update locks wallet then modifies flagged payouts. Payout approval locks payout then calls the provider and locks wallet.

Opposite lock order creates this cycle:

```text
bank edit: wallet -> payout
payout approval: payout -> external provider -> wallet
```

If the approval transaction is aborted as the deadlock victim, the provider may have paid while the ledger and payout state roll back.

Evidence:

- `src/main/java/base/service/WalletService.java:164-197`
- `src/main/java/base/service/PayoutService.java:100-112`

## P1 — Default bank transfers are fake but still consume ledger balance

`payout.bank-transfer.mode` defaults to `mock`. The mock returns a deterministic reference and performs no transfer. The payout is nevertheless marked `PAID` and the wallet is debited.

The scheduler is active by default. First payouts are flagged, but an admin approval still records a fake payment and destroys spendable ledger balance.

Evidence:

- `src/main/java/base/service/BankTransferService.java:13-28`
- `src/main/java/base/service/PayoutScheduler.java:24-56`
- `src/main/resources/application.properties:82-89`

Mock payouts must use an isolated demo profile and demo data store, or stop before ledger posting.

## P1 — VietQR directs money outside the assumed settlement model

For bank records that pass the code lookup, mock initiation can generate a real VietQR payload pointing to the seller's bank account for the full order amount. Internal settlement then credits 95% to seller escrow and 5% to the platform.

If the QR is actually scanned:

- Seller receives 100% directly.
- Platform receives no actual commission.
- Seller additionally receives a 95% internal wallet claim that can later be paid out.

Suggested improvements:

- Choose one settlement model: collect into a platform-controlled account and allocate internally, or treat seller-direct QR as externally settled without creating a withdrawable seller wallet credit.
- Give each transfer a unique reference and verify the receiving account, amount, currency, and bank transaction before marking payment successful.
- Do not expose a real payable QR from mock initiation; use visibly non-payable demo data in isolated environments.
- Reconcile bank receipts to orders and commissions before escrow or payout becomes available.

Evidence: `src/main/java/base/service/PaymentGatewayService.java:48-59`.

## P1 — VietQR is public and incompatible with normal configured-bank data

`POST /vietqr/generate` has no authentication annotation, request-size control, quota, or rate limit and performs in-process QR rendering on every call. It can be used as an anonymous CPU/memory amplification endpoint.

The normal seller checkout path is also internally inconsistent:

- Bank settings store `Bank.name` such as `MB Bank`, `Vietcombank`, or `Techcombank` in `Wallet.bankName`.
- `VietQRService` expects short lookup keys such as `MB`, `VCB`, or `TCB`.
- Some names happen to match (`ACB`, `BIDV`, `MSB`), but many seeded banks fail as unsupported.
- `generateQrBase64()` already returns a complete `data:image/png;base64,...` URL, while both checkout and wallet-top-up UIs prepend that prefix again, producing an invalid image source.

Suggested improvements:

- Pass the selected bank's stored BIN (or one canonical supported code) to `VietQRService`; do not persist a display name as an integration key.
- Return either raw Base64 or a complete data URL under an accurately named field, then consume it exactly once in both UIs.
- Require authentication, bound every string and amount, cap request/body size, and add per-user/IP rate limits.
- Cache deterministic render results or generate QR images client-side after the backend returns a validated payment payload.
- Add parameterized contract tests for every seeded bank and both frontend render paths.

Evidence:

- `src/main/java/base/rest/VietQRResource.java:15-52`
- `src/main/java/base/service/VietQRService.java:27-112`
- `src/main/java/base/service/WalletService.java:164-186`
- `src/main/resources/db/migration/V41__create_bank_catalog.sql:12-27`
- `frontend/src/pages/user-wallet-page.tsx:152`
- `frontend/src/pages/cart-checkout-page.tsx:1437`

## P1 — Reconciliation is not independent and usually proves nothing

Every wallet mutation immediately writes a checkpoint copied from the post-mutation wallet balance. Reconciliation starts from the latest checkpoint and replays only later transactions. Normally there are no later transactions, so it compares the wallet to a copy of itself.

Additional failures:

- Reconciliation classifies pending transactions using transaction type `PENDING`, which is not used.
- Real escrow types are `ESCROW_HOLD`, `ESCROW_RELEASE`, and `ESCROW_REFUND`.
- Nothing invokes `ReconciliationService`; it is dead code.
- The alternate `createCheckpoint()` rebuild path never assigns the required checkpoint UUID and would fail on persist if invoked.
- Checkpoints are mutable and deletable.
- A bad wallet mutation can be copied into a checkpoint and subsequently reported healthy.

Suggested improvements:

- Rebuild balances from the immutable transaction origin or balanced journal entries, not from the current wallet projection.
- Create checkpoints periodically from a verified prefix, include a hash/sequence boundary, and make them append-only.
- Schedule reconciliation independently of write transactions and quarantine a wallet on any mismatch.
- Fix or remove the unused checkpoint rebuild path and test deliberate wallet, entry, sequence, and checkpoint corruption.

Evidence:

- `src/main/java/base/service/WalletService.java:200-342`
- `src/main/java/base/service/LedgerCheckpointService.java:25-118`
- `src/main/java/base/service/ReconciliationService.java:42-75`

## P1 — The ledger is not double-entry

Each `WalletTransaction` changes one wallet. There is no journal/batch object connecting all legs of a payment or proving that debits equal credits.

Missing accounting accounts include:

- gateway cash/receivable
- platform clearing
- refund payable
- payout clearing
- chargeback/dispute liability

`ESCROW_RELEASE` is represented by one `CREDIT` entry that semantically decreases pending and increases available. Generic credit-minus-debit queries cannot interpret the ledger without special transaction-type logic.

The append-only behavior is useful, but this is a wallet event log, not a general ledger.

## P1 — Database financial invariants are missing

The schema does not enforce:

- unique `(tenant_id, wallet_id, sequence_no)`
- unique ledger business reference
- positive transaction, payment, escrow, refund, payout, and top-up amounts
- nonnegative available and pending wallet balances
- allowed owner, credit/debit, transaction, or state values
- one escrow record per order and seller wallet
- one successful/captured payment per order
- foreign keys from intent, escrow, and refund order IDs to orders
- foreign keys from customer IDs in payment intents to customers
- checkpoint linkage to the referenced wallet transaction
- unique checkpoint sequence per wallet

Append-only triggers block `UPDATE` and `DELETE`, but arbitrary invalid `INSERT` remains possible.

Evidence:

- `src/main/resources/db/migration/V18__add_payment_and_wallet_tables.sql`
- `src/main/resources/db/migration/V20__optimize_ledger_indexes.sql`

## P1 — Escrow creation lacks a database uniqueness guarantee

`EscrowService` checks for an existing record before wallet mutation, but there is no unique constraint on tenant/order/seller-wallet. Concurrent creation can persist duplicate escrow records even when wallet reference idempotency prevents the second balance credit.

Duplicate records later distort commission calculation and refund/release reporting.

Evidence: `src/main/java/base/service/EscrowService.java:37-68`.

## P1 — Currency migration V39 is unsafe and incomplete

V39 has several independent financial defects:

1. Uses multiplier `250`, while runtime payment conversion uses `254`.
2. Checks voucher type `FLAT`, while code and seed data use `FIXED_AMOUNT`.
3. Converts payment intents but omits payment transactions and payment refunds.
4. Omits historical product-variant prices even though V22 gave variants their own USD currency/value columns; checkout then reads the unconverted variant price without consulting currency.
5. Omits customer lifetime spend, carts, and cart items, leaving mixed-currency aggregate and cart data.
6. Merges USD ledger entries into an existing VND wallet without renumbering sequence numbers.
7. Merges checkpoints from separate histories into one wallet without rebuilding them.
8. Drops append-only triggers during the migration.
9. Performs a multi-step conversion on MariaDB, where DDL is not transactionally rolled back.
10. Can apply the multiplier twice after an interrupted run. For example, wallet transactions are multiplied before wallet currency changes; a retry can select the still-USD wallet and multiply those transactions again.

Suggested improvements:

- Do not edit an already-applied V39; ship a new forward-only corrective migration.
- Run preflight queries and abort on mixed/ambiguous histories, overflow risk, duplicate sequence ranges, or unexpected voucher types.
- Convert all related rows from one documented exchange-rate snapshot, rebuild merged sequences/checkpoints, and store migration markers so reruns are safe.
- Recreate and verify append-only triggers even on failure, then run post-migration balance, currency, row-count, and referential-integrity assertions.
- Test the migration against populated pre-V39 backups, existing VND/USD wallet pairs, and intentionally interrupted stages before deployment.

Evidence: `src/main/resources/db/migration/V39__convert_usd_to_vnd.sql`.

## P2 — Monetary overflow is unchecked

`WalletService` mutates balances using ordinary `long` addition and subtraction. Credits can wrap a positive balance negative. Checkout subtotal, percentage, allocation, commission, and FX multiplication contain similar unchecked operations.

`Money.fromDecimal()` uses `BigDecimal.longValue()`, which silently truncates out-of-range values rather than rejecting them.

Required changes:

- Use `Math.addExact`, `subtractExact`, and `multiplyExact`.
- Use `BigDecimal.longValueExact()` after scale/range validation.
- Define per-operation and per-wallet maximums.
- Enforce corresponding database checks.

Evidence:

- `src/main/java/base/service/WalletService.java:200-342`
- `src/main/java/base/model/Money.java:28-37`

## P2 — First-time wallet creation is race-prone

`getOrCreateWallet()` performs find-then-insert. Concurrent first creation for the same owner/currency can cause one request to fail with a unique-constraint exception rather than loading and returning the winning row.

This is especially relevant during concurrent settlement into a newly provisioned seller or platform wallet.

Evidence: `src/main/java/base/service/WalletService.java:39-67`.

## P2 — Checkpoint storage grows once per ledger mutation

Every transaction writes a complete checkpoint, doubling the number of financial rows without providing periodic checkpoint compression. The design carries checkpoint complexity and storage cost while reconciliation still cannot independently validate history.

## P2 — Correction linkage is unused

`WalletTransaction.correctionOfTransactionId` exists but no service populates or validates it. Adjustments therefore have no formal reversal relationship to the entry they correct.

Evidence: `src/main/java/base/entity/WalletTransaction.java:37-38`.

## P2 — Bank details and payout exports expose full account numbers

Wallet and payout DTOs return full bank account numbers, and CSV export includes them. Access control is mostly tenant/role scoped, but the data should still be masked in ordinary responses and protected as sensitive financial information.

The CSV escaping handles delimiters and quotes but does not neutralize cells beginning with `=`, `+`, `-`, or `@`. A business owner can place a spreadsheet formula in the account-holder/number fields; an administrator opening the payout export in a spreadsheet may execute it. Validate bank fields and apply formula-injection-safe CSV encoding.

Suggested improvements:

- Mask account numbers in ordinary wallet/payout responses and expose full values only through a separately authorized, audited export flow.
- Validate account numbers against the selected bank's allowed format and normalize account-holder names to an approved character set.
- Prefix spreadsheet-formula-leading cells with a safe apostrophe (or use a library with CSV-injection protection) before standard CSV quoting.
- Encrypt full bank details at rest, define retention/access logging, and add malicious-cell export tests.

Evidence:

- `src/main/java/base/rest/WalletResource.java:97-133`
- `src/main/java/base/rest/PayoutResource.java:61-93`
- `src/main/java/base/service/WalletService.java:164-186`
- `src/main/java/base/service/PayoutService.java:187-193`
- `src/main/java/base/service/PayoutService.java:238-288`

## P2 — Revenue reporting disagrees with settlement semantics

Revenue reporting filters escrow by `order.currency`. When orders remain USD while payment and escrow are VND, commission resolves to zero.

Refunds are included in report totals even when the order was never recognized as completed revenue; only commission and inventory-cost reversal are gated on revenue recognition. This can produce negative net revenue for a declined order whose sale was never counted.

Evidence:

- `src/main/java/base/service/RevenueService.java:336-375`
- `src/main/java/base/service/RevenueService.java:388-400`

---

# Test and verification assessment

## Verification performed

- Frontend `npm ci`: completed in the original review snapshot.
- A fresh `npm run build` from an archive of the fetched remote revision passed.
- `./gradlew compileJava compileTestJava --offline --no-daemon` from that exact revision passed on Java 25.
- The full backend suite could not establish a pass because MariaDB was unavailable in the review environment.
- A fresh `./gradlew test --offline --no-daemon` reproduced the reported result exactly: 147 tests considered, 4 Quarkus boot failures, and 130 skipped after database connection refusal.
- Static source count: exactly 147 `@Test` annotations and no parameterized, repeated, factory, or template tests.
- Duplicate OpenAPI operation IDs were emitted during the build.

No claim is made that the backend test suite passes against a running database.

## Positive test coverage

The tests demonstrate awareness of:

- pessimistic wallet locking
- concurrent debit/overdraft prevention
- tenant-scoped wallet mutation
- basic intent state transitions
- same-payload idempotency
- repeated escrow release/refund
- payout repeat protection
- append-only update/delete triggers
- ledger projection in standard flows
- inventory and last-item concurrency

## Missing adversarial tests

The following are not adequately covered:

- seller-side `DELIVERED -> COMPLETED` releasing escrow without buyer confirmation
- post-payment mutation of order totals and divergence from intent/ledger records
- two distinct intents succeeding for one order
- idempotency key reused with a different order or amount
- gateway transaction reused across intents
- status tampering while preserving a valid webhook signature
- webhook currency/gateway mismatch
- unknown webhook statuses
- successful webhook settlement after intent expiry
- stale `PROCESSING` and retry-after-`FAILED` idempotency
- unauthorized and cross-tenant top-up completion
- wallet-payment refund
- fabricated gateway refund reference recorded as completed
- refund after escrow release
- external provider success followed by local rollback
- payout balance changing while flagged
- bank edit racing payout approval
- mock payout incorrectly consuming real ledger balance
- predictable Keycloak password creation and failed registration-password replacement
- actual Keycloak MFA enforcement after enrollment
- missing tenant/organization claim fail-closed behavior
- cross-business user, customer, WooCommerce channel, and catalog access
- customer DTOs excluding user secrets and product/order cost fields
- user-settings persistence with distinct Keycloak and application UUIDs
- VietQR generation for every seeded bank and the frontend image-source contract
- payout CSV formula injection
- intentional wallet/checkpoint corruption followed by reconciliation
- duplicate wallet sequence numbers
- duplicate escrow records
- interrupted and rerun V39 migration
- USD/VND wallet merge with preexisting histories, variant prices, customer totals, and cart data
- monetary overflow and maximum limits
- concurrent first-time wallet creation

Some concurrency tests catch exceptions and compute expected balances only from operations that happened to succeed. Those tests can pass while the system experiences excessive deadlocks or transaction failures.

`MigrationValidationTest` checks that tables and columns exist, but does not validate constraints, transformed values, exchange consistency, sequence rebuilding, or recovery after partial migration.

---

# What the interns did well

The following work should be preserved and strengthened:

- Pessimistic wallet locking prevents straightforward concurrent overdrafts.
- Wallet mutations validate positive amounts and same-currency operation in normal service paths.
- Most payment and wallet lookups include tenant scoping.
- Stable business references make repeated escrow and payout mutations idempotent in standard paths.
- Settlement database mutations are generally grouped in one transaction.
- Append-only database triggers are a useful foundational control.
- Refund logic recognizes that platform commission must be reversed.
- Escrow release/refund methods are idempotent in their normal `HELD` state path, although caller authorization and released-fund recovery need redesign.
- Payout risk checks consider first payout, bank changes, large balances, disputes, and failures.
- The test suite shows meaningful intent around concurrency, tenant isolation, and financial invariants.

The central weakness is that the implementation was designed from wallet methods outward. Financial systems need to start from threat models, provider failure modes, accounting journals, recovery states, and database invariants, then implement service methods inside those boundaries.

---

# Prioritized remediation plan

## Phase 0 — Containment

1. Rotate and purge committed credentials.
2. Remove the shared Keycloak password, force-reset every affected account, and require one-time enrollment credentials.
3. Remove seller/operator authority to complete orders and release escrow.
4. Disable top-up completion outside an isolated demo profile.
5. Disable all mock payment completion, mock webhooks, and fabricated refunds outside that profile.
6. Disable automated payouts while the bank adapter is mock.
7. Remove production debug resources and internal exception details.
8. Authenticate and rate-limit upload and VietQR rendering endpoints.

## Phase 1 — Canonical financial model

1. Select one canonical operating currency, presently VND.
2. Set order currency from validated product/cart currency during checkout.
3. Remove ad hoc `250` and `254` multipliers.
4. Define explicit FX quotes if multi-currency is actually required.
5. Add exact arithmetic and monetary limits.
6. Repair V39 with a forward-only corrective migration covering transactions, refunds, variants, customer totals, carts, sequence rebuilding, and validation queries.

## Phase 2 — Payment state machine

1. Add order-level payment state and lock the order on capture.
2. Enforce one successful capture per order.
3. Bind idempotency keys to request fingerprints.
4. Verify gateway-specific callbacks against exact raw request bytes.
5. Sign/validate status, amount, currency, gateway, event ID, and timestamp.
6. Reject duplicate gateway transactions bound to another intent.
7. Add automatic duplicate-capture reversal.
8. Reject or explicitly reconcile successful callbacks received after intent expiry.

## Phase 3 — Ledger redesign

1. Introduce a journal/batch entity.
2. Store balanced debit and credit entries for every operation.
3. Model available, pending, clearing, external cash, refund payable, and payout clearing accounts explicitly.
4. Add database constraints for amounts, states, unique sequences, references, and balance rules.
5. Link adjustments to corrected transactions.
6. Rebuild reconciliation from immutable entries rather than wallet/checkpoint state.
7. Alert and quarantine wallets when reconciliation fails.

## Phase 4 — Refund and payout safety

1. Implement wallet-origin refunds that return money to the originating wallet.
2. Implement real provider refund adapters and never mark a fabricated reference complete.
3. Support released-escrow reversals and chargeback liabilities.
4. Reserve payout balances when a request is created.
5. Use durable provider-operation states and an outbox worker.
6. Reconcile provider state independently.
7. Standardize lock ordering before any external call.
8. Never report `REFUNDED` or `PAID` until provider confirmation and ledger posting are both durable.

## Phase 5 — Authorization and API hardening

1. Replace entity responses with DTOs.
2. Hide MFA secrets, Keycloak IDs, product/order cost fields, and full bank account details.
3. Implement MFA in Keycloak and derive enrollment status from the identity provider.
4. Require an explicit validated tenant/organization claim and fail closed when it is absent.
5. Restrict role assignment and user/customer/WooCommerce access by caller role and business scope.
6. Resolve customer and voucher ownership exclusively from authenticated identity.
7. Make paid order totals immutable and separate buyer confirmation from seller fulfillment commands.
8. Repair chatbot tenant filtering.
9. Remove operator mutation access where operators are read-only.
10. Remove hand-written frontend fetch calls.

## Phase 6 — Test and delivery controls

1. Run the entire backend suite in CI.
2. Run frontend `npm run build` in CI.
3. Add the missing adversarial tests listed above.
4. Test migrations on populated pre-V39 snapshots and interrupted-state fixtures.
5. Add secret scanning and generated-artifact checks.
6. Fix OpenAPI operation ID collisions.
7. Align Java versions across project documentation, Gradle, and CI.
8. Update startup/architecture documentation for MariaDB and the actual module/test inventory.
9. Split future work into reviewable feature branches with clear commit subjects.

---

# Merge recommendation

**Do not approve `origin/develop` for merge or deployment in its reviewed state.**

The branch is suitable as a prototype and a source of reusable service-level ideas. It requires immediate identity-credential remediation, closure of the escrow and business-authorization bypasses, a canonical currency repair, payment/refund/payout state-machine redesign, ledger constraint work, and expanded CI before it should handle any real financial value.

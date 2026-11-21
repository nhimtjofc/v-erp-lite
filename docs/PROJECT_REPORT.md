# BÁO CÁO TỔNG HỢP DỰ ÁN V-ERP LITE

> Phiên bản báo cáo: 1.0 — Ngày: 2026-08-06
> Nguồn: `docs/ARCHITECTURE_ARC42.md`, `SHOWCASE_GUIDE.md`, `AGENTS.md`, `ecommerce-chatbot/README.md`, `docker-compose.yml`, lịch sử git, mã nguồn.

---

## MỤC LỤC

1. [Mục tiêu dự án](#1-mục-tiêu-dự-án)
2. [Công nghệ sử dụng](#2-công-nghệ-sử-dụng)
3. [Trách nhiệm dự án đảm nhận](#3-trách-nhiệm-dự-án-đảm-nhận)
4. [Thành tựu](#4-thành-tựu)
5. [Kiến trúc](#5-kiến-trúc)
6. [Các tính năng chính](#6-các-tính-năng-chính)
7. [Khó khăn và cách giải quyết](#7-khó-khăn-và-cách-giải-quyết)
8. [Phụ lục](#8-phụ-lục)

---

## 1. Mục tiêu dự án

**V-ERP Lite** là nền tảng **ERP tinh gọn (Lite)** dành cho doanh nghiệp vừa & nhỏ (SMB), được mở rộng thành hệ sinh thái **thương mại điện tử đa người bán (Multi-vendor Marketplace)** với bán hàng đa kênh và trợ lý mua sắm AI.

Hệ thống được thiết kế theo mô hình **Multi-tenant** (đa khách thuê): nhiều tổ chức/doanh nghiệp cùng chạy trên một hạ tầng dùng chung nhưng dữ liệu được cô lập tuyệt đối.

### 1.1 Bốn mục tiêu kinh doanh cốt lõi

| Mã | Mục tiêu | Mô tả |
|----|----------|-------|
| **G-1** | Tinh gọn hóa vận hành bán hàng & tồn kho | Quản lý sản phẩm, biến thể, phân loại, voucher, luồng đơn hàng khép kín từ đặt hàng → hoàn tất. |
| **G-2** | Minh bạch dòng tiền đa người bán | Giữ tiền trung gian (Escrow), chỉ giải phóng cho người bán khi người mua nhận hàng, tự động tính hoa hồng sàn và thuế. |
| **G-3** | Tự động hóa bán hàng đa kênh | Đồng bộ 2 chiều danh mục & trạng thái đơn giữa V-ERP và WooCommerce qua Webhook chuẩn hóa. |
| **G-4** | Trải nghiệm mua sắm thông minh & an toàn với AI | Trợ lý AI tra cứu đơn hàng, tìm kiếm sản phẩm ngữ nghĩa (Semantic Search trên Vector DB) mà **không rò rỉ PII**. |

### 1.2 Bốn trụ cột chất lượng

1. **Bảo mật & cô lập dữ liệu**: OIDC RS256 qua Keycloak; RBAC 4 vai trò; cô lập Tenant/Business mức CSDL; Hardened AI zero-PII-leakage.
2. **Toàn vẹn tài chính**: Tiền tệ Minor Units (BIGINT VND); Pessimistic Lock chống race-condition ví; Sổ cái append-only + checkpoint kiểm toán; Idempotency cho mọi giao dịch thanh toán.
3. **Hiệu năng & phản hồi**: Khởi động Quarkus < 3s; độ trễ API p95 < 200ms; TanStack Query invalidation & caching.
4. **Mở rộng & tích hợp**: OpenAPI-First tự sinh hey-api SDK; Webhook HMAC SHA-256 + khử trùng lặp; chuẩn MCP cho AI.

---

## 2. Công nghệ sử dụng

### 2.1 Backend (Quarkus / Java)

| Thành phần | Công nghệ |
|------------|-----------|
| Framework | Quarkus 3.33.1 (Java 24/25) |
| ORM | Hibernate ORM + Panache (Active Record) |
| CSDL | MariaDB 11.4 LTS (JSON type, InnoDB ACID, TZ `Asia/Ho_Chi_Minh`) |
| Migration | Flyway (V1 → V66, ~66 migration files) |
| Xác thực | Keycloak 26.6.1 (OIDC, JWT RS256), `quarkus-oidc`, `quarkus-smallrye-jwt`, MFA TOTP RFC 6238 |
| REST | JAX-RS (`quarkus-rest` + `quarkus-rest-jackson`) |
| Validation | Hibernate Validator |
| Scheduler | `quarkus-scheduler` (escrow release, owner decision deadline, payout) |
| Cache | `quarkus-cache` |
| VietQR | `emv-qrcode` + ZXing (sinh QR offline) |
| API Docs | OpenAPI/SmallRye (`operationIdStrategy=METHOD`) |

### 2.2 Frontend (React 19 / TypeScript)

| Thành phần | Công nghệ |
|------------|-----------|
| Framework | React 19.2 + TypeScript 5.9 + Vite 7 |
| Routing | TanStack Router (file-based) |
| Server state | TanStack Query |
| Bảng dữ liệu | TanStack Table |
| Styling | Tailwind CSS v4 (`@theme` trong CSS, không có `tailwind.config.js`) |
| UI primitives | Base UI v1.5+ — **tuyệt đối không dùng Radix** |
| API client | hey-api SDK sinh tự động từ OpenAPI — **không viết tay `fetch`** |
| Auth | `oidc-client-ts` (PKCE S256, `automaticSilentRenew: false` + tự làm mới bằng timer) |
| i18n | i18next + react-i18next + i18next-http-backend |
| Khác | `qrcode`, `zustand` (channel selection), `class-variance-authority`, `lucide-react` |

### 2.3 AI Assistant (Python)

- Python 3.11 / **FastAPI** (MCP Server), uvicorn single-worker.
- **Google Gemini** (LLM) + **Pinecone** (Vector DB) cho semantic search.
- 4 công cụ read-only, Output Validator 4 tầng, telemetry bảo mật quyền riêng tư.

### 2.4 Hạ tầng

- **Docker Compose**: mariadb, keycloak, wordpress-db, wordpress, wordpress-setup (WP-CLI bootstrap WooCommerce 10.9.4).
- 2 WordPress plugin custom bind-mount: `v-erp-catalog-connector`, `v-erp-woo-checkout`.
- Seeded user: `admin@v-erp.com` / `password`; Keycloak admin: `admin`/`admin` tại `:8181`.

---

## 3. Trách nhiệm dự án đảm nhận

Dự án **V-ERP Lite** đảm nhận và giải quyết các bài toán sau:

### 3.1 Quản lý đa khách thuê (Multi-tenant)

- Cô lập dữ liệu theo cột `tenant_id` trên mọi bảng nghiệp vụ (không dùng Hibernate multi-tenancy).
- Trích xuất danh tính từ JWT qua `CallerContext` (request-scoped); mọi query đơn bản ghi bắt buộc `AND tenantId = ?2`.
- Một tenant có thể vận hành **nhiều cửa hàng (Business/Merchant)** — nền tảng multi-vendor.
- Phân quyền cấp 2 qua `BusinessAccess`: OWNER chỉ thao tác trên sản phẩm/đơn/ví thuộc `business_id` được gán (`V44__assign_users_to_businesses.sql`).

### 3.2 Vận hành bán hàng & kho

- Quản lý sản phẩm, biến thể, SKU, danh mục, tag, hình ảnh.
- Theo dõi tồn kho & **tồn kho tạm giữ** khi đặt hàng; chống bán quá số lượng (`SELECT ... FOR UPDATE` khi giữ tồn kho).
- Voucher/giảm giá, mua nhiều người bán trong một giỏ (checkout tách theo từng seller).

### 3.3 Vòng đời đơn hàng khép kín

- Checkout (server tự tính lại giá/thuế/phí/tồn kho) → `PENDING_PAYMENT` → `PAID_ESCROW`.
- Owner quyết định: **Accept** (`OWNER_ACCEPTED` → `FULFILLING` → `DELIVERED`) hoặc **Decline + refund**.
- Buyer xác nhận nhận hàng → `COMPLETED` (kích hoạt giải phóng escrow).
- Hỗ trợ **hủy đơn** và **trả hàng/hoàn tiền theo từng item** (per-item return/refund kèm chuyển tiền ví).
- Timeline lịch sử đơn (`OrderHistory`), deadline quyết định của owner (`OwnerDecisionScheduler`).

### 3.4 Dòng tiền đa người bán đáng tin cậy (Escrow)

- Thanh toán của khách được giữ tại **Platform Wallet** dưới dạng `EscrowRecord` (trạng thái `HELD`).
- Tiền chỉ giải phóng khi: buyer xác nhận nhận hàng **hoặc** quá 72h sau khi giao không khiếu nại (`EscrowReleaseScheduler`).
- Sau khi trừ hoa hồng sàn (5%) và điều chỉnh doanh thu (`RevenueAdjustment`), tiền ròng chuyển sang số dư khả dụng người bán.
- Sổ cái **append-only** (`WalletTransaction`) + `LedgerCheckpoint` đối soát; mọi số tiền dùng Minor Units nguyên.

### 3.5 Ví điện tử & tự động chi trả (Wallet & Payout)

- Ví đa loại (VND/USD), phân biệt `available_balance` (khả dụng) và `pending_balance` (chờ giải ngân).
- Tự động **payout screening** định kỳ: số dư đủ ngưỡng → tự chi trả; phát hiện rủi ro/vượt hạn mức → vào **hàng đợi ngoại lệ cho admin**.
- Admin: batch approve/reject, review từng payout, export CSV; approval dùng transaction + kiểm tra số dư + idempotency + pessimistic lock.
- Kết nối ngân hàng (bank details), adapter mock bank-transfer (sẵn sàng Strategy Pattern cho gateway thật).

### 3.6 Bán hàng đa kênh (WooCommerce)

- Nhận đơn từ WooCommerce qua **webhook inbound xác thực HMAC SHA-256** (`HmacSignatureFilter`).
- Khử trùng lặp webhook bằng `ExternalEntityMapping` (unique `channel_id + external_id`) + `InboundEventLog`.
- Đồng bộ catalog (upsert mapping ID ngoài), health check channel, reconcile/retry đơn lỗi.
- 2 plugin WordPress custom: catalog connector + WooCommerce checkout (Dev 5).
- Backend luôn tự xác thực lại giá & tồn kho; ánh xạ trạng thái đơn WC ↔ V-ERP.

### 3.7 Xác thực & phân quyền tập trung

- Keycloak OIDC PKCE (S256) cho SPA; JWT RS256; silent refresh proactive.
- RBAC 4 vai trò: `admin`, `owner`, `operator`, `customer` — backend là nơi chốt quyền (`@RolesAllowed`).
- MFA TOTP (RFC 6238) qua `TotpUtil`.
- Đồng bộ tài khoản 2 chiều DB ↔ Keycloak (`KeycloakSyncService`, `@Startup`, `SyncResource`).

### 3.8 Trợ lý mua sắm AI an toàn

- **Identity nằm ngoài Model**: token/user_id/tenant_id không vào prompt — lưu trong `RequestContext` (contextvars) và tiêm ngầm khi gọi Quarkus.
- Chỉ **4 công cụ read-only** (`list_customer_orders`, `get_order_status`, `get_total_products_count`, `search_knowledge_base`) — AI không thể tạo/sửa/xóa.
- **4-tầng Deterministic Output Validator**: `tech_leak`, `instruction_leak`, `action_claim`, `pii`.
- Vòng lặp tool giới hạn (`max_steps = 5`), retry hiệu chỉnh 1 bước không tool, fallback an toàn, SessionStore chống race-condition.
- Telemetry không lưu prompt/PII; 48 bài kiểm thử bất biến bảo mật offline.

### 3.9 Cấu hình đa tầng & đa ngôn ngữ

- Settings **3 lớp**: hardcoded defaults ← tenant config ← user overrides → effective settings (deep-merge).
- 6 trang cài đặt (Regional, Currency, Features, Communication, Security, Integrations).
- i18n động en/vi do backend phục vụ với **SHA-256 ETag cache** (`I18nResource`).

---

## 4. Thành tựu

### 4.1 Chức năng đã hoàn thiện (có UI)

- Storefront: duyệt sản phẩm theo danh mục, tìm kiếm, biến thể, giỏ hàng, áp voucher.
- Checkout một người bán mỗi đơn; màn hình thanh toán người mua (tạo PaymentIntent, QR, polling, thử lại, kết quả thành công/thất bại).
- Trang chi tiết đơn: payment activity, escrow, timeline, owner decision (accept/decline + refund), confirm received.
- Ví người bán: available/pending balance, ledger chỉ ghi thêm, payout history, tự động payout + card "Automated payout status".
- Dashboard admin: hàng đợi payout ngoại lệ, risk events, batch approve/reject, export CSV.
- Notifications (badge chưa đọc), Revenue, Vouchers, Subscriptions (BASE/PREMIUM + payment).
- Settings 6 trang + trang Integrations WooCommerce (health, catalog sync progress).
- Chat widget AI Shopping Assistant trong storefront.

### 4.2 Chất lượng hệ thống

- Toàn bộ stack chạy được trong **Docker Compose local** với tài nguyên giới hạn (Keycloak capped 1.5GB).
- Tài liệu kiến trúc chuẩn **ARC42** v2.4.0, kèm Mermaid diagrams và bảng ADR.
- **Kịch bản showcase** rút gọn 5 phút và hướng dẫn trình diễn đầy đủ (`SHOWCASE_GUIDE.md`).
- Handoff WooCommerce (Dev1 → Dev 2–6), audit nhánh develop 2026-07-22.
- Bộ kiểm thử AI: 48 security-invariants tests; bộ test webhook + tenant isolation phía backend.

### 4.3 Kết quả kỹ thuật mục tiêu

- Khởi động Quarkus < 3s; reload dev < 1s.
- API p95 < 200ms; 100% truy vấn trái tenant bị chặn.
- 0% PII rò rỉ qua AI chatbot; không tạo đơn trùng khi webhook lặp.

---

## 5. Kiến trúc

### 5.1 Tổng quan hệ thống

```
┌──────────────────────────────────────────────────────────────────────┐
│  Tầng trình duyệt & đối tác                                          │
│   React 19 SPA (:5174)        WordPress/WooCommerce (:8090)          │
└──────────────┬──────────────────────────────┬────────────────────────┘
               │ OIDC PKCE                     │ Webhook HMAC
               │ REST / Bearer JWT              ▼
┌──────────────▼──────────────────────────────┐
│  Keycloak IAM (:8181)  ◄─── Admin REST ───┐ │
└───────────────────────────────────────────┼─┘
                                             │
┌─────────────────────────────┐   ┌──────────▼─────────────────────────┐
│  FastAPI AI MCP (:8000)     │   │  Quarkus Core Backend (:8081)       │
│  • Gemini + Pinecone        │◄──┤  base.entity / base.rest            │
│  • Output Validator 4 tầng  │   │  base.service / base.security       │
└─────────────────────────────┘   │  Flyway migrations                  │
                                   └──────────┬────────────────────────┘
                                              │ JDBC
                                    ┌─────────▼─────────┐
                                    │  MariaDB 11.4     │
                                    │  (v-erp-lite :3307)│
                                    └───────────────────┘
```

### 5.2 Backend — Modular Monolith (Panache Active Record)

| Package | Trách nhiệm |
|---------|-------------|
| `base.entity` | JPA entities (40+): Order, Product, Wallet, EscrowRecord, PaymentIntent, LedgerCheckpoint, Tenant, Business, Role, User… |
| `base.rest` | JAX-RS resources (30+): Order, Product, PaymentIntent, Wallet, Payout, Ecommerce, Admin, Subscription, Settings… |
| `base.service` | Business logic (30+): PaymentService, EscrowService, WalletService, PayoutAutomationService, ReturnRefundService, EcommerceOrderService… |
| `base.security` | CallerContext, BusinessAccess, HmacSignatureFilter, TotpUtil, PaymentAccess, SubscriptionEntitlementFilter |
| `base.model` | Money (minor units) |

**REST chuẩn số nhiều**: `/api/customers`, `/api/users`, `/api/tenants`, `/api/businesses`, `/api/orders`, `/api/products` (lưu ý đã migrate `/business` → `/businesses`).

### 5.3 Mô hình dữ liệu & tiền tệ

- BaseEntity mang `@Version long version` (optimistic lock) — mọi bảng nghiệp vụ.
- Tiền tệ lưu dạng `BIGINT` minor units (chuẩn hóa qua `Money.java`, migration `V22__migrate_money_to_minor_units.sql`).
- JSONB columns dùng `@JdbcTypeCode(SqlTypes.JSON)` (không dùng `@Convert`).
- Flyway: V1 → V66 (~66 files), underscore versioning cho interleaved migration (V3_1 chạy giữa V3 và V4; V6 cố ý bỏ qua).

### 5.4 Đồng thời & toàn vẹn tài chính

- **Optimistic lock** (`@Version`) cho update chung.
- **Pessimistic write lock** (`SELECT ... FOR UPDATE`) trên mọi biến động Wallet/Escrow.
- **Sổ cái append-only** + `LedgerCheckpointService` đối soát tổng số dư.
- **Idempotency** qua `PaymentIdempotency` / key ngữ nghĩa bền cho webhook (`V42_1__track3_idempotency.sql`).

### 5.5 AI — Hardened Architecture

```
ChatWidget ──(message + JWT)──► FastAPI
                                  │ RequestContext (ContextVar)
                                  ▼
                             UnifiedAgent (Gemini, bounded loop ≤5 steps)
                                  │ 4 read-only MCP tools
                                  ▼
                          Deterministic Output Validator (4 tầng)
                                  │ retry 1 bước (không tool) / SAFE_FALLBACK
                                  ▼
                          Server-Owned SessionStore (atomic append)
```

---

## 6. Các tính năng chính

| Nhóm | Tính năng |
|------|-----------|
| **Sản phẩm** | Product/Variant/SKU, option, tag, category, ảnh, giá khuyến mãi, tồn kho & tồn giữ, cảnh báo sắp hết hàng |
| **Đơn hàng** | Checkout server-authoritative, multi-vendor giỏ, state machine đầy đủ, timeline, soft-delete |
| **Thanh toán** | PaymentIntent (expiry 30 phút), idempotency, mock gateway, VietQR EMVCo, polling, refund khi owner từ chối |
| **Escrow** | Hold tại platform wallet, release khi confirm / hết 72h, hoa hồng 5%, `RevenueAdjustment` |
| **Wallet/Payout** | Ví seller/platform, available/pending, bank details, auto-payout screening, risk events, admin exception queue, batch action, CSV export |
| **Trả hàng** | Return/refund theo từng item, hoàn tiền ví, trả lại tồn kho giữ |
| **Voucher** | Tạo/áp voucher, điều kiện tối thiểu, thu thập voucher trên storefront |
| **Customer** | Hồ sơ, membership/points/rank, phân quyền sở hữu đơn |
| **Notification** | Thông báo theo recipient, badge chưa đọc |
| **Subscription** | Gói BASE/PREMIUM, hạn mức vận hành, thanh toán online, ghi nhận lỗi |
| **Admin** | Quản lý tenant/user/role/business, mock-bank dashboard, subscription payments |
| **Settings** | 3 lớp defaults←tenant←user, 6 trang con, đổi ngôn ngữ tức thời |
| **E-commerce** | Webhook HMAC, dedup, catalog sync, health check, reconcile/retry |
| **AI** | Chat tra cứu đơn, semantic search, 4-tầng validator, telemetry an toàn |
| **i18n** | en/vi, ETag SHA-256 cache |

---

## 7. Khó khăn và cách giải quyết

| # | Khó khăn | Giải pháp triển khai |
|---|----------|----------------------|
| 1 | **Race-condition biến động ví/escrow** (thanh toán, hoàn tiền, payout đồng thời) | `PESSIMISTIC_WRITE` trên Wallet + `@Transactional` + idempotency key; sổ cái append-only để đối soát |
| 2 | **Rò rỉ dữ liệu đa tenant** khi lập trình viên quên lọc | Chuẩn hóa mọi query `find` kèm `AND tenantId = ?2`; bộ test `PaymentTenantIsolationTest`; `BusinessAccess` chặn cấp 2 cho OWNER |
| 3 | **Sai số dấu phẩy động của tiền tệ** | Lưu toàn bộ số tiền là `BIGINT` minor units (VND), migration quy đổi dữ liệu cũ (`V22`, `V39`, `V54`) |
| 4 | **Webhook giả mạo & trùng lặp** (nhân đôi đơn, trừ kho 2 lần) | HMAC SHA-256 qua `HmacSignatureFilter`; dedup qua `ExternalEntityMapping` + `InboundEventLog`; idempotency key ngữ nghĩa bền |
| 5 | **LLM rò rỉ PII / prompt injection / bịa đặt hành động** | Identity nằm ngoài model (`RequestContext`); chỉ 4 tools read-only; 4-tầng validator tất định; retry không-tool + `SAFE_FALLBACK`; telemetry không lưu nội dung |
| 6 | **Hiệu năng & khởi động chậm của backend** | Chọn Quarkus thay Spring Boot (RAM giảm ~60%, boot < 3s); connection pool tối ưu; chỉ mục ledger (`V20__optimize_ledger_indexes.sql`) |
| 7 | **Đồng bộ tài khoản DB ↔ Keycloak** (user tạo ở DB không có Keycloak) | `KeycloakSyncService` chạy `@Startup`; `SyncResource` trigger thủ công; AdminResource sync 2 chiều; xử lý lỗi WARN-and-continue |
| 8 | **Vòng lặp đăng nhập OIDC (redirect loop)** | Đồng bộ KC URL mặc định 8181 ở cả `auth/index.ts` và `interceptors.ts`; `failAuth()` xóa đúng key `oidc.user:*` + redirect SSO |
| 9 | **Vite stale cache sau khi đổi dependency** | Xóa `node_modules/.vite` rồi restart; khắc phục lỗi "Failed to fetch dynamically imported module" |
| 10 | **Data cũ lệch đơn vị tiền & trường thừa** | Chuỗi migration quy đổi (`V22`, `V25`, `V39`), dọn cột orphan (`V26`), enforce VND (`V54`) |
| 11 | **Escrow hiển thị sai số tiền sau hoa hồng** | Sửa `pending_balance` hiển thị đúng giá trị ròng sau phí; "total = confirmed earnings only" (`c27ed27`, `67a69ae`, `cbac25e`) |
| 12 | **God Classes & Monster Components** | Ghi nhận nợ kỹ thuật TD-02 (`OrderResource` 67KB, `order-detail-page` 90KB, `cart-checkout-page` 81KB) — kế hoạch phân rã theo ngữ cảnh |

### 7.1 Nợ kỹ thuật còn lại (từ audit 2026-07-22)

| Mã | Nội dung | Ưu tiên |
|----|----------|---------|
| TD-01 | Seed password mặc định (`password`) trong Keycloak | P1 — bắt buộc đổi mật khẩu lần đầu |
| TD-02 | God Controllers & Monster React pages | P2 — phân rã |
| TD-03 | Sổ cái single-entry → nâng lên double-entry | P1 |
| TD-04 | Gateway thanh toán mock → kết nối Napas/VNPay/PayOS thật | P2 |
| TD-05 | Chưa có Vitest/Playwright cho frontend | P2 |
| TD-06 | File binary `python-installer.exe` trong git | P3 — dọn repo |

---

## 8. Phụ lục

### 8.1 Các cổng & tài khoản

| Dịch vụ | Cổng | Ghi chú |
|---------|------|---------|
| Backend Quarkus | 8081 | root `/api/`, health tại `/api/health`, swagger `/api/q/swagger-ui` |
| Frontend Vite | 5174 | proxy `/api` → 8081 (khi bỏ `VITE_API_BASE_URL`) |
| Keycloak | 8181 | realm `v-erp-lite`, admin `admin`/`admin` |
| MariaDB | 3307 | DB `v-erp-lite` |
| WordPress | 8090 | WooCommerce, `admin`/`password` |
| AI FastAPI | 8000 | MCP chat server |

User seed: `admin@v-erp.com`, `operator@v-erp.com`, `owner@v-erp.com` — mật khẩu `password` (chỉ cho demo).

### 8.2 Quy ước code

- 4-space indent, brace cùng dòng; 1 public type/file; `interface` ưu tiên cho object shape.
- Lombok `@Getter`/`@Setter`; không DTO — trả entity trực tiếp.
- `ThingResource` / `ThingService` / Panache repository inline.
- Cấm: Redis, MinIO, messaging, WebSockets, custom JWT, hand-written `fetch`, Radix, bypass Flyway, Hibernate multi-tenancy.

### 8.3 Khởi động

```bash
docker compose up -d                 # MariaDB + Keycloak + WordPress
./gradlew quarkusDev                 # Backend :8081
cd frontend && npm run dev           # Frontend :5174
```

### 8.4 Lệnh kiểm tra

```bash
./gradlew test                       # Backend tests
cd frontend && npm run build         # THE verification gate (tsc -b && vite build)
cd frontend && npm run generate-api  # Regenerate hey-api SDK từ openapi-spec.yaml
cd ecommerce-chatbot && python run_tests.py   # 48 AI security tests
```

---

*Báo cáo được tổng hợp từ mã nguồn và tài liệu hiện có trong repository; không đưa ra thông tin ngoài phạm vi dự án.*

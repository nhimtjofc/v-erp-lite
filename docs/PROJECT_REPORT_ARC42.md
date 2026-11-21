# BÁO CÁO KIẾN TRÚC TỔNG HỢP

## Hệ thống V-ERP Lite

**Loại tài liệu:** Tài liệu Kiến trúc Phần mềm theo chuẩn ARC42 (12 phần)

*Phạm vi hệ thống: ERP Lite · Marketplace đa người bán · Wallet / Escrow / Payout · Omni-channel (WooCommerce) · AI Shopping Assistant*

| Thuộc tính | Giá trị |
| :--- | :--- |
| **Loại tài liệu** | Báo cáo kiến trúc phần mềm (Architecture Report) theo chuẩn ARC42 |
| **Cấu trúc** | 12 phần arc42 + 3 phụ lục |
| **Phiên bản** | 3.0 |
| **Ngày phát hành** | 2026-08-11 |
| **Trạng thái** | Draft — chờ review của architecture team |
| **Phạm vi phân phối** | Nội bộ (Internal) |
| **Đơn vị soạn thảo** | Architecture Documentation (docs-as-code) |
| **Hệ thống áp dụng** | Backend `src/` · Frontend `frontend/src/` · Chatbot `ecommerce-chatbot/` · Mock-bank `mock-bank/` · Hạ tầng `docker-compose.yml` |

> **Tóm tắt tài liệu:** Báo cáo mô tả kiến trúc tổng thể hệ thống **V-ERP Lite** theo khuôn mẫu ARC42 gồm 12 phần — giới thiệu & mục tiêu, ràng buộc, bối cảnh & phạm vi, chiến lược giải pháp, phân rã khối xây dựng, luồng runtime, triển khai, khái niệm xuyên suốt, quyết định kiến trúc, yêu cầu chất lượng, rủi ro & nợ kỹ thuật, thuật ngữ — kèm 3 phụ lục (quy ước code, runbook vận hành, danh mục migration). Bản 3.0 được **tái kiểm chứng trực tiếp từ mã nguồn** (backend, frontend, chatbot, hạ tầng, CI/CD).

---

## Kiểm soát tài liệu

### 1. Lịch sử tài liệu

| Phiên bản | Ngày | Thay đổi |
| :--- | :--- | :--- |
| 1.0 | 2026-08-11 | Bản tổng hợp đầu tiên theo cấu trúc ARC42 |
| 2.0 | 2026-08-11 | Viết lại toàn diện theo chuẩn senior: ADR, stakeholder matrix, quality scenarios, risk matrix |
| 3.0 | 2026-08-11 | **Tái kiểm chứng 100% bằng đọc lại mã nguồn.** Sửa số liệu test (188 test/36 file), wallet types (SELLER/ADMIN/PLATFORM), payout status (AUTO_APPROVED/FLAGGED/APPROVED/REJECTED/PAID), path ecommerce (`/integrations/ecommerce/v1/...`), CI thực tế (chỉ 4 test class, không build frontend), xóa giả định Dockerfile (không tồn tại — mọi thứ chạy host trừ DB/Keycloak/WP), bổ sung chatbot chi tiết (4 tools, 4 validator checks, session store, telemetry), frontend routes/pages thực tế, danh sách đầy đủ 66 Flyway migrations, cấu hình runtime & payout config |

### 2. Tài liệu tham chiếu

| Ký hiệu | Tài liệu / Nguồn |
| :--- | :--- |
| [R1] | Mã nguồn backend `src/` (Java/Quarkus) |
| [R2] | Mã nguồn frontend `frontend/src/` (React/TypeScript) |
| [R3] | Mã nguồn chatbot `ecommerce-chatbot/` (Python/FastAPI) |
| [R4] | Mã nguồn `mock-bank/` (Node/Express) |
| [R5] | Hạ tầng `docker/`, `docker-compose.yml`, `keycloak/realm-v-erp-lite.json` |
| [R6] | Quy ước dự án `AGENTS.md` |
| [R7] | Tài liệu `docs/ARCHITECTURE_ARC42.md`, `docs/PROJECT_REPORT.md`, `develop-branch-audit-2026-07-22.md` |
| [R8] | Cấu hình build/runtime `build.gradle`, `application.properties` |
| [R9] | CI/CD `.github/workflows/payment-ci.yml` |

### 3. Mục lục

1. [Giới thiệu & Mục tiêu](#1-giới-thiệu--mục-tiêu-introduction--goals)
2. [Ràng buộc](#2-ràng-buộc-constraints)
3. [Bối cảnh & Phạm vi](#3-bối-cảnh--phạm-vi-context--scope)
4. [Chiến lược Giải pháp](#4-chiến-lược-giải-pháp-solution-strategy)
5. [Góc nhìn Khối xây dựng](#5-góc-nhìn-khối-xây-dựng-building-block-view)
6. [Góc nhìn Runtime](#6-góc-nhìn-runtime-runtime-view)
7. [Góc nhìn Triển khai](#7-góc-nhìn-triển-khai-deployment-view)
8. [Các Khái niệm Xuyên suốt](#8-các-khái-niệm-xuyên-suốt-crosscutting-concepts)
9. [Quyết định Kiến trúc](#9-quyết-định-kiến-trúc-architecture-decisions)
10. [Yêu cầu Chất lượng](#10-yêu-cầu-chất-lượng-quality-requirements)
11. [Rủi ro & Nợ kỹ thuật](#11-rủi-ro--nợ-kỹ-thuật-risks--technical-debt)
12. [Thuật ngữ](#12-thuật-ngữ-glossary)

Phụ lục: [A — Quy ước code](#phụ-lục-a--quy-ước-code) · [B — Runbook vận hành](#phụ-lục-b--runbook-vận-hành) · [C — Bảng liệt kê Migration](#phụ-lục-c--bảng-liệt-kê-flyway-migrations)

---

## 1. Giới thiệu & Mục tiêu (Introduction & Goals)

### 1.1 Tóm tắt điều hành (Executive Summary)

**V-ERP Lite** là một nền tảng ERP tinh gọn, đa khách thuê (multi-tenant), theo mô hình **Modular Monolith**: một backend Quarkus duy nhất (**Java 24 target / 25 CI runtime**, 120 file Java, ~14,7k LOC) phục vụ đồng thời bốn khả năng cốt lõi:

1. **Lõi ERP & Marketplace** — catalog (sản phẩm, biến thể, option, nhãn, phân loại), giỏ hàng, đơn hàng, khách hàng, gian hàng (business), voucher, kho, nhập vai customer.
2. **Động cơ tài chính** — ví (SELLER/ADMIN/PLATFORM), escrow 7 ngày, phân bổ 95/5, PaymentIntent/PaymentRefund, hoàn tiền/trả hàng, payout tự động có kiểm soát rủi ro (rủi ro-scored, admin duyệt).
3. **Tích hợp đa kênh** — WooCommerce (catalog sync ERP→Woo, đơn Woo→ERP qua webhook HMAC, trạng thái 2 chiều, reconcile), cổng thanh toán mock-bank + VietQR offline chuẩn EMVCo.
4. **Trợ lý mua sắm AI** — chatbot FastAPI (Gemini + Pinecone) với kiến trúc "identity outside model", 4 công cụ chỉ đọc, output validator 4 tầng, session store + telemetry.

Frontend là một SPA **React 19.2 + TypeScript 5.9 + Vite 7.3** (172 file TS, ~33,8k LOC) dùng TanStack Router/Query/Table, SDK sinh tự động từ OpenAPI (hey-api), UI Base UI v1.5 + Tailwind v4, i18next, zustand.

Hệ thống vận hành cục bộ bằng Docker Compose một lệnh **chỉ cho hạ tầng**: MariaDB 11.4 (2 instance: app + WordPress), Keycloak 26.6.1, WordPress 7.0/WooCommerce 10.9.4. Backend/frontend/mock-bank/chatbot chạy trên host bằng lệnh dev (quarkusDev / npm run dev / node / uvicorn).

**Điểm mạnh kiến trúc:** kỷ luật tài chính (minor units VND, pessimistic lock, append-only ledger + checkpoint, idempotency), cô lập tenant bằng cột `tenant_id` lọc thủ công, hợp đồng frontend–backend type-safe (SDK sinh tự động), chuỗi bảo mật webhook HMAC, và kiến trúc AI an toàn (identity outside model).

**Điểm rủi ro cần lưu ý (chi tiết §11):** Google OAuth client secret bị commit trong `realm-v-erp-lite.json`; mock payment hoạt động ở mọi profile; JWT lưu trong localStorage; CI chỉ chạy subset test; CORS `*`; fetch tay tồn tại ở 6 chỗ (vi phạm quy ước SDK).

### 1.2 Mục tiêu hệ thống (Goals & Requirements Overview)

| ID | Mục tiêu | Ưu tiên | Kết quả đo được (KPI) |
| :--- | :--- | :--- | :--- |
| **G-1** | Quản lý khách hàng & gian hàng đa tenant | **Phải có** | Mỗi tenant cô lập tuyệt đối; CRUD Customer/User/Business; membership (thẻ MEM-, điểm, tier) chính xác |
| **G-2** | Bán hàng đa kênh (storefront nội bộ + WooCommerce) | **Phải có** | 100% sản phẩm approve được sync sang Woo; đơn Woo về ERP không trùng; trạng thái 2 chiều khớp |
| **G-3** | Thanh toán & escrow an toàn, minh bạch dòng tiền | **Phải có** | Không âm số dư; escrow HELD→RELEASED đúng điều kiện; phân bổ 95/5 không sai số; không double-charge |
| **G-4** | Payout tự động có kiểm soát rủi ro | **Phải có** | Ví ≥ 100.000 VND được xử lý; ≥ 50.000.000 VND phải duyệt admin; không chuyển khi thiếu bank details |
| **G-5** | Gói đăng ký (BASE/PREMIUM) + feature gating | **Phải có** | BASE trial 14 ngày; nâng cấp prorated; tenant hết hạn bị khóa ghi (entitlement filter) |
| **G-6** | Báo cáo doanh thu (daily/MTD/monthly, COGS, điều chỉnh) | **Nên có** | Báo cáo khớp sổ cái; điều chỉnh được audit |
| **G-7** | Trợ lý AI an toàn | **Nên có** | 0% PII rò rỉ qua chat; agent không thực hiện hành động ghi; output validator chặn ảo giác |
| **G-8** | Bảo mật & tuân thủ (OIDC, RBAC, MFA, HMAC webhook) | **Phải có** | Truy vấn trái tenant bị chặn 100%; webhook replay bị dedup; MFA bảo vệ thao tác nhạy cảm |

### 1.3 Các bên liên quan (Stakeholders)

| Stakeholder | Vai trò | Kỳ vọng kiến trúc | Ảnh hưởng |
| :--- | :--- | :--- | :--- |
| **Platform Administrator** | Quản trị tenant, duyệt rủi ro payout, giám sát | Cô lập tenant triệt để, phát hiện gian lận, danh tính tập trung | Cao |
| **Business Owner (Seller)** | Quản lý catalog, duyệt đơn, cấu hình ví/payout | Dòng tiền ký quỹ minh bạch, nhận tiền đúng hạn | Cao |
| **Operator (Staff)** | Xử lý đơn, giao vận, hoàn/trả | Thao tác nhanh, trạng thái tức thời, tránh ghi đè (409) | Trung bình |
| **End Buyer (Customer)** | Mua sắm, thanh toán, theo dõi đơn, chat AI | Trải nghiệm mượt, phản hồi tức thì, AI không ảo giác | Trung bình |
| **System Integrator** | Tích hợp gateway thật, ngân hàng, kênh ngoài | API OpenAPI 3.1 ổn định, webhook HMAC, idempotency | Trung bình |
| **Dev/QA team** | Phát triển theo slice, bảo trì, kiểm thử | Quy ước rõ, môi trường 1 lệnh, CI feedback nhanh | Cao |

### 1.4 Mục tiêu chất lượng hàng đầu (Top Quality Goals)

1. **Bảo mật & cô lập dữ liệu** — OIDC RS256 qua Keycloak (realm `v-erp-lite`, JWKS), RBAC 4 vai trò (admin/operator/businessOwner/customer), cô lập tenant + business cấp CSDL, AI zero-PII.
2. **Tính toàn vẹn tài chính** — minor units VND, pessimistic lock `FOR UPDATE`, append-only ledger + checkpoint, idempotency toàn diện (webhook/API/khóa tự nhiên).
3. **Hiệu năng & phản hồi** — Quarkus khởi động nhanh, API p95 < 200ms (mục tiêu), cache/invalidation qua TanStack Query.
4. **Khả năng mở rộng & tích hợp** — OpenAPI-first sinh SDK, webhook HMAC + dedup, MCP-style cho AI.

---

## 2. Ràng buộc (Constraints)

### 2.1 Ràng buộc kỹ thuật (Technical Constraints)

| Mã | Ràng buộc | Giá trị cụ thể (đã xác minh) | Độ cứng |
| :--- | :--- | :--- | :--- |
| **TC-01** | Backend framework | Quarkus **3.33.1.1**, Java **24** (source/target) / 25 (CI runtime), single-module Gradle | Cứng |
| **TC-02** | Database | MariaDB **11.4** — 2 instance: app (`:3307`, DB `v-erp-lite`) + WordPress (`wordpress-db` không mở port host); Keycloak dùng chung DB app | Cứng |
| **TC-03** | Schema migration | Flyway V1→V66; `migrate-at-start`, `baseline-on-migrate`; cấm thay đổi schema ad-hoc | Cứng |
| **TC-04** | Identity & Access | Keycloak 26.6.1 (`KC_FEATURES=organization`), OIDC bearer JWT RS256, PKCE S256 cho SPA; client `v-erp-app` (public) + `v-erp-admin` (service account) | Cứng |
| **TC-05** | Frontend | React 19.2.7 + TS 5.9.3 + Vite 7.3.5; TanStack Router 1.170/Query 5.101/Table 8.21; **Base UI v1.5.0 (cấm Radix)**; Tailwind v4 (không `tailwind.config.js`) | Cứng |
| **TC-06** | Frontend↔Backend contract | SDK hey-api (`@hey-api/openapi-ts` 0.98.2; plugins: client-fetch/sdk/typescript(`enums:false`)/react-query); cấm `fetch` tay (có 6 chỗ vi phạm — tech debt) | Cứng |
| **TC-07** | Tiền tệ | Chỉ VND, minor units (`long`), `Money` value-object VND-only, `currency='VND'` enforced (V54) | Cứng |
| **TC-08** | Multi-tenancy | Không Hibernate multi-tenancy (`multitenant=NONE`); cột `tenant_id` lọc thủ công | Cứng |
| **TC-09** | AI assistant | Python FastAPI (`ecommerce-chatbot/`), Gemini 2.5 Flash, Pinecone (`ecommerce-products`), MCP-style | Mềm |
| **TC-10** | Gateways | Mock-bank (Node/Express `:3000`, secret `mock_secret_v_erp_lite_2025`) + VietQR offline (EMVCo, `emv-qrcode` lib) | Mềm (seam sẵn sàng) |

### 2.2 Ràng buộc tổ chức & quy trình (Organizational Constraints)

- **OC-01 — Local-first:** docker-compose chỉ khởi động hạ tầng (DB/Keycloak/WP); backend/frontend/mock-bank/chatbot chạy host (quarkusDev / npm run dev / node server.js / uvicorn).
- **OC-02 — Package phẳng:** `base.entity` / `base.rest` / `base.service` / `base.security` / `base.model`; naming `ThingResource`/`ThingService`.
- **OC-03 — Không DTO cho CRUD:** entity trả về trực tiếp; ngoại lệ: `EcommerceOrderDto`, `ProviderOperationResult` (integration).
- **OC-04 — REST:** path số nhiều (`/api/customers`, `/api/businesses`, `/api/orders`); root path `/api/`.
- **OC-05 — Cổng chất lượng:** backend không lint task; frontend `npm run build` = `tsc -b && vite build` là gate; `./gradlew test` chạy **188 test/36 file**; CI chỉ chạy **4 test class**.
- **OC-06 — Không stack nặng:** cấm Redis, MinIO, messaging, WebSockets, async job queue, CRM, MapStruct, JacksonJsonConverter.

### 2.3 Ràng buộc pháp lý & tuân thủ (Regulatory)

- **RC-01 — Bảo mật dữ liệu cá nhân:** PII phải kiểm soát theo tenant; AI không log/tiết lộ PII (chat-widget dùng `dangerouslySetInnerHTML` — XSS risk).
- **RC-02 — VietQR/EMVCo:** QR đúng chuẩn EMVCo (tag 00/01/38/53/54/58/62, CRC16) để app ngân hàng VN quét được.
- **RC-03 — Ghi chú:** chưa có compliance chính thức (PCI-DSS/GDPR) xác nhận; cần rà soát trước production.
- **RC-04 — Google OAuth:** IdP Google bật sẵn trong realm với client secret **committed** — phải xoay vòng trước production.

---

## 3. Bối cảnh & Phạm vi (Context & Scope)

### 3.1 Bối cảnh nghiệp vụ (Business Context)

```text
┌────────────────────────────────────────────────────────────────────┐
│                            NGƯỜI DÙNG                              │
│   Admin sàn  ·  Chủ gian hàng (Seller)  ·  NV vận hành  ·  Khách   │
└──────────────────────────────────┬─────────────────────────────────┘
                                   │ sử dụng
                                   ▼
┌────────────────────────────────────────────────────────────────────┐
│                    V-ERP LITE — LÕI HỆ THỐNG                       │
│                                                                    │
│   ┌──────────────────┐        ┌─────────────────────────────┐     │
│   │ React SPA        │◄──────►│ Quarkus Backend             │     │
│   │ (storefront +    │        │ (ERP + Wallet/Escrow/Payout)│     │
│   │  admin portal)   │        └──────────────┬──────────────┘     │
│   └───────┬──────────┘                       │                    │
│           │                                  │                    │
│           └──────────────► FastAPI AI Assistant ─────────────────┘
│                                                                    │
└───┬────────────┬──────────────────────────┬─────────────────────────┘
    │            │                          │
    ▼            ▼                          ▼
┌────────────┐ ┌──────────────────┐  ┌───────────────────┐
│ Keycloak   │ │ WordPress +      │  │ Mock-Bank +       │
│ IAM        │ │ WooCommerce      │  │ VietQR            │
│ (realm     │ │ (2 plugin)       │  │                   │
│  v-erp-lite)│ │                 │  │                   │
└────────────┘ └──────────────────┘  └───────────────────┘

  Gemini 2.5 Flash + Pinecone  (LLM mà AI Assistant gọi qua tool loop)
```

### 3.2 Ngữ cảnh kỹ thuật & Interface Inventory (Technical Context)

| Kênh | Giao thức / Định dạng | Bảo mật & chống trùng | Vị trí code |
| :--- | :--- | :--- | :--- |
| SPA ↔ Backend | HTTPS/JSON REST (`/api/**`) | OIDC Bearer JWT RS256; interceptor refresh + retry 401 | `client-config.ts`, `interceptors.ts`, `CallerContext.java` |
| Backend ↔ Keycloak | OIDC Discovery + Admin REST | Client Credentials `v-erp-admin`/`v-erp-admin-secret` | `KeycloakSyncService.java`, `application.properties` |
| Backend ↔ MariaDB | JDBC (Agroal pool, max 8) | Credentials qua env; Flyway migrate-at-start | `application.properties` |
| Woo → Backend | Webhook HTTP inbound `POST /api/integrations/ecommerce/v1/orders` | `@PermitAll` + HMAC-SHA256 (`X-WC-Webhook-Signature`) + dedup `WebhookEvent` | `EcommerceOrderResource.java`, `EcommerceOrderService.java` |
| Backend → Woo | REST catalog (`/changes`, `/summary`, `/mappings`) | Plugin `class-api-client.php`, plugin dùng `V_ERP_API_BASE_URL` | `EcommerceCatalogResource.java` |
| Backend ↔ Mock-Bank | REST `POST /api/payments/mockbank/*` + webhook `POST /api/payments/webhook/{gateway}` | `@HmacSecure` (X-Webhook-Signature) + `InboundEventLog` dedup (409 replay) | `MockBankResource.java`, `PaymentWebhookResource.java`, `HmacSignatureFilter.java` |
| SPA → Chatbot | HTTP/JSON `POST /chat` | Bearer JWT; `verify_bearer` JWKS (issuer bắt buộc, audience không bắt buộc) | `server.py`, `request_context.py` |
| Chatbot → Backend | HTTP REST (4 tools) | Forward user JWT (identity outside model) | `agents/unified_agent.py` |
| Backend → LLM/Vector | Gemini SDK + Pinecone | API key qua env (`GEMINI_API_KEY`, `PINECONE_API_KEY`) | `agents/unified_agent.py`, `search_engine/vector_client.py` |
| Browser → Images | Multipart upload / GET | MIME whitelist, 5MB cap, path-traversal guard | `ImageResource.java` |
| Admin UI ↔ Admin API | REST (`/admin`, `/admin/tenants/...`) | RBAC `admin` | `AdminResource.java`, `AdminSubscription*` |

### 3.3 Phạm vi chức năng được hỗ trợ

1. **Catalog & bán hàng** — sản phẩm, biến thể, option, nhãn, phân loại, voucher (2 loại + mã nhập tay), giỏ hàng, checkout (shipping / discount / VAT / tax), thanh toán ví hoặc VietQR/BANK_TRANSFER.
2. **Đặt hàng & hoàn thành** — máy trạng thái Order 13 trạng thái, SLA owner 24h + auto-decline, confirm-receipt, lịch sử `OrderHistory` (sequence).
3. **Escrow & hoàn tiền** — HELD→RELEASED→REFUNDED, release 7 ngày, hoàn tiền theo dòng (request-cancel / request-return), phê duyệt owner/admin, force-release (dev).
4. **Wallet & payout** — ví SELLER/ADMIN/PLATFORM, top-up (nạp tiền + VietQR), payout rủi ro-scored, duyệt admin, export CSV.
5. **Subscription** — BASE (trial 14 ngày) / PREMIUM, feature gates, capacity limits, upgrade prorated, thanh toán gói + duyệt admin + mock-webhook.
6. **Báo cáo doanh thu** — daily/MTD/monthly, revenue adjustment, COGS theo inventory cost snapshots.
7. **Settings 3 lớp** — mặc định ← tenant ← user; ép VND.
8. **Thông báo nội bộ** — Notification cho buyer + staff (poll 30s).
9. **WooCommerce integration** — health, catalog sync, order webhook, status sync, reconcile (per externalId).
10. **AI assistant** — hỏi về đơn của tôi / tìm sản phẩm (semantic search qua Pinecone).
11. **Admin platform** — tenants, users, subscription & payments, payout exceptions, risk events, mock-bank dashboard.

### 3.4 Ngoài phạm vi (Out of Scope)

- Redis, MinIO, messaging, WebSockets, async job queue, CRM, billing real-time.
- Gateway thanh toán/ngân hàng **thật** (chỉ mock + VietQR offline; adapter seam đã chuẩn bị).
- Multi-realm Keycloak / database-per-tenant.
- Mobile native; PWA; offline-first.
- E2E / UI test tự động; frontend unit test (backlog).
- Elasticsearch production (chatbot có `elasticsearch_client.py` nhưng vector search chính dùng Pinecone).

---

## 4. Chiến lược Giải pháp (Solution Strategy)

### 4.1 Ba trụ cột chiến lược

```text
┌────────────────────────────────────────────────────────────────────┐
│  TRỤ CỘT 1 — Modular Monolith + Tenant Isolation                   │
│                                                                    │
│  • 1 backend Quarkus duy nhất (120 Java files, 39 entities,        │
│    33 resources)                                                   │
│  • Cô lập tenant bằng cột tenant_id lọc thủ công + guard           │
│    (CallerContext / resolveUser)                                   │
│  • Identity tập trung Keycloak OIDC                                │
│    (RBAC 4 vai trò + organization claim)                           │
├────────────────────────────────────────────────────────────────────┤
│  TRỤ CỘT 2 — Financial Integrity by Design                         │
│                                                                    │
│  • Minor units VND (Money value-object)                            │
│  • PESSIMISTIC_WRITE lock trên ví + ledger                         │
│  • Append-only ledger + LedgerCheckpoint + cân đối thường xuyên    │
│  • Idempotency 3 lớp (webhook / API / khóa tự nhiên)               │
├────────────────────────────────────────────────────────────────────┤
│  TRỤ CỘT 3 — OpenAPI-first + AI Safety                             │
│                                                                    │
│  • SDK sinh tự động (hey-api) → contract type-safe 2 chiều         │
│  • Webhook HMAC + dedup + reconcile                                │
│  • AI "identity outside model": 4 công cụ chỉ đọc + validator      │
│    4 tầng                                                          │
└────────────────────────────────────────────────────────────────────┘
```

### 4.2 Các lựa chọn thay thế đã cân nhắc (Alternatives Considered)

| Quyết định | Đã chọn | Lý do | Phương án thay thế & lý do loại bỏ |
| :--- | :--- | :--- | :--- |
| Dạng kiến trúc | **Modular Monolith** | 6 dev, phạm vi "lite", vận hành 1 lệnh | *Microservices*: phức tạp hạ tầng + distributed transaction vùng tài chính; *Monolith thuần*: thiếu ranh giới module |
| Multi-tenancy | **Cột tenant_id lọc thủ công** | Hibernate multi-tenancy chưa stable trên Quarkus 3.x | *DB-per-tenant*: nặng; roadmap nếu cần isolation vật lý |
| Tiền tệ | **long minor units + Money value-object** | Tránh lỗi floating-point | *BigDecimal luân chuyển*: dễ nhầm đơn vị; *float/double*: cấm |
| Concurrency tài chính | **PESSIMISTIC_WRITE lock** | Sổ dư ví là critical section | *Optimistic*: đủ cho CRUD chung (version), không đủ cho tài chính |
| Frontend contract | **SDK sinh OpenAPI** | Chống drift schema, type-safe | *Hợp đồng thủ công*: drift; *GraphQL*: overkill |
| Identity | **Keycloak (realm v-erp-lite, org feature)** | RBAC/MFA/org claim sẵn sàng | *Auth0/Firebase*: không local-first; *tự viết JWT*: cấm |
| AI chat | **FastAPI + Gemini 2.5 Flash + Pinecone, "identity outside model"** | An toàn, model không cầm JWT | *Embed LLM vào backend*: phụ thuộc Python nặng; *model cầm secret*: mất kiểm soát PII |
| Đồng bộ Woo | **Webhook inbound HMAC + catalog sync chủ động + reconcile** | eventual consistency đủ tin cậy không cần messaging | *Chỉ webhook*: mất nếu lệch; *hàng đợi ngoài*: cấm OC-06 |
| Escrow release | **Scheduler hằng đêm cron** | Đơn giản, đủ độ trễ | *Thời điểm thật (real-time)*: cần event infra không có |

---

## 5. Góc nhìn Khối xây dựng (Building Block View)

### 5.1 Sơ đồ tổng thể cấp 1 (whitebox)

```text
┌─────────────────────────────────────────────────────────────────────┐
│                    FRONTEND SPA (:5174)                             │
│  ┌──────────────┐ ┌────────────────┐ ┌───────────────────────────┐  │
│  │ TanStack     │ │ TanStack Query │ │ hey-api SDK (gen/)        │  │
│  │ Router 1.170 │ │ 5.101          │ │ sdk.gen 1247 · types 3967 │  │
│  │ 57 route     │ │ cache +        │ │ react-query 3221 dòng     │  │
│  │ files        │ │ invalidation   │ │                           │  │
│  └──────────────┘ └────────────────┘ └───────────────────────────┘  │
│  ┌──────────────┐ ┌────────────────┐ ┌───────────────────────────┐  │
│  │ oidc-client- │ │ Base UI 1.5 +  │ │ zustand 5                 │  │
│  │ ts 3.5       │ │ Tailwind v4    │ │ auth-store + theme-store  │  │
│  │ PKCE S256 +  │ │ 26 components  │ │                           │  │
│  │ refresh      │ │               │ │                           │  │
│  └──────────────┘ └────────────────┘ └───────────────────────────┘  │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ /api/** + JWT
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        BACKEND QUARKUS (:8081)                       │
│  REST layer     33 resources + 2 DTO + 2 exception mappers          │
│  Service layer  33 services (8 lĩnh vực)                            │
│  Persistence    Panache + 39 entities                               │
│  Security       CallerContext · TenantResolver · HmacFilter ·       │
│                 TotpUtil                                            │
│  Schedulers     EscrowRelease (cron 1:00) · OwnerDecision (5m) ·    │
│                 Payout (24h)                                        │
│  Ledger & Money append-only + Money VND                             │
└───────┬──────────────────────┬────────────────────┬─────────────────┘
        │ JDBC                 │ OIDC / Admin REST  │ HMAC webhook + REST
        ▼                      ▼                    ▼
┌──────────────┐        ┌───────────────┐   ┌───────────────────┐
│ MariaDB      │        │ Keycloak      │   │ WordPress +       │
│ :3307 (app)  │        │ :8181         │   │ WooCommerce :8090 │
└──────────────┘        └───────────────┘   └───────────────────┘

  Hạ tầng chạy host ngoài Docker:
    Mock-Bank :3000  (REST + webhook ↔ Backend)
    FastAPI AI :8000 (SPA gọi /chat + JWT; tools → Backend; Gemini + Pinecone)
```

### 5.2 Phân rã Backend (Level 2)

**REST Inventory** — 33 resource classes trong `base.rest` (xác minh từ `@Path`), root `/api/`:

| Nhóm | Resource (@Path) | Điểm nổi bật |
| :--- | :--- | :--- |
| Health | `HealthResource` (`/health`) | liveness |
| Tenant | `TenantResource` (`/tenants`) | CRUD |
| TenantConfig | `TenantConfigResource` (`/tenant-settings`, `/effective`, `/user`) | JSONB config 3 lớp |
| User & Auth | `UserResource` (`/users`, `/me`), `AuthResource` (`/auth/password`, `/auth/mfa/setup|verify|disable|status`, `/auth/register`) | MFA TOTP, register |
| Customer | `CustomerResource` (`/customers`, `/customers/me`) | CRUD + search escape LIKE |
| Business | `BusinessResource` (`/businesses`) | CRUD |
| Catalog | `CategoryResource` (`/categories`), `TagResource` (`/tags`), `ProductResource` (`/products`), `VoucherResource` (`/vouchers`, `/validate`, `/my`, `/{id}/collect`), `BankResource` (`/banks`) | product + variant + option, voucher |
| Image | `ImageResource` (`/images/upload`, `/images/{filename}`) | 5MB, MIME whitelist |
| Orders | `OrderResource` (`/orders`, `/checkout`, `/stats`, `/{id}/history`, `/{id}/escrow[/force-release]`, `/{id}/refunds`, `/{id}/owner-decision`, `/{id}/confirm-receipt`, `/{orderId}/items/{itemId}/{cancel,owner-cancel,request-return,approve-return,reject-return,request-cancel,approve-cancel,reject-cancel,refund-cancel,release-cancel-to-seller,refund-return}`) | máy trạng thái 13, SLA, hoàn/trả theo dòng |
| Wallet | `WalletResource` (`/wallets`, `/me`, `/stats`, `/escrow/force-release`, `/transactions/all`, `/{id}/transactions`, `/me/bank-details`, `/me/top-up`, `/top-up/{id}/complete`, `/{id}/{credit,debit,adjust}`) | top-up + VietQR, debit/credit thủ công (dev) |
| Payment | `PaymentIntentResource` (`/payment-intents`, `/order/{orderId}`), `PaymentWebhookResource` (`/payments/webhook/{gateway}`, `@HmacSecure`), `MockBankResource` (`/payments/mockbank/initiate-charge`, `/simulate-transferred`), `VietQRResource` (`/vietqr/generate`) | intent + webhook + mock gateway + QR |
| Payout | `PayoutResource` (`/payouts`, `/status`, `/screen`, `/{id}/approve|reject`, `/batch-approve|reject`, `/export`) | risk-scored, CSV |
| Subscription | `SubscriptionResource` (`/subscriptions`, `/plans`, `/me`), `SubscriptionPaymentResource` (`/subscriptions/payments`, `/{id}/mock-webhook`), `AdminSubscriptionResource` (`/admin/tenants/{tenantCode}/subscription`, `/mock-time`), `AdminSubscriptionPaymentResource` (`/admin/subscription-payments`, `/stats`, `/{id}/approve|reject`) | BASE/PREMIUM, admin duyệt |
| Revenue | `RevenueResource` (`/revenue/daily`, `/month-to-date`, `/monthly`, `/adjustments`) | COGS, adjustment |
| Notification | `NotificationResource` (`/notifications`, `/count`, `/{id}/read`, `/read-all`) | poll |
| WooCommerce | `EcommerceIntegrationResource` (`/integrations/ecommerce/v1/health`), `EcommerceCatalogResource` (`/integrations/ecommerce/v1/catalog/{changes,summary,mappings}`), `EcommerceOrderResource` (`/integrations/ecommerce/v1/orders`, `/{externalId}/status`, `/{externalId}/reconcile`) | webhook `@PermitAll` + HMAC |
| Sync | `SyncResource` (`/sync`, `/sync/keycloak`, `/sync/keycloak/tenants/{code}`) | Keycloak sync thủ công |
| I18n | `I18nResource` (`/i18n/{locale}/{namespace}`) | ETag SHA-256 |
| Admin | `AdminResource` (`/admin`, `/admin/tenants/{tenantId}/users`, `/admin/risk-events`) | user CRUD + sync |
| Debug | `DebugDbResource` (`/debug-db`, `/caller`, `/orders`) | dev-only |
| *Mapper* | `ConstraintViolationExceptionMapper`, `DebugExceptionMapper` | 409 / 400 |
| *DTO* | `EcommerceOrderDto`, `ProviderOperationResult` | chỉ integration |

**Service Layer** — 33 service trong `base.service` (xác minh đầy đủ):

| Module | Service | Điểm đáng chú ý |
| :--- | :--- | :--- |
| **Tài chính** | `WalletService`, `MoneyService`, `LedgerCheckpointService`, `EscrowService`, `EscrowReleaseScheduler`, `PayoutService`, `PayoutAutomationService`, `PayoutScheduler`, `PaymentIntentService`, `PaymentService`, `PaymentGatewayService`, `PaymentRefundService`, `BankTransferService`, `VietQRService`, `IdempotencyService`, `ReconciliationService` | `PESSIMISTIC_WRITE`, ledger append-only, checkpoint, risk score (threshold 100k/50M, window P90D, spike 3.0), QR EMVCo |
| **Ordering** | `OrderFulfillmentService`, `OwnerDecisionScheduler`, `ReturnRefundService` | SLA 24h, scan 5m, cancel/return flow |
| **Catalog** | `InventoryCostService`, `RevenueService` | COGS snapshot, báo cáo |
| **Tích hợp** | `EcommerceOrderService` (HMAC + dedup), `EcommerceCatalogService`, `EcommerceIntegrationService`, `EcommerceChannelStore`, `EcommerceChannelBootstrap`, `EcommerceSecretResolver` | secret theo channel reference, bootstrap dev |
| **Subscription** | `EntitlementService`, `SubscriptionPaymentService` | feature gates (vouchers, reports, ecommerce, onlinePayments, walletPayouts) |
| **Hệ thống** | `TenantConfigService`, `KeycloakSyncService`, `UserProvisioningService`, `NotificationService`, `SchemaBootstrap` | auto-provision user, sync KC |

**Security layer** (`base.security`, 8 file): `CallerContext`, `TenantResolver`, `TotpUtil`, `HmacSignatureFilter` + `HmacSecure` annotation, `SubscriptionEntitlementFilter`.

**Scheduler — "những robot chạy nền"**

Trong hệ thống có vài "robot" tự động làm việc theo lịch, không cần ai bấm nút. Dễ hình dung nhất là một **đồng hồ báo thức**: đến giờ là tự động hoạt động. Cụ thể:

- **EscrowReleaseScheduler** — robot *giải phóng tiền ký quỹ*: khi khách mua hàng, tiền bị "giữ" lại **7 ngày** để đảm bảo người bán giao hàng đúng cam kết. Mỗi ngày lúc **1 giờ sáng**, robot rà soát khoản ký quỹ nào đã đủ 7 ngày thì chuyển tiền về cho người bán (95%) và sàn (5%).
- **OwnerDecisionScheduler** — robot *tự xử lý đơn quá hạn*: mỗi **5 phút**, kiểm tra các đơn mà chủ gian hàng chưa duyệt quá **24 giờ** → tự động từ chối, để khách không phải chờ vô thời hạn.
- **PayoutScheduler** — robot *rút tiền về ngân hàng*: mỗi **24 giờ**, nhặt những ví người bán có đủ số dư tối thiểu (100.000 VND) và tạo yêu cầu rút tiền.

> **Giải thích mã kỹ thuật:** `cron 0 0 1 * * ?` = chạy vào giây 0, phút 0, giờ 1 (tức **1 giờ sáng**), mỗi ngày, mọi tháng. `PT5M` = 5 phút, `PT24H` = 24 giờ. `concurrentExecution=SKIP` = nếu lần chạy trước chưa xong thì bỏ qua lượt này, tránh chạy chồng chéo.

| Tên robot | Chạy khi nào | Làm gì (dễ hiểu) | Thông số kỹ thuật (cho dev) |
| :--- | :--- | :--- | :--- |
| `EscrowReleaseScheduler` | Mỗi ngày, 1 giờ sáng | Giải phóng tiền ký quỹ đã đủ 7 ngày | cron `0 0 1 * * ?` |
| `OwnerDecisionScheduler` | Mỗi 5 phút | Tự từ chối đơn chưa được duyệt quá 24 giờ | `every = PT5M`, `concurrentExecution=SKIP` |
| `PayoutScheduler` | Mỗi 24 giờ (trễ 1 phút) | Tạo yêu cầu rút tiền cho ví người bán đủ điều kiện | `every = PT24H`, `delayed = PT1M`, `concurrentExecution=SKIP` |

### 5.3 Phân rã Frontend (Level 2)

**Frontend là phần "mặt tiền" người dùng nhìn thấy** — chính là website chạy trong trình duyệt (đăng nhập, xem sản phẩm, đặt hàng, theo dõi ví...). Nó được chia thành nhiều "khối", mỗi khối đảm nhiệm một việc riêng:

| Khối | Làm gì? (dễ hiểu) | Công nghệ dùng | Vị trí code (cho dev) |
| :--- | :--- | :--- | :--- |
| **Định tuyến** (Routing) | Quyết định hiển thị trang nào theo đường dẫn trên trình duyệt (vd: gõ `/orders` → mở trang đơn hàng). Tổng cộng **57 đường dẫn/trang**. | TanStack Router (tự sinh `routeTree.gen.ts`) | `src/routes/` |
| **Kiểm soát đăng nhập** | Chặn người chưa đăng nhập vào trang nội bộ; chưa có phiên → tự đưa về trang đăng nhập | guard `beforeLoad` | `src/routes/_authenticated.tsx` |
| **Lấy & quản lý dữ liệu** | Gọi dữ liệu từ backend, lưu tạm (cache) trong 30 giây để trang mở nhanh, tránh gọi đi gọi lại | TanStack Query v5.101 (`staleTime: 30s`) | `src/shared/api/*.ts` |
| **Gọi API** | Cầu nối gửi yêu cầu lên backend; nếu phiên hết hạn giữa chừng thì tự xin token mới rồi thử lại | hey-api SDK + `interceptors.ts` | `src/shared/api/client-config.ts` |
| **Đăng nhập / phiên** | Phối hợp với Keycloak để đăng nhập và giữ phiên đăng nhập | oidc-client-ts 3.5 (PKCE S256) | `src/shared/auth/index.ts` |
| **Bộ giao diện (UI)** | Các khối hình có sẵn để dựng giao diện: nút bấm, bảng, hộp thoại, menu... (**26 thành phần**) | Base UI v1.5 + Tailwind v4 | `src/shared/ui/` |
| **Ngôn ngữ (i18n)** | Chuyển đổi ngôn ngữ hiển thị (hệ thống hỗ trợ 9 nhóm ngôn ngữ) | i18next 26.3 | `src/i18n/` |
| **Trạng thái chung** | Lưu thông tin dùng chung toàn app: ai đang đăng nhập, chế độ tối/sáng... | zustand 5 | `src/shared/store/` |
| **Chế độ tối/sáng** | Tự động bật giao diện tối hoặc sáng theo cài đặt của hệ điều hành | CSS class `.dark` + `use-theme.ts` | `src/shared/hooks/use-theme.ts` |

> **Cấu trúc thực tế (đã xác minh):** không có `src/features/`; phần ngôn ngữ đặt ở `src/i18n/`; phần trạng thái chung đặt ở `src/shared/store/`.

**Các trang chính (44 trang):** trang chủ (shop) · giỏ hàng & thanh toán · sản phẩm (thêm/sửa) · đơn hàng (chi tiết, xử lý thanh toán, hàng chờ chủ duyệt) · khách hàng · gian hàng · voucher · ví (người bán / người mua) · doanh thu · thông báo · đăng ký gói (BASE/PREMIUM) · hồ sơ cá nhân · cài đặt (khu vực, tiền tệ, tính năng, bảo mật, tích hợp) · quản trị (tenants, payout, thanh toán gói, mock-bank) · đăng nhập.

> **Lưu ý (nợ kỹ thuật):** có 6 chỗ lập trình viên gọi API "bằng tay" thay vì dùng SDK chung (`shared/api/cart.ts`, `order-detail-page.tsx`, `orders-page.tsx`, `mock-bank-dashboard-page.tsx`, `chat-widget.tsx`, `vietnam-provinces.ts`). Hệ quả: mất tính năng tự làm mới phiên và dễ lệch chuẩn — nằm trong danh sách nợ kỹ thuật T2, dự kiến dọn dẹp dần.

### 5.4 Khối hạ tầng (docker-compose — xác minh đầy đủ)

| Dịch vụ | Image | Cổng host | Ghi chú |
| :--- | :--- | :--- | :--- |
| `mariadb` | mariadb:11.4 | 3307:3306 | volume `mariadbdata`; healthcheck; DB `v-erp-lite` |
| `keycloak` | quay.io/keycloak/keycloak:26.6.1 | 8181 | `--import-realm`; `KC_FEATURES=organization`; JVM 512m–1g; mem cap 1.5G; dùng chung DB app |
| `wordpress-db` | mariadb:11.4 | (không mở) | volume `wordpressdbdata`; DB `wordpress` |
| `wordpress` | wordpress:7.0.0-php8.3-apache | 8090:80 | volume `wordpressdata`; bind-mount 2 plugin; `host.docker.internal:host-gateway`; `V_ERP_API_BASE_URL` |
| `wordpress-setup` | wordpress:cli-2.12.0-php8.3 | — | one-shot: cài WooCommerce 10.9.4, activate plugin |

> **Không có** Dockerfile cho backend/frontend/mock-bank/chatbot — chúng chạy trên host trong dev. `docker-compose.yml` chỉ khởi động hạ tầng.

---

## 6. Góc nhìn Runtime (Runtime View)

### 6.1 Luồng xác thực & giữ phiên (SPA ↔ Keycloak ↔ Backend)

```text
Người dùng              React SPA               Keycloak :8181         Backend :8081         MariaDB
   │                       │                        │                      │                   │
   │── Truy cập /dashboard ──►│                     │                      │                   │
   │                       │── OIDC Auth Request (PKCE S256) ──►│          │                   │
   │                       │◄── Trang đăng nhập ──│                    │                   │
   │── Nhập credentials ──►│                      │                      │                   │
   │                       │◄── Authorization code → access_token + refresh_token ──│        │
   │                       │                                                          │
   │                       │── userLoaded → mirror token vào localStorage            │
   │                       │   + lên lịch refresh chủ động (60s trước expiry)        │
   │                       │── GET /api/users/me (Authorization: Bearer JWT) ─────────►│     │
   │                       │                      │                      │── Verify RS256 + JWKS
   │                       │                      │                      │   CallerContext.tenantCode()
   │                       │                      │                      │   từ claim organization/
   │                       │                      │                      │   tenant_code/azp
   │                       │                      │                      │── user = find(email,
   │                       │                      │                      │   tenantId); resolveUser()
   │                       │                      │                      │   auto-provision nếu chưa có
   │                       │                      │                      │─────────────────────────────►│
   │                       │◄── 200 + thông tin user (RBAC roles) ──────│                     │
   │                       │                                                          │

   Ghi chú:
   [401 giữa request → interceptor refreshOnce() coalesced → retry]
   [refresh lỗi vĩnh viễn (invalid_grant) → failAuth() xóa oidc.user:* + redirect SSO]
```

### 6.2 Luồng mua hàng & escrow (checkout → thanh toán → giải ngân)

```text
Buyer                  Backend :8081             Wallet               Ledger                Escrow
  │                        │                       │                    │                     │
  │── POST /api/orders/checkout (ví hoặc VietQR) ──►│                  │                     │
  │                        │── Validate tồn kho + lock variant (PESSIMISTIC_WRITE)
  │                        │── Tạo Order (PENDING_PAYMENT) + phân bổ dòng theo business
  │                        │                                                        │
  │  Thanh toán ví:        │                                                          │
  │                        │── Debit ví buyer (lock ví) + credit ví platform ──►│      │
  │                        │── Ghi debit/credit entries ─────────────────────────────►│
  │                        │── Tạo escrow HELD (95% seller / 5% platform),             │
  │                        │   releaseDueAt = +7 ngày ──────────────────────────────────►│
  │                        │── Order → PAID_ESCROW
  │                        │                                                        │
  │  VietQR / BANK_TRANSFER:                                                        │
  │                        │── Tạo PaymentIntent + QR (EMVCo) → AWAITING_PAYMENT     │
  │                        │── Webhook mock-bank (POST /api/payments/webhook/MOCK)   │
  │                        │   xác nhận → xử lý như thanh toán ví                    │
  │                        │                                                        │

  ── 7 ngày sau: EscrowReleaseScheduler (cron 1:00) ──────────────────────────────
  │                        │                       │                    │           │
  │                        │── Release escrow đủ điều kiện (HELD + releaseDueAt hết hạn)──►│
  │                        │── Credit 95% vào ví seller + 5% platform revenue ───►│    │
  │                        │── Ghi entries + đánh dấu RELEASED ─────────────────────►│    │
  │                        │                                                        │

  Ghi chú: Hoàn/trả → Refund theo dòng (item-level), escrow → REFUNDED, hoàn ví buyer.
```

### 6.3 Luồng webhook WooCommerce (inbound, replay-safe)

```text
WooCommerce (plugin)        EcommerceOrderResource        EcommerceOrderService        MariaDB
  │                                │                              │                       │
  │── POST /api/integrations/ecommerce/v1/orders?tenantId&salesChannelId
  │   (X-WC-Webhook-Signature HMAC-SHA256 + Delivery-ID + Topic)
  │───────────────────────────────►│                              │                       │
  │                                │── verifyWebhookSignature(rawBody, sig, tenantId, channelId)
  │                                │──────────────────────────────►│                       │
  │                                │◄── 400 nếu thiếu/không khớp secret
  │                                │   (EcommerceSecretResolver theo channel)
  │                                │── getOrCreateWebhookEvent (dedup theo channel+eventId)
  │                                │──────────────────────────────►│                       │
  │                                │                              │  Đã xử lý (replay):   │
  │                                │◄── 200, không trùng lặp      │  trả về ngay           │
  │                                │                              │                       │
  │                                │                              │  Mới:                  │
  │                                │                              │── persist WebhookEvent ─►│
  │                                │                              │── processOrderWebhook /
  │                                │                              │   updateOrderWebhook ──►│
  │                                │                              │── Cập nhật trạng thái ERP → Woo (outbound status)
  │                                │                              │── markWebhookEventSuccess/failed ──►│
  │                                │◄── 200 OK ───────────────────│                       │
```

### 6.4 Luồng payout tự động có kiểm soát rủi ro

```text
┌─────────────────────────────────────┐
│ PayoutScheduler                     │
│ (mỗi 24h, delayed 1m, SKIP nếu bận) │
└──────────────────┬──────────────────┘
                   ▼
┌─────────────────────────────────────┐
│ Scan ví SELLER                      │
│ availableBalance ≥ 100.000 VND      │
│ │                                   │
│ ├─ Thiếu bank-details ──► (bỏ qua, chờ cập nhật)
│ └─ Đủ ──► tạo PayoutRequest (lock ví + đóng băng số dư)
└──────────────────┬──────────────────┘
                   ▼
┌────────────────────────────────────────────────────────┐
│ Cần duyệt admin?                                       │
│  • Số tiền > 50.000.000 VND                            │
│  • HOẶC risk score cao (window P90D, spike 3.0)        │
└─────────────┬──────────────────────────┬───────────────┘
              │ Không                    │ Có
              ▼                          ▼
┌──────────────────────┐   ┌────────────────────────────────┐
│ AUTO_APPROVED        │   │ FLAGGED + VendorRiskEvent      │
│ → vào PayoutBatch    │   └──────────────┬─────────────────┘
└──────────┬───────────┘                  │ Admin duyệt:
           │                              │ POST /api/payouts/{id}/
           │                              │   approve | reject (RBAC admin)
           │                              ▼
           │                    ┌───────────────────────────────┐
           │                    │ Từ chối → hủy, không chuyển    │
           │                    └───────────────────────────────┘
           │                              │ Duyệt
           └──────────────┬───────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│ Export CSV + bank-transfer (mode=mock)                  │
│ → trạng thái PAID + tạo payout ledger entry             │
└─────────────────────────────────────────────────────────┘
```

### 6.5 Vòng đời subscription (BASE/PREMIUM)

```text
┌──────────────────────────────────────────────┐
│ Tenant mới: BASE (trial 14 ngày)             │
└─────────────────────┬────────────────────────┘
                      ▼
┌──────────────────────────────────────────────┐
│ Hết trial?                                   │
│  ├─ Chưa ──► Hoạt động bình thường:          │
│  │           feature gates: vouchers /       │
│  │           reports / ecommerce /           │
│  │           onlinePayments / walletPayouts  │
│  └─ Có, chưa trả phí ──► Khóa ghi (read-only │
│               qua SubscriptionEntitlement-   │
│               Filter)                        │
└─────────────────────┬────────────────────────┘
                      ▼
┌──────────────────────────────────────────────┐
│ Owner mua / nâng cấp PREMIUM                 │
│ (prorated, thanh toán gói + mock-webhook)    │
└─────────────────────┬────────────────────────┘
                      ▼
┌──────────────────────────────────────────────┐
│ Admin duyệt thanh toán gói                   │
│  ├─ Duyệt ──► Kích hoạt PREMIUM + mở feature │
│  │            nâng cao                       │
│  └─ Từ chối ──► Vẫn ở BASE                   │
└──────────────────────────────────────────────┘
        (Đổi gói / gia hạn → quay lại vòng BASE/PREMIUM)
```

### 6.6 Luồng AI assistant (identity outside model)

```text
Buyer                FastAPI Chatbot :8000           Backend :8081        Gemini 2.5 Flash + Pinecone
  │                        │                              │                        │
  │── POST /chat (Bearer JWT, {message, session_id}) ──►│                       │
  │                        │── verify_bearer: JWKS + issuer (không enforce audience)
  │                        │── RequestContext {user_id, email, tenant, roles,
  │                        │    access_token} — giữ ngoài model
  │                        │── Gemini 2.5 Flash + 4 tools + system instruction (không PII)
  │                        │───────────────────────────────────────────────►│
  │                        │◄── Tool call (vd: list_customer_orders) ──────│
  │                        │── _validate_args (chặn tham số danh tính)
  │                        │   + max 5 bước tool loop
  │                        │── GET /api/orders (forward JWT gốc) ──────────►│
  │                        │◄── Dữ liệu order đã lọc tenant (BE enforce RBAC + tenant) ──│
  │                        │── output_validator 4 checks (tech leak /
  │                        │   instruction leak / action claim / PII fabricated)
  │                        │   Nếu fail → 1 bước retry không-tool → SAFE_FALLBACK
  │◄── Trả lời an toàn ───│
  │                        │
  Ghi chú: SessionStore trả 409 khi busy; telemetry ghi lại mỗi tool call.
```

### 6.7 Kịch bản lỗi & xử lý (Failure Handling)

| Tình huống | Hành vi hệ thống | Nơi xử lý |
| :--- | :--- | :--- |
| JWT hết hạn giữa request | Silent refresh coalesced (`refreshOnce`) → retry; fail vĩnh viễn → `failAuth()` đăng xuất | `interceptors.ts` |
| Webhook Woo replay | Dedup qua `WebhookEvent` (channel+eventId); bỏ qua trùng | `EcommerceOrderService` |
| Webhook thanh toán replay / chữ ký sai | `InboundEventLog` + `@HmacSecure` → 409 / 401 | `PaymentWebhookResource`, `HmacSignatureFilter` |
| Ghi đè đồng thời (CRUD) | `@Version` optimistic lock → 409 | BaseEntity |
| Cập nhật số dư đồng thời | `LockModeType.PESSIMISTIC_WRITE` seri hóa | `WalletService` |
| Migration lỗi | Flyway fail-fast khi khởi động; sửa bằng V-next (`out-of-order` dev) | Flyway |
| Keycloak sync lỗi | Log WARN, tiếp tục; cứu bằng `POST /api/sync/keycloak` | `KeycloakSyncService` |
| Payout thiếu bank details | Không tạo request; chờ cập nhật `bank-details` | `PayoutAutomationService` |
| Chatbot lỗi validator | Retry 1 bước không-tool → `SAFE_FALLBACK`, không hiện ảo giác | `output_validator.py` |
| Session chatbot đang bận | HTTP 409 SessionBusy | `session_store.py` |
| Duplicate key / FK violation | `ConstraintViolationExceptionMapper` → 409 | mapper |

---

## 7. Góc nhìn Triển khai (Deployment View)

### 7.1 Topology triển khai cục bộ (xác minh từ docker-compose + workflow)

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                        DEVELOPER MACHINE (host)                         │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    DOCKER COMPOSE (5 services)                     │  │
│  │  ┌──────────────┐   ┌────────────────┐   ┌───────────────────┐    │  │
│  │  │ Keycloak     │   │ MariaDB app    │   │ MariaDB wordpress │    │  │
│  │  │ :8181        │   │ :3307          │   │ (internal, ẩn)    │    │  │
│  │  │ (organization│   │ (DB v-erp-lite)│   │                   │    │  │
│  │  │  feature)    │   │                │   │                   │    │  │
│  │  └──────────────┘   └────────────────┘   └───────────────────┘    │  │
│  │  ┌─────────────────────────────┐   ┌──────────────────────────┐   │  │
│  │  │ WordPress :8090             │   │ wordpress-setup          │   │  │
│  │  │ + 2 plugin V-ERP            │   │ (one-shot, exit 0)       │   │  │
│  │  └─────────────────────────────┘   └──────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│   Backend :8081 (quarkusDev, JDWP 5005)  ── JDBC/OIDC ──► Docker      │
│   Vite Dev :5174 (proxy /api → :8081)     ── /api + JWT ──► Backend   │
│   Mock-Bank :3000 (node server.js)        ◄── webhook + REST ── Backend│
│   FastAPI AI :8000 (uvicorn)              ◄── /chat + JWT ── Vite     │
│                                                                         │
└─────────────────────────────────────┬───────────────────────────────────┘
                                      ▼
                          ┌──────────────────────┐
                          │  CLOUD (external)    │
                          │  Gemini API · Pinecone│
                          └──────────────────────┘
```

### 7.2 Cấu hình runtime & profile (xác minh từ application.properties)

| Khía cạnh | Dev (mặc định) | Test | Ghi chú |
| :--- | :--- | :--- | :--- |
| Database | `jdbc:mariadb://localhost:3307/v-erp-lite` | H2 (quarkus-test) | pool max 8, db-kind mariadb 11.4 |
| HTTP | port 8081, root-path `/api/`, host 0.0.0.0, max-body 10M | — | CORS enabled, origins `*` |
| Flyway | `migrate-at-start=true`, `baseline-on-migrate=true`, `baseline-version=0`; dev thêm `repair-at-start`, `out-of-order` | chạy đầy đủ | cấm thay đổi schema ngoài migration |
| OIDC | `auth-server-url=http://localhost:8181/realms/v-erp-lite`, client-id `v-erp-app`, `role-claim-path=realm_access/roles` | mocked | `application-type=service` |
| Keycloak Admin | `v-erp-admin`/`v-erp-admin-secret`, CLIENT_CREDENTIALS | — | admin client |
| Ecommerce | `ecommerce.health.timeout=PT3S`, `allow-private-hosts` (false prod / true dev), bootstrap dev (tenant `v-erp-app`, business `b1000000-...`, store `http://localhost:8090`, secret-reference `local`) | — | secret theo channel, không global fallback |
| SLA/scan | `orders.owner-decision-sla=PT24H`, `scan-interval=PT5M` | — | scheduler |
| Payout | `minimum-threshold-minor=100000`, `review-threshold-minor=50000000`, `risk-window=P90D`, `balance-spike-multiplier=3.0`, `bank-transfer.mode=mock`, interval PT24H | — | mock ở mọi profile (risk) |
| Log | console + `logs/application.log`; dev `hibernate-orm.log.sql=true` | console | — |

### 7.3 Bảng port chuẩn

| Port | Dịch vụ | Địa chỉ dev |
| :--- | :--- | :--- |
| 8081 | Quarkus backend (`/api/`) | localhost |
| 5174 | Vite frontend | localhost |
| 8181 | Keycloak | localhost |
| 3307 | MariaDB (app, host) | localhost |
| 8090 | WooCommerce | localhost |
| 3000 | Mock-Bank | localhost |
| 8000 | FastAPI chatbot | localhost |
| 5005 | JDWP backend (debug) | localhost |

### 7.4 Build & CI/CD (xác minh từ .github/workflows/payment-ci.yml)

```text
┌─────────────────────────────┐
│ Commit / PR → main, develop │
└──────────────┬──────────────┘
               ▼
┌──────────────────────────────────────────────┐
│ CI workflow: payment-ci.yml                  │
└──────────────┬───────────────────────────────┘
               ▼
┌──────────────────────────────────────────────┐
│ setup-java: temurin 25 + gradle cache        │
└──────────────┬───────────────────────────────┘
               ▼
┌──────────────────────────────────────────────┐
│ docker compose up -d                         │
│ (MariaDB + Keycloak)                         │
└──────────────┬───────────────────────────────┘
               ▼
┌──────────────────────────────────────────────┐
│ Wait healthchecks                            │
│ (mariadb ping + Keycloak openid-configuration)│
└──────────────┬───────────────────────────────┘
               ▼
┌──────────────────────────────────────────────────────────────┐
│ ./gradlew test — CHỈ 4 test class:                           │
│  1. WalletServiceTest                                        │
│  2. PaymentTenantIsolationTest                               │
│  3. PaymentModuleInvariantTest                               │
│  4. MigrationValidationTest                                  │
└──────────────┬───────────────────────────────────────────────┘
               ▼
┌──────────────────────────────────────────────┐
│ docker compose down -v                       │
└──────────────────────────────────────────────┘
```

- **Backend CI:** chỉ chạy subset 4 test class trên stack docker thật; **không** chạy `npm run build` frontend; **không** chạy coverage gate.
- **Frontend build:** chỉ chạy cục bộ `npm run build` (tsc -b && vite build) — là verification gate chính.
- **Chưa có:** prod env, HTTPS terminate, DB backup automation, monitoring, artifact registry, deploy tự động.

---

## 8. Các Khái niệm Xuyên suốt (Crosscutting Concepts)

### 8.1 Bảo mật (Security)

```text
┌────────────────────────────────────────────────────────────────────┐
│  XÁC THỰC & PHÂN QUYỀN                                             │
│  • Keycloak OIDC (RS256, JWKS) — realm v-erp-lite, org feature    │
│  • RBAC 4 vai: admin / operator / businessOwner / customer        │
│  • MFA TOTP: /auth/mfa/setup | verify | disable | status          │
│  • JWT: organization → tenantCode() (fallback tenant_code, azp,   │
│    'default')                                                      │
├────────────────────────────────────────────────────────────────────┤
│  CÔ LẬP DỮ LIỆU                                                    │
│  • Cột tenant_id trong mọi truy vấn get/update/delete             │
│  • CallerContext.resolveUser() / resolveBusiness()                │
│  • SubscriptionEntitlementFilter: hết hạn → chặn ghi              │
├────────────────────────────────────────────────────────────────────┤
│  TÍCH HỢP & WEBHOOK                                                │
│  • HMAC-SHA256: X-WC-Webhook-Signature (Woo) +                    │
│    X-Webhook-Signature (payment)                                  │
│  • Dedup: WebhookEvent (channel+eventId) + InboundEventLog        │
│  • EcommerceSecretResolver theo channel, không global fallback    │
├────────────────────────────────────────────────────────────────────┤
│  AI BOUNDARY                                                       │
│  • Identity outside model: JWT/PII không vào LLM                  │
│  • 4 công cụ chỉ đọc + _validate_args chặn tham số danh tính     │
│  • output_validator 4 deterministic checks                        │
└────────────────────────────────────────────────────────────────────┘
```

Nguyên tắc tối thiểu: **tenant-first**; **RBAC đúng vai trò**; **không log PII thô**; **webhook `@PermitAll` nhưng tự xác minh HMAC**; **JWT trong localStorage mirror** (biết rủi ro XSS — backlog).

### 8.2 Toàn vẹn tài chính (Financial Integrity)

| Nguyên tắc | Cơ chế | Ví dụ |
| :--- | :--- | :--- |
| Không dùng số thực | `Money` value-object, `long` minor units, `currency='VND'` enforced (V54) | `Money.of(95_000)` |
| Serial hóa số dư | `LockModeType.PESSIMISTIC_WRITE` trên Wallet | `WalletService.walletForUpdate()` |
| Dấu vết bất biến | Ledger append-only + `LedgerCheckpoint` (available/pending balance) | `LedgerCheckpointService` |
| Phân bổ chuẩn | 95% seller / 5% platform trên dòng | `EscrowService` |
| Chống trùng | Idempotency API + dedup webhook | `WebhookEvent`, `InboundEventLog`, `PaymentIdempotency` |
| Cân đối | Tổng debit = tổng credit; reconcile định kỳ | `ReconciliationService` |
| Audit | `OrderHistory` (sequence), `VendorRiskEvent`, SystemLog | mọi chuyển trạng thái có lịch sử |

### 8.3 Concurrency & Toàn vẹn dữ liệu

- **Optimistic lock** (`@Version`) cho CRUD chung → 409 khi ghi đè (BaseEntity có `version` cột).
- **Pessimistic lock** (`PESSIMISTIC_WRITE`) cho ví/ledger/payout → seri hóa critical section.
- **Scheduler `concurrentExecution=SKIP`** — tránh chạy đè khi turn trước còn dang dở.
- **DB unique constraint** cho khóa nghiệp vụ: wallet (tenant+owner+currency), webhook (channel+event).
- **Order state machine** — 13 trạng thái, chuyển trạng thái qua service có kiểm tra hợp lệ.

### 8.4 Xử lý lỗi & Ngoại lệ

- `ConstraintViolationExceptionMapper` → 409 (duplicate/FK, DB-level).
- `DebugExceptionMapper` + Bean Validation `@NotNull`/`@Size` → 400 tự động (JAX-RS).
- Lỗi API trả JSON; chatbot ẩn stack trace, trả lời fallback thân thiện.
- Không để lộ stack trace ra response; log có chủ đích.

### 8.5 i18n & Theme

- i18n: 9 namespaces, backend `/api/i18n/{locale}/{namespace}` có ETag SHA-256 (cache `ConcurrentHashMap`), localStorage `language`.
- Theme: `.dark` class, CSS var `--success`, `matchMedia('(prefers-color-scheme: dark)')`, default dark.

### 8.6 Cấu hình & Secret

- Env vars / `.env`: DB (`MARIADB_*`), Keycloak (`VITE_KEYCLOAK_*`, `KEYCLOAK_URL`), Woo (`WOOCOMMERCE_WEBHOOK_SECRETS_<REFERENCE>`, `WORDPRESS_*`), AI (`GEMINI_API_KEY`, `PINECONE_API_KEY`).
- `application.properties`: datasource, OIDC, admin client, ecommerce, payout, scheduler.
- **Cảnh báo:** Google OAuth secret committed trong `realm-v-erp-lite.json`; `mock_secret_v_erp_lite_2025` hardcode trong `mock-bank/server.js`.

### 8.7 Quan sát & Vận hành

- Health: `GET /api/health`; Woo health `GET /api/integrations/ecommerce/v1/health` (`allow-private-hosts` tùy profile).
- Scheduler: cấu hình qua env (`enableEscrowScheduler` pattern).
- `DebugDbResource` (dev-only) giúp kiểm tra nhanh.
- Chưa có metrics/APM/structured logging đầy đủ (backlog).

---

## 9. Quyết định Kiến trúc (Architecture Decisions)

> Format: **ADR — Context / Decision / Consequences / Alternatives considered**.

### ADR-01 — Modular Monolith thay vì Microservices

- **Context:** 6 dev, phạm vi "lite", cần cả ERP lẫn wallet/escrow trong một hệ thống vận hành cục bộ 1 lệnh.
- **Decision:** một Quarkus app duy nhất, phân khối theo package (`base.*`), biên giới qua service layer; không có messaging.
- **Consequences:** giao dịch phân tán nội bộ đơn giản (không cần saga cho escrow/payout); deploy 1 artifact. Nhược: không scale module độc lập.
- **Alternatives:** microservices (chi phí hạ tầng + phân tán tài chính), monolith thuần (thiếu ranh giới rõ).

### ADR-02 — Tenant isolation bằng cột `tenant_id` + guard 2 tầng

- **Context:** cần đa khách thuê; Hibernate multi-tenancy chưa stable trên Quarkus 3.x (`multitenant=NONE`).
- **Decision:** mọi entity có `tenantId`; resource check qua `CallerContext.tenantCode()`; query luôn thêm `AND tenantId = ?2`; `resolveUser()`/`resolveBusiness()` chuẩn hóa lookup.
- **Consequences:** lỗi lập trình có thể gây rò rỉ — giảm bằng quy ước + test isolation + CI (PaymentTenantIsolationTest). Chi phí index cột tenant.
- **Alternatives:** DB-per-tenant (nặng; roadmap nếu cần isolation vật lý), schema-per-tenant (chưa stable).

### ADR-03 — Tiền dùng minor units VND + value-object `Money`

- **Context:** tài chính không sai số; tiền luân chuyển qua escrow/refund/payout.
- **Decision:** chỉ VND (`currency='VND'` enforced V54), lưu `long`; `Money` value-object (1 file `base.model.Money`) bọc đơn vị.
- **Consequences:** không lỗi làm tròn; cần kiểm soát biên API (minor↔major).
- **Alternatives:** BigDecimal (dễ nhầm đơn vị), float/double (cấm).

### ADR-04 — Idempotency & dedup bất biến ở biên tích hợp

- **Context:** webhook Woo + gateway thanh toán có thể gửi lại.
- **Decision:** dedup khóa tự nhiên + `WebhookEvent` (channel+eventId) + `InboundEventLog`; API ghi idempotent.
- **Consequences:** không double-charge, không đơn trùng; chi phí lưu log + check trước xử lý.
- **Alternatives:** tin "one-shot" của bên gửi (không an toàn thực tế).

### ADR-05 — Lock 2 cấp: optimistic cho CRUD, pessimistic cho tài chính

- **Context:** cập nhật đồng thời khác nhau giữa dữ liệu nghiệp vụ và số dư tiền.
- **Decision:** BaseEntity `@Version` (409); ví/ledger dùng `PESSIMISTIC_WRITE` (`walletForUpdate`).
- **Consequences:** UX cần xử lý 409; tài chính an toàn với chi phí khóa ngắn.
- **Alternatives:** chỉ optimistic cho tiền (lệch số dư trong window); chỉ pessimistic mọi nơi (chậm).

### ADR-06 — OpenAPI-first, SDK sinh tự động (hey-api)

- **Context:** frontend/backend song song; cần contract type-safe, tránh drift.
- **Decision:** backend sinh OpenAPI 3.1.1 (`operationIdStrategy=METHOD`); frontend commit `openapi-spec.yaml`, regen SDK (`@hey-api/openapi-ts` 0.98.2); cấm `fetch` tay (6 chỗ vi phạm tồn tại — tech debt).
- **Consequences:** đổi API = đổi spec + regen + typecheck; pipeline build có chi phí.
- **Alternatives:** hợp đồng thủ công (drift), GraphQL (overkill).

### ADR-07 — Identity tập trung Keycloak (realm v-erp-lite)

- **Context:** cần OIDC, RBAC, MFA, admin, org claim, sync user.
- **Decision:** Keycloak 26.6.1, `KC_FEATURES=organization`; client `v-erp-app` (public PKCE S256) + `v-erp-admin` (service account); claim `organization` là nguồn tenant chính; IdP Google bật.
- **Consequences:** tenant được quản lý qua Keycloak Organizations; phụ thuộc vòng đời KC. Secret Google committed (risk).
- **Alternatives:** tự viết JWT (cấm), Auth0/Firebase (không local-first).

### ADR-08 — WooCommerce sync: webhook inbound + catalog sync + reconcile

- **Context:** đơn kênh ngoài phải về ERP chắc chắn, trạng thái 2 chiều, không mất đơn.
- **Decision:** webhook HMAC inbound (`@PermitAll` + verify signature) + dedup; catalog sync chủ động (`/changes`, `/summary`, `/mappings`); reconcile per `externalId`.
- **Consequences:** eventual consistency; có chi phí reconcile; dễ debug nhờ secret theo channel + WebhookEvent.
- **Alternatives:** chỉ webhook (mất nếu lệch — đã từng xảy ra), messaging (cấm OC-06).

### ADR-09 — AI assistant "identity outside model"

- **Context:** chat truy vấn dữ liệu cá nhân — nguy cơ PII rò rỉ, prompt injection, ảo giác.
- **Decision:** FastAPI giữ `RequestContext` (user_id, email, tenant, roles, access_token) ngoài model; `verify_bearer` JWKS; model chỉ có 4 tool chỉ đọc; `_validate_args` chặn tham số danh tính; output_validator 4 checks + 1 bước retry không-tool → SAFE_FALLBACK.
- **Consequences:** an toàn hơn; phụ thuộc Gemini/Pinecone; cần hạ tầng riêng.
- **Alternatives:** nhúng LLM vào backend (phụ thuộc nặng), model cầm secret (mất kiểm soát).

### ADR-10 — Mock payment đóng khung bằng seam adapter

- **Context:** phạm vi "lite" chưa có gateway thật, nhưng không khóa cứng vào mock.
- **Decision:** `PaymentGatewayService` + `BankTransferService` là seam; `VietQRService` chuẩn EMVCo; webhook `@HmacSecure` sẵn cho adapter thật; `payout.bank-transfer.mode=mock`.
- **Consequences:** thay gateway thật chỉ thêm adapter + config; **mock hoạt động mọi profile** (risk R8).
- **Alternatives:** tích hợp gateway thật ngay (chưa có sandbox/quota), hardcode VietQR (mất seam).

### ADR-11 — Scheduler thay cho event-driven cho escrow/release

- **Context:** không có messaging/WebSockets (OC-06); escrow release cần độ trễ tối đa 24h.
- **Decision:** `EscrowReleaseScheduler` cron `0 0 1 * * ?` quét escrow hết hạn; `OwnerDecisionScheduler` mỗi 5 phút; `PayoutScheduler` mỗi 24h; `concurrentExecution=SKIP`.
- **Consequences:** đơn giản, đủ tin cậy cho phạm vi; độ trễ giải ngân ≤ 24h; clock phải chuẩn UTC/Asia-HCM.
- **Alternatives:** event bus (cấm), release real-time (cần infra).

---

## 10. Yêu cầu Chất lượng (Quality Requirements)

### 10.1 Cây chất lượng (Quality Tree)

```text
┌─────────────────────────────────────────────────┐
│             CHẤT LƯỢNG V-ERP LITE               │
└──┬──────────┬──────────┬──────────┬──────────┬──┘
   │          │          │          │          │
   ▼          ▼          ▼          ▼          ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────────┐
│ BẢO MẬT │ │ TOÀN VẸN│ │ HIỆU    │ │ TÍCH HỢP│ │ KHẢ NĂNG     │
│         │ │ TÀI     │ │ NĂNG    │ │ & MỞ    │ │ BẢO TRÌ      │
│         │ │ CHÍNH   │ │         │ │ RỘNG    │ │              │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └──────┬───────┘
     │           │           │           │             │
     ▼           ▼           ▼           ▼             ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────────┐
│ OIDC    │ │ Không   │ │ API p95 │ │ OpenAPI │ │ Migration    │
│ RS256 + │ │ lỗi     │ │ < 200ms │ │ contract│ │ an toàn (66) │
│ RBAC +  │ │ làm tròn│ │ + khởi  │ │ ổn định │ │ + quy ước    │
│ MFA     │ │ + không │ │ động    │ │ + webhook│ │ code rõ      │
│ + cô lập│ │ double- │ │ Quarkus │ │ replay- │ │              │
│ tenant  │ │ charge  │ │ nhanh   │ │ safe    │ │              │
│ + AI    │ │ + audit │ │ + UI    │ │         │ │              │
│ zero-PII│ │ đầy đủ  │ │ < 200ms │ │         │ │              │
└─────────┘ └─────────┘ └─────────┘ └─────────┘ └──────────────┘
```

### 10.2 Kịch bản chất lượng (Quality Scenarios)

| ID | Kịch bản | Tiêu chí chấp nhận | Test hiện có |
| :--- | :--- | :--- | :--- |
| **QS-1** | User tenant A gọi `GET /customers/{id}` của tenant B | 404/403, không lộ dữ liệu | `PaymentTenantIsolationTest` |
| **QS-2** | 2 người bán cập nhật cùng sản phẩm | 1 request 409; UI báo "đã thay đổi" | WalletServiceConcurrencyTest (tương tự) |
| **QS-3** | 2 webhook thanh toán trùng `eventId` | Chỉ 1 lần cập nhật ví; lần 2 → 409 | `PaymentWebhookResourceTest` |
| **QS-4** | Webhook Woo gửi lại đơn | Không tạo đơn trùng; log dedup | `EcommerceOrderServiceTest` |
| **QS-5** | Hoàn tiền theo dòng khi escrow RELEASED | Cân bằng escrow ↔ ledger khớp; audit | `LedgerInvariantTest`, `LedgerCheckpointServiceTest` |
| **QS-6** | Payout vượt ngưỡng rủi ro | Request ở FLAGGED, phải admin duyệt | `PayoutServiceTest`, `PayoutAutomationServiceTest` |
| **QS-7** | Chat AI hỏi "đơn của tôi" | Chỉ dữ liệu của user đó, đúng tenant; chặn PII | `test_output_validator.py`, `test_agent_loop.py` |
| **QS-8** | `npm run build` sau khi sửa API | `tsc` không lỗi type; SDK đã regen | CI (không có frontend gate) |
| **QS-9** | Restart với migration mới | Flyway đúng thứ tự, fail-fast nếu lỗi | `MigrationValidationTest` |
| **QS-10** | Token hết hạn giữa request | Silent refresh tự động, không logout | thủ công (frontend) |
| **QS-11** | Tính tiền giỏ hàng (discount/VAT/tax) | Không sai số minor units | `MoneyServiceTest` (25) |

### 10.3 Chiến lược kiểm thử (xác minh thực tế)

| Tầng | Hiện trạng | Chi tiết |
| :--- | :--- | :--- |
| Backend unit/service | Có — **188 test / 36 file** | `./gradlew test`; tập trung payment security (29), money (25), wallet/escrow balance (14+13+4), ecommerce (11), entitlements, migration validation |
| Backend IT (quarkus-test) | Có — trong cùng suite | H2 test profile |
| CI | Hạn chế — chỉ 4 test class | `payment-ci.yml`; stack docker thật |
| Frontend typecheck | Có — gate cục bộ | `npm run build` (tsc -b && vite build) |
| Frontend unit test | Không — chưa có runner | backlog |
| E2E | Không — chưa có | backlog |
| Chatbot tests | Có — pytest (`tests/test_*.py`) | session store, request context, output validator, agent loop |
| Coverage metric | chưa báo cáo | cần CI coverage gate |

---

## 11. Rủi ro & Nợ kỹ thuật (Risks & Technical Debt)

### 11.1 Ma trận rủi ro & mitigation

| Mã | Rủi ro | Ảnh hưởng | Mức | Mitigation / trạng thái |
| :--- | :--- | :--- | :--- | :--- |
| **R1** | Google OAuth client secret committed trong `keycloak/realm-v-erp-lite.json` | Lộ khóa → chiếm quyền đăng nhập Google | **Cao** | Xoay secret, nhắc tải realm từ env/secret manager, secret scanner CI |
| **R2** | Số dư ví lệch do thiếu khóa ledger | Mất tiền, mất uy tín | **Cao** | `LedgerCheckpointService` + `ReconciliationService` + test invariant (LedgerInvariantTest, SellerPendingBalanceTest) |
| **R3** | Cô lập tenant bị vi phạm bởi lỗi lập trình | Rò rỉ dữ liệu chéo tenant | **Cao** | Quy ước `AND tenantId`; `PaymentTenantIsolationTest`; review; mở rộng test phủ mọi resource |
| **R4** | Webhook Woo/gateway mất mát (missed delivery) | Đơn/payment không về | **Trung bình** | Dedup + reconcile per externalId; `EcommerceOrderResource` có `/reconcile`, retry |
| **R5** | Hardcode URL/localhost (`.env` frontend `VITE_API_BASE_URL=http://localhost:8081`; chatbot tool gọi `http://localhost:8081` cứng) | SAI ở môi trường khác | **Trung bình** | Chuyển sang env của môi trường; `V_ERP_API_BASE_URL` đã env hóa ở WordPress |
| **R6** | AI prompt injection / ảo giác | Trả lời sai hoặc lộ dữ liệu | **Cao** | 4-tầng validator + tools chỉ đọc + `_validate_args`; test adversarial trong pytest |
| **R7** | Escrow/payout chạy sai khi clock lệch | Release sớm/trễ | **Trung bình** | `Instant`/UTC + timezone Asia/Ho_Chi_Minh; cron 1:00; test ngày biên |
| **R8** | Mock payment/`bank-transfer.mode=mock` chạy mọi profile | "Tiền ảo" ngoài ý muốn ở prod | **Cao** | Khoá mock theo env; flag production; adapter thật |
| **R9** | CORS `*` trong prod | Truy cập chéo origin | **Cao** | Cấu hình origin allowlist theo env; test prod CORS |
| **R10** | Voucher/giảm giá exploit (code đoán, cap thiếu) | Lỗ doanh thu | **Trung bình** | Mã khó đoán + unique DB + giới hạn dùng; audit |
| **R11** | Database thiếu backup tự động | Mất dữ liệu tài chính | **Cao** | Backup script + test restore (backlog) |
| **R12** | JWT lưu localStorage (mirror `access_token`/`refresh_token`) | XSS → đánh cắp token | **Trung bình** | Token ngắn hạn + refresh rotation; khóa XSS (sanitize chat-widget `dangerouslySetInnerHTML`); cân nhắc cookie+CSRF sau |
| **R13** | `chat-widget.tsx` dùng `dangerouslySetInnerHTML` render AI response | XSS qua AI response | **Trung bình** | Sanitize HTML phía client trước khi render; validator chặn HTML `<script>` |
| **R14** | Frontend có 6 chỗ `fetch` tay (bỏ qua interceptors/SDK) | Mất 401-retry, lệch contract | **Trung bình** | Thanh lý dần về SDK (tech debt T2) |

### 11.2 Backlog nợ kỹ thuật (Prioritized)

| Ưu tiên | Hạng mục | Lý do |
| :--- | :--- | :--- |
| **P0** | Rotate Google OAuth secret + đưa realm secrets ra ngoài repo | R1 |
| **P0** | Env hóa URL backend trong chatbot tools + frontend `.env`; loại hardcode | R5 |
| **P0** | Khoá mock payment/bank-transfer theo env; flag production | R8 |
| **P0** | CORS allowlist theo môi trường | R9 |
| **P1** | CI: chạy đầy đủ `./gradlew test` (188 test) thay vì 4 class; thêm frontend build gate | QS-8, R3 |
| **P1** | Coverage gate + báo cáo coverage | R2 |
| **P1** | Hoàn thiện test escrow/refund/payout biên + phủ tenant isolation mọi resource | R2, R3, R7 |
| **P1** | E2E test cốt lõi (checkout, payout, admin) | chất lượng |
| **P1** | Frontend unit test runner (Vitest) | QS-8 |
| **P1** | Sanitize chat-widget HTML + hạn chế `dangerouslySetInnerHTML` | R13 |
| **P2** | Backup tự động + test restore MariaDB | R11 |
| **P2** | Metrics/APM + structured logging | quan sát |
| **P2** | Thanh lý 6 chỗ `fetch` tay về SDK | ADR-06, R14 |
| **P2** | HTTPS terminate + reverse proxy prod | bảo mật |
| **P3** | Hợp nhất `docs/ARCHITECTURE_ARC42.md` cũ — tránh hai nguồn sự thật | maintainability |

---

## 12. Thuật ngữ (Glossary)

| Thuật ngữ | Định nghĩa |
| :--- | :--- |
| **Arc42** | Khuôn mẫu tài liệu kiến trúc phần mềm 12 phần. |
| **Tenant** | Khách thuê (organization) cô lập dữ liệu bằng `tenant_id`; trong Keycloak là một Organization. |
| **Business / Gian hàng** | Đơn vị bán trong tenant; owner quản lý. |
| **Escrow** | Tạm giữ tiền sau checkout (7 ngày) trước khi giải ngân cho seller. |
| **Wallet** | Ví nội bộ: `SELLER` / `ADMIN` / `PLATFORM`. |
| **Ledger** | Sổ cái append-only ghi mọi biến động số dư; `LedgerCheckpoint` chốt số dư định kỳ. |
| **Payout** | Rút tiền từ ví SELLER (mock bank-transfer); status: AUTO_APPROVED/FLAGGED/APPROVED/REJECTED/PAID. |
| **Minor units** | Đơn vị nhỏ nhất của tiền tệ (1 VNĐ); tránh sai số. |
| **Money** | Value-object bọc số tiền VND (minor units) trong `base.model.Money`. |
| **PaymentIntent** | Tham chiếu thanh toán có trạng thái, dùng cho top-up/đơn. |
| **Idempotency** | Khả năng xử lý trùng request mà không gây tác dụng phụ lặp. |
| **OIDC / PKCE** | OpenID Connect + Proof Key for Code Exchange (SPA auth). |
| **JWT RS256** | Token chữ ký bất đối xứng do Keycloak cấp. |
| **RBAC** | Role-Based Access Control: admin / operator / businessOwner / customer. |
| **MFA TOTP** | Xác thực 2 lớp bằng mã xoay vòng theo thời gian. |
| **HMAC-SHA256** | Chữ ký tin cậy cho webhook (WooCommerce, gateway). |
| **Flyway** | Công cụ migration schema bằng file SQL có version (V1→V66). |
| **PanacheEntityBase** | Base class JPA của Quarkus (active record kiểu Panache). |
| **WooCommerce** | Plugin e-commerce của WordPress dùng làm kênh bán ngoài; 2 plugin V-ERP (catalog-connector, woo-checkout). |
| **VietQR / EMVCo** | Chuẩn QR thanh toán cho các app ngân hàng VN. |
| **MCP-style** | Cách định nghĩa công cụ AI theo chuẩn Model Context Protocol-like. |
| **Identity outside model** | Kiến trúc AI: JWT/PII nằm ngoài LLM; model chỉ gọi tool có ngữ cảnh hẹp. |
| **HELD/RELEASED/REFUNDED** | Trạng thái escrow: đang giữ / đã giải ngân / đã hoàn. |
| **BASE / PREMIUM** | Gói đăng ký; BASE có trial 14 ngày; feature gates qua EntitlementService. |
| **Order statuses** | 13 trạng thái: PENDING_PAYMENT, PAID_ESCROW, OWNER_ACCEPTED, FULFILLING, DELIVERED, COMPLETED, OWNER_DECLINED, REFUNDED, PENDING, CONFIRMED, SHIPPING, SHIPPED, CANCELLED. |

---

## Phụ lục A — Quy ước code

- **Backend:** Java 24/25, 4-space indent, braces cùng dòng; Lombok `@Getter/@Setter`; `ThingResource`/`ThingService`; một top-level public type / file; không DTO trừ integration.
- **Khoá truy vấn tenant:** mọi `find("id = ?1 and tenantId = ?2", ...)`; `CallerContext.resolveUser()` thay duplicate `User.find(email, tenantId)`.
- **REST:** path số nhiều; root `/api/`; exception mapper cho 409/400.
- **DB:** chỉ đổi schema qua migration (V-next); JSONB dùng `@JdbcTypeCode(SqlTypes.JSON)` + `format.global=ignore`.
- **Frontend:** TS strict; SDK sinh; không `fetch` tay (đang có 6 vi phạm); không Radix; `text-success` cho màu thành công.
- **i18n:** thêm key đủ 9 namespace; không chuỗi trần.

## Phụ lục B — Runbook vận hành

```bash
# 1) Khởi động hạ tầng (MariaDB + Keycloak + WordPress)
docker compose up -d

# 2) Backend dev (terminal 2)
./gradlew quarkusDev            # :8081, JDWP 5005

# 3) Frontend dev (terminal 3)
cd frontend && npm run dev      # :5174

# 4) Mock-bank (terminal 4, tùy chọn — dùng khi test thanh toán)
node mock-bank/server.js        # :3000

# 5) Chatbot (terminal 5, tùy chọn)
# đặt GEMINI_API_KEY, PINECONE_API_KEY trong ecommerce-chatbot/.env
uvicorn mcp_server.server:app --reload --port 8000   # từ thư mục ecommerce-chatbot

# 6) Regen SDK sau khi đổi API backend
curl -s http://localhost:8081/api/q/openapi.yaml -o frontend/openapi-spec.yaml
cd frontend && npm run generate-api

# 7) Verify build frontend (gate bắt buộc)
cd frontend && npm run build

# 8) Test backend (cục bộ)
./gradlew test

# 9) Cứu Keycloak sync lỗi
curl -X POST http://localhost:8081/api/sync/keycloak

# 10) Xóa cache Vite nếu lỗi dynamic import
rm -rf frontend/node_modules/.vite
```

## Phụ lục C — Bảng liệt kê Flyway Migrations (đầy đủ, V1→V66)

| Phạm vi | Migration | Mục đích |
| :--- | :--- | :--- |
| **Schema nền** | V1 | Cấu trúc bảng ban đầu |
| **User & security** | V2, V3, V3_1, V8 | Profile fields, MFA, `keycloak_id` nullable, cột `version` |
| **Seed & sync** | V4, V7, V16, V28, V35, V51, V52, V55 | Seed users/tenant, multi-vendor sample, owner products, demo bank, business owners |
| **Settings** | V5 | `tenant_settings`, `user_settings` |
| **Catalog** | V9, V10, V11_1 | Product/variant/options/tags, inventory tracking |
| **Orders & vouchers** | V12_1, V13, V14, V15, V16_1, V17, V19, V23, V24, V25, V26, V27, V33, V34, V35_1, V36, V40, V44, V49, V50, V55, V56, V57, V59 | orders, order_history, customer role, soft delete, memberships, business link, cart, VAT/discount minor units, owner deadline, addresses, return/cancel fields, sequence |
| **Money & tài chính** | V18, V20, V22, V39, V46, V54 | payment/wallet tables, ledger index, migrate minor units, USD→VND, revenue adjustments, enforce VND |
| **Webhook & ecommerce** | V21, V30, V31, V32 | webhook_events, sales channels, external mappings, dedup |
| **Escrow/payout** | V37, V41, V42, V42_1, V43, V45, V45_1, V46_1, V58 | bank details, bank catalog, link wallets↔banks/users, topups, idempotency (track3), migrate escrow→platform wallet |
| **Notification** | V47, V48 | notification recipients |
| **Subscription** | V60, V61, V62, V63, V64, V65, V66 | tenant subscriptions, BASE/PREMIUM rename, operating capabilities, payments, retire PLUS, failed payments, base online payments |
| **Cleanup** | V26 | bỏ cột orphan |

> Quy ước: underscore version (`V3_1`, `V11_1`, `V35_1`, `V42_1`, `V45_1`, `V46_1`) để chèn giữa chuỗi; **V6 bị bỏ (không tồn tại)**; chạy `migrate-at-start` + `baseline-on-migrate`; dev bật `repair-at-start` + `out-of-order`.

---

## Phê duyệt bản phát hành

| Vai trò | Chức danh | Họ và tên | Ngày | Chữ ký |
| :--- | :--- | :--- | :--- | :--- |
| **Soạn thảo** (Author) | Architecture Documentation | | 2026-08-11 | |
| **Rà soát** (Reviewer) | Architecture Team | | | |
| **Phê duyệt** (Approver) | | | | |

> **Tuyên bố bảo mật:** Tài liệu này thuộc phạm vi phân phối nội bộ. Không sao chép hoặc phổ biến ra ngoài phạm vi khi chưa được phê duyệt. Các cảnh báo nhạy cảm về bảo mật trong tài liệu (secret OAuth, mock secret, CORS) chỉ phục vụ mục đích nội bộ và phải được xử lý trước khi đưa lên môi trường production.

---

*Hết tài liệu (bản 3.0, tái kiểm chứng mã nguồn 2026-08-11). Mọi thay đổi kiến trúc nên cập nhật tài liệu này và ADR tương ứng.*





# Kế hoạch Thiết kế & Triển khai Hệ thống Thanh toán Multi-vendor (V-ERP Lite)

Tài liệu này trình bày giải pháp kiến trúc và kế hoạch triển khai chi tiết cho hệ thống Thanh toán (Payment System) đa nhà bán hàng (Multi-vendor) tích hợp trực tiếp vào dự án **V-ERP Lite** (Backend: Quarkus, Frontend: React/Vite, Database: MariaDB, Auth: Keycloak).

---

## 0. Quy Ước Thuật Ngữ (Glossary)

Các thuật ngữ tài chính/kỹ thuật khó đọc sẽ ghi kèm tiếng Anh ngay lần đầu hoặc tại các phần quan trọng:

* Ví (wallet): tài khoản số dư nội bộ của Seller/Admin/Platform.
* Sổ cái (ledger): lịch sử ghi nhận toàn bộ biến động tiền.
* Bút toán (journal entry / ledger entry): một dòng ghi nhận nghiệp vụ tài chính trong sổ cái.
* Kế toán kép (double-entry bookkeeping): mô hình ghi nhận mỗi nghiệp vụ bằng các bút toán cân bằng giữa các tài khoản.
* Tạm giữ tiền (escrow): tiền đã thanh toán nhưng chưa giải ngân cho Seller.
* Giải ngân (release / disbursement): chuyển tiền từ trạng thái tạm giữ sang số dư khả dụng.
* Rút tiền (payout): Seller yêu cầu chuyển tiền từ ví nội bộ về tài khoản ngân hàng.
* Đối soát (reconciliation): kiểm tra số dư tổng hợp có khớp với lịch sử giao dịch hay không.
* Đơn vị tiền nhỏ nhất (minor unit): đơn vị lưu tiền dạng số nguyên, ví dụ 1 USD = 100 cents.
* Điểm chốt số dư (checkpoint): snapshot số dư tại một mốc giao dịch để tăng tốc truy vấn.
* Nguồn sự thật (source of truth): dữ liệu gốc được tin cậy khi kiểm toán/đối soát.
* Ý định thanh toán (PaymentIntent): bản ghi đại diện cho một lần Buyer có ý định thanh toán trước khi gateway xác nhận thành công.
* Tính bất biến khi gọi lại (idempotency): cùng một request/webhook gửi lặp lại vẫn chỉ tạo một kết quả nghiệp vụ.
* Cô lập tenant (tenant isolation): dữ liệu của tenant này không được đọc/ghi chéo sang tenant khác.
* Trạng thái hữu hạn (state machine): danh sách trạng thái hợp lệ và các bước chuyển trạng thái được phép.
* Cờ tính năng (feature flag): công tắc bật/tắt tính năng mà không cần tách nhánh code dài ngày.

---

## 1. Phân Tích Rủi Ro & Thách Thức Kỹ Thuật (Problems Analysis)

Áp dụng hệ thống tài chính vào một codebase Multi-tenant, "Lite" (không sử dụng Redis, Message Queue) mang lại các thách thức lớn sau:

### 1.1. Tranh Chấp Tài Nguyên (Resource Contention) & Race Conditions
* **Thách thức:** Khi xảy ra giao dịch đồng thời (concurrent transactions), ví dụ nhận tiền từ đơn hàng hoàn tất cùng lúc với yêu cầu rút tiền (payout), hai luồng xử lý có thể đọc số dư cũ, tính toán và ghi đè số dư mới dẫn đến thất thoát tiền hoặc âm tài khoản.
* **Điểm yếu hiện tại:** Dự án hiện tại sử dụng Hibernate Panache và chỉ dùng `@Version`, tức khóa lạc quan (Optimistic Locking). Khóa lạc quan sẽ ném ra `OptimisticLockException` và rollback nếu có va chạm, điều này tốt cho dữ liệu cấu hình nhưng đối với ví tiền (wallet) chịu tải cao, tỷ lệ lỗi giao dịch sẽ rất lớn, gây trải nghiệm tệ.
* **Giải pháp:** Sử dụng **khóa bi quan (Pessimistic Locking)** khi cập nhật ví tiền (wallet):
  ```java
  Wallet wallet = entityManager.find(Wallet.class, walletId, LockModeType.PESSIMISTIC_WRITE);
  ```
  Thao tác này tương đương lệnh `SELECT ... FOR UPDATE` trong MariaDB, chặn tất cả các luồng khác đọc/ghi dòng này cho đến khi transaction hiện tại kết thúc. Đồng thời bổ sung constraint ở DB: `CHECK (available_balance >= 0)`.

### 1.2. Tính Trùng Lặp (Duplicate Handling) & Idempotency
* **Thách thức:** Cổng thanh toán (Stripe, VNPay, MoMo) có cơ chế gọi lại Webhook nhiều lần nếu không nhận được phản hồi 200 OK ngay lập tức. Khách hàng cũng có thể nhấn nút "Thanh toán" hai lần liên tiếp.
* **Điểm yếu hiện tại:** JAX-RS Endpoints hiện tại chưa có cơ chế kiểm tra trùng lặp (Idempotency check).
* **Giải pháp:** Xây dựng bảng `payment_idempotency` lưu trữ `idempotency_key` (hoặc `gateway_transaction_id`). Mọi yêu cầu thanh toán/webhook phải đi qua bộ lọc kiểm tra khóa này:
  1. Nếu khóa đã tồn tại với trạng thái `SUCCESS`, trả về ngay kết quả đã lưu.
  2. Nếu khóa đang được xử lý (`PROCESSING`), chặn hoặc trả về lỗi chờ.
  3. Nếu chưa có, tạo mới và tiếp tục xử lý trong Transaction.

### 1.3. Tính Toàn Vẹn Sổ Cái (Ledger Integrity) & Kế Toán Kép (Double-Entry Bookkeeping)
* **Thách thức:** Việc cộng trừ số dư trực tiếp trên bảng `Wallet` rất dễ bị sai lệch và không thể kiểm toán (audit).
* **Giải pháp:** Thiết kế theo mô hình **sổ cái (ledger)**. Số dư ví (wallet balance) không bao giờ được thay đổi bằng `setter` tự do. Mọi biến động số dư phải được thực hiện qua `WalletService` và luôn đi kèm với việc tạo bút toán (journal entry / ledger entry) tương ứng trong bảng `wallet_transactions`. Số dư ví phải luôn bằng tổng (sum) các giao dịch trong lịch sử ví.

### 1.4. Xử Lý Webhook & Quản Lý Vòng Đời Tạm Giữ Tiền (Escrow Lifecycle)
* **Thách thức:** Tiền thanh toán của User phải được giữ ở trạng thái số dư tạm giữ `pending_balance` (escrow/pending balance) và chỉ chuyển sang số dư khả dụng `available_balance` (available balance) của Seller khi đơn hàng hoàn thành (`COMPLETED`), hoặc hoàn lại (refund) cho User nếu đơn bị hủy (`CANCELLED`).
* **Giải pháp:** Thiết kế bảng `escrow_records` để quản lý. Sử dụng cơ chế:
  - **Event-driven:** Lắng nghe cập nhật trạng thái đơn hàng để giải ngân (release/disbursement) hoặc hoàn tiền (refund).
  - **Scheduled Release:** Sử dụng `@Scheduled` của Quarkus để quét hàng ngày các khoản tạm giữ (escrow holds) đã quá hạn khiếu nại (ví dụ: 7 ngày kể từ lúc giao hàng) để tự động giải ngân (auto release).

### 1.5. Multi-tenancy & Tenant Isolation
* **Thách thức:** Đây là hệ thống tài chính trong codebase multi-tenant, nên lỗi lọc tenant có thể làm lộ số dư, giao dịch, webhook hoặc payout giữa các tenant khác nhau.
* **Nguyên tắc bắt buộc:**
  - Tất cả bảng tài chính (`wallets`, `wallet_transactions`, `ledger_checkpoints`, `escrow_records`, `payment_intents`, `payment_transactions`, `payout_requests`, `payment_idempotency`) phải có `tenant_id`.
  - Mọi unique constraint nghiệp vụ phải scoped theo tenant, ví dụ `UNIQUE (tenant_id, owner_type, owner_id, currency)` cho ví và `UNIQUE (tenant_id, idempotency_key)` cho idempotency.
  - Tất cả query trong service/resource phải lấy `tenantId` từ security context, không nhận `tenant_id` từ request body/query param của client.
  - Webhook từ gateway phải resolve được tenant qua `payment_intent_id`, `gateway_transaction_id` hoặc metadata đã ký (signed metadata), tuyệt đối không tin tenant do payload ngoài gửi lên nếu chưa xác thực chữ ký (signature verification).
  - Bổ sung test cô lập tenant (tenant isolation): cùng `owner_id`/`order_id` giả lập ở 2 tenant khác nhau không được đọc/chỉnh sửa chéo dữ liệu.

### 1.6. PaymentIntent-style Flow
* **Thách thức:** Nếu tạo `PaymentTransaction` trực tiếp khi Buyer bấm thanh toán, hệ thống dễ bị rối trạng thái giữa các lần retry, đổi phương thức thanh toán, webhook đến muộn hoặc khách mở nhiều tab checkout.
* **Giải pháp:** Áp dụng mô hình giống **Stripe PaymentIntent**: tạo một thực thể `payment_intents` đại diện cho ý định thanh toán của một đơn hàng trước khi có giao dịch gateway thật.
  - `PaymentIntent` là aggregate chính cho checkout: chứa `tenant_id`, `order_id`, `customer_id`, `amount_minor`, `currency`, `status`, `expires_at`, `idempotency_key`, `metadata`.
  - `PaymentTransaction` là attempt/gateway event gắn với `payment_intent_id`, có thể có nhiều bản ghi nếu Buyer retry hoặc đổi gateway.
  - State machine đề xuất: `REQUIRES_PAYMENT_METHOD` -> `REQUIRES_CONFIRMATION` -> `REQUIRES_ACTION` (nếu cần 3DS/QR/redirect) -> `PROCESSING` -> `SUCCEEDED`; nhánh phụ gồm `REQUIRES_CAPTURE` (nếu authorize/capture tách riêng), `FAILED`, `CANCELLED`, `EXPIRED`.
  - Split payment và escrow chỉ được kích hoạt đúng một lần khi `PaymentIntent` chuyển sang `SUCCEEDED`.
  - Webhook xử lý theo `payment_intent_id` nội bộ, không xử lý trực tiếp theo `order_id` để tránh double charge/double release.

### 1.7. Sổ Cái Chỉ Ghi Thêm (Append-only Ledger) & Điểm Chốt (Checkpoint)
* **Thách thức:** Sổ cái (ledger) là nguồn sự thật tài chính (source of truth). Nếu cho phép update/delete bút toán (ledger entry), hệ thống mất khả năng audit và rất khó đối soát (reconciliation) khi có lỗi.
* **Nguyên tắc bắt buộc:** Bảng `wallet_transactions` là **chỉ ghi thêm (append-only)**:
  - Không update/delete giao dịch đã ghi. Muốn sửa sai phải tạo giao dịch đảo (reversal transaction) hoặc giao dịch điều chỉnh (adjustment transaction) mới có `reference_type`, `reference_id`, `correction_of_transaction_id`.
  - Mỗi nghiệp vụ tài chính phải tạo đủ cặp bút toán (journal entries) theo kế toán kép (double-entry) hoặc tối thiểu phải có bất biến cân bằng (balance invariant) rõ ràng giữa platform, seller, escrow, payout clearing.
  - Service layer không expose setter thay đổi amount/status của ledger entry sau khi đã commit.
  - Database nên chặn update/delete bằng trigger hoặc privilege hạn chế nếu phù hợp với môi trường triển khai.
* **Điểm chốt (checkpoint) để tăng tốc:** Thêm bảng `ledger_checkpoints` lưu snapshot số dư theo ví tại một mốc `last_transaction_id`/`last_sequence`. Khi truy vấn số dư hoặc đối soát (reconciliation), hệ thống tính từ checkpoint gần nhất + các transaction mới sau checkpoint, thay vì sum toàn bộ lịch sử.

### 1.8. Kiểu Dữ Liệu Tiền Tệ (Money Type) & Làm Tròn (Rounding)
* **Thách thức:** Dùng `double`/`float` cho tiền sẽ gây lỗi làm tròn (rounding error), đặc biệt khi tính hoa hồng (commission), hoàn tiền một phần (partial refund) hoặc chia tiền multi-vendor.
* **Quy định:** Tuyệt đối không dùng `float`/`double` cho tiền.
  - Ưu tiên lưu DB bằng `BIGINT amount_minor` theo đơn vị tiền nhỏ nhất (minor unit), ví dụ USD có exponent 2 thì `$10.25` lưu là `1025`; VND có exponent 0 thì `10,000 VND` lưu là `10000`.
  - Nếu cần dùng `BigDecimal` ở Java cho tính toán tỷ lệ, phải chuẩn hóa qua `Money` value object, quy định scale theo currency exponent và rounding mode rõ ràng (`HALF_UP` hoặc policy do business quyết định).
  - DB schema phải có `currency CHAR(3)` và các cột tiền đặt tên rõ theo quy ước `*_amount_minor`, `*_balance_minor`.
  - Commission 5% phải tính qua utility tập trung, có test cho các case lẻ cent, refund một phần, multi-currency và currency exponent 0/2.

### 1.9. Phát Triển Dựa Trên Trunk (Trunk-based Development) cho Payment Module
* **Thách thức:** Payment/ledger có phạm vi ảnh hưởng lớn (large blast radius). Nếu mỗi nhóm làm feature branch dài ngày rồi merge cuối sprint, xung đột migration, entity và API rất dễ làm vỡ bất biến tài chính (financial invariant).
* **Quy trình đề xuất:**
  - Làm việc trên trunk/main với nhánh ngắn sống dưới 1 ngày; PR nhỏ, merge liên tục sau khi CI xanh.
  - Tính năng lớn phải đi sau cờ tính năng (feature flag) hoặc trạng thái API chưa bật trên UI, tránh giữ nhánh dài.
  - Migration phải forward-only, không sửa migration đã merge; nếu cần chỉnh schema thì tạo migration mới.
  - Mỗi PR chạm vào ledger/payment phải kèm test bất biến (invariant) tối thiểu: idempotency, tenant isolation, append-only ledger, no negative balance.
  - CI bắt buộc chạy backend tests, migration validation và frontend typecheck trước khi merge.

---

## 2. Thiết Kế Database Schema (Flyway Migration V16)

Để hiện thực hóa mô hình Multi-vendor, ta cần bổ sung mối quan hệ giữa sản phẩm với người bán (Business) và thiết lập hệ thống Ví/Giao dịch:

erDiagram
    business ||--o{ products : "owns"
    business ||--o{ orders : "receives"
    business ||--|| wallets : "has"
    wallets ||--o{ wallet_transactions : "records"
    wallets ||--o{ ledger_checkpoints : "snapshots"
    orders ||--o{ escrow_records : "secures"
    orders ||--o{ payment_intents : "initiates"
    payment_intents ||--o{ payment_transactions : "attempts"
    wallets ||--o{ payout_requests : "requests"
    
    products {
        UUID id PK
        string tenant_id
        UUID business_id FK
        string sku
        string name
        long base_price_minor
        string currency
    }
    
    orders {
        UUID id PK
        string tenant_id
        UUID customer_id FK
        UUID business_id FK
        string order_number
        string status
        long final_amount_minor
        string currency
    }
    
    wallets {
        UUID id PK
        string tenant_id
        string owner_type
        UUID owner_id
        long available_balance_minor
        long pending_balance_minor
        string currency
        long version
    }
    
    wallet_transactions {
        UUID id PK
        string tenant_id
        UUID wallet_id FK
        long amount_minor
        string type
        string transaction_type
        string reference_type
        UUID reference_id
        UUID correction_of_transaction_id
        long sequence_no
        string description
        timestamp created_at
    }

    ledger_checkpoints {
        UUID id PK
        string tenant_id
        UUID wallet_id FK
        UUID last_transaction_id FK
        long last_sequence_no
        long available_balance_minor
        long pending_balance_minor
        timestamp checkpointed_at
    }
    
    escrow_records {
        UUID id PK
        string tenant_id
        UUID order_id FK
        UUID seller_wallet_id FK
        long amount_minor
        string currency
        string status
        timestamp release_due_at
    }

    payment_intents {
        UUID id PK
        string tenant_id
        UUID order_id FK
        UUID customer_id FK
        long amount_minor
        string currency
        string status
        string idempotency_key
        timestamp expires_at
    }
    
    payment_transactions {
        UUID id PK
        string tenant_id
        UUID payment_intent_id FK
        string gateway
        string gateway_transaction_id
        long amount_minor
        string currency
        string status
    }
    
    payout_requests {
        UUID id PK
        string tenant_id
        UUID wallet_id FK
        long amount_minor
        string currency
        string status
        string bank_name
        string bank_account_number
        string bank_account_holder
    }

**Ràng buộc schema bắt buộc:**
* Các cột tiền dùng `BIGINT *_minor` và `currency CHAR(3)`, không dùng `FLOAT`/`DOUBLE`.
* `wallet_transactions` là chỉ ghi thêm (append-only). Cần cân nhắc trigger chặn `UPDATE`/`DELETE` hoặc phân quyền DB để service chỉ được insert.
* Các khóa unique phải có `tenant_id`, ví dụ:
  - `wallets`: `UNIQUE (tenant_id, owner_type, owner_id, currency)`.
  - `payment_intents`: `UNIQUE (tenant_id, idempotency_key)`.
  - `payment_transactions`: `UNIQUE (tenant_id, gateway, gateway_transaction_id)`.
  - `ledger_checkpoints`: index `(tenant_id, wallet_id, last_sequence_no)`.
* Các bảng tài chính phải có index theo `(tenant_id, created_at)` hoặc `(tenant_id, status)` tùy truy vấn vận hành để tránh scan chéo tenant.

---

## 3. Kế Hoạch Triển Khai 4 Tuần (4-Week Execution Plan)

### Sprint 1: Thiết Kế Database, Lõi Sổ Cái (Core Ledger) & Mô Hình Hóa Multi-vendor
* **Mục tiêu:** Hoàn thành hạ tầng cơ sở dữ liệu, phân rã sản phẩm/đơn hàng theo Seller (Business), dựng lõi giao dịch ví (wallet) và sổ cái (ledger).

* **Phân bổ công việc:**
  * **Dev 1 (Tech Lead / DB Arch):**
    - Viết Flyway Migration `V16__add_payment_and_wallet_tables.sql` tạo các bảng: `wallets`, `wallet_transactions`, `ledger_checkpoints`, `escrow_records`, `payout_requests`, `payment_intents`, `payment_transactions`, `payment_idempotency`.
    - Chuẩn hóa schema multi-tenant: mọi bảng tài chính có `tenant_id`, unique/index đều scoped theo tenant.
    - Chuẩn hóa kiểu tiền (money type): dùng `BIGINT *_minor` + `currency CHAR(3)` ở DB; không dùng `FLOAT`/`DOUBLE`.
    - Viết Flyway Migration `V17__link_product_and_order_to_business.sql` bổ sung `business_id` vào `products` và `orders`.
    - Tạo các Entity Java tương ứng kế thừa `BaseEntity` (hoặc `PanacheEntityBase` tùy bảng hệ thống): `Wallet`, `WalletTransaction`, `LedgerCheckpoint`, `EscrowRecord`, `PayoutRequest`, `PaymentIntent`, `PaymentTransaction`.
  * **Dev 2 (Backend Core):**
    - Hiện thực hóa `WalletService` với các phương thức cốt lõi sử dụng khóa bi quan (Pessimistic Locking):
      - `createWallet(ownerType, ownerId)`
      - `creditWalletPending(walletId, amount, txnType, refType, refId)` (nhận tiền tạm giữ / credit pending balance)
      - `releaseEscrow(escrowId)` (giải ngân escrow / chuyển từ pending sang available)
      - `debitWalletAvailable(walletId, amount, txnType, refType, refId)` (trừ số dư khả dụng / debit available balance khi rút hoặc hoàn tiền)
    - Tạo `Money` value object hoặc utility chuẩn hóa `BigDecimal` -> đơn vị tiền nhỏ nhất (minor units), currency exponent và rounding policy.
    - Đảm bảo `WalletService` chỉ append bút toán (ledger entry); không update/delete bản ghi trong `wallet_transactions`.
  * **Dev 3 (Backend Integration):**
    - Thiết kế cơ chế xử lý Idempotency: Tạo `IdempotencyFilter` hoặc `IdempotencyService` lưu trữ và xác thực khóa giao dịch.
    - Thiết kế `PaymentIntentService`: tạo/cancel/expire/confirm PaymentIntent và quản lý state machine thanh toán.
    - Tạo Mock Payment Gateway Service để giả lập việc thanh toán thẻ qua VNPay/Stripe mà không cần gọi API thật ở giai đoạn đầu.
  * **Dev 4 (Frontend UX - Buyer):**
    - Khảo sát mã nguồn Giỏ hàng/Thanh toán hiện tại (`frontend/src/pages/cart-checkout-page.tsx`).
    - Phác thảo lại giao diện Checkout hỗ trợ hiển thị danh sách sản phẩm gom theo từng Seller/Cửa hàng.
  * **Dev 5 (Frontend Dashboard - Seller/Admin):**
    - Thiết lập khung giao diện quản lý ví (wallet) cho Seller và màn hình duyệt rút tiền (payout approval) cho Admin.
  * **Dev 6 (QA/QC & DevOps):**
    - Thiết lập môi trường CI chạy Docker Compose với DB MariaDB và Keycloak phục vụ test tự động.
    - Viết Integration Test ban đầu cho `WalletService` để kiểm tra luồng nạp/rút số dư cơ bản.
    - Thêm CI guard cho payment module: migration validation, tenant isolation test, bất biến ledger append-only (append-only invariant) và money rounding test.

---

### Sprint 2: Core Wallet Logic, Xử Lý Tạm Giữ (Escrow) & Chia Dòng Tiền (Split Payment)
* **Mục tiêu:** Hoàn thiện logic nghiệp vụ ví (wallet), tự động chia tiền (split payment) sau khi thanh toán thành công và quản lý tạm giữ tiền (escrow).

* **Phân bổ công việc:**
  * **Dev 2 (Backend Core) - Chủ trì Sprint:**
    - Phát triển logic **chia dòng tiền (Split Payment)** trong `PaymentService`: Khi `PaymentIntent` chuyển sang `SUCCEEDED`, tự động chia `final_amount_minor`:
      - 5% hoa hồng nền tảng (platform commission) -> Chuyển vào ví (wallet) Admin/Platform.
      - 95% -> Chuyển vào ví Seller dưới dạng số dư tạm giữ `pending_balance` (pending balance) thông qua `EscrowRecord`.
    - Đảm bảo split payment chỉ chạy một lần bằng idempotency key và trạng thái PaymentIntent, kể cả khi webhook gateway gửi lặp.
    - Viết logic giải ngân tiền tạm giữ (escrow release) qua `releaseEscrow`: Cập nhật trạng thái `EscrowRecord` thành `RELEASED`, giảm `pending_balance` và tăng `available_balance` của ví Seller.
  * **Dev 1 (Tech Lead / DB Arch):**
    - Xây dựng Quarkus Scheduler (`EscrowReleaseScheduler`) quét định kỳ mỗi ngày các `escrow_records` có `release_due_at <= NOW()` và `status = 'HELD'` để tự động hoàn tất và giải ngân (auto release).
    - Viết `LedgerCheckpointService` tạo điểm chốt (checkpoint) định kỳ theo ví sau mỗi N giao dịch hoặc theo lịch, phục vụ truy vấn số dư nhanh.
    - Viết các câu query JPA tối ưu để tổng hợp số dư sổ cái (ledger balance) từ checkpoint gần nhất và đối chiếu (reconcile) với số dư thực tế của ví để phát hiện sai lệch.
  * **Dev 3 (Backend Integration):**
    - Tạo REST Endpoint nhận Webhook từ cổng thanh toán (`/api/payments/webhook/{gateway}`).
    - Tích hợp logic xử lý Webhook: Ghi nhận webhook vào bảng `webhook_events`, kiểm tra idempotency, resolve tenant qua `PaymentIntent`, cập nhật trạng thái `PaymentTransaction` thành `SUCCESS` và kích hoạt chuyển trạng thái `PaymentIntent` sang `SUCCEEDED`.
  * **Dev 4 (Frontend UX - Buyer):**
    - Xây dựng giao diện thanh toán trực tuyến (chọn phương thức VNPay/Thẻ quốc tế), hiển thị QR code Mock để Buyer thực hiện thanh toán trực quan.
  * **Dev 5 (Frontend Dashboard - Seller/Admin):**
    - Hoàn thiện giao diện ví Seller (`/dashboard/wallet`): Hiển thị số dư khả dụng (`available_balance` / available balance), số dư đóng băng/tạm giữ (`pending_balance` / pending balance), danh sách lịch sử giao dịch (`wallet_transactions`).
  * **Dev 6 (QA/QC & DevOps):**
    - Viết Automation Test kiểm thử đồng thời (Concurrency Test) cho việc nạp/rút ví: Giả lập 50 luồng đồng thời rút tiền từ một ví để đảm bảo không bị quá bán (overdrafting) và không xảy ra deadlock.

---

### Sprint 3: Hoàn Thiện Luồng Rút Tiền (Payout), Quản Lý Đơn Hàng & Dashboard Admin/Seller
* **Mục tiêu:** Cho phép người bán gửi yêu cầu rút tiền (payout) về tài khoản ngân hàng, Admin phê duyệt yêu cầu và tích hợp đồng bộ trạng thái đơn hàng với tạm giữ tiền (escrow).

* **Phân bổ công việc:**
  * **Dev 2 (Backend Core):**
    - Xây dựng luồng rút tiền (payout):
      - API `POST /api/payouts` cho Seller gửi yêu cầu rút tiền (payout request). Kiểm tra số dư khả dụng (available balance), trừ ngay `available_balance` và giữ ở trạng thái tạm khóa (reserved/held) trong ví, hoặc tạo giao dịch payout đang chờ xử lý (Payout PENDING).
      - API `POST /api/payouts/{id}/approve` và `POST /api/payouts/{id}/reject` dành riêng cho Admin. Nếu Approve, tiền trừ hẳn. Nếu Reject, hoàn lại tiền vào `available_balance` của Seller.
  * **Dev 3 (Backend Integration):**
    - Kết nối Webhook giải ngân (disbursement webhook, nếu có cổng Payout tự động) hoặc viết tính năng xuất file Excel danh sách lệnh chuyển khoản ngân hàng hàng loạt (Batch Payout) định dạng Napas cho Admin dễ duyệt.
    - Tích hợp thay đổi trạng thái đơn hàng: Khi đơn hàng chuyển sang `COMPLETED` (từ API Order), tự động kích hoạt giải ngân escrow sớm mà không cần đợi Scheduler.
  * **Dev 4 (Frontend UX - Buyer):**
    - Tích hợp hiển thị trạng thái thanh toán trên trang Lịch sử đơn hàng của Buyer.
    - Xây dựng nút "Đã nhận được hàng" (Confirm Receipt) giúp Buyer xác nhận hoàn thành đơn sớm, giải ngân (release/disburse) tiền ngay cho Seller.
  * **Dev 5 (Frontend Dashboard - Seller/Admin) - Chủ trì Sprint:**
    - Phát triển giao diện Yêu cầu rút tiền (Payout Request) cho Seller (nhập số tiền, thông tin ngân hàng).
    - Phát triển giao diện Admin Payout Management dành cho Admin: Danh sách các yêu cầu rút tiền (payout requests) đang chờ duyệt, các nút Phê duyệt (Approve) / Từ chối (Reject) kèm theo ghi chú lý do.
  * **Dev 1 (Tech Lead / DB Arch):**
    - Rà soát tính bảo mật: Thêm phân quyền (authorization) `@RolesAllowed("businessOwner")` trên các API ví/rút tiền của Seller, và `@RolesAllowed("admin")` trên API phê duyệt.
    - Đảm bảo cơ chế cô lập tenant (Tenant Isolation) hoạt động chính xác cho ví của các Seller thuộc các Tenant khác nhau.
  * **Dev 6 (QA/QC & DevOps):**
    - Kiểm thử hồi quy (regression test) toàn bộ luồng: Từ lúc Buyer tạo đơn -> Thanh toán thành công -> Tách tiền (split payment) -> Hoàn thành đơn -> Giải ngân ví (wallet release) -> Rút tiền ví (payout) -> Admin phê duyệt.

---

### Sprint 4: Kiểm Thử Hiệu Năng (Performance Test), Đối Soát (Reconciliation) & Đóng Gói Release
* **Mục tiêu:** Kiểm tra bảo mật toàn diện, tối ưu hóa truy vấn Database, đối soát tài chính (financial reconciliation) tự động và đóng gói sản phẩm.

* **Phân bổ công việc:**
  * **Dev 6 (QA/QC & DevOps) - Chủ trì Sprint:**
    - Chạy Performance & Load Test cho hệ thống thanh toán và xử lý ví bằng JMeter hoặc Gatling.
    - Kiểm tra các lỗ hổng bảo mật: Thao túng tham số thanh toán (Parameter Tampering), gửi đè gói tin (Replay Attack).
  * **Dev 1 (Tech Lead / DB Arch):**
    - Phát triển công cụ tự động đối soát nội bộ (Internal Reconciliation Task): Quét định kỳ đối chiếu chéo checkpoint + phần ledger phát sinh sau checkpoint của mỗi ví phải luôn bằng đúng `available_balance_minor + pending_balance_minor`. Nếu phát hiện sai lệch (mismatch), tự động khóa ví và cảnh báo hệ thống.
    - Tạo các database indexes tối ưu cho bảng giao dịch ví, checkpoint và bảng đối soát (reconciliation tables) để tránh khóa bảng lâu khi dữ liệu lớn.
    - Kiểm tra cơ chế chỉ ghi thêm (append-only) của ledger: không có API/service path nào update/delete `wallet_transactions` sau commit.
  * **Dev 2 (Backend Core) & Dev 3 (Backend Integration):**
    - Sửa lỗi phát sinh từ kết quả QA.
    - Đóng gói tài liệu API chuẩn OpenAPI/Swagger. Chạy `npm run generate-api` trên frontend để đồng bộ hóa SDK sạch sẽ lần cuối.
  * **Dev 4 (Frontend UX) & Dev 5 (Frontend Dashboard):**
    - Kiểm tra responsive giao diện ví, lịch sử giao dịch và luồng thanh toán trên các thiết bị Mobile/Tablet.
    - Tối ưu hóa UI/UX: Hiển thị các loading state trực quan, thông báo Toast báo lỗi/thành công mượt mà.
  * **Cả đội ngũ:**
    - Thực hiện Demo hệ thống. Deploy lên môi trường Staging kiểm thử tích hợp cuối cùng trước khi bàn giao.

---

## 4. Chi Tiết File Cần Tạo / Chỉnh Sửa (Detailed WBS)

Dưới đây là danh mục chi tiết các file trong dự án cần được tạo mới hoặc thay đổi, phân chia cụ thể cho từng vị trí Developer để phục vụ cho việc quản lý mã nguồn:

### 4.1. Tầng Database & Entities (Dev 1 phụ trách chính)

* **[NEW]** `src/main/resources/db/migration/V16__add_payment_and_wallet_tables.sql`
  - Định nghĩa cấu trúc các bảng tài chính cốt lõi: `wallets`, `wallet_transactions`, `ledger_checkpoints`, `escrow_records`, `payout_requests`, `payment_intents`, `payment_transactions`, `payment_idempotency`.
  - Dùng `tenant_id`, `BIGINT *_minor`, `currency CHAR(3)`, unique/index scoped theo tenant và cơ chế chỉ ghi thêm (append-only) cho sổ cái (ledger).
* **[NEW]** `src/main/resources/db/migration/V17__link_product_and_order_to_business.sql`
  - Bổ sung cột `business_id` (UUID) tham chiếu bảng `business` vào các bảng `products` và `orders`.
* **[NEW]** [Wallet.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/Wallet.java)
  - JPA Entity ánh xạ bảng `wallets`. Kế thừa `BaseEntity` (có sẵn `tenantId`, `@Version` để chống xung đột bằng khóa lạc quan phụ / secondary Optimistic Lock).
* **[NEW]** [WalletTransaction.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/WalletTransaction.java)
  - JPA Entity ghi nhận bút toán sổ cái (journal entry / ledger entry). Đây là entity chỉ ghi thêm (append-only), không expose update/delete nghiệp vụ.
* **[NEW]** [LedgerCheckpoint.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/LedgerCheckpoint.java)
  - JPA Entity lưu điểm chốt số dư (balance checkpoint) tại một `lastSequenceNo`/`lastTransactionId` để tăng tốc truy vấn và đối soát (reconciliation).
* **[NEW]** [EscrowRecord.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/EscrowRecord.java)
  - JPA Entity quản lý trạng thái tạm giữ tiền (escrow) của đơn hàng.
* **[NEW]** [PayoutRequest.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/PayoutRequest.java)
  - JPA Entity xử lý các yêu cầu rút tiền (payout requests) của Seller.
* **[NEW]** [PaymentIntent.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/PaymentIntent.java)
  - JPA Entity đại diện cho ý định thanh toán theo mô hình Stripe PaymentIntent, quản lý state machine trước khi tạo/sau khi nhận gateway transaction.
* **[NEW]** [Money.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/model/Money.java)
  - Value object chuẩn hóa đơn vị tiền nhỏ nhất (minor units), currency exponent và chính sách làm tròn (rounding policy), ngăn dùng `double`/`float` trong payment code.
* **[MODIFY]** [Product.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/Product.java)
  - Bổ sung thuộc tính `private Business business` xác định sản phẩm thuộc cửa hàng nào.
* **[MODIFY]** [Order.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/entity/Order.java)
  - Bổ sung thuộc tính `private Business business` để phân tách đơn hàng đơn lẻ theo Seller.

### 4.2. Tầng Nghiệp Vụ Core Backend (Dev 2 phụ trách chính)

* **[NEW]** [WalletService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/WalletService.java)
  - Lớp nghiệp vụ thực hiện cộng/trừ ví sử dụng `LockModeType.PESSIMISTIC_WRITE`, append bút toán (ledger entry) và cập nhật số dư chiếu (balance projection) song song.
* **[NEW]** [PaymentService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/PaymentService.java)
  - Logic phân chia tỷ lệ dòng tiền (split payment) và thanh toán, chỉ kích hoạt khi `PaymentIntent` chuyển sang `SUCCEEDED`.
* **[NEW]** [PaymentIntentService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/PaymentIntentService.java)
  - Quản lý vòng đời PaymentIntent: create, confirm, cancel, expire, mark processing/succeeded/failed theo state machine.
* **[NEW]** [EscrowService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/EscrowService.java)
  - Quản lý logic tạm giữ tiền (escrow) của khách hàng, giải ngân (release/disbursement) cho nhà bán.
* **[NEW]** [LedgerCheckpointService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/LedgerCheckpointService.java)
  - Tạo và đọc điểm chốt (checkpoint) để tính số dư/đối soát nhanh mà vẫn giữ ledger append-only là nguồn sự thật (source of truth).
* **[NEW]** [MoneyService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/MoneyService.java)
  - Tập trung logic hoa hồng (commission), làm tròn (rounding), currency exponent, chuyển đổi `BigDecimal` sang đơn vị tiền nhỏ nhất (minor units).
* **[NEW]** [WalletResource.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/rest/WalletResource.java)
  - JAX-RS Resource cung cấp các API endpoint truy vấn số dư và lịch sử giao dịch ví.
* **[NEW]** [PayoutResource.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/rest/PayoutResource.java)
  - JAX-RS Resource phục vụ API tạo lệnh rút tiền (payout request), phê duyệt/từ chối lệnh rút tiền.
* **[MODIFY]** [OrderResource.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/rest/OrderResource.java)
  - Tích hợp gọi sang `EscrowService` để giải ngân (release/disburse) tiền khi đơn hàng hoàn thành thành công.

### 4.3. Tầng Tích Hợp Cổng Thanh Toán & Webhooks (Dev 3 phụ trách chính)

* **[NEW]** [IdempotencyService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/IdempotencyService.java)
  - Kiểm tra, lưu giữ các khóa idempotency key để loại bỏ giao dịch trùng lặp (duplicate transactions).
* **[NEW]** [PaymentGatewayService.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/service/PaymentGatewayService.java)
  - Khởi tạo đường link thanh toán (giả lập hoặc kết nối API VNPay/Stripe thật) từ `PaymentIntent`, gắn metadata nội bộ để webhook resolve đúng tenant/order.
* **[NEW]** [PaymentWebhookResource.java](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/src/main/java/base/rest/PaymentWebhookResource.java)
  - Nơi tiếp nhận Webhooks gọi lại từ phía Cổng thanh toán, xác thực chữ ký (signature verification), kiểm tra idempotency và chuyển trạng thái `PaymentIntent`/`PaymentTransaction`.

### 4.4. Tầng Giao Diện Buyer UX (Dev 4 phụ trách chính)

* **[MODIFY]** [cart-checkout-page.tsx](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/frontend/src/pages/cart-checkout-page.tsx)
  - Cập nhật giao diện thanh toán, chia sản phẩm trong giỏ theo Seller và cho phép chọn thanh toán qua ví/cổng.
* **[NEW]** [payment-processing-page.tsx](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/frontend/src/pages/payment-processing-page.tsx)
  - Màn hình chờ thanh toán/hiển thị QR để người mua quét mã.
* **[MODIFY]** [order-detail-page.tsx](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/frontend/src/pages/order-detail-page.tsx)
  - Hiển thị thông tin tiến độ dòng tiền tạm giữ (escrow progress) và thêm nút xác nhận đơn.

### 4.5. Tầng Giao Diện Seller & Admin Dashboard (Dev 5 phụ trách chính)

* **[NEW]** [seller-wallet-page.tsx](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/frontend/src/pages/seller-wallet-page.tsx)
  - Màn hình quản lý ví (wallet) của Seller: hiển thị số dư, giao dịch, form rút tiền (payout form).
* **[NEW]** [admin-payouts-page.tsx](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/frontend/src/pages/admin/admin-payouts-page.tsx)
  - Màn hình phê duyệt giao dịch rút tiền (payout approval) dành cho Admin hệ thống.
* **[MODIFY]** [routes/_authenticated.tsx](file:///c:/Users/5530/Documents/GitHub/v-erp-lite/frontend/src/routes/_authenticated.tsx)
  - Tích hợp thêm các route mới vào Menu điều hướng của trang ứng dụng.

### 4.6. Tầng QA & DevOps Automation (Dev 6 phụ trách chính)

* **[NEW]** `src/test/java/base/service/WalletServiceConcurrencyTest.java`
  - Integration Tests chạy bằng Quarkus Test để giả lập race condition trên ví số dư.
* **[NEW]** `src/test/java/base/service/LedgerInvariantTest.java`
  - Kiểm tra sổ cái chỉ ghi thêm (append-only ledger), giao dịch đảo/điều chỉnh (reversal/adjustment) đúng chuẩn và tổng ledger khớp số dư chiếu (balance projection).
* **[NEW]** `src/test/java/base/service/LedgerCheckpointServiceTest.java`
  - Kiểm tra tính số dư từ điểm chốt (checkpoint) + giao dịch phát sinh (delta transactions) cho kết quả giống sum toàn bộ ledger.
* **[NEW]** `src/test/java/base/service/MoneyServiceTest.java`
  - Kiểm tra đơn vị tiền nhỏ nhất (minor units), currency exponent, làm tròn hoa hồng (rounding commission) và các case lẻ cent/VND exponent 0.
* **[NEW]** `src/test/java/base/service/PaymentIntentServiceTest.java`
  - Kiểm tra trạng thái hữu hạn (state machine) của PaymentIntent và đảm bảo split payment chỉ chạy một lần khi webhook retry.
* **[NEW]** `src/test/java/base/service/PaymentTenantIsolationTest.java`
  - Kiểm tra dữ liệu ví, ý định thanh toán (payment intent), rút tiền (payout), webhook không đọc/ghi chéo giữa các tenant.
* **[NEW]** `src/test/java/base/rest/PaymentWebhookResourceTest.java`
  - Integration Tests giả lập luồng webhook thanh toán gửi lặp lại và kiểm tra idempotency.

---

## 5. Kế Hoạch Xác Minh & Kiểm Thử (Verification Plan)

### Kiểm Thử Tự Động (Automated Tests)
Chạy bộ test kiểm thử tích hợp để xác minh hoạt động của Core Wallet và khả năng chịu tải đồng thời:
```bash
./gradlew test --tests "base.service.WalletServiceConcurrencyTest"
./gradlew test --tests "base.service.LedgerInvariantTest"
./gradlew test --tests "base.service.LedgerCheckpointServiceTest"
./gradlew test --tests "base.service.MoneyServiceTest"
./gradlew test --tests "base.service.PaymentIntentServiceTest"
./gradlew test --tests "base.service.PaymentTenantIsolationTest"
./gradlew test --tests "base.rest.PaymentWebhookResourceTest"
```

### Kiểm Thử Thủ Công (Manual Verification)
1. Sử dụng Keycloak đăng nhập dưới tài khoản Seller (ví dụ `seller1@v-erp.com`), truy cập `/dashboard/wallet` để kiểm tra ví (wallet) tự động khởi tạo bằng 0.
2. Dùng tài khoản Buyer thực hiện mua hàng từ Seller này. Tiến hành thanh toán qua Mock QR Code.
3. Kiểm tra DB xem đơn hàng đã cập nhật trạng thái thanh toán chưa, ví Admin tăng 5%, ví Seller tăng 95% ở mục số dư đóng băng/tạm giữ (`pending_balance` / pending balance).
4. Dùng tài khoản Buyer nhấn nút "Xác nhận nhận hàng" (hoặc giả lập thời gian trôi qua 7 ngày). Kiểm tra số dư của Seller chuyển từ `pending_balance` sang `available_balance`.
5. Thực hiện gửi yêu cầu rút tiền (payout request) từ tài khoản Seller. Đăng nhập tài khoản Admin phê duyệt lệnh rút tiền, kiểm tra số dư khả dụng (available balance) bị trừ chuẩn xác.

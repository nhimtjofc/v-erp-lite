# Dữ liệu của V-ERP Lite

Phụ trách: N10-D
Cập nhật: 15/11/2026

## 1. Mục đích và phạm vi

Tài liệu mô tả dữ liệu nghiệp vụ mà V-ERP Lite lưu, nơi dữ liệu được tạo, ai sở hữu, vòng đời, mức phân loại và các ràng buộc chất lượng. Nguồn sự thật là mã nguồn JPA trong `src/main/java/base/entity/` và migration Flyway trong `src/main/resources/db/migration/`; tài liệu này không thay thế hai nguồn đó.

Phạm vi gồm:

- Cơ sở dữ liệu quan hệ MariaDB 11.4 của backend Quarkus.
- Phiên chat trong bộ nhớ của dịch vụ AI (không nằm trong MariaDB).
- Chỉ mục vector Pinecone phục vụ tìm kiếm sản phẩm (không phải nguồn sự thật nghiệp vụ).

Ngoài phạm vi: log hệ thống, log truy cập web server, bản sao lưu hạ tầng và dữ liệu của Keycloak. Keycloak là nguồn sự thật cho danh tính và thông tin đăng nhập; backend chỉ giữ `keycloak_id` tham chiếu.

## 2. Nguyên tắc lưu trữ

| Nguyên tắc | Cách thực hiện | Nguồn |
|---|---|---|
| Một cơ sở dữ liệu quan hệ | MariaDB 11.4, tên `v-erp-lite`, cổng ngoài `3307` | `docker-compose.yml`, `application.properties` |
| Khóa chính | UUID do ứng dụng sinh, trừ bảng danh mục dùng khóa tự nhiên | các lớp `base.entity` |
| Đa tenant | Cột `tenant_id` kiểu `VARCHAR(64)` trên bảng nghiệp vụ; lọc thủ công trong mọi truy vấn | `CallerContext`, `AGENTS.md` |
| Tiền tệ | `BIGINT` theo đơn vị nhỏ (minor units), mặc định VND | NFR-03, `V22`, `V54` |
| Thời gian | `Instant` lưu UTC; hiển thị theo múi giờ người dùng | các entity |
| Schema | Chỉ thay đổi qua Flyway `V{version}__{mô_tả}.sql` | NFR-10 |
| Số phiên bản | Một số bảng có cột `version` cho optimistic locking | `V8` |
| Dữ liệu bán cấu trúc | Cột JSON dùng `@JdbcTypeCode(SqlTypes.JSON)`, không dùng converter tự viết | `TenantConfig`, `UserSettings`, `WebhookEvent` |

## 3. Danh mục bảng

Ký hiệu phân loại: **C** công khai, **N** nội bộ, **P** dữ liệu cá nhân, **B** bí mật, **T** tài chính.

| Nhóm | Bảng | Mục đích | Phân loại | Chủ sở hữu |
|---|---|---|---|---|
| Nền tảng | `tenant` | Danh mục tenant, mã tenant dùng trong JWT | N | Quản trị sàn |
| Nền tảng | `role` | Vai trò hệ thống: ADMIN, OWNER, OPERATOR, CUSTOMER | C | Quản trị sàn |
| Nền tảng | `users` | Tài khoản người dùng, `keycloak_id`, hồ sơ, MFA | P, B | Quản trị sàn |
| Nền tảng | `tenant_config` | Cấu hình tenant dạng JSON | N | Quản trị sàn |
| Nền tảng | `user_settings` | Tùy chọn người dùng dạng JSON | P | Người dùng |
| Đối tác | `customer` | Khách hàng, điểm thành viên, hạng | P | Tenant |
| Đối tác | `business` | Doanh nghiệp/người bán, mã số thuế | P, N | Chủ doanh nghiệp |
| Đối tác | `banks` | Danh mục ngân hàng | C | Quản trị sàn |
| Catalog | `categories` | Danh mục sản phẩm | C | Người bán |
| Catalog | `products` | Sản phẩm, giá, tồn kho, thuộc tính JSON | C, T | Người bán |
| Catalog | `product_variants` | Biến thể, SKU, tồn kho riêng | C, T | Người bán |
| Catalog | `tags` | Nhãn sản phẩm | C | Người bán |
| Catalog | `product_tags` | Bảng nối sản phẩm - nhãn | C | Người bán |
| Bán hàng | `vouchers` | Mã giảm giá, hạn dùng, giới hạn | N, T | Người bán |
| Bán hàng | `customer_voucher` | Voucher khách đã thu thập hoặc dùng | P, T | Tenant |
| Bán hàng | `carts`, `cart_items` | Giỏ hàng cũ, còn bảng nhưng không còn entity sử dụng | N | Tenant |
| Đơn hàng | `orders` | Đầu đơn, trạng thái, địa chỉ giao, tổng tiền | P, T | Người bán và sàn |
| Đơn hàng | `order_items` | Dòng hàng, giá vốn, trạng thái hủy/trả từng dòng | T | Người bán |
| Đơn hàng | `order_history` | Nhật ký chuyển trạng thái, đánh số tuần tự | N | Sàn |
| Thanh toán | `payment_intents` | Ý định thanh toán, hạn 30 phút, số tiền | T | Sàn |
| Thanh toán | `payment_transactions` | Giao dịch tại cổng, mã giao dịch ngoài | T, B | Sàn |
| Thanh toán | `payment_refunds` | Yêu cầu hoàn tiền và mã hoàn tiền cổng | T, B | Sàn |
| Thanh toán | `payment_idempotency` | Khóa idempotency theo tenant | N, T | Sàn |
| Thanh toán | `webhook_events` | Webhook đến, payload gốc, trạng thái xử lý | N, B, P | Sàn |
| Thanh toán | `inbound_event_logs` | Dấu sự kiện cổng đã xử lý để chống trùng | N | Sàn |
| Ví và sổ cái | `wallets` | Ví người bán, số dư khả dụng/chờ, ngân hàng nhận tiền | T, B | Sàn |
| Ví và sổ cái | `wallet_transactions` | Bút toán append-only của ví | T | Sàn |
| Ví và sổ cái | `wallet_topups` | Lệnh nạp ví | T | Người dùng |
| Ví và sổ cái | `ledger_checkpoints` | Điểm đối soát số dư và chuỗi bút toán | T | Sàn |
| Escrow | `escrow_records` | Ký quỹ theo đơn và người bán, trạng thái HELD/RELEASED/REFUNDED | T | Sàn |
| Payout | `payout_requests` | Yêu cầu rút tiền, trạng thái duyệt và chi trả | T, B | Chủ doanh nghiệp |
| Rủi ro | `vendor_risk_events` | Cảnh báo rủi ro payout | N, T | Sàn |
| Doanh thu | `revenue_adjustments` | Điều chỉnh doanh thu ghi nhận | T | Sàn |
| Tích hợp | `sales_channels` | Kênh bán ngoài, cấu hình đồng bộ | N, B | Sàn |
| Tích hợp | `external_entity_mappings` | Ánh xạ id nội bộ với id kênh ngoài | N | Sàn |
| Thông báo | `notifications` | Thông báo cho người dùng, người nhận | P, N | Sàn |
| Gói dịch vụ | `subscription_plans` | Gói BASE và PREMIUM | C, T | Sàn |
| Gói dịch vụ | `tenant_subscriptions` | Gói đang dùng của tenant, hạn mức | N, T | Sàn |
| Gói dịch vụ | `subscription_payments` | Thanh toán gói dịch vụ | T | Sàn |

`product_tags`, `carts`, `cart_items` không có entity tương ứng trong `base.entity`; chúng là bảng lịch sử hoặc bảng nối do migration tạo. Không dùng `carts`/`cart_items` cho luồng mới.

## 4. Quan hệ dữ liệu chính

- `tenant` 1-n `users`, `customer`, `business`, `orders`, `wallets`, `tenant_subscriptions`, `tenant_config`.
- `business` 1-n `products`, `orders`, `payout_requests`.
- `users` (chủ doanh nghiệp) 1-n `wallets` theo `owner_type='SELLER'`; ví không có khóa ngoại trực tiếp tới `business`.
- `orders` 1-n `order_items`, `order_history`, `payment_intents`, `escrow_records`, `notifications`.
- `orders` 1-1 `payment_intents` theo nghiệp vụ tại một thời điểm; một đơn có thể có nhiều lần thử thanh toán.
- `payment_intents` 1-n `payment_transactions`, 1-n `payment_refunds`.
- `escrow_records` 1-n `wallet_transactions` qua cặp `reference_type='ESCROW'`, `reference_id`.
- `wallets` 1-n `wallet_transactions`, 1-n `wallet_topups`, 1-n `payout_requests`.
- `sales_channels` 1-n `external_entity_mappings`, `webhook_events`.
- `subscription_plans` 1-n `tenant_subscriptions`, 1-n `subscription_payments`.

Sơ đồ lớp đầy đủ ở `diagrams/class.mmd`; sơ đồ trạng thái đơn ở `diagrams/state.mmd`.

## 5. Vòng đời dữ liệu

### 5.1 Đặt hàng và thanh toán

1. Khách tạo đơn: ghi `orders`, `order_items`, `order_history`; giá và tồn kho do server tính lại.
2. Tạo `payment_intents` với hạn 30 phút và khóa idempotency.
3. Cổng trả kết quả: ghi `payment_transactions`; nếu thành công, tạo `escrow_records` trạng thái `HELD` và bút toán `ESCROW_HOLD`.
4. Đơn chuyển `PAID_ESCROW`; `releaseDueAt` được tính bằng thời điểm thanh toán cộng 7 ngày.

### 5.2 Giao hàng, giải phóng escrow

1. Owner nhận đơn, đơn đi qua `OWNER_ACCEPTED`, `FULFILLING`, `DELIVERED`, `PENDING_COMPLETION`.
2. Khi người mua xác nhận hoặc escrow tới hạn, `EscrowService` giải phóng: escrow `RELEASED`, bút toán `ESCROW_RELEASE`, trừ hoa hồng 5 phần trăm, cộng phần còn lại cho người bán bằng `ESCROW_SELLER_CREDIT`.
3. `LedgerCheckpointService` ghi checkpoint để đối soát.

### 5.3 Hủy, trả hàng, hoàn tiền

1. Yêu cầu hủy hoặc trả hàng ghi trạng thái trên `order_items` và `orders`.
2. Staff duyệt: `ReturnRefundService` gọi `PaymentRefundService`, escrow chuyển `REFUNDED`, ví nhận bút toán `REFUND` hoặc `ESCROW_REFUND`, đơn chuyển `REFUNDED`.
3. Mọi thay đổi trạng thái đơn đều ghi `order_history`.

### 5.4 Payout

1. Chủ doanh nghiệp tạo `payout_requests`.
2. `PayoutService` kiểm tra số dư khả dụng, ngưỡng tối thiểu, cửa sổ rủi ro và có thể tạo `vendor_risk_events`.
3. Chi trả thành công ghi bút toán `PAYOUT` và cập nhật trạng thái yêu cầu.

### 5.5 Đồng bộ kênh bán

1. `sales_channels` giữ cấu hình kênh; `external_entity_mappings` ánh xạ sản phẩm/đơn với id ngoài.
2. Webhook vào ghi `webhook_events` (payload gốc) và `inbound_event_logs` (dấu chống trùng).
3. Sự kiện trùng trả 200 nhưng bỏ qua xử lý.

### 5.6 Trợ lý AI

- Backend là nguồn sự thật; tool chỉ đọc 4 API: danh sách đơn, trạng thái đơn, số lượng sản phẩm, tìm kiếm tri thức.
- Phiên chat nằm trong bộ nhớ tiến trình FastAPI, tối đa 100 tin nhắn mỗi phiên, mất khi khởi động lại; không ghi prompt hay tin nhắn vào MariaDB.
- Pinecone chỉ chứa embedding và metadata sản phẩm; không chứa đơn hàng, ví hay dữ liệu cá nhân.

## 6. Phân loại và bảo vệ dữ liệu cá nhân

| Trường | Bảng | Mức | Kiểm soát hiện có |
|---|---|---|---|
| Họ tên, email, số điện thoại | `users`, `customer`, `business` | P | Chỉ trả qua API đã xác thực; lọc tenant |
| Địa chỉ giao hàng, người nhận | `orders` | P | Mã hóa đường truyền; hiển thị theo quyền đơn |
| Mã số thuế, tên doanh nghiệp | `business` | P, N | Owner và admin |
| Số tài khoản, tên ngân hàng | `wallets` | P, B | Chỉ owner/admin; không ghi log |
| `keycloak_id`, `totp_secret` | `users` | B | Không trả ra API; MFA do backend kiểm |
| Mã giao dịch cổng, chữ ký webhook | `payment_transactions`, `webhook_events` | B | Chỉ nội bộ; payload gốc hạn chế truy cập |
| Nội dung tin nhắn chat | bộ nhớ AI | P | Không lưu DB, không ghi telemetry |

Quy tắc:

- Danh tính lấy từ JWT, không nhận `tenant_id`, `user_id` từ thân yêu cầu.
- Mọi truy vấn đơn bản ghi phải có `tenant_id`; với OWNER thêm `business_id`.
- Bí mật (mật khẩu DB, client secret Keycloak, API key Gemini/Pinecone) nằm trong biến môi trường, không nằm trong bảng.
- AI không nhận PII trong prompt; validator đầu ra chặn lộ số điện thoại, email, traceback và tên tệp.
- Telemetry AI chỉ ghi thời gian, số lượt tool, trạng thái, số lần thử lại.

## 7. Ràng buộc chất lượng

- `money` luôn là `BIGINT`; không dùng số thực cho tiền.
- Duy nhất theo tenant: `orders(tenant_id, order_number)`, `vouchers(tenant_id, code)`, `payment_idempotency(tenant_id, idempotency_key)`, `wallets(tenant_id, owner_type, owner_id, currency)`.
- Giao dịch cổng duy nhất theo `(tenant_id, gateway, gateway_transaction_id)`.
- `wallet_transactions` chỉ thêm, không sửa/xóa; `LedgerCheckpoint` dùng để phát hiện lệch.
- `order_history` có `sequence_no` tăng dần để giữ thứ tự chuyển trạng thái.
- Trạng thái đơn chỉ chuyển theo bảng trong `OrderResource.isAllowedStatusTransition`.
- Tiền tệ mặc định VND; migration `V54` ép ràng buộc tiền tệ.

## 8. Lưu giữ, xóa và sao lưu

Đây là chính sách đề xuất cho đồ án; hiện chưa có job tự động xóa hoặc ẩn danh.

| Nhóm | Thời hạn đề xuất | Cách xử lý |
|---|---|---|
| Đơn hàng, escrow, ví, sổ cái, payout, hoàn tiền | 10 năm | Giữ bản ghi tài chính; ẩn danh PII sau 24 tháng không hoạt động |
| `order_history`, `ledger_checkpoints` | 10 năm | Giữ nguyên để đối soát |
| `webhook_events` (payload gốc) | 90 ngày | Xóa payload, giữ metadata và idempotency |
| `inbound_event_logs`, `payment_idempotency` | 180 ngày | Xóa theo lô |
| `notifications` | 12 tháng | Xóa mềm hoặc xóa cứng |
| Phiên chat AI | Trong RAM | Mất khi khởi động lại; không sao lưu |
| Pinecone | Theo vòng đời sản phẩm | Xóa embedding khi sản phẩm bị xóa khỏi catalog |

Sao lưu hiện tại: volume Docker `mariadbdata`. Chưa cấu hình lịch sao lưu tự động, bản sao ngoài máy hay thử khôi phục định kỳ; đây là việc cần bổ sung trước khi chạy thật.

## 9. Khoảng trống và nợ kỹ thuật dữ liệu

- Chưa bật Row-Level Security; cách ly tenant hoàn toàn dựa vào mã ứng dụng (NFR-01).
- Sổ cái là single-entry, chưa đối ứng Nợ/Có (TD-03).
- `webhook_events.raw_payload` có thể chứa PII; chưa có job xóa payload.
- Chưa có mã hóa dữ liệu khi lưu trữ ngoài mã hóa volume của hạ tầng.
- Bảng `carts`, `cart_items` còn nhưng không còn entity; cần quyết định xóa hoặc giữ.
- Phiên AI không bền vững; khởi động lại làm mất ngữ cảnh.
- `releaseDueAt` tính từ lúc thanh toán, không phải lúc giao; cần chốt lại nếu nghiệp vụ yêu cầu 7 ngày sau khi nhận hàng.
- Chưa có công cụ kiểm tra tự động dữ liệu mồ côi giữa `orders`, `payment_intents` và `escrow_records`.

## 10. Truy vết

| Yêu cầu | Cách tài liệu đáp ứng |
|---|---|
| NFR-01 | Mục 2, 6: lọc tenant và quy tắc truy vấn |
| NFR-03 | Mục 3, 7: minor units, khóa bi quan, append-only |
| NFR-04 | Mục 3, 7: `payment_idempotency`, khóa duy nhất |
| NFR-06 | Mục 5.5: chống trùng webhook |
| NFR-07, NFR-08 | Mục 5.6, 6: AI read-only, không lưu PII |
| NFR-10 | Mục 2: schema qua Flyway |
| FR-10, FR-12, FR-15 | Mục 5.2, 5.3: escrow, hoàn tiền |
| FR-28..FR-32 | Mục 5.6: dữ liệu cho trợ lý AI |

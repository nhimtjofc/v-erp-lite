# WooCommerce MVP — Handoff từ Dev 1

Nguồn chuẩn: [CUSTOMER_COMMERCE_WOOCOMMERCE_DESIGN.md](CUSTOMER_COMMERCE_WOOCOMMERCE_DESIGN.md). Khi handoff này khác file design, ưu tiên file design.

## Dev 1 đã hoàn thành

### Database foundation

- `V30__create_sales_channels.sql`: sales channel theo `tenant_id + business_id`, store URL, trạng thái kết nối/health, thời điểm kiểm tra và `secret_reference` không chứa secret thật.
- `V31__create_external_entity_mappings.sql`: mapping ERP ID ↔ Woo ID, có unique constraint ở cả hai chiều để retry đồng thời không tạo mapping trùng.
- `V32__add_woocommerce_webhook_deduplication.sql`: bổ sung `sales_channel_id`, stable `external_event_id`, `channel_metadata` và unique constraint `(tenant_id, sales_channel_id, external_event_id)`.
- Các foreign key ghép tenant/channel/business ngăn liên kết chéo tenant ngay ở database.

### Backend foundation

- Thêm entity `SalesChannel`, `ExternalEntityMapping` và metadata WooCommerce trên `WebhookEvent`.
- Thêm `GET /api/integrations/woocommerce/v1/health?businessId=...` với mọi lookup/count/update đều tenant-scoped.
- Health response chỉ trả `secretConfigured`; secret được resolve từ server-side config/environment theo `secret_reference` và không có fallback dùng chung tenant.
- Health probe không follow redirect, chặn private/loopback host ở production và chỉ cho phép localhost trong dev/test.
- Trạng thái `CONFIGURED` nghĩa là store reachable + secret đã cấu hình nhưng chưa handshake. Chỉ connector của Dev 3/5 mới được xác nhận `CONNECTED` và ghi `lastConnectedAt` sau handshake/HMAC hợp lệ.
- Dev bootstrap tạo hoặc cập nhật channel local cho tenant/business seed, không ghi secret vào database hay log.

### Local WordPress + WooCommerce

- Docker Compose có `wordpress-db`, `wordpress` và one-shot `wordpress-setup`.
- WordPress/WooCommerce được pin version, setup idempotent, có bounded retry và tự activate plugin.
- Container gọi backend local qua `host.docker.internal:8081`; WordPress mở tại `http://localhost:8090`.
- Hướng dẫn chạy bằng tiếng Việt: [docker/wordpress/README.md](docker/wordpress/README.md).

### Integration Settings

- Card WooCommerce đã được gắn vào trang `/settings/regional`.
- Hiển thị store URL, connection status, health, `secretConfigured`, last check, mapping/webhook count.
- Có loading, error, refresh và các trạng thái `CONNECTED`, `CONFIGURED`, `DISCONNECTED`, `DISABLED`, `ERROR`.
- Dùng generated client nền hiện có; không regenerate SDK và không sửa route tree.

### Stabilization

- Xóa đoạn debug frontend từng log raw access token và gọi `fetch` thủ công.
- Cô lập `LedgerCheckpointServiceTest` bằng test transaction và `flushAndClear()` để full suite không còn lỗi persistence context ngẫu nhiên.

## Contract Dev 2–6 phải dùng

- Mọi query integration bắt buộc có `tenantId`; lookup theo ID phải kèm tenant.
- Dùng `salesChannelId` để partition mapping, webhook và trạng thái sync.
- `external_event_id` phải là idempotency key ngữ nghĩa ổn định do connector tạo. Không dùng riêng `X-WC-Webhook-Delivery-ID` để đảm bảo exactly-once; delivery ID chỉ lưu trong `channel_metadata`.
- Retry phải dùng lại cùng idempotency key và upsert qua unique constraint hiện có.
- Không lưu HMAC signature, secret, Authorization header hoặc credential trong metadata/log/API response.
- Tuần MVP chỉ hỗ trợ COD/mock bank. Không thêm Stripe, VNPay, refund, return, SSO hoặc multi-seller.
- Không sửa migration/hạ tầng dùng chung; nếu cần schema mới, báo Dev 1.

## Việc tiếp theo theo từng dev

| Dev | Việc cần làm tiếp | Kết quả nhìn thấy / DoD |
|---|---|---|
| Dev 2 | Tạo catalog resource/service riêng cho `GET /api/integrations/woocommerce/v1/catalog/changes`; xuất product, variant, giá và tồn theo tenant/channel; dùng `ExternalEntityMapping` để upsert và resume an toàn. Không sửa `ProductResource`. | Mở Products → chạy/refresh catalog sync → thấy summary; fixture/API trả đúng product/variant/giá/tồn và retry không tạo bản ghi trùng. |
| Dev 3 | Viết phần catalog của WordPress plugin; pull changes từ Dev 2, upsert Woo product/variation, lưu ERP ID trong Woo metadata, có wp-admin health/last sync/error/retry. | Mở wp-admin → chạy catalog sync → thấy kết quả; mở storefront → thấy sản phẩm ERP đúng variant/giá/tồn. |
| Dev 4 | Tạo order resource/service riêng cho `POST .../orders` và `POST .../orders/{externalId}/reconcile`; backend tự tính lại giá/tồn, reserve tồn atomically, ghi event/mapping bằng stable idempotency key. Không sửa `OrderResource`. | Checkout Woo COD/mock bank → order xuất hiện trong V-ERP đúng một lần; gửi lặp/retry vẫn một order; hai checkout tranh SKU cuối chỉ một request thành công. |
| Dev 5 | Viết phần checkout/status của WordPress plugin; ký HMAC server-side, gửi stable idempotency key, lưu ERP order ID, retry cùng key và đồng bộ fulfillment về My Account. Chỉ sửa checkout plugin. | Mở Woo checkout → đặt COD/mock bank → thấy ERP sync; mở My Account → thấy trạng thái confirm/ship/complete cập nhật và retry rõ ràng. |
| Dev 6 | Regenerate SDK và sửa navigation/route tree; nối Products/Orders UI, business/channel selection, Woo ID, sync error, retry/reconcile; chạy E2E và release verification. | Mở Orders → thấy Woo ID/lỗi sync → retry/reconcile → thấy kết quả; chạy storefront → V-ERP → `COMPLETED` mà không trùng dữ liệu hoặc đọc chéo tenant. |

## Phối hợp và thứ tự merge

1. Dev 2 ↔ Dev 3 khóa catalog contract bằng fixture trước khi nối storefront.
2. Dev 4 ↔ Dev 5 khóa order/HMAC/idempotency contract trước khi checkout thật.
3. Dev 6 chỉ regenerate SDK sau khi API Dev 2/4 ổn định.
4. Thứ tự merge: Dev 1 → Dev 2/4 → Dev 3/5 → Dev 6 → verification.

## Verification đã chạy

- Backend: `./gradlew test --rerun-tasks --no-daemon` — 123 tests, 0 failure, 0 error.
- Frontend: `npm run build` — pass.
- Docker: WordPress/WooCommerce setup hoàn tất; WooCommerce active và container gọi được backend health.

DoD Dev 1:

- Mở `http://localhost:8090/wp-admin/plugins.php` → đăng nhập local → nhìn thấy WooCommerce **Active**.
- Mở `http://localhost:5174/settings/regional` → bấm **Kiểm tra lại** → nhìn thấy store URL, health, connection status và chỉ cờ `secretConfigured`, không thấy secret thật.

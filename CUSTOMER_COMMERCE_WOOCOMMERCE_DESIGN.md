# Customer Commerce — WooCommerce MVP

## 1. Mục tiêu

Trong một tuần phải hoàn thành luồng:

```text
ERP publish sản phẩm
    -> WooCommerce hiển thị sản phẩm
    -> khách checkout COD/mock bank
    -> ERP nhận order đúng một lần
    -> staff confirm/ship/complete
    -> WooCommerce hiển thị trạng thái mới
```

Không làm trong tuần này: Stripe/VNPay, refund, return, SSO, multi-seller và multi-warehouse.

## 2. Kiến trúc tối thiểu

- WooCommerce: storefront, cart, checkout và My Account.
- V-ERP: nguồn chuẩn của sản phẩm, giá, tồn và trạng thái vận hành order.
- WordPress plugin: lớp kết nối mỏng giữa WooCommerce và V-ERP.
- Staff chỉ vận hành order trong V-ERP; `wp-admin` dùng cho cấu hình và theo dõi sync.

Luồng dữ liệu:

```text
Catalog: V-ERP -> WooCommerce
Order:   WooCommerce -> V-ERP
Status:  V-ERP -> WooCommerce
```

## 3. Code hiện có phải reuse

| Nhu cầu | Code đã có | Cách dùng |
|---|---|---|
| Catalog | `Product`, `ProductVariant`, `Category`, `Tag` | Tạo projection API, không xây catalog mới |
| Giá và tồn | minor unit, sale price, `stockQuantity`, `reservedQuantity` | Dùng để publish và quote lại khi checkout |
| Checkout | `OrderResource.checkoutOrder()` | Tách thành service dùng chung cho staff và Woo |
| Order | `Order`, `OrderItem`, `OrderHistory`, voucher | Reuse snapshot, lịch sử và state transition |
| Payment | `PaymentIntentService`, mock payment | Bọc bằng Woo adapter, không viết payment core mới |
| Bảo vệ request | idempotency, HMAC và `WebhookEvent` hiện có | Reuse pattern; thêm Woo event ID và secret theo channel |
| Staff UI | order list/detail hiện có | Thêm Woo ID, sync status và retry/reconcile |

Không dùng `CartResource.checkoutCart()` cho Woo vì endpoint này nhận discount/VAT/shipping từ client và trừ tồn ngay.

## 4. Phần cần viết mới

### Backend

```text
GET  /api/integrations/woocommerce/v1/catalog/changes
POST /api/integrations/woocommerce/v1/orders
POST /api/integrations/woocommerce/v1/orders/{externalId}/reconcile
GET  /api/integrations/woocommerce/v1/health
```

Thêm dữ liệu tối thiểu:

- `sales_channels`: tenant/business, store URL và trạng thái.
- `external_entity_mappings`: map ERP ID với Woo ID.
- Woo event ID/channel metadata và unique constraint để chống xử lý trùng.

### WordPress plugin

- Pull catalog và upsert product/variant.
- Gửi order sang ERP với HMAC và idempotency key.
- Lưu ERP product/order IDs trong Woo metadata.
- Nhận trạng thái confirm/ship/complete.
- Có health, last sync và retry thủ công.

### Frontend

- Integration Settings: store URL, health và connection status.
- Products: catalog sync summary.
- Orders: Woo ID, sync status, lỗi và retry/reconcile.

## 5. Phân chia 6 dev

| Dev | Việc chính | Kết quả phải nhìn thấy |
|---|---|---|
| Dev 1 | Sales channel, mapping, Docker WordPress/Woo | Integration Settings hiện health và trạng thái kết nối |
| Dev 2 | Catalog adapter ERP → Woo | Products hiện sync summary; API trả đúng product/variant/giá/tồn |
| Dev 3 | Woo catalog plugin | Product ERP xuất hiện trên Woo storefront; wp-admin hiện kết quả sync |
| Dev 4 | Checkout/order adapter Woo → ERP | Woo checkout báo giá/tồn đúng; order xuất hiện trong V-ERP đúng một lần |
| Dev 5 | Woo checkout, HMAC và status sync | My Account hiện ERP sync và trạng thái fulfillment |
| Dev 6 | Staff UI, retry/reconcile và E2E | Orders hiện Woo ID, lỗi sync và kết quả retry/reconcile |

Phối hợp trực tiếp:

- Dev 2 ↔ Dev 3: catalog.
- Dev 4 ↔ Dev 5: checkout/order.
- Dev 1 cung cấp foundation.
- Dev 6 ghép luồng và xác nhận release.

## 6. Definition of Done

Mỗi PR phải ghi:

> Mở trang nào → thao tác gì → nhìn thấy kết quả gì.

Task chưa hoàn thành nếu chỉ có entity, API, database row hoặc log.

Điều kiện chung:

- Mọi query integration đều tenant-scoped.
- Backend tự tính lại giá và tồn; không tin price, tenant hoặc payment status từ client.
- Retry không tạo trùng product, order, payment hoặc reservation.
- Hai checkout tranh SKU cuối chỉ một request thành công.
- UI có loading, error và trạng thái retry rõ ràng.
- Backend tests và compile pass.
- Frontend `npm run build` pass.
- Plugin activate và health check pass.

## 7. Secret

Secret không được nằm trong JavaScript bundle, localStorage, HTML, API GET response, log hoặc Git.

Secret nằm trong environment/secret store phía server. Frontend chỉ được thấy:

- `secretConfigured: true/false`;
- connection/health status;
- thời điểm kiểm tra hoặc rotate gần nhất.

Nếu rotate từ UI, secret chỉ gửi một chiều qua HTTPS; backend không trả lại sau khi lưu.

## 8. Timeline

| Ngày | Kết quả bắt buộc |
|---|---|
| Thứ Hai | Khóa API contract; backend compile; frontend build; plugin activate |
| Thứ Ba | Catalog và order module chạy độc lập bằng mock/fixture |
| Thứ Tư | Một order COD chạy end-to-end |
| Thứ Năm | Pass duplicate, replay, tenant, concurrency và timeout tests |
| Thứ Sáu | Clean install và demo storefront → `COMPLETED` |

Nếu hết Thứ Tư chưa có happy path, dừng thêm tính năng và tập trung integration.

## 9. Tránh conflict

- Chỉ Dev 1 sửa migration và cấu hình hạ tầng dùng chung.
- Chỉ Dev 6 regenerate SDK và sửa navigation/route tree.
- Dev 3 chỉ sửa catalog plugin; Dev 5 chỉ sửa checkout plugin.
- Dev 2 và Dev 4 tạo resource/service riêng, hạn chế sửa chung `ProductResource` và `OrderResource`.
- Merge hai lần mỗi ngày: 11:30 và 16:30.
- Thứ tự merge: Dev 1 → Dev 2/4 → Dev 3/5 → Dev 6 → verification.
- Không format file ngoài phạm vi.

## 10. Release gate

Chỉ release khi:

1. Product ERP hiển thị và mua được trên Woo.
2. Woo order xuất hiện trong V-ERP đúng một lần.
3. Staff confirm/ship/complete trên V-ERP.
4. Woo hiển thị trạng thái mới.
5. Retry, webhook lặp và restart không tạo dữ liệu trùng.
6. Không đọc chéo tenant và không lộ secret.

# Thiết kế khu mua sắm khách hàng tách riêng bằng WooCommerce

## 1. Mục tiêu

Tài liệu này thiết kế một **khu mua sắm dành riêng cho khách hàng** dựa trên ý tưởng và khả năng sẵn có của WooCommerce, đồng thời giữ V-ERP Lite làm hệ thống vận hành nội bộ.

Mục tiêu chính:

- Khách hàng mua sắm trên path/domain riêng, không đi vào dashboard của nhân viên.
- Dùng **WooCommerce Core miễn phí** dưới dạng plugin của WordPress để có storefront, catalog projection, giỏ hàng, checkout và trang tài khoản khách hàng.
- V-ERP quản lý nguồn catalog, tồn kho, đơn hàng vận hành, thanh toán và đối soát.
- Phân rõ stage nào do khách hàng, loại nhân viên nào, quản trị viên nào xử lý.
- Hỗ trợ nhiều luồng thanh toán: cổng thanh toán thật, COD/chuyển khoản và ngân hàng giả lập có số dư.
- Thiết kế được các tình huống khó: callback trùng, thanh toán treo, hết hàng đồng thời, giao một phần, hủy, hoàn tiền, chargeback và lệch dữ liệu giữa hai hệ thống.

Tài liệu là **thiết kế mục tiêu**, không khẳng định toàn bộ chức năng dưới đây đã có trong code hiện tại.

## 2. Quyết định kiến trúc

### 2.1. Storefront tách riêng

Khuyến nghị production:

| Không gian | URL mẫu | Người dùng | Công nghệ |
|---|---|---|---|
| Storefront | `https://shop.example.com/` | Khách vãng lai, khách hàng | WordPress + WooCommerce |
| Tài khoản khách | `https://shop.example.com/my-account/` | Khách hàng | WooCommerce My Account |
| Back office | `https://erp.example.com/` | Nhân viên, quản trị viên | React/Vite hiện tại |
| ERP API | `https://erp.example.com/api/` | Storefront và back office | Quarkus hiện tại |

Nếu bắt buộc dùng cùng domain thì dùng:

- `/shop/*`: reverse proxy sang WordPress/WooCommerce.
- `/app/*`: frontend V-ERP dành cho staff.
- `/api/*`: Quarkus.
- `/wp-admin/*`, `/wp-json/*`: WordPress, chỉ mở đúng phạm vi cần thiết.

Không nên chỉ tạo thêm `/shop` trong React hiện tại rồi cho customer và staff dùng chung layout. Tách deployment/path giúp:

- Không lộ navigation và chức năng nội bộ cho khách.
- Không trộn lifecycle phiên đăng nhập WordPress với Keycloak staff.
- Có thể thay theme, SEO và nội dung bán hàng mà không ảnh hưởng ERP.
- Thu hẹp quyền của từng API và giảm nguy cơ customer gọi nhầm API staff.

### 2.2. WooCommerce miễn phí dùng đến đâu

WooCommerce Core vẫn là plugin miễn phí, mã nguồn mở cho WordPress. Core cung cấp catalog, biến thể, cart, checkout, order, account và API; có thể cài từ WordPress Admin hoặc thư mục plugin. Tuy nhiên cần tách ba loại chi phí:

- **Miễn phí bản quyền core** không đồng nghĩa miễn phí hosting, domain, vận hành và bảo mật.
- Payment processor như Stripe vẫn thu phí giao dịch theo hợp đồng.
- Một số gateway địa phương, subscription, booking, vận chuyển hoặc workflow đặc biệt có thể cần plugin trả phí hoặc connector tự phát triển.

Nguồn tham khảo chính thức:

- [WooCommerce trên WordPress.org](https://wordpress.org/plugins/woocommerce/)
- [WooCommerce pricing](https://woocommerce.com/pricing/)
- [WooCommerce REST API](https://developer.woocommerce.com/docs/apis/rest-api/)
- [WooCommerce webhooks](https://developer.woocommerce.com/docs/apis/rest-api/v3/webhooks/)

### 2.3. Nguồn dữ liệu chuẩn

Không cho WooCommerce và V-ERP cùng tự do sửa một loại dữ liệu. Quy định nguồn chuẩn như sau:

| Dữ liệu | Nguồn chuẩn | Bản sao/Projection |
|---|---|---|
| SKU, biến thể, giá niêm yết, tồn khả dụng | V-ERP | WooCommerce |
| Nội dung SEO/landing page | WordPress | Không bắt buộc đồng bộ về ERP |
| Cart và checkout session | WooCommerce | Không cần đồng bộ trước khi đặt hàng |
| Đơn vừa checkout | WooCommerce tạo trước | V-ERP tiếp nhận và trở thành nguồn vận hành |
| Fulfillment, đóng gói, giao hàng | V-ERP | Đồng bộ trạng thái thân thiện về WooCommerce |
| Kết quả thanh toán | Payment processor là bằng chứng ngoài | V-ERP payment ledger là sổ nội bộ; WooCommerce là projection cho khách |
| Refund/chargeback | Processor + V-ERP | WooCommerce hiển thị kết quả |
| Hồ sơ nhân viên và quyền | Keycloak + V-ERP | Không đồng bộ sang WordPress trừ service account |
| Tài khoản khách | WooCommerce ở phase đầu | Map sang `Customer` trong V-ERP bằng external ID |

Nguyên tắc quan trọng: **không dùng email làm khóa duy nhất lâu dài**. Lưu mapping `(tenant_id, channel, external_customer_id)`; email chỉ hỗ trợ ghép lần đầu có kiểm soát.

## 3. Vai trò và ranh giới quyền

### 3.1. Vai trò nghiệp vụ

| Vai trò | Mã đề xuất | Trách nhiệm |
|---|---|---|
| Khách vãng lai | `guest` | Xem hàng, tạo cart, checkout nếu tenant cho phép |
| Khách hàng | `customer` | Đặt hàng, thanh toán, xem đơn của mình, yêu cầu hủy/đổi trả |
| Nhân viên CSKH | `customerSupport` | Xác minh khách, ghi chú, hỗ trợ hủy; không được tự hoàn tiền |
| Nhân viên xử lý đơn | `orderOperator` | Duyệt đơn, giữ hàng, đóng gói, bàn giao vận chuyển |
| Nhân viên kho | `warehouseOperator` | Pick/pack, điều chỉnh reservation theo quyền hạn, ghi nhận thiếu/hỏng |
| Nhân viên kế toán | `financeOperator` | Xác nhận chuyển khoản, xử lý refund, đối soát, xem ledger |
| Chủ doanh nghiệp | `businessOwner` | Catalog, giá, chính sách bán, báo cáo và phê duyệt nghiệp vụ lớn |
| Quản trị tenant | `tenantAdmin` | Người dùng, cấu hình cửa hàng, processor và secret của tenant |
| Quản trị hệ thống | `systemAdmin` | Tenant, vận hành nền tảng; không mặc định được sửa tiền của tenant |

### 3.2. Mapping với role hiện tại

Code hiện có chủ yếu dùng `admin`, `operator`, `businessOwner`, `customer`. Để không mở rộng role quá sớm:

| Role hiện tại | Phase 1 | Đích dài hạn |
|---|---|---|
| `customer` | Customer storefront | Giữ nguyên |
| `operator` | CSKH + order + warehouse + finance cơ bản | Tách thành các role staff chuyên biệt |
| `businessOwner` | Chủ doanh nghiệp/tenant | Giữ nguyên, thêm approval policy |
| `admin` | Quản trị hệ thống hiện tại | Tách rõ `tenantAdmin` và `systemAdmin` |

Không nên coi `admin` là một stage của đơn hàng. Admin là quyền can thiệp/duyệt ngoại lệ; stage phải phản ánh trạng thái nghiệp vụ thực tế.

### 3.3. Ma trận hành động quan trọng

| Hành động | Customer | CSKH | Order staff | Kho | Kế toán | Owner/Admin |
|---|---:|---:|---:|---:|---:|---:|
| Tạo đơn/thanh toán | Có | Tạo hộ có audit | Không | Không | Không | Không |
| Hủy trước khi xử lý | Yêu cầu/tự hủy theo policy | Duyệt | Duyệt | Không | Không | Override |
| Xác nhận/giữ tồn | Không | Không | Có | Có | Không | Override |
| Pick/pack/ship | Không | Xem | Có | Có | Không | Override |
| Xác nhận chuyển khoản | Không | Chỉ đính kèm chứng từ | Không | Không | Có | Override |
| Refund | Yêu cầu | Tiếp nhận | Không | Xác nhận hàng trả | Thực thi | Duyệt theo ngưỡng |
| Điều chỉnh ledger/balance | Không | Không | Không | Không | Tạo yêu cầu | Duyệt, luôn bằng bút toán đảo/điều chỉnh |
| Cấu hình processor/webhook secret | Không | Không | Không | Không | Xem hạn chế | Tenant admin |

## 4. Luồng ứng dụng theo stage

### Stage 0 — Nhân viên cấu hình trước khi mở bán

**Actor chính:** `businessOwner`, `tenantAdmin`; kế toán hỗ trợ payment; kho hỗ trợ inventory.

1. Owner tạo sản phẩm, variant, SKU, giá và trạng thái bán trong V-ERP.
2. Kho nhập tồn và chính sách cho phép oversell/backorder.
3. Kế toán cấu hình currency, thuế, COD/chuyển khoản và processor.
4. Tenant admin tạo REST key tối thiểu quyền và webhook secret cho connector WooCommerce.
5. V-ERP publish catalog sang WooCommerce.
6. Nhân viên kiểm tra sản phẩm projection rồi mới chuyển `DRAFT -> ACTIVE`.

### Stage 1 — Khách khám phá sản phẩm

**Actor chính:** `guest` hoặc `customer`.

1. Khách vào `shop.example.com`, tìm kiếm, lọc category/tag và xem variant.
2. WooCommerce hiển thị giá và tồn projection gần nhất.
3. Khi thêm cart, storefront kiểm tra lại variant còn bán được.
4. Giá trong cart chỉ là tạm tính; giá, voucher, thuế và shipping phải tính lại khi checkout.

**Staff chỉ xuất hiện khi:** nội dung sai, sản phẩm bị ẩn, giá/tồn lệch hoặc khách cần tư vấn.

### Stage 2 — Cart và checkout

**Actor chính:** customer; WooCommerce điều phối.

1. Khách nhập địa chỉ, shipping option, voucher và thông tin xuất hóa đơn.
2. WooCommerce tạo `checkout_token`/order draft và gọi V-ERP quote endpoint.
3. V-ERP tính lại theo minor unit: item subtotal, item discount, order discount, shipping, VAT, rounding và grand total.
4. V-ERP giữ tồn có thời hạn, trả về `reservation_id`, `expires_at` và quote hash.
5. Nếu quote đã đổi, UI bắt khách xác nhận lại trước khi trả tiền.
6. WooCommerce tạo order `pending payment` và lưu `erp_order_id`, `reservation_id` trong metadata.

**Order staff** chỉ xử lý sau khi order được accept; không sửa tay tổng tiền sau khi khách đã bắt đầu payment. Nếu bắt buộc sửa, phải hủy intent cũ và tạo revision/order mới.

### Stage 3 — Thanh toán

**Actor chính:** customer và processor. **Kế toán** xử lý phương thức thủ công/ngoại lệ.

1. Customer chọn payment method.
2. Hệ thống tạo một `PaymentIntent` cho một order revision.
3. Client chỉ nhận `client_secret`/redirect URL cần thiết, không nhận secret key.
4. Kết quả cuối cùng chỉ được chấp nhận từ webhook đã xác minh hoặc truy vấn trực tiếp processor; không tin `return_url` từ trình duyệt.
5. Khi `PaymentIntent=SUCCEEDED`, order chuyển sang `PAID`, reservation chuyển thành committed stock.
6. Khi thất bại có thể retry bằng intent/attempt mới, nhưng không tạo thêm order trừ khi order đã hết hạn.

### Stage 4 — Xác nhận và fulfillment

**Actor chính:** `orderOperator`, `warehouseOperator`.

1. Order staff kiểm tra fraud flag, địa chỉ, payment và reservation.
2. Đơn hợp lệ chuyển `CONFIRMED`.
3. Kho tạo pick list, quét đúng SKU/variant/serial nếu có.
4. Thiếu hàng: chuyển item sang `BACKORDERED`, đề nghị thay thế, giao một phần hoặc hủy phần thiếu.
5. Đóng gói tạo shipment; một order có thể có nhiều shipment.
6. Khi carrier nhận hàng, shipment chuyển `SHIPPED`; V-ERP đẩy timeline thân thiện về WooCommerce.

### Stage 5 — Giao hàng và hoàn tất

**Actor chính:** carrier, customer, CSKH.

1. Carrier callback `DELIVERED`; hoặc staff xác nhận từ bằng chứng giao hàng.
2. Customer có cửa sổ báo chưa nhận/hư hỏng.
3. Hết cửa sổ khiếu nại, order chuyển `COMPLETED` và escrow được release nếu áp dụng.
4. Customer được phép review chỉ với item đã giao.

### Stage 6 — Hậu mãi, hủy, trả hàng, refund

**Actor chính:** customer -> CSKH -> kho -> kế toán; owner/admin duyệt ngoại lệ.

1. Customer tạo yêu cầu theo từng item, số lượng và lý do.
2. CSKH kiểm tra thời hạn/chính sách và cấp RMA nếu cần trả vật lý.
3. Kho nhận, phân loại `RESTOCKABLE`, `DAMAGED`, `MISSING`.
4. Kế toán tạo refund toàn phần hoặc một phần, gắn đúng payment transaction.
5. Webhook processor xác nhận refund trước khi ledger ghi `REFUNDED`.
6. Voucher, loyalty, commission, tax và inventory được đảo theo policy, không xóa lịch sử cũ.

## 5. Các state machine độc lập

Không dùng một cột `order.status` để đại diện đồng thời thanh toán, kho và giao hàng.

### 5.1. Commerce order

```text
DRAFT
  -> PENDING_PAYMENT
  -> PLACED
  -> CONFIRMED
  -> FULFILLING
  -> PARTIALLY_SHIPPED
  -> SHIPPED
  -> DELIVERED
  -> COMPLETED

Nhánh kết thúc: CANCELLED, CLOSED
Nhánh hậu mãi: RETURN_REQUESTED -> RETURNING -> RETURNED
```

`CLOSED` nghĩa là không còn hành động nghiệp vụ mở, không đồng nghĩa đã thanh toán đủ.

### 5.2. Payment intent

```text
CREATED -> REQUIRES_ACTION -> PROCESSING -> SUCCEEDED
   |              |              |
   +-----------> FAILED <--------+
   +-----------> CANCELLED
   +-----------> EXPIRED
```

Một order có thể có nhiều attempt, nhưng tổng capture hợp lệ không được vượt `amount_due` trừ trường hợp tip/adjustment được policy cho phép.

### 5.3. Refund

```text
REQUESTED -> APPROVED -> PROCESSING -> SUCCEEDED
     |           |           |
     +-------> REJECTED      +-> FAILED -> RETRYING
```

### 5.4. Inventory reservation và fulfillment

```text
AVAILABLE -> RESERVED -> COMMITTED -> PICKED -> PACKED -> SHIPPED
                 |          |          |
                 +----------+----------+-> RELEASED

Shipment: CREATED -> LABELLED -> HANDED_OVER -> IN_TRANSIT
         -> DELIVERED | DELIVERY_FAILED | LOST | RETURN_TO_SENDER
```

### 5.5. Mapping sang trạng thái WooCommerce

WooCommerce core thường hiển thị các trạng thái như pending payment, on hold, processing, completed, failed, cancelled và refunded. V-ERP giữ state chi tiết; WooCommerce chỉ nhận projection:

| V-ERP | WooCommerce | Ghi chú khách thấy |
|---|---|---|
| `PENDING_PAYMENT` | `pending` | Chờ thanh toán |
| Chuyển khoản chờ xác nhận/COD accepted | `on-hold` | Đã nhận đơn, đang xác minh |
| `PAID` + đang fulfillment | `processing` | Đang chuẩn bị/giao hàng |
| `COMPLETED` | `completed` | Hoàn tất |
| Payment hết retry | `failed` | Thanh toán thất bại |
| `CANCELLED` | `cancelled` | Đã hủy |
| Refund toàn phần | `refunded` | Đã hoàn tiền |
| Refund một phần | giữ trạng thái order + note/refund data | Không đánh dấu toàn đơn refunded |

## 6. Thiết kế payment chi tiết

### 6.1. Interface chung

Mọi phương thức triển khai qua adapter:

```text
PaymentProcessor
  createIntent(order, amount, currency, idempotencyKey)
  confirm(intent, customerAction)
  getStatus(externalIntentId)
  capture(externalIntentId, amount)
  cancel(externalIntentId)
  refund(externalTransactionId, amount, reason, idempotencyKey)
  verifyWebhook(headers, rawBody)
```

Adapter đề xuất:

- `StripePaymentProcessor`: processor thật; ưu tiên hosted checkout/Payment Element để giảm phạm vi PCI.
- `ManualBankTransferProcessor`: chuyển khoản thật nhưng kế toán xác nhận thủ công hoặc theo bank statement.
- `CashOnDeliveryProcessor`: không thu tiền lúc checkout.
- `MockBankPaymentProcessor`: chỉ dev/test/demo, có account và balance giả lập.

Không hard-code Stripe vào OrderService. Order chỉ nói với `PaymentService`; processor được chọn theo cấu hình tenant và currency.

### 6.2. Luồng Stripe hoặc processor tương tự

1. `POST /storefront/v1/orders/{id}/payment-intents` với `Idempotency-Key`.
2. Backend khóa order revision, kiểm tra amount/currency rồi tạo internal intent.
3. Adapter tạo external intent kèm metadata tối thiểu: tenant reference, ERP order public ID, internal intent ID; không gửi PII thừa.
4. Customer hoàn thành 3DS/redirect trên UI của processor.
5. Browser trở lại `payment-result`, chỉ hiển thị `PROCESSING` và poll API của V-ERP.
6. Processor gửi webhook; V-ERP xác minh signature trên **raw body**, lưu `external_event_id` unique rồi mới xử lý.
7. Transaction cập nhật intent, payment transaction, ledger và order một lần.
8. Duplicate webhook trả 2xx với kết quả cũ; event đến sai thứ tự được đối chiếu bằng state/version, không hạ `SUCCEEDED` về `PROCESSING`.

Hỗ trợ hai kiểu capture:

- **Immediate capture:** dùng cho hàng thông thường, tiền thu ngay khi checkout.
- **Authorize then capture:** chỉ dùng khi processor/currency hỗ trợ và thời gian fulfillment nằm trong authorization window. Capture lúc order được staff accept; nếu quá hạn phải re-authorize.

### 6.3. COD

1. Checkout tạo order với `payment_status=UNPAID`, `payment_method=COD`.
2. Có thể yêu cầu OTP/xác nhận điện thoại cho đơn rủi ro cao.
3. Order staff duyệt rồi fulfillment.
4. Carrier thu tiền, gửi settlement report hoặc staff nhập biên nhận.
5. Kế toán đối soát và ghi payment `SUCCEEDED`; chênh lệch tạo exception case.
6. Giao thất bại: return-to-sender, release/restore stock theo chất lượng hàng; không tạo refund vì chưa thu tiền.

### 6.4. Chuyển khoản ngân hàng thủ công

1. Hệ thống sinh virtual reference duy nhất cho order và hướng dẫn số tiền chính xác.
2. Order `ON_HOLD`; reservation có TTL dài hơn card nhưng vẫn hữu hạn.
3. Kế toán nhập/đồng bộ statement và match theo reference + amount + currency.
4. Khớp đủ: payment `SUCCEEDED`; thiếu/thừa tiền: `REVIEW_REQUIRED`.
5. Tiền đến sau khi order hết hạn: không tự hồi sinh order; kế toán hoàn tiền hoặc xin customer tạo order mới.

### 6.5. Ngân hàng giả lập có balance

Phạm vi: dev, automated test, demo nội bộ. Không bật trong production profile.

#### Dữ liệu

| Bảng | Trường chính |
|---|---|
| `mock_bank_accounts` | `id`, `owner_ref`, `currency`, `status`, `available_balance_minor`, `version` |
| `mock_bank_transactions` | `id`, `account_id`, `direction`, `amount_minor`, `currency`, `type`, `reference`, `created_at` |
| `mock_bank_holds` | `id`, `account_id`, `payment_intent_id`, `amount_minor`, `status`, `expires_at` |

Số tiền luôn là integer minor unit, không dùng `double`. Transaction là append-only; balance là projection để đọc nhanh và phải khớp tổng ledger.

#### API test đề xuất

```text
POST /api/test-support/mock-bank/accounts
POST /api/test-support/mock-bank/accounts/{id}/deposit
GET  /api/test-support/mock-bank/accounts/{id}
POST /api/test-support/mock-bank/payment-intents/{id}/authorize
POST /api/test-support/mock-bank/payment-intents/{id}/capture
POST /api/test-support/mock-bank/payment-intents/{id}/fail
POST /api/test-support/mock-bank/transactions/{id}/refund
```

Guard bắt buộc:

- Chỉ tồn tại khi profile `dev`/`test` hoặc feature flag server-side.
- Endpoint deposit/fail/complete chỉ cho test admin.
- Không dùng chung bảng với wallet seller thật.
- Debit dùng row lock hoặc optimistic retry và constraint balance không âm.
- Idempotency key unique theo `(tenant_id, operation, key)`.

#### Kịch bản mẫu

- Account A có `1,000,000 VND`; order `250,000 VND` -> authorize tạo hold, available còn `750,000`.
- Capture -> hold đóng, tạo debit `250,000`, merchant clearing account nhận credit cân bằng.
- Refund `100,000` -> merchant clearing debit, Account A credit `100,000`.
- Hai request capture đồng thời -> đúng một request thành công.
- Balance không đủ -> intent `FAILED/INSUFFICIENT_FUNDS`, order vẫn `PENDING_PAYMENT` cho phép chọn phương thức khác.

### 6.6. Ledger, escrow và payout

- Payment success ghi bút toán cân bằng; không chỉ `wallet.balance += amount`.
- Tiền seller có thể vào `pending/escrow` trước, sau `COMPLETED` mới thành available.
- Refund trước release lấy từ escrow; refund sau release lấy từ available hoặc tạo receivable nếu seller thiếu tiền.
- Fee, tax, shipping, seller payable và platform commission là các account riêng.
- Mọi adjustment phải là bút toán mới có `reversal_of`; không sửa/xóa transaction cũ.
- Payout không phải refund: payout chuyển tiền seller ra bank, refund trả tiền customer.

### 6.7. Idempotency và webhook

Khóa tối thiểu:

| Nghiệp vụ | Unique key |
|---|---|
| Tạo order từ Woo | `(tenant_id, channel, external_order_id)` |
| Tạo payment intent | `(tenant_id, order_id, order_revision, idempotency_key)` |
| Processor webhook | `(processor, external_event_id)` |
| Capture/refund | `(processor, operation, idempotency_key)` |
| Catalog sync | `(tenant_id, entity_type, entity_id, version)` |

Webhook phải xác minh HMAC/signature, timestamp/replay window, amount, currency, merchant account và liên kết tenant. WooCommerce hỗ trợ HMAC-SHA256 cho webhook; connector phải lưu delivery/event ID và trả 2xx nhanh sau khi transaction đã commit.

## 7. Hợp đồng tích hợp WooCommerce — V-ERP

### 7.1. Connector

Tạo một WordPress plugin mỏng, ví dụ `v-erp-woocommerce-connector`:

- Nhận catalog projection từ V-ERP hoặc chủ động pull bằng API key read-only.
- Gửi event order created/updated/refund về V-ERP.
- Lưu `erp_product_id`, `erp_variant_id`, `erp_order_id`, `erp_customer_id` trong metadata.
- Không chứa business rule tính tiền nguồn chuẩn.
- Có trang health/status, lần sync cuối và nút retry thủ công.

Không sửa trực tiếp WooCommerce Core vì update plugin sẽ ghi đè thay đổi.

### 7.2. API namespace riêng

```text
# Customer/storefront-facing Quarkus APIs
GET  /api/storefront/v1/catalog/products
POST /api/storefront/v1/quotes
POST /api/storefront/v1/reservations
POST /api/storefront/v1/orders
GET  /api/storefront/v1/orders/{publicId}
POST /api/storefront/v1/orders/{publicId}/payment-intents
POST /api/storefront/v1/orders/{publicId}/cancel-requests
POST /api/storefront/v1/orders/{publicId}/return-requests

# WooCommerce integration APIs
POST /api/integrations/woocommerce/v1/webhooks
GET  /api/integrations/woocommerce/v1/catalog/changes
POST /api/integrations/woocommerce/v1/reconcile/orders/{externalId}

# Staff APIs remain back-office scoped
/api/products
/api/orders
/api/payment-intents
/api/wallets
/api/payouts
```

Customer không được gửi `customerId`, `tenantId`, price, discount hoặc payment status như dữ liệu đáng tin. Backend suy ra identity/tenant và tự tính tiền.

### 7.3. Bảo mật service-to-service

- HTTPS bắt buộc.
- Woo REST consumer key chỉ quyền tối thiểu và xoay vòng được.
- V-ERP webhook dùng secret riêng từng tenant/store.
- Secret nằm trong environment/secret store, không commit vào repo hoặc trả về frontend.
- Allowlist source khi phù hợp nhưng không thay thế signature verification.
- Rate limit public checkout và webhook.
- Log audit không chứa card data, client secret, password hoặc raw PII không cần thiết.

## 8. Vòng đời sản phẩm trên không gian mạng

### 8.1. Trạng thái sản phẩm

```text
DRAFT -> REVIEW -> ACTIVE -> PAUSED -> ARCHIVED
                  |    |
                  |    +-> OUT_OF_STOCK
                  +------> RECALL
```

- `DRAFT`: staff chỉnh sửa, không publish.
- `REVIEW`: owner duyệt giá, ảnh, pháp lý và tồn.
- `ACTIVE`: được publish sang WooCommerce.
- `PAUSED`: tạm ngừng; cart cũ phải revalidate.
- `OUT_OF_STOCK`: còn trang SEO nhưng không mua được, tùy policy.
- `RECALL`: khẩn cấp; chặn checkout và tìm các order chưa giao để staff xử lý.
- `ARCHIVED`: không bán, không hard-delete vì order cũ còn tham chiếu.

### 8.2. Version và snapshot

Order item phải snapshot tại lúc đặt:

- SKU, tên/variant label, unit price, tax class, discount allocation.
- Hình ảnh/URL có thể snapshot nếu cần hóa đơn/tranh chấp.
- Product thay tên/giá sau đó không làm thay đổi order cũ.
- Catalog event mang `version`; Woo bỏ qua event cũ hơn projection hiện tại.

### 8.3. Tồn kho online

```text
sellable = on_hand - reserved - safety_stock - blocked
```

- Add-to-cart không giữ tồn lâu; reservation bắt đầu ở checkout.
- Reservation có TTL và token; payment webhook tới sát hạn vẫn xử lý theo policy rõ ràng.
- Capture thành công nhưng reservation đã mất: chuyển `PAID_REVIEW_REQUIRED`, không tự oversell hoặc âm kho.
- Multi-warehouse ở phase sau; phase đầu mỗi tenant/business có một stock pool rõ ràng.

## 9. Các scenario khó phải cover

| Scenario | Cách xử lý bắt buộc |
|---|---|
| Customer double-click Pay | Idempotency key; một intent nghiệp vụ, có thể nhiều processor attempt có kiểm soát |
| Webhook gửi lặp | Unique external event ID; trả kết quả cũ, không cộng tiền/tồn lần hai |
| Webhook đến trước redirect | Webhook quyết định; trang result đọc trạng thái server |
| Event đến sai thứ tự | State transition guard/version; không lùi trạng thái terminal |
| Payment success nhưng Woo/V-ERP timeout | Processor query + reconcile bằng external ID; order vào review, không thu lần hai |
| Hết hàng giữa checkout và payment | Reservation; nếu mất reservation sau payment thì paid-review/refund/substitution theo lựa chọn khách |
| Giá đổi khi cart mở lâu | Quote version/hash; bắt xác nhận lại trước payment |
| Voucher dùng đồng thời vượt quota | Atomic consume khi order accepted; release khi hủy theo policy |
| Một order có nhiều seller | Tách thành parent checkout + child order theo business; payment allocation và shipment riêng |
| Một item giao trước, item khác chờ | Shipment/item state riêng; order `PARTIALLY_SHIPPED` |
| Customer hủy sau khi đã ship | Chuyển thành return flow, không cancel trực tiếp |
| Partial refund | Refund theo item/amount; phân bổ tax, discount, fee; Woo order không chuyển full-refunded |
| Refund processor thành công nhưng app timeout | Reconcile bằng refund external ID; idempotent ledger posting |
| Chargeback sau payout seller | Tạo dispute/negative receivable; khóa payout theo risk policy, không sửa payment cũ |
| COD giao thành công nhưng thiếu tiền | Settlement exception; kế toán review, không tự đánh dấu paid đủ |
| Chuyển khoản thừa/thiếu/sai reference | `REVIEW_REQUIRED`, match thủ công có audit |
| Khách đổi email | Giữ external customer mapping; không tạo Customer mới chỉ vì email đổi |
| Xóa/anonymize customer | Ẩn/anonymize PII theo retention; giữ số liệu order/ledger cần cho kế toán |
| WooCommerce down | ERP staff vẫn xử lý đơn đã nhập; storefront maintenance, sync/reconcile sau |
| V-ERP down lúc checkout | Không nhận order paid mới nếu không đảm bảo reservation/import; hiển thị retry an toàn |
| Webhook secret bị lộ | Rotate secret, hỗ trợ overlap ngắn, audit/replay recent events |
| Product bị recall khi đang giao | Chặn đơn chưa ship, tạo case liên hệ/return cho đơn đã ship |

## 10. Mô hình dữ liệu bổ sung đề xuất

Không cần thay thế các bảng payment/order hiện có; bổ sung theo migration mới sau khi đối chiếu schema thực tế:

| Entity/bảng | Mục đích |
|---|---|
| `sales_channels` | Store WooCommerce của từng tenant/business, URL, trạng thái, cấu hình không nhạy cảm |
| `external_entity_mappings` | Map product/variant/customer/order giữa ERP và Woo |
| `inventory_reservations` | Giữ tồn theo checkout/order, TTL, trạng thái |
| `order_revisions` | Khóa amount/quote version cho payment |
| `shipments`, `shipment_items` | Giao một phần và nhiều kiện |
| `return_requests`, `return_items` | RMA/return theo item |
| `refund_requests` | Approval và trạng thái processor refund |
| `integration_events` | Inbox idempotent cho webhook, trạng thái xử lý/lỗi |
| `sync_failures` | Hàng đợi lỗi nhỏ trong DB để admin retry thủ công, không cần message broker |
| `mock_bank_accounts/holds/transactions` | Chỉ dev/test |

Tất cả bảng nghiệp vụ phải có `tenant_id`; truy vấn theo ID vẫn phải thêm tenant filter. Dữ liệu tài chính dùng minor unit + currency. Entity có cập nhật cạnh tranh phải có version hoặc row lock phù hợp.

## 11. Quan sát vận hành và đối soát

Dashboard staff cần các queue thay vì chỉ một bảng order:

- Payment processing quá thời gian.
- Paid nhưng chưa có reservation/stock committed.
- Woo order chưa import vào ERP.
- ERP order khác amount/status với Woo.
- Webhook signature fail hoặc delivery fail.
- Refund/chargeback cần review.
- COD/bank transfer chưa đối soát.
- Shipment giao thất bại/return-to-sender.

Mỗi record hiển thị correlation IDs: ERP order ID, Woo order ID, payment intent ID, processor transaction ID và webhook delivery ID. Không hiển thị secret.

Vì V-ERP Lite không dùng message queue/async job, phase đầu xử lý webhook đồng bộ trong transaction ngắn và cung cấp nút/API **retry + reconcile thủ công**. Nếu tải tăng đủ lớn mới lập quyết định kiến trúc riêng cho outbox worker; không lén đưa hạ tầng mới vào phase đầu.

## 12. Phân phase triển khai

### Phase 0 — Chuẩn hóa ranh giới

- Chốt domain/path storefront và tenant resolution.
- Chốt source of truth, role mapping và order/payment states.
- Audit schema/API hiện có; không sửa migration đã chạy.
- Threat model checkout, webhook và tenant isolation.

### Phase 1 — Storefront tối thiểu

- Cài WordPress + WooCommerce Core.
- Tạo connector và mapping product/variant.
- Publish catalog V-ERP -> Woo.
- Guest/customer cart, checkout, order import.
- COD + mock bank; inventory reservation.
- Staff queue cho confirm/pick/pack/ship.

**Exit gate:** một đơn đi từ storefront đến completed; retry không tạo order/payment/tồn trùng.

### Phase 2 — Processor thật

- Stripe adapter/test mode hoặc processor được chọn.
- Signature verification, 3DS, capture/cancel/refund.
- Ledger/escrow integration và đối soát.
- Partial refund, timeout và duplicate webhook tests.

**Exit gate:** test matrix success/fail/timeout/replay/refund pass và không lưu card data.

### Phase 3 — Hậu mãi và vận hành khó

- Shipment/item-level states, partial delivery.
- Return/RMA, replacement, partial refund.
- COD settlement, bank transfer exception.
- Chargeback/dispute và payout hold policy.

### Phase 4 — Tối ưu, chưa mặc định làm

- SSO customer bằng OIDC nếu thực sự cần.
- Multi-warehouse, advanced promotion, subscription.
- Tự động reconciliation/outbox worker nếu quy mô chứng minh cần thiết.

## 13. Test và release gate

### Automated

- Tenant isolation cho mọi storefront/integration API.
- Idempotency: create order, capture, webhook, refund.
- Concurrency: stock reservation, voucher quota, mock bank debit.
- State transition hợp lệ và chặn transition ngược.
- Ledger cân bằng và balance projection khớp.
- Contract tests payload WooCommerce/connector.
- Security tests: signature sai, replay, amount/currency/tenant tampering.

### Manual end-to-end

1. Publish sản phẩm có variant từ ERP sang Woo.
2. Guest checkout bằng COD; staff confirm -> ship -> complete.
3. Customer checkout mock bank đủ tiền và không đủ tiền.
4. Gửi callback trùng 3 lần, chỉ ghi một payment.
5. Hai customer mua SKU cuối cùng đồng thời, chỉ một reservation thành công.
6. Thanh toán rồi partial refund một item.
7. Giao một phần rồi return item còn lại.
8. Ngắt Woo hoặc ERP giữa luồng, bật lại và reconcile không thu tiền lần hai.

Không release production nếu còn đường dẫn cho customer gọi API staff, webhook chưa verify signature, money còn dùng floating point, hoặc chưa có quy trình reconcile.

## 14. Các quyết định cần chủ dự án xác nhận trước khi code

1. Chọn URL production: `shop.example.com` + `erp.example.com` (khuyến nghị) hay cùng domain `/shop` + `/app`?
2. Phase 1 cho guest checkout hay bắt buộc đăng ký customer?
3. Customer đăng nhập bằng tài khoản WordPress riêng (khuyến nghị phase đầu) hay bắt buộc SSO Keycloak ngay?
4. Phase 1 là một seller/tenant hay cart có thể chứa nhiều business/seller?
5. Processor thật ưu tiên Stripe, VNPay, MoMo hay nhà cung cấp khác; currency chính là VND hay đa tiền tệ?
6. Có dùng escrow/seller payout ngay không, hay đây là cửa hàng single-merchant chỉ cần payment settlement?
7. Chính sách reservation TTL, hủy đơn, return window và approval threshold cho refund là bao nhiêu?
8. WooCommerce chỉ làm storefront hay nhân viên cửa hàng cũng được phép quản lý order trong `wp-admin`? Khuyến nghị: staff vận hành trong V-ERP; `wp-admin` chỉ dành cho kỹ thuật/content admin.

Các câu trên không chặn việc dựng Phase 0, nhưng phải được chốt trước khi khóa schema và viết connector/payment adapter.

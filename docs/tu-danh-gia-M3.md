# Tự đánh giá M3 — Mô hình lớp và trạng thái

Nhóm: N__10
Phụ trách: N10-C
Cặp sơ đồ: `diagrams/class.mmd`, `diagrams/state.mmd`, `diagrams/state-escrow.mmd`,
`diagrams/state-thanh-toan.mmd`
Nguồn đối chiếu: `src/main/java/base/entity`, `src/main/java/base/rest`, `src/main/java/base/service`,
`ecommerce-chatbot/mcp_server`, `ecommerce-chatbot/agents`

## 1. Phạm vi

M3 mô tả mô hình tĩnh (lớp, thuộc tính, quan hệ) và mô hình động (trạng thái) của ba luồng đã đặc tả
ở Gói 3: đặt hàng và thanh toán, xử lý đơn và giải phóng escrow, trợ lý AI tra cứu. Sơ đồ lớp gồm
cả lớp application layer và lớp AI để mọi participant trong `diagrams/seq-*.mmd` đều là lớp có thật
trong sơ đồ.

## 2. Sản phẩm

| Tệp | Nội dung | Số lượng |
|-----|----------|----------|
| `diagrams/class.mmd` | 59 lớp: entity, giá trị, service, resource, AI | 59 |
| `diagrams/state.mmd` | Vòng đời `Order` | 14 trạng thái |
| `diagrams/state-escrow.mmd` | Vòng đời `EscrowRecord` | 3 trạng thái |
| `diagrams/state-thanh-toan.mmd` | Vòng đời `PaymentIntent` | 5 trạng thái |
| `docs/tu-danh-gia-M3.md` | Tự đánh giá và bảng đối chiếu | tệp này |

## 3. Danh mục lớp trong `class.mmd`

### 3.1 Nền tảng và hệ thống

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `PanacheEntityBase` | framework | Quarkus | Base của entity không có audit |
| `BaseEntity` | abstract | `base/entity/BaseEntity.java` | `tenantId`, audit, `@Version` |
| `Tenant` | entity | `base/entity/Tenant.java` | Đơn vị thuê bao hệ thống |
| `Role` | entity | `base/entity/Role.java` | Vai trò hệ thống |
| `TenantConfig` | entity | `base/entity/TenantConfig.java` | Cấu hình JSON theo tenant |
| `UserSettings` | entity | `base/entity/UserSettings.java` | Cấu hình JSON theo người dùng |

### 3.2 Người dùng và cửa hàng

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `User` | entity | `base/entity/User.java` | Tài khoản nội bộ, liên kết Keycloak |
| `Business` | entity | `base/entity/Business.java` | Cửa hàng/người bán |
| `Customer` | entity | `base/entity/Customer.java` | Người mua, điểm và hạng |

### 3.3 Danh mục sản phẩm

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `Category` | entity | `base/entity/Category.java` | Cây danh mục |
| `Product` | entity | `base/entity/Product.java` | Sản phẩm, giá, tồn kho |
| `ProductVariant` | entity | `base/entity/ProductVariant.java` | Biến thể SKU, tồn riêng |
| `Tag` | entity | `base/entity/Tag.java` | Nhãn nhiều-nhiều |
| `ProductOption` | value object | `base/entity/ProductOption.java` | Cấu hình lựa chọn JSON |

### 3.4 Khuyến mãi

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `Voucher` | entity | `base/entity/Voucher.java` | Mã giảm giá, hạn mức |
| `CustomerVoucher` | entity | `base/entity/CustomerVoucher.java` | Ví voucher của khách |

### 3.5 Đơn hàng

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `Order` | entity | `base/entity/Order.java` | Aggregate gốc của đơn |
| `OrderItem` | entity | `base/entity/OrderItem.java` | Dòng hàng, hủy/trả từng phần |
| `OrderHistory` | entity | `base/entity/OrderHistory.java` | Audit chuyển trạng thái |

### 3.6 Thanh toán

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `PaymentIntent` | entity | `base/entity/PaymentIntent.java` | Ý định thanh toán, hạn 30 phút |
| `PaymentTransaction` | entity | `base/entity/PaymentTransaction.java` | Giao dịch cổng thanh toán |
| `PaymentRefund` | entity | `base/entity/PaymentRefund.java` | Hoàn tiền qua cổng |
| `PaymentIdempotency` | entity | `base/entity/PaymentIdempotency.java` | Khử trùng yêu cầu |
| `WebhookEvent` | entity | `base/entity/WebhookEvent.java` | Nhật ký webhook nhận vào |

### 3.7 Ví, escrow và đối soát

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `Wallet` | entity | `base/entity/Wallet.java` | Ví seller/platform/admin |
| `WalletTransaction` | entity | `base/entity/WalletTransaction.java` | Bút toán append-only |
| `WalletTopUp` | entity | `base/entity/WalletTopUp.java` | Nạp tiền |
| `PayoutRequest` | entity | `base/entity/PayoutRequest.java` | Yêu cầu rút tiền |
| `Bank` | entity | `base/entity/Bank.java` | Danh mục ngân hàng |
| `LedgerCheckpoint` | entity | `base/entity/LedgerCheckpoint.java` | Điểm đối soát số dư |
| `EscrowRecord` | entity | `base/entity/EscrowRecord.java` | Tiền tạm giữ theo đơn |
| `RevenueAdjustment` | entity | `base/entity/RevenueAdjustment.java` | Điều chỉnh doanh thu thủ công |
| `VendorRiskEvent` | entity | `base/entity/VendorRiskEvent.java` | Tín hiệu rủi ro người bán |

### 3.8 Thông báo, thuê bao và tích hợp

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `Notification` | entity | `base/entity/Notification.java` | Thông báo trong ứng dụng |
| `SubscriptionPlan` | entity | `base/entity/SubscriptionPlan.java` | Gói BASE/PREMIUM |
| `TenantSubscription` | entity | `base/entity/TenantSubscription.java` | Gói đang dùng của tenant |
| `SubscriptionPayment` | entity | `base/entity/SubscriptionPayment.java` | Thanh toán gói |
| `SalesChannel` | entity | `base/entity/SalesChannel.java` | Kênh bán (WooCommerce...) |
| `ExternalEntityMapping` | entity | `base/entity/ExternalEntityMapping.java` | Ánh xạ id nội bộ - ngoài |
| `InboundEventLog` | entity | `base/entity/InboundEventLog.java` | Sự kiện cổng thanh toán đã xử lý |

### 3.9 Application layer

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `OrderResource` | resource | `base/rest/OrderResource.java` | API đơn hàng, luật chuyển trạng thái |
| `OrderFulfillmentService` | service | `base/service/OrderFulfillmentService.java` | Owner nhận/từ chối, hoàn tất |
| `PaymentIntentService` | service | `base/service/PaymentIntentService.java` | Tạo/hết hạn ý định thanh toán |
| `PaymentService` | service | `base/service/PaymentService.java` | Tất toán, tạo escrow |
| `PaymentRefundService` | service | `base/service/PaymentRefundService.java` | Hoàn tiền qua cổng |
| `EscrowService` | service | `base/service/EscrowService.java` | Giữ, giải phóng, hoàn escrow |
| `EscrowReleaseScheduler` | scheduler | `base/service/EscrowReleaseScheduler.java` | Giải phóng escrow tới hạn |
| `OwnerDecisionScheduler` | scheduler | `base/service/OwnerDecisionScheduler.java` | Hủy đơn quá hạn owner quyết |
| `WalletService` | service | `base/service/WalletService.java` | Ghi có/ghi nợ ví |
| `LedgerCheckpointService` | service | `base/service/LedgerCheckpointService.java` | Đối soát sổ cái |
| `EntitlementService` | service | `base/service/EntitlementService.java` | Gate tính năng theo gói |
| `ReturnRefundService` | service | `base/service/ReturnRefundService.java` | Hủy/trả hàng từng item |
| `NotificationService` | service | `base/service/NotificationService.java` | Tạo thông báo |

### 3.10 Lớp AI

| Lớp | Loại | Nguồn | Vai trò |
|-----|------|-------|---------|
| `ChatWidget` | React | `ecommerce-chatbot/frontend/chat_widget.tsx` | Gửi tin nhắn kèm JWT |
| `ChatEndpoint` | FastAPI | `ecommerce-chatbot/mcp_server/server.py` | Xác thực, điều phối `/chat` |
| `RequestContext` | identity | `ecommerce-chatbot/mcp_server/request_context.py` | Danh tính lấy từ JWT |
| `SessionStore` | store | `ecommerce-chatbot/mcp_server/session_store.py` | Lịch sử server-owned |
| `UnifiedAgent` | agent | `ecommerce-chatbot/agents/unified_agent.py` | Vòng lặp tool có giới hạn |
| `OutputValidator` | guard | `ecommerce-chatbot/mcp_server/output_validator.py` | 4 tầng kiểm câu trả lời |

## 4. Đối chiếu quan hệ lớp với mã

| Quan hệ | Bằng chứng |
|---------|-----------|
| `BaseEntity` có `tenantId`, audit, `version` | `BaseEntity.java` |
| `Order` 1-n `OrderItem` | `Order.items` với `cascade = ALL` |
| `Order` n-1 `Customer`, `Business` | `Order.customer`, `Order.business` |
| `OrderItem` n-1 `Product`, `ProductVariant` | `OrderItem.product`, `OrderItem.productVariant` |
| `Product` 1-n `ProductVariant` | `Product.variants` |
| `Product` n-n `Tag` | `product_tags` join table |
| `Product` n-1 `Category` | `Product.category` |
| `Wallet` 1-n `WalletTransaction` | `WalletTransaction.wallet` |
| `Wallet` n-1 `User`, `Bank` | `Wallet.user`, `Wallet.bank` |
| `PayoutRequest`, `WalletTopUp` n-1 `Wallet` | khóa ngoại `wallet_id` |
| `EscrowRecord` tham chiếu `Order` bằng `orderId` và `Wallet` bằng `sellerWallet` | `EscrowRecord.java` |
| `PaymentIntent` tham chiếu `Order` bằng `orderId`; `PaymentRefund` n-1 `PaymentIntent` | `PaymentIntent.java`, `PaymentRefund.java` |
| `TenantSubscription` dùng `planCode` chuỗi, không FK tới `SubscriptionPlan` | `TenantSubscription.java` |
| `ExternalEntityMapping` tham chiếu `SalesChannel` bằng `salesChannelId` | `ExternalEntityMapping.java` |
| `OrderHistory` KHÔNG kế thừa `BaseEntity` | `OrderHistory extends PanacheEntityBase` |

## 5. Đối chiếu trạng thái với mã

### 5.1 Order

Nguồn: `OrderResource.isAllowedStatusTransition` và `OrderResource.updateStatus`.

| Từ | Sang | Ghi chú |
|----|------|---------|
| `PENDING_PAYMENT` | `PAID_ESCROW`, `CANCELLED` | hết hạn 30 phút thì hủy |
| `PAID_ESCROW` | `OWNER_ACCEPTED`, `OWNER_DECLINED`, `CANCELLED` | owner quyết trong SLA |
| `OWNER_ACCEPTED` | `FULFILLING` | chỉ một bước |
| `FULFILLING` | `DELIVERED` | giao xong |
| `DELIVERED` | `PENDING_COMPLETION` | tự động, không dừng ở `DELIVERED` |
| `PENDING_COMPLETION` | `COMPLETED`, `CANCELLED` | xác nhận hoặc hủy |
| `OWNER_DECLINED` | `REFUNDED` | đối soát thủ công; `declineAndRefund()` hoàn tiền nhưng giữ `OWNER_DECLINED` |
| `RETURN_REQUESTED` | `RETURN_APPROVED` | duyệt trả |
| `RETURN_APPROVED` | `REFUNDED` | hoàn tiền |
| legacy | `CANCELLED`/`SHIPPED` | `PENDING`, `CONFIRMED`, `SHIPPING`, `SHIPPED` |

Các trạng thái yêu cầu hủy (`CANCEL_REQUESTED`, `CANCEL_APPROVED`) do `ReturnRefundService` đặt,
không nằm trong bảng chuyển của `updateStatus`; sơ đồ ghi nhánh này để phản ánh đúng.

### 5.2 EscrowRecord

`HELD` → `RELEASED` hoặc `REFUNDED`; không có chuyển ngược. Bằng chứng: `EscrowService`
kiểm tra `HELD` trước khi giải phóng/hoàn và đặt trạng thái cuối.

### 5.3 PaymentIntent

`REQUIRES_PAYMENT_METHOD` → `PROCESSING` → `SUCCEEDED`/`FAILED`; hết hạn → `CANCELLED`.
Bằng chứng: `PaymentIntentService.expireIntent`, `PaymentWebhookResource`.

## 6. Sai lệch và giới hạn đã biết

1. `DELIVERED` là trạng thái trung gian: API tự chuyển sang `PENDING_COMPLETION`, nên sơ đồ đơn
   giản hóa bước này bằng chuyển tiếp có nhãn "tự động".
2. `releaseDueAt` được tính từ lúc thanh toán (`PaymentService`), không phải từ lúc giao; sơ đồ
   ghi đúng như mã nhưng đây là điểm cần chốt lại nghiệp vụ.
3. `RevenueAdjustment` là điều chỉnh doanh thu thủ công, không tham gia giải phóng escrow; sơ đồ
   chỉ nối với `Business`, không nối với `EscrowService`.
4. Sơ đồ lớp có 59 lớp, nhiều hơn phạm vi 3 use case, vì giữ đủ entity để đối chiếu `base/entity`.
5. Chưa chạy được `scripts/kiem_moc.py` (không có trong kho); việc kiểm cú pháp Mermaid làm bằng
   mắt và đối chiếu từng dòng với mã.

## 7. Tự chấm theo tiêu chí M3

| Tiêu chí | Cách kiểm | Kết quả | Bằng chứng |
|----------|-----------|---------|------------|
| Có `diagrams/class.mmd` | kiểm tệp | Đạt | 59 lớp |
| Có `diagrams/state.mmd` | kiểm tệp | Đạt | 14 trạng thái Order |
| Sơ đồ khớp mã | đối chiếu từng lớp/trạng thái với nguồn | Đạt sau khi sửa 5 lỗi | mục 4, 5, 6 |
| Participant sequence là lớp trong class diagram | so khớp tên | Đạt | 3 tệp `seq-*.mmd` |
| Mỗi lớp có trong `docs/` | bảng mục 3 | Đạt | 59 dòng |
| Có tự đánh giá | tệp này | Đạt | mục 1-8 |

## 8. Việc còn lại

- Bổ sung test tự động đọc `.mmd` để chặn participant lạ (khi có `scripts/kiem_moc.py`).
- Chốt chính sách `releaseDueAt` tính từ giao hàng hay thanh toán.
- Vẽ thêm state cho `PayoutRequest` nếu Gói 5 cần luồng payout chi tiết.

# Đặc tả UC-01 — Đặt hàng và thanh toán escrow

Phụ trách: N10-C
Liên quan: FR-05, FR-06, FR-07, FR-08, FR-09, NFR-03, NFR-04
Sơ đồ: `diagrams/use-case.mmd`, `diagrams/seq-checkout-thanh-toan.mmd`, `diagrams/state.mmd`

## 1. Thông tin chung

| Thuộc tính | Giá trị |
|------------|---------|
| Mã | UC-01 |
| Tên | Đặt hàng và thanh toán escrow |
| Actor chính | Khách mua (Customer) |
| Actor phụ | Cổng thanh toán, Keycloak, Chủ shop (nhận thông báo) |
| Mục tiêu | Tạo đơn hợp lệ, giữ tồn kho, nhận tiền vào escrow `HELD` |
| Trigger | Khách bấm "Đặt hàng" ở giỏ hàng |
| Tiền điều kiện | Đã đăng nhập; giỏ có ít nhất 1 item; tenant đang ở trạng thái ghi được |
| Hậu điều kiện thành công | Đơn `PENDING_PAYMENT` → sau thanh toán `PAID_ESCROW`; `EscrowRecord` `HELD` với `releaseDueAt = now + 7 ngày`; tồn kho bị giữ; có `OrderHistory` |
| Hậu điều kiện thất bại | Không tạo đơn hoặc đơn `PENDING_PAYMENT` hết hạn; tồn kho được trả; không có giao dịch tiền |

## 2. Luồng chính

1. Khách mở giỏ, chọn/bỏ item, nhập voucher (nếu có).
2. Hệ thống hiển thị tạm tính theo từng người bán; client không gửi giá quyết định.
3. Khách nhập địa chỉ giao, chọn phương thức thanh toán, bấm "Đặt hàng".
4. `OrderResource` xác thực JWT, dựng `CallerContext` (tenant, user, roles).
5. Hệ thống kiểm tra tenant còn ghi được (`EntitlementService.requireWritable`) và hạn mức.
6. Hệ thống tách giỏ theo `business_id`; với mỗi người bán tạo một `Order` và các `OrderItem`.
7. Server tự tính lại giá bán, voucher, thuế, phí giao; ghi `subtotal/minor`, `discount`, `tax`,
   `finalAmount` bằng minor units.
8. Hệ thống giữ tồn kho bằng khóa bi quan (`SELECT ... FOR UPDATE`), tăng `reservedQuantity`;
   nếu không đủ tồn thì hủy giao dịch (xem 4a).
9. Hệ thống tạo `PaymentIntent` (số tiền = `finalAmountMinor`, hạn 30 phút, idempotency key)
   và trả mã QR VietQR.
10. Khách quét QR hoặc xác nhận thanh toán trên màn hình.
11. Cổng thanh toán gọi webhook/kết quả; `PaymentService` xác thực, ghi `PaymentTransaction`
    `SUCCESS`, chuyển `PaymentIntent` `SUCCEEDED`.
12. Hệ thống chuyển đơn sang `PAID_ESCROW`, tạo `EscrowRecord` `HELD` gắn với ví người bán,
    đặt `releaseDueAt = now + orders.escrow-hold-days` (mặc định 7 ngày), đặt
    `ownerDecisionDeadline`, ghi `OrderHistory` và thông báo cho chủ shop.
13. Trả kết quả thành công cho khách; màn hình hiển thị trạng thái đơn và hướng dẫn chờ giao.

## 3. Luồng thay thế và ngoại lệ

- **4a. Tồn kho không đủ:** hủy giao dịch, trả 409 kèm item thiếu; không tạo đơn, không giữ tiền.
- **6a. Giỏ có nhiều người bán:** tạo nhiều đơn, mỗi đơn một PaymentIntent; hiển thị tổng theo đơn.
- **8a. Voucher không hợp lệ/hết lượt:** từ chối ở bước 8, trả 400; khách có thể bỏ voucher.
- **9a. Tenant hết hạn gói:** chặn ở bước 5, trả 402; chỉ cho xem dữ liệu.
- **10a. Khách không thanh toán trong 30 phút:** PaymentIntent hết hạn (`expireIntent`), đơn
  `PENDING_PAYMENT` bị hủy, tồn kho được trả; khách có thể đặt lại.
- **11a. Thanh toán thất bại:** PaymentIntent `FAILED`, đơn giữ `PENDING_PAYMENT` cho tới hết hạn;
  khách được thử lại với cùng idempotency key.
- **11b. Webhook gửi lặp:** khử trùng theo `gateway + gateway_transaction_id`; bỏ qua bản ghi lặp.
- **12a. Tạo escrow lỗi:** rollback toàn bộ giao dịch thanh toán; không đánh dấu đơn đã thanh toán.

## 4. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|----|---------|
| BR-01 | Giá, thuế, phí, voucher do server tính lại; không tin giá client gửi |
| BR-02 | Không bán vượt tồn khả dụng (`stockQuantity - reservedQuantity`) |
| BR-03 | Một đơn thuộc đúng một người bán; giỏ nhiều người bán tách thành nhiều đơn |
| BR-04 | Tiền lưu BIGINT minor units; đơn vị VND |
| BR-05 | PaymentIntent hết hạn sau 30 phút; idempotency key duy nhất theo tenant |
| BR-06 | Thanh toán thành công luôn tạo escrow `HELD` trước khi đơn sang `PAID_ESCROW` |
| BR-07 | Hoa hồng sàn 5% chỉ tính khi giải phóng escrow, không tính lúc thanh toán |
| BR-24 | Thời hạn giữ escrow mặc định 7 ngày (`orders.escrow-hold-days`); hiện tính từ lúc thanh toán |

## 5. Trạng thái liên quan

`PENDING_PAYMENT` → `PAID_ESCROW` (thành công). Nhánh lỗi: `PENDING_PAYMENT` → `CANCELLED`
(hết hạn/không thanh toán). Chi tiết ở `diagrams/state.mmd`.

## 6. Tiêu chí chấp nhận (Given–When–Then)

**AC-01.1 Đặt hàng thành công một người bán**
- Given khách đã đăng nhập, giỏ có 2 sản phẩm còn tồn, tenant đang ACTIVE
- When khách bấm đặt hàng và thanh toán thành công
- Then hệ thống tạo đúng 1 đơn, trạng thái `PAID_ESCROW`, 1 `EscrowRecord` `HELD`,
  `reservedQuantity` tăng đúng số lượng, và có bản ghi `OrderHistory` `PENDING_PAYMENT → PAID_ESCROW`

**AC-01.2 Không bán vượt tồn**
- Given sản phẩm còn 1 cái, khách đặt 2 cái
- When bấm đặt hàng
- Then hệ thống trả 409, không tạo đơn, không tạo PaymentIntent, tồn kho không đổi

**AC-01.3 Giỏ nhiều người bán**
- Given giỏ có sản phẩm của 2 cửa hàng
- When đặt hàng
- Then hệ thống tạo 2 đơn riêng, mỗi đơn một `PaymentIntent`, tổng tiền mỗi đơn khớp tạm tính

**AC-01.4 Idempotency thanh toán**
- Given một PaymentIntent đã `SUCCEEDED`
- When webhook/kết quả thanh toán được gửi lại 2 lần
- Then chỉ có 1 `PaymentTransaction` `SUCCESS`, 1 escrow, trạng thái đơn không đổi

**AC-01.5 Tenant hết hạn**
- Given tenant có gói đã hết hạn
- When khách cố đặt hàng
- Then hệ thống trả 402, không tạo đơn, dữ liệu vẫn xem được

## 7. Truy vết mã nguồn

| Thành phần | Vị trí |
|------------|--------|
| Endpoint tạo/đọc đơn | `src/main/java/base/rest/OrderResource.java` |
| Tính giá, voucher | `base.service.OrderFulfillmentService`, `base.entity.Voucher` |
| Giữ tồn kho | `base.entity.Product`, truy vấn khóa bi quan trong `OrderResource` |
| Thanh toán | `base.service.PaymentIntentService`, `base.service.PaymentService` |
| Escrow | `base.service.EscrowService`, `base.entity.EscrowRecord` |
| Lịch sử đơn | `base.entity.OrderHistory` |
| Gói thuê bao | `base.service.EntitlementService` |

## 8. Câu hỏi mở

- Thời gian giữ tồn kho trước khi hủy đơn có nên cấu hình theo tenant?
- `releaseDueAt` nên tính từ lúc giao hàng thay vì lúc thanh toán? Hiện mã nguồn tính từ lúc
  thanh toán, nên escrow có thể tới hạn trước khi đơn được giao.
- Có cần đặt cọc một phần (partial payment) cho đơn lớn không? (ngoài phạm vi hiện tại)

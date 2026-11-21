# Đặc tả UC-02 — Xử lý đơn và giải phóng escrow

Phụ trách: N10-B
Liên quan: FR-10, FR-11, FR-12, FR-13, FR-15, FR-16, NFR-03
Sơ đồ: `diagrams/use-case.mmd`, `diagrams/seq-escrow-release.mmd`, `diagrams/state.mmd`

## 1. Thông tin chung

| Thuộc tính | Giá trị |
|------------|---------|
| Mã | UC-02 |
| Tên | Xử lý đơn và giải phóng escrow |
| Actor chính | Chủ shop (owner), Nhân viên vận hành (operator), Khách mua (xác nhận) |
| Actor phụ | Scheduler giải phóng escrow, Ví người bán |
| Mục tiêu | Đưa đơn từ `PAID_ESCROW` tới `COMPLETED` và chuyển tiền ròng cho người bán |
| Trigger | Đơn mới ở `PAID_ESCROW` hoặc scheduler tới hạn |
| Tiền điều kiện | Đơn `PAID_ESCROW`; escrow `HELD`; user có quyền trên đơn/cửa hàng |
| Hậu điều kiện thành công | Đơn `COMPLETED`; escrow `RELEASED`; ví người bán tăng số dư khả dụng (đã trừ 5%); ví platform giữ hoa hồng; có sổ cái + checkpoint cập nhật |
| Hậu điều kiện thất bại | Đơn giữ nguyên trạng thái; escrow vẫn `HELD`; không ghi sổ cái |

## 2. Luồng chính

1. Hệ thống hiển thị đơn `PAID_ESCROW` cho chủ shop kèm `ownerDecisionDeadline`.
2. Chủ shop mở đơn, kiểm tra hàng và chọn "Nhận đơn".
3. `OrderFulfillmentService` kiểm tra quyền owner trên `business_id` của đơn.
4. Hệ thống chuyển trạng thái `PAID_ESCROW → OWNER_ACCEPTED`, ghi `OrderHistory`.
5. Nhân viên vận hành đóng gói, cập nhật `OWNER_ACCEPTED → FULFILLING → DELIVERED`.
   Hệ thống tự chuyển tiếp `DELIVERED → PENDING_COMPLETION` để bắt đầu cửa sổ chờ xác nhận.
6. Mỗi lần đổi trạng thái, hệ thống ghi `OrderHistory` với người thao tác và thời điểm.
7. Người mua xem đơn `PENDING_COMPLETION` và bấm "Đã nhận hàng".
8. Hệ thống kiểm tra escrow còn `HELD`; chuyển đơn `COMPLETED`, ghi `OrderHistory`.
9. `EscrowService` giải phóng escrow: tính hoa hồng sàn 5%, chuyển tiền từ ví platform
   sang ví người bán với số tiền ròng.
10. Ghi `WalletTransaction` `CREDIT` loại `ESCROW_SELLER_CREDIT`; cập nhật `LedgerCheckpoint`.
11. Đặt escrow `RELEASED`; nếu đơn đang `PENDING_COMPLETION` thì escrow tự đưa đơn sang
    `COMPLETED`; gửi thông báo cho người bán và người mua.

## 3. Luồng tự động giải phóng

- **Đường scheduler:** `EscrowReleaseScheduler` chạy định kỳ, tìm escrow `HELD` có
  `releaseDueAt <= now` (mặc định `now + 7 ngày` tại thời điểm giữ escrow), rồi thực hiện bước 8–11.
- Scheduler dùng cùng `EscrowService` nên quy tắc phí và sổ cái không bị lệch so với luồng tay.
- Lưu ý: `releaseDueAt` hiện được tính từ lúc thanh toán (`PaymentService`), không phải từ lúc
  giao hàng; xem câu hỏi mở.

## 4. Luồng thay thế và ngoại lệ

- **2a. Chủ shop từ chối:** đơn `OWNER_DECLINED`, escrow `REFUNDED`, hoàn tiền cho người mua,
  tồn kho được trả, ghi `OrderHistory`. (UC-09 mở rộng)
- **2b. Quá `ownerDecisionDeadline` chưa quyết:** scheduler xử lý theo chính sách (hủy + hoàn tiền),
  ghi lý do vào `OrderHistory`.
- **3a. Owner không thuộc cửa hàng:** trả 404/403, không lộ sự tồn tại của đơn.
- **5a. Operator cập nhật sai thứ tự trạng thái:** từ chối 409, không ghi lịch sử.
- **8a. Escrow không còn `HELD`:** từ chối giải phóng, trả 409; ghi log để đối soát.
- **9a. Ví người bán không tồn tại:** tạo ví theo owner nếu hợp lệ; nếu không, rollback.
- **9b. Giao dịch đồng thời trên cùng ví:** khóa bi quan bảo đảm tuần tự; không âm ví.
- **7a. Người mua chưa nhận nhưng quá 7 ngày:** scheduler vẫn giải phóng theo chính sách (mặc định),
  trừ khi có yêu cầu trả hàng/khiếu nại đang mở.

## 5. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|----|---------|
| BR-08 | Chỉ owner của `business_id` tương ứng được nhận/từ chối đơn |
| BR-09 | Chuyển trạng thái đơn phải theo thứ tự hợp lệ; mọi thay đổi ghi `OrderHistory` |
| BR-10 | Escrow chỉ giải phóng khi `HELD`; sau đó là `RELEASED` hoặc `REFUNDED`, không quay lại |
| BR-11 | Hoa hồng sàn 5% giữ lại ở ví platform tại thời điểm giải phóng, ghi thành bút toán riêng |
| BR-12 | Mọi biến động ví dùng `PESSIMISTIC_WRITE` và ghi `WalletTransaction` append-only |
| BR-13 | Số dư khả dụng = tiền đã giải phóng - payout; số dư chờ phản ánh escrow đang giữ |
| BR-14 | Tổng sổ cái phải khớp `LedgerCheckpoint`; sai lệch là lỗi nghiêm trọng |

## 6. Trạng thái liên quan

`PAID_ESCROW` → `OWNER_ACCEPTED` → `FULFILLING` → `DELIVERED` → `PENDING_COMPLETION`
→ `COMPLETED`.
Nhánh: `PAID_ESCROW` → `OWNER_DECLINED` (escrow `REFUNDED`, đơn chỉ sang `REFUNDED` khi staff
đối soát); `PENDING_COMPLETION` → trả hàng
(`RETURN_REQUESTED` → `RETURN_APPROVED`) → `REFUNDED`. Chi tiết ở `diagrams/state.mmd`.

## 7. Tiêu chí chấp nhận (Given–When–Then)

**AC-02.1 Owner nhận đơn**
- Given đơn `PAID_ESCROW` thuộc cửa hàng của owner
- When owner bấm "Nhận đơn"
- Then đơn `OWNER_ACCEPTED`, có `OrderHistory`, các bên còn lại không có quyền thao tác

**AC-02.2 Giao hàng theo thứ tự**
- Given đơn `OWNER_ACCEPTED`
- When operator cập nhật `DELIVERED` mà bỏ qua `FULFILLING`
- Then hệ thống từ chối 409 và trạng thái không đổi

**AC-02.3 Người mua xác nhận giải phóng escrow**
- Given đơn `PENDING_COMPLETION`, escrow `HELD` với số tiền 1.000.000 VND
- When người mua bấm "Đã nhận hàng"
- Then đơn `COMPLETED`, escrow `RELEASED`, ví người bán +950.000 VND (trừ 5%),
  có 1 `WalletTransaction` `CREDIT/ESCROW_SELLER_CREDIT`, checkpoint khớp

**AC-02.4 Tự động giải phóng sau 7 ngày**
- Given đơn `PENDING_COMPLETION`, escrow `HELD`, `releaseDueAt` đã qua, không có khiếu nại
- When scheduler chạy
- Then đơn `COMPLETED` và escrow được giải phóng đúng một lần

**AC-02.5 Owner từ chối**
- Given đơn `PAID_ESCROW`, escrow `HELD`
- When owner từ chối
- Then đơn `OWNER_DECLINED`, escrow `REFUNDED`, có bút toán hoàn tiền, tồn kho được trả

**AC-02.6 Chống giải phóng hai lần**
- Given escrow đã `RELEASED`
- When scheduler hoặc người dùng kích hoạt giải phóng lần nữa
- Then hệ thống từ chối, không phát sinh bút toán thứ hai

## 8. Truy vết mã nguồn

| Thành phần | Vị trí |
|------------|--------|
| Endpoint đơn/owner decision | `base.rest.OrderResource` |
| Xử lý trạng thái đơn | `base.service.OrderFulfillmentService` |
| Giải phóng escrow | `base.service.EscrowService` |
| Scheduler 7 ngày | `base.service.EscrowReleaseScheduler` |
| Tạo escrow lúc thanh toán | `base.service.PaymentService` |
| Deadline owner | `base.service.OwnerDecisionScheduler` |
| Ví và sổ cái | `base.service.WalletService`, `base.entity.Wallet`, `base.entity.WalletTransaction` |
| Đối soát | `base.service.LedgerCheckpointService`, `base.entity.LedgerCheckpoint` |

## 9. Câu hỏi mở

- Có cần cấu hình thời hạn escrow theo ngành hàng/gói không?
- `releaseDueAt` nên tính từ lúc giao hàng thay vì lúc thanh toán? Hiện tại escrow có thể tới
  hạn trước khi đơn được giao.
- Khi giải phóng một phần (trả hàng từng item), phí sàn hoàn lại theo tỷ lệ hay giữ nguyên?

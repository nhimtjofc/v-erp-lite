# Yêu cầu hệ thống — V-ERP Lite

Phụ trách: N10-A
Cập nhật: Vòng 1 (Gói 2) — 2026-10-18

Tài liệu này tổng hợp yêu cầu của sản phẩm V-ERP Lite sau vòng phỏng vấn 1. Yêu cầu được
viết lại theo góc nhìn người dùng, có mã truy vết để nối sang đặc tả use case (Gói 3) và
sơ đồ lớp/trạng thái (Gói 4). Nội dung phản ánh đúng những gì mã nguồn hiện thực, không
đề xuất tính năng ngoài phạm vi "lite".

## 1. Bối cảnh và mục tiêu

V-ERP Lite là nền tảng ERP tinh gọn cho doanh nghiệp vừa và nhỏ, mở rộng thành sàn
thương mại điện tử đa người bán. Hệ thống chạy multi-tenant: nhiều tổ chức dùng chung hạ
tầng nhưng dữ liệu phải cô lập tuyệt đối.

Bốn mục tiêu kinh doanh:

| Mã | Mục tiêu | Thước đo thành công |
|----|----------|---------------------|
| G-1 | Tinh gọn vận hành bán hàng và tồn kho | Một đơn hàng đi trọn vòng đời không cần thao tác ngoài hệ thống |
| G-2 | Minh bạch dòng tiền đa người bán | Tiền của người mua được giữ (escrow) đến khi nhận hàng; sai lệch sổ cái bằng 0 |
| G-3 | Tự động hóa bán hàng đa kênh | Webhook WooCommerce trùng lặp không tạo đơn thứ hai |
| G-4 | Trải nghiệm mua sắm an toàn với AI | 0% PII rò rỉ qua chatbot; AI không tạo/sửa/xóa dữ liệu |

## 2. Các bên liên quan

| Vai trò | Mã | Nhu cầu chính |
|---------|----|---------------|
| Khách mua (customer) | N10-C | Duyệt sản phẩm, đặt hàng, thanh toán, theo dõi đơn, trả hàng, được AI hỗ trợ |
| Chủ shop (owner) | N10-B | Quản lý cửa hàng, sản phẩm, tồn kho, quyết định nhận/từ chối đơn, xem doanh thu, rút tiền |
| Nhân viên vận hành (operator) | N10-D | Xử lý đơn, đóng gói, bàn giao vận chuyển, xử lý hoàn/đổi trả |
| Admin nền tảng | N10-A | Quản lý tenant, người dùng, phân quyền, duyệt payout ngoại lệ, duyệt thanh toán gói, cấu hình hệ thống |
| Cổng thanh toán / ngân hàng | N10-E | Nhận yêu cầu thanh toán, trả kết quả qua webhook (chế độ mock khi demo) |

## 3. Phạm vi

### Trong phạm vi

- Quản lý sản phẩm, biến thể, SKU, danh mục, tag, tồn kho và tồn kho tạm giữ.
- Giỏ hàng, checkout server-authoritative, voucher, đơn hàng nhiều người bán.
- Thanh toán online qua PaymentIntent, QR (VietQR), polling kết quả.
- Escrow: giữ tiền, giải phóng khi nhận hàng hoặc quá hạn, hoàn tiền khi từ chối/hủy/trả hàng.
- Ví người bán, sổ cái append-only, payout tự động và duyệt tay.
- Bán hàng đa kênh WooCommerce qua webhook HMAC, đồng bộ catalog, khử trùng lặp.
- Gói thuê bao BASE/PREMIUM, giới hạn theo gói, nâng cấp prorated.
- Cài đặt 3 lớp (defaults ← tenant ← user), đa ngôn ngữ en/vi.
- Trợ lý AI read-only tra cứu đơn hàng và tìm sản phẩm.

### Ngoài phạm vi

- Kế toán tổng hợp double-entry, hóa đơn điện tử, thuế tự động nộp cơ quan thuế.
- Ứng dụng mobile native, chat realtime nhiều người, WebSocket.
- Thanh toán thật (VNPay/PayOS/Napas) — hiện dùng mock gateway có seam adapter.
- Redis, MinIO, message broker, async job ngoài scheduler của Quarkus.

## 4. Yêu cầu chức năng

### 4.1 Danh mục và tồn kho

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-01 | Chủ shop tạo/sửa/ẩn sản phẩm với SKU, giá gốc, giá khuyến mãi, ảnh, danh mục, tag | Must |
| FR-02 | Hệ thống quản lý biến thể và tồn kho theo từng biến thể; hiển thị tồn khả dụng = tồn - giữ | Must |
| FR-03 | Khi đặt hàng, hệ thống giữ tồn kho bằng khóa bi quan, không cho bán vượt tồn | Must |
| FR-04 | Cảnh báo sản phẩm sắp hết hàng theo ngưỡng cấu hình | Should |

### 4.2 Đặt hàng và thanh toán

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-05 | Khách duyệt sản phẩm, thêm vào giỏ, áp voucher hợp lệ | Must |
| FR-06 | Checkout tách giỏ theo từng người bán; server tự tính lại giá, thuế, phí, tồn kho | Must |
| FR-07 | Tạo PaymentIntent có hạn 30 phút và idempotency key; hỗ trợ QR và polling | Must |
| FR-08 | Khi thanh toán thành công, đơn chuyển `PAID_ESCROW` và tiền vào escrow `HELD` | Must |
| FR-09 | Thanh toán thất bại/hết hạn có thể thử lại; không tạo giao dịch trùng | Must |

### 4.3 Xử lý đơn và escrow

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-10 | Chủ shop nhận đơn (`OWNER_ACCEPTED` → `FULFILLING`) hoặc từ chối kèm hoàn tiền | Must |
| FR-11 | Nhân viên cập nhật giao hàng (`DELIVERED`), mỗi thay đổi ghi `OrderHistory` | Must |
| FR-12 | Người mua xác nhận nhận hàng → `COMPLETED`, giải phóng escrow cho người bán | Must |
| FR-13 | Quá thời hạn giữ escrow (mặc định 7 ngày) mà không khiếu nại, scheduler tự giải phóng escrow | Must |
| FR-14 | Hủy đơn/trả hàng theo từng item, hoàn tiền về ví và trả lại tồn kho | Should |
| FR-15 | Hoa hồng sàn 5% trừ khi giải phóng; điều chỉnh doanh thu ghi nhận riêng | Must |

### 4.4 Ví và payout

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-16 | Ví phân biệt số dư khả dụng và số dư chờ; mọi biến động ghi sổ cái append-only | Must |
| FR-17 | Payout định kỳ tự động nếu đủ ngưỡng; vượt hạn mức/rủi ro vào hàng đợi admin | Must |
| FR-18 | Admin duyệt/từ chối payout theo lô, xuất CSV, kiểm tra số dư và idempotency | Must |
| FR-19 | Đối soát số dư bằng `LedgerCheckpoint` | Should |

### 4.5 Đa kênh

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-20 | Nhận webhook WooCommerce có chữ ký HMAC SHA-256; từ chối chữ ký sai | Must |
| FR-21 | Khử trùng lặp webhook theo `gateway + event_id` và mapping đơn ngoài | Must |
| FR-22 | Đồng bộ catalog và ánh xạ ID ngoài; health check kênh; reconcile đơn lỗi | Should |

### 4.6 Gói thuê bao và cài đặt

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-23 | Tenant dùng thử BASE 14 ngày; nâng cấp PREMIUM prorated theo số ngày còn lại | Must |
| FR-24 | Feature gate và hạn mức (users/businesses/products) do backend quyết định | Must |
| FR-25 | Tenant hết hạn/suspend bị khóa ghi (read-only), không xóa dữ liệu | Must |
| FR-26 | Cài đặt 3 lớp; user ghi đè tenant, tenant ghi đè default | Should |
| FR-27 | Chuyển ngôn ngữ en/vi tức thời, bản dịch do backend phục vụ | Should |

### 4.7 Trợ lý AI

| Mã | Yêu cầu | Ưu tiên |
|----|---------|---------|
| FR-28 | Chat chỉ nhận `message` và `session_id`; không nhận lịch sử từ client | Must |
| FR-29 | Định danh lấy từ JWT, không xuất hiện trong prompt/tool schema | Must |
| FR-30 | Chỉ 4 tool read-only: danh sách đơn, chi tiết đơn, đếm sản phẩm, tìm trong knowledge base sản phẩm | Must |
| FR-31 | Câu trả lời qua 4 tầng kiểm tra tất định trước khi trả/lưu | Must |
| FR-32 | Lỗi server trả thông báo chung, không lộ stack trace | Must |

## 5. Yêu cầu phi chức năng

| Mã | Nhóm | Yêu cầu | Thước đo |
|----|------|---------|----------|
| NFR-01 | Bảo mật | Mọi truy vấn đơn bản ghi lọc theo `tenant_id`; OWNER lọc thêm `business_id` | 100% request trái tenant trả 404 |
| NFR-02 | Bảo mật | Xác thực OIDC RS256 qua Keycloak; backend chốt quyền bằng `@RolesAllowed` | Không có endpoint nghiệp vụ nào không xác thực |
| NFR-03 | Toàn vẹn | Tiền lưu BIGINT minor units; mọi biến động ví dùng khóa bi quan | Không âm ví, không lệch sổ cái |
| NFR-04 | Toàn vẹn | Mọi giao dịch thanh toán/payout có idempotency key | Gửi lặp không tạo bản ghi mới |
| NFR-05 | Hiệu năng | API p95 < 200ms; khởi động Quarkus < 3s | Đo trên môi trường local |
| NFR-06 | Tin cậy | Webhook trùng lặp trả 200 nhưng bỏ qua xử lý | 5 webhook trùng → 1 đơn |
| NFR-07 | Bảo mật AI | 0% PII rò rỉ; AI không tự nhận đã thay đổi dữ liệu | 48 test bất biến pass |
| NFR-08 | Riêng tư | Telemetry chỉ ghi metadata, không ghi prompt/PII/args/results | Rà log |
| NFR-09 | Khả dụng | Lỗi server trả thông báo generic, chi tiết chỉ ở log server | Không có traceback trong response |
| NFR-10 | Bảo trì | Mã nguồn theo package phẳng `base.*`; schema qua Flyway | Không sửa tay schema |
| NFR-11 | Tương thích | OpenAPI-first, SDK hey-api sinh tự động, không fetch viết tay | Build frontend xanh |
| NFR-12 | Đa ngôn ngữ | en/vi, ETag SHA-256 cho tài nguyên dịch | 304 khi không đổi |

## 6. Ràng buộc

- Backend: Quarkus 3.33.1, Java 25, Hibernate Panache, MariaDB, Flyway.
- Frontend: React 19 + TypeScript + Vite, TanStack Router/Query, Base UI (không Radix).
- AI: FastAPI + Gemini + Pinecone, chỉ read-only, identity ngoài model.
- Không dùng Redis, MinIO, messaging, WebSocket, custom JWT.
- Mọi thay đổi schema phải qua migration Flyway.

## 7. Ưu tiên MoSCoW (vòng 1)

- **Must:** FR-01, 02, 03, 05, 06, 07, 08, 10, 11, 12, 13, 15, 16, 17, 18, 20, 21,
  23, 24, 25, 28, 29, 30, 31, 32; NFR-01..04, 07, 08.
- **Should:** FR-04, 14, 19, 22, 26, 27; NFR-05, 06, 09..12.
- **Could:** báo cáo doanh thu nâng cao, xuất CSV tùy biến.
- **Won't (vòng 1):** double-entry ledger, cổng thanh toán thật, mobile app.

## 8. Truy vết

| Yêu cầu | Use case (Gói 3) | Sơ đồ |
|---------|------------------|-------|
| FR-05..09 | UC-01 Đặt hàng và thanh toán | `seq-checkout-thanh-toan.mmd` |
| FR-10..15 | UC-02 Xử lý đơn và giải phóng escrow | `seq-escrow-release.mmd` |
| FR-28..32 | UC-03 Trợ lý AI tra cứu đơn hàng | `seq-ai-tra-cuu.mmd` |
| FR-16..19 | UC-05 Quản lý ví và payout | `class.mmd`, `state.mmd` |
| FR-20..22 | UC-06 Bán hàng đa kênh | `class.mmd` |
| FR-23..25 | UC-07 Quản lý gói thuê bao | `state.mmd` |

## 9. Giả định và câu hỏi mở

1. Giả định mỗi tenant có thể có nhiều cửa hàng; quyền OWNER giới hạn theo `business_id`.
2. Giả định thời gian giữ escrow mặc định 7 ngày (`orders.escrow-hold-days`), có thể cấu hình sau.
3. Câu hỏi mở: có cần xuất hóa đơn VAT cho từng đơn không? (ngoài phạm vi M3)
4. Câu hỏi mở: tỷ lệ hoa hồng sàn cố định 5% hay cấu hình theo tenant? (hiện cố định)
5. Câu hỏi mở: khi tenant hết hạn, dữ liệu giữ bao lâu trước khi lưu trữ lạnh?

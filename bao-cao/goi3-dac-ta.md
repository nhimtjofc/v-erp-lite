# Báo cáo Gói 3 — Đặc tả use case

Nhóm: N__10
Phụ trách: N10-A
Hạn: CN 25/10/2026 (23:59)
Thẻ: `goi-3`
Milestone Issues: `Gói 3`

## 1. Đã làm gì

1. Vẽ sơ đồ use case tổng thể `diagrams/use-case.mmd` với 10 use case và 7 tác nhân
   (4 người dùng, 3 hệ thống ngoài).
2. Viết 3 đặc tả use case đầy đủ theo cùng một khuôn:
   - `docs/dac-ta-UC-01-dat-hang-thanh-toan.md`
   - `docs/dac-ta-UC-02-xu-ly-don-giai-phong-escrow.md`
   - `docs/dac-ta-UC-03-ai-tra-cuu-don-hang.md`
3. Vẽ 3 sequence diagram (vượt mức tối thiểu 2):
   - `diagrams/seq-checkout-thanh-toan.mmd`
   - `diagrams/seq-escrow-release.mmd`
   - `diagrams/seq-ai-tra-cuu.mmd`
4. Viết user story và tiêu chí Given–When–Then cho từng use case (mục 3) để đưa lên Issues.
5. Đối chiếu từng bước trong đặc tả với lớp/hàm thật trong mã nguồn (mục 5).

## 2. Ba quyết định thiết kế lớn nhất

1. **Chọn 3 use case theo trục giá trị, không theo trục màn hình.** UC-01 (đặt hàng),
   UC-02 (xử lý đơn + escrow), UC-03 (AI tra cứu) bao trọn luồng tiền và luồng dữ liệu quan
   trọng nhất. Các màn hình phụ (voucher, notification, settings) không cần đặc tả riêng ở
   gói này vì đã nằm trong luồng chính hoặc chỉ là CRUD.
2. **Đặc tả theo trạng thái, không theo thao tác UI.** Mỗi use case ghi rõ trạng thái trước/sau
   (`PAID_ESCROW`, `OWNER_ACCEPTED`, `COMPLETED`, escrow `HELD/RELEASED/REFUNDED`) để nối
   trực tiếp sang `state.mmd` ở Gói 4 và tránh mô tả mơ hồ.
3. **Đưa bất biến bảo mật vào tiêu chí chấp nhận.** Với UC-03, các tiêu chí cross-user,
   cross-tenant, thiếu token, đối số danh tính và rò rỉ traceback là AC bắt buộc, không phải
   ghi chú. Điều này biến yêu cầu phi chức năng thành thứ kiểm được.

## 3. User story và Given–When–Then (đưa lên Issues)

> Mỗi khối dưới đây là nội dung một hạng mục Issues, gắn milestone `Gói 3`.
> Người phụ trách dùng mã bút danh; không ghi họ tên.

### US-01 — Đặt hàng và thanh toán (UC-01)

Là khách mua, tôi muốn đặt hàng và thanh toán an toàn để đơn được giữ tồn kho và tiền được
bảo vệ cho tới khi tôi nhận hàng.

- Given giỏ có sản phẩm còn tồn và tenant đang ACTIVE
- When tôi đặt hàng và thanh toán thành công
- Then đơn ở `PAID_ESCROW`, có escrow `HELD`, tồn kho bị giữ, có timeline trạng thái

Truy vết: UC-01, FR-05..09, BR-01..07. Cách làm: `agent`. Phụ trách: N10-C.

### US-02 — Không bán vượt tồn (UC-01)

Là chủ shop, tôi muốn hệ thống chặn đặt quá tồn để không phải hủy đơn vì thiếu hàng.

- Given sản phẩm còn 1 cái
- When khách đặt 2 cái
- Then hệ thống trả 409 và không tạo đơn/PaymentIntent

Truy vết: UC-01, FR-03, BR-02. Cách làm: `tay`. Phụ trách: N10-B.

### US-03 — Chủ shop nhận/từ chối đơn (UC-02)

Là chủ shop, tôi muốn nhận hoặc từ chối đơn có thanh toán để kiểm soát hàng trước khi giao.

- Given đơn `PAID_ESCROW` thuộc cửa hàng của tôi
- When tôi nhận đơn
- Then đơn `OWNER_ACCEPTED`; nếu tôi từ chối thì đơn `OWNER_DECLINED` và escrow được hoàn

Truy vết: UC-02, FR-10, BR-08, BR-09. Cách làm: `agent`. Phụ trách: N10-B.

### US-04 — Giải phóng escrow khi nhận hàng (UC-02)

Là chủ shop, tôi muốn nhận tiền sau khi khách xác nhận nhận hàng để dòng tiền công bằng.

- Given đơn `DELIVERED`, escrow `HELD` 1.000.000 VND
- When khách xác nhận đã nhận hàng
- Then đơn `COMPLETED`, escrow `RELEASED`, ví người bán +950.000 VND, có bút toán sổ cái

Truy vết: UC-02, FR-12, FR-15, BR-10..14. Cách làm: `agent`. Phụ trách: N10-B.

### US-05 — Tự động giải phóng sau 7 ngày (UC-02)

Là chủ shop, tôi muốn escrow tự giải phóng sau thời hạn để không bị treo tiền.

- Given đơn `PENDING_COMPLETION` quá 7 ngày, không có khiếu nại
- When scheduler chạy
- Then escrow được giải phóng đúng một lần và đơn `COMPLETED`

Truy vết: UC-02, FR-13. Cách làm: `agent`. Phụ trách: N10-B.

### US-06 — AI tra cứu đơn của chính tôi (UC-03)

Là khách mua, tôi muốn hỏi trợ lý AI về đơn của mình để không phải nhớ mã đơn.

- Given tôi đã đăng nhập và có đơn đang giao
- When tôi hỏi "đơn của tôi đang giao không?"
- Then trợ lý trả đúng đơn của tôi, không có dữ liệu người khác

Truy vết: UC-03, FR-28..30, BR-15..18. Cách làm: `agent`. Phụ trách: N10-D.

### US-07 — AI không lộ dữ liệu chéo (UC-03)

Là admin nền tảng, tôi muốn trợ lý AI bị chặn khi hỏi dữ liệu ngoài quyền để bảo vệ tenant.

- Given user A hỏi mã đơn của user B hoặc dữ liệu tenant khác
- When tool gọi backend
- Then backend chỉ trả dữ liệu trong quyền; câu trả lời không chứa dữ liệu ngoài quyền

Truy vết: UC-03, FR-29, NFR-01, BR-15..16. Cách làm: `agent`. Phụ trách: N10-D.

### US-08 — AI không lộ lỗi kỹ thuật (UC-03)

Là khách mua, tôi muốn nhận thông báo lỗi thân thiện thay vì traceback.

- Given backend hoặc tool gặp exception
- When tôi gửi tin nhắn
- Then tôi chỉ thấy thông báo chung, không có traceback/tên file

Truy vết: UC-03, FR-32, NFR-09, BR-22. Cách làm: `tay`. Phụ trách: N10-D.

## 4. Thẩm định kết quả agent

### Lần 1 — Kiểm đặc tả với mã nguồn

- Kiểm bằng gì: đối chiếu từng bước UC-01/UC-02 với `OrderResource`,
  `OrderFulfillmentService`, `EscrowService`, `EscrowReleaseScheduler`, `WalletService`;
  kiểm tên trạng thái với hằng chuỗi trong entity.
- Kết quả: agent viết "giải phóng escrow khi giao hàng" — **sai**, mã nguồn giải phóng khi
  người mua xác nhận hoặc quá 7 ngày. Agent cũng bỏ sót bước ghi `OrderHistory`. Đã sửa cả hai.
- Kết luận: sai một bước nghiệp vụ và thiếu một bước audit; sửa xong mới đạt.

### Lần 2 — Kiểm tính nhất quán giữa 3 sơ đồ

- Kiểm bằng gì: đối chiếu participant trong 3 file `.mmd` với danh sách lớp dự kiến ở
  `diagrams/class.mmd`; kiểm mỗi actor trong use case có ít nhất một use case; kiểm mã BR
  trong đặc tả khớp bảng quy tắc ở `docs/yeu-cau.md`.
- Kết quả: agent dùng participant `PaymentGateway` không có trong sơ đồ lớp — **sai**; đã đổi
  thành `PaymentService`/`PaymentTransaction`. Agent cũng gán nhầm BR-03 cho escrow; đã sửa.
- Kết luận: đạt sau khi sửa; giữ quy tắc "participant phải là lớp có thật".

## 5. Truy vết đặc tả → mã nguồn

| Use case | Endpoint / lớp chính | Tệp |
|----------|----------------------|-----|
| UC-01 | `OrderResource`, `PaymentIntentService`, `PaymentService`, `EscrowService` | `base/rest/OrderResource.java`, `base/service/*` |
| UC-02 | `OrderFulfillmentService`, `EscrowService`, `EscrowReleaseScheduler`, `WalletService` | `base/service/*` |
| UC-03 | `/chat`, `RequestContext`, `SessionStore`, `UnifiedAgent`, `OutputValidator` | `ecommerce-chatbot/mcp_server/*`, `agents/unified_agent.py` |

## 6. Retro ba dòng

1. Giữ: viết AC dạng Given–When–Then ngay khi chốt use case.
2. Bỏ: dùng tên lớp/hệ thống không tồn tại trong sơ đồ.
3. Thử: mỗi đặc tả có một bảng truy vết mã nguồn ngay cuối tệp.

## 7. Đối chiếu tiêu chí Gói 3

| Tiêu chí | Trạng thái | Ghi chú |
|----------|------------|---------|
| `diagrams/use-case.mmd` | Đạt | 10 UC, 7 actor |
| Ba `docs/dac-ta-UC-*.md` | Đạt | UC-01, UC-02, UC-03 |
| Ít nhất hai `diagrams/seq-*.mmd` | Đạt | 3 sequence |
| User story + Given–When–Then trên Issues (milestone Gói 3) | Chờ thao tác | Nội dung sẵn ở mục 3; cần tạo hạng mục trên GitHub Issues |

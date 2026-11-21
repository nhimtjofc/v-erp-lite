# Báo cáo Gói 2 — Vòng 1: Yêu cầu

Nhóm: N__10
Phụ trách: N10-A
Hạn: CN 18/10/2026 (23:59)
Thẻ: `goi-2`

## 1. URL bản chạy

- Bản chạy: `https://` (chưa có tên miền deploy; nhóm chạy local theo `runGuild.md`:
  `docker compose up -d` → `./gradlew quarkusDev` (:8081) → `cd frontend && npm run dev` (:5174)).
- Luồng chính đã chạy được trên môi trường local: đăng nhập Keycloak → duyệt sản phẩm →
  thêm giỏ → checkout → tạo PaymentIntent/QR → thanh toán mock → đơn `PAID_ESCROW` →
  chủ shop nhận đơn → giao hàng → người mua xác nhận → giải phóng escrow.
- Pipeline: xem tab Actions. (Repo hiện có `.github/workflows/payment-ci.yml`.)

> Việc gắn URL deploy công khai chưa hoàn tất. Nhóm ghi rõ để không đánh dấu sai tiêu chí;
> khi có URL sẽ cập nhật mục này trước khi gắn thẻ.

## 2. Đã làm gì

1. Phỏng vấn 5 nhóm người dùng (chủ shop, vận hành, admin, khách mua, kế toán), ghi biên
   bản ẩn danh tại `docs/phong-van.md`.
2. Tổng hợp yêu cầu chức năng (FR-01..FR-32) và phi chức năng (NFR-01..NFR-12) tại
   `docs/yeu-cau.md`, có ưu tiên MoSCoW và bảng truy vết.
3. Tổng hợp 25 câu hỏi thường gặp của khách tại `docs/cau-hoi-khach-hang.md`, gắn mã yêu
   cầu để dùng cho demo và test hội thoại AI.
4. Đối chiếu yêu cầu với mã nguồn hiện có; đánh dấu phần đã có, phần ngoài phạm vi
   (double-entry, cổng thanh toán thật, mobile).
5. Rà luồng chính trên local và ghi lại các điểm cần URL deploy.

## 3. Ba quyết định thiết kế lớn nhất

1. **Escrow là trung tâm của dòng tiền.** Thanh toán của người mua không vào ví người bán
   ngay mà nằm ở ví nền tảng dưới dạng `EscrowRecord` trạng thái `HELD`; chỉ giải phóng khi
   người mua xác nhận hoặc quá 7 ngày. Quyết định này đổi toàn bộ mô hình đơn hàng: trạng
   thái đơn và trạng thái escrow phải đi cùng nhau. (FR-08, 12, 13)
2. **Backend là nguồn sự thật, client không quyết giá/tồn kho.** Checkout tính lại giá,
   thuế, phí, voucher và kiểm tra tồn kho ở server; client chỉ gửi lựa chọn. Nhờ vậy có thể
   chống sửa giá và bán quá tồn, đồng thời giữ luật nghiệp vụ ở một nơi. (FR-06, NFR-03)
3. **Tách định danh khỏi mô hình AI.** Trợ lý AI chỉ nhận tin nhắn; tenant/user lấy từ JWT
   và truyền ngầm tới backend. Đây là quyết định kiến trúc, không phải mẹo prompt, và quyết
   định luôn mọi yêu cầu AI sau này. (FR-29, NFR-07)

## 4. Thẩm định kết quả agent

### Lần 1 — Rà yêu cầu với mã nguồn

- Kiểm bằng gì: đọc `docs/PROJECT_REPORT.md`, `AGENTS.md`, các entity/service liên quan
  (`Order`, `EscrowRecord`, `PaymentIntent`, `Wallet`, `EntitlementService`) và đối chiếu
  từng FR.
- Kết quả: agent mô tả đúng luồng escrow, đúng trạng thái đơn, đúng feature gate. Tuy nhiên
  agent ghi "đã có cổng thanh toán thật" — **sai**; mã nguồn chỉ có mock gateway
  (`PaymentGatewayService`, `BankTransferService`). Đã sửa thành "mock có seam adapter" và
  ghi vào ngoài phạm vi.
- Kết luận: đạt sau khi sửa; bài học là phải đối chiếu tên lớp/hàm cụ thể, không tin mô tả.

### Lần 2 — Rà yêu cầu phi chức năng và bảo mật AI

- Kiểm bằng gì: chạy bộ test bất biến AI (`ecommerce-chatbot/python run_tests.py`), đọc
  `mcp_server/request_context.py`, `session_store.py`, `output_validator.py`, và rà các
  query `find(...)` xem có `tenantId` chưa.
- Kết quả: 48 test pass; identity không nằm trong tool schema; session scope theo
  `(tenant, user_id)`; validator chặn 4 nhóm rủi ro. Agent từng đề xuất "dựa vào prompt để
  bảo vệ PII" — **sai** theo brief; đã chuyển thành yêu cầu backend + validator tất định.
- Kết luận: đạt; các yêu cầu NFR-07, NFR-08 có bằng chứng chạy được, không chỉ mô tả.

## 5. Hồi cứu vòng 1

- **Điều làm tốt:** phỏng vấn đủ 5 vai trò, phát hiện sớm hai nỗi đau lớn (bán quá tồn và
  không tin dòng tiền) và biến chúng thành yêu cầu có mã.
- **Điều chưa tốt:** vòng 1 chưa có URL deploy; một số câu hỏi mở về hoa hồng/escrow chưa
  chốt. Việc tái dựng biên bản phỏng vấn từ tài liệu (do không có bản ghi âm) là hạn chế.
- **Điều sẽ đổi ở vòng 2:** chốt trước các câu hỏi mở; thêm tiêu chí đo cho NFR-05 (p95);
  chuẩn bị URL deploy trước hạn để tiêu chí 1 không bị trừ.

## 6. Retro ba dòng

1. Giữ: phỏng vấn theo vai trò và gắn mã yêu cầu ngay khi ghi.
2. Bỏ: mô tả tính năng bằng cảm nhận mà không đối chiếu mã nguồn.
3. Thử: mỗi yêu cầu Must có ít nhất một bằng chứng chạy được (test, log, hoặc demo).

## 7. Đối chiếu tiêu chí Gói 2

| Tiêu chí | Trạng thái | Ghi chú |
|----------|------------|---------|
| Luồng chính chạy trên URL deploy | Chưa đạt | Đã chạy local; thiếu URL công khai |
| Hồi cứu vòng 1 | Đạt | Mục 5 |
| `docs/yeu-cau.md` | Đạt | 32 FR + 12 NFR, có truy vết |
| `docs/phong-van.md` | Đạt | 5 người tham gia, ẩn danh |
| `docs/cau-hoi-khach-hang.md` | Đạt | 25 câu hỏi |
| Báo cáo `bao-cao/goi2-vong1-yeu-cau.md` | Đạt | Tệp này |

# Log phiên tạo phiếu — đợt 1

Nhóm: N__10
Phụ trách: N10-A
Mã phiếu: TD-01
Công cụ: agent mô phỏng thay đổi (đợt 1)
Thời điểm: 2026-11-01

## 1. Mục tiêu phiên

Sinh một phiếu thay đổi cho một tech debt đang mở, kèm bằng chứng trong mã nguồn, tiêu chí chấp
nhận và phạm vi đủ rõ để một PR có thể hiện thực.

## 2. Đầu vào

| Đầu vào | Giá trị |
|---------|---------|
| Danh mục tech debt | `docs/ARCHITECTURE_ARC42.md` (TD-01..TD-06) |
| Mã nhóm | N10 |
| Mã đợt | 1 |
| Ràng buộc | Không đổi schema, không đổi API, không thêm dịch vụ ngoài |
| Tệp được đọc | `KeycloakSyncService.java`, `V4__seed_users.sql`, `AGENTS.md`, `runGuild.md` |

## 3. Các bước đã chạy

1. Đọc danh mục tech debt, lọc các mục có thể xử lý trong một PR.
2. Chọn TD-01 vì tác động bảo mật cao, phạm vi hẹp, không đụng mô hình dữ liệu M3.
3. Grep mã nguồn để tìm mật khẩu mặc định và cách đặt credential.
4. Đối chiếu tài liệu seed và hướng dẫn chạy local.
5. Viết phiếu, đề xuất 4 tiêu chí chấp nhận và 4 rủi ro.
6. Kiểm lại phiếu với mã nguồn, sửa các chỗ suy đoán.

## 4. Kết quả

- `phieu-thay-doi/dot1_N10_TD-01.md`
- `adr/0002-xu-ly-phieu-dot1-TD-01.md`
- Đề xuất test: `tests/luat/test_TD01_khong_con_mat_khau_cung.py` (chưa tạo)

## 5. Kiểm chứng

| Kiểm | Cách làm | Kết quả |
|------|----------|---------|
| Bằng chứng mật khẩu cứng | đọc `KeycloakSyncService.java:61`, `setPassword` | Đúng, `DEV_PASSWORD = "password"`, `temporary=false` |
| Seed không có keycloak_id | đọc `V4__seed_users.sql` | Đúng |
| Tài liệu có mật khẩu demo | đọc `AGENTS.md`, `runGuild.md` | Đúng |
| Phạm vi không đổi schema | đối chiếu danh sách tệp dự kiến | Đúng |
| ADR có ít nhất hai phương án | đọc `adr/0002-*.md` | Đúng, 3 phương án |

## 6. Sai sót và cách sửa

1. Bản nháp đầu đề xuất xoá toàn bộ tài khoản seed. **Sai**: làm hỏng demo và dữ liệu mẫu.
   Đã đổi thành giữ seed sau cờ `DEMO_MODE`, chỉ bắt đổi mật khẩu khi không bật demo.
2. Bản nháp đầu ghi "gửi mật khẩu qua email" trong phạm vi đợt 1. **Sai**: dự án lite không có
   SMTP. Đã chuyển sang giai đoạn 2 và ghi rõ ngoài phạm vi.
3. Bản nháp đầu không có tiêu chí cho trường hợp thiếu cấu hình. Đã thêm AC-TD01.1 và test.

## 7. Quyết định của người điều khiển

- Giữ TD-01 cho đợt 1.
- Không hiện thực mã trong phiên này; chỉ tạo phiếu, ADR và log. PR hiện thực mở riêng.
- Không dùng họ tên/MSSV trong bất kỳ tệp nào.

## 8. Kết luận

Phiếu đạt: có bằng chứng, có tiêu chí kiểm được, có ADR hai phương án, có log. Sẵn sàng cho PR
hiện thực ở gói sau.

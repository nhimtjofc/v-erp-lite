# ADR-0002 — Xử lý phiếu TD-01: bỏ mật khẩu seed mặc định

Phụ trách: N10-A
Trạng thái: Chấp nhận
Ngày: 2026-11-01
Người đề xuất: N10-A
Phiếu liên quan: `phieu-thay-doi/dot1_N10_TD-01.md` (TD-01)
Liên quan: NFR-01, NFR-06

## Bối cảnh

`KeycloakSyncService` tạo tài khoản Keycloak cho mọi user trong DB và đặt mật khẩu cố định
`"password"` với `temporary=false`. Tài khoản seed trong `V4__seed_users.sql` không có
`keycloak_id`, nên lần khởi động đầu tiên sẽ được tạo với mật khẩu này. Mật khẩu xuất hiện trong
mã nguồn, trong lịch sử git và trong tài liệu hướng dẫn. Đây là TD-01 mức đỏ.

Ràng buộc:

- Không đổi schema, không đổi API.
- Không thêm dịch vụ ngoài (không SMTP trong giai đoạn này).
- Demo local vẫn phải chạy được để phục vụ chấm bài.

## Phương án

### Phương án A — Required action + cấu hình theo môi trường

Bỏ hằng số mật khẩu. Đọc `keycloak.dev-password` từ cấu hình, chỉ có giá trị mặc định trong profile
`%dev`. User tạo mới nhận `temporary=true` và required action `UPDATE_PASSWORD`. Tài khoản seed
được giữ mật khẩu demo khi `DEMO_MODE=true`, ngược lại dùng mật khẩu ngẫu nhiên một lần.

- Ưu: thay đổi nhỏ, không phụ thuộc dịch vụ ngoài, giải quyết đúng gốc rễ.
- Nhược: người dùng phải đổi mật khẩu ở lần đầu; cần cập nhật tài liệu demo.

### Phương án B — Mật khẩu ngẫu nhiên một lần gửi qua email

Sinh mật khẩu ngẫu nhiên cho từng user và gửi qua email kèm liên kết đặt lại.

- Ưu: không cần chia sẻ mật khẩu chung; phù hợp production.
- Nhược: cần SMTP, template email, xử lý lỗi gửi; vượt phạm vi "lite" ở giai đoạn này.

### Phương án C — Bỏ đăng nhập mật khẩu, chỉ dùng SSO

Tắt password grant, chỉ cho đăng nhập qua IdP ngoài.

- Ưu: giảm bề mặt tấn công mạnh nhất.
- Nhược: phá vỡ luồng demo và yêu cầu hạ tầng ngoài; không tương thích mục tiêu môn học.

### Phương án D — Giữ nguyên, chỉ thêm cảnh báo

- Ưu: không rủi ro hỏng demo.
- Nhược: không xử lý tech debt; mật khẩu vẫn công khai. Loại.

## Quyết định

Chọn **phương án A** cho đợt 1. Phương án B được ghi nhận cho giai đoạn 2 khi có nhu cầu gửi thông
báo. Phương án C và D bị loại.

## Hệ quả

Tích cực:

- Không còn mật khẩu dùng chung trong mã nguồn.
- User mới buộc đổi mật khẩu, giảm rủi ro tài khoản mặc định.
- Có điểm cấu hình rõ ràng cho từng môi trường.

Tiêu cực:

- Luồng demo phải bật `DEMO_MODE=true`; tài liệu cần ghi rõ.
- Cần test tích hợp với Keycloak Admin API.
- Người dùng mới thêm một bước đổi mật khẩu.

Không đổi:

- Schema DB, API, luồng OIDC ở frontend.
- Mô hình lớp và trạng thái ở M3.

## Việc cần làm

1. Sửa `KeycloakSyncService`: bỏ `DEV_PASSWORD`, đọc cấu hình, đặt `temporary=true`.
2. Thêm cờ `DEMO_MODE` cho seed.
3. Cập nhật `AGENTS.md`, `runGuild.md`.
4. Thêm test `tests/luat/test_TD01_khong_con_mat_khau_cung.py` và test tích hợp.
5. Mở PR ghi `Dòng #<số hạng mục>` cho hạng mục TD-01.

## Truy vết

- Phiếu: TD-01, `phieu-thay-doi/dot1_N10_TD-01.md`
- Log: `log-tac-tu/phieu-dot1.md`
- Tech debt gốc: TD-01 trong `docs/ARCHITECTURE_ARC42.md`

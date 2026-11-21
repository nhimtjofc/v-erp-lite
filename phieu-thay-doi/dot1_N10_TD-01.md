# Phiếu thay đổi đợt 1 — TD-01

Phụ trách: N10-A
Đợt: 1
Mã phiếu: TD-01
Nguồn: agent mô phỏng thay đổi (đợt 1), danh mục tech debt `docs/ARCHITECTURE_ARC42.md`
Trạng thái: Đã tiếp nhận, đang xử lý

## 1. Bối cảnh

Ứng dụng tạo tài khoản Keycloak tự động cho người dùng trong DB. Mật khẩu được đặt cứng trong mã
nguồn và dùng chung cho mọi tài khoản mới. Đây là tech debt **TD-01 (mức đỏ)** trong
`docs/ARCHITECTURE_ARC42.md`: "mật khẩu seed mặc định".

## 2. Hiện trạng và bằng chứng

| Bằng chứng | Vị trí |
|------------|--------|
| Hằng số mật khẩu dùng chung | `base/service/KeycloakSyncService.java:61` (`DEV_PASSWORD = "password"`) |
| Mật khẩu không bắt đổi | `KeycloakSyncService.setPassword` đặt `cred.setTemporary(false)` |
| Tài khoản seed không có `keycloak_id` | `V4__seed_users.sql` |
| Tài liệu hướng dẫn dùng mật khẩu demo | `AGENTS.md`, `runGuild.md` |
| Realm cho phép reset mật khẩu | `KeycloakSyncService` bật `resetPasswordAllowed` |

Hệ quả: bất kỳ ai biết email seed đều đăng nhập được bằng một mật khẩu công khai; không có bước
buộc đổi; mật khẩu nằm trong lịch sử git.

## 3. Vấn đề

1. Mật khẩu mặc định là thông tin công khai, không đổi theo môi trường.
2. Người dùng mới không bị buộc đổi mật khẩu ở lần đăng nhập đầu.
3. Tài liệu demo chứa mật khẩu thật, dễ bị dùng ngoài môi trường dev.
4. Không có kiểm thử nào chặn việc quay lại hành vi cũ.

## 4. Phạm vi thay đổi

Trong phạm vi:

- `base/service/KeycloakSyncService.java`: bỏ hằng số mật khẩu cứng; đọc từ cấu hình
  `keycloak.dev-password` (mặc định chỉ trong profile `%dev`).
- Người dùng tạo mới qua `AdminResource` và qua sync: đặt `temporary=true` và gán required action
  `UPDATE_PASSWORD`.
- Tài khoản seed: chỉ giữ mật khẩu dev khi `DEMO_MODE=true`; ngoài ra dùng mật khẩu ngẫu nhiên
  một lần và bắt đổi.
- `AGENTS.md`, `runGuild.md`: ghi rõ mật khẩu demo chỉ dùng cho máy local, không dùng cho môi
  trường công khai.
- Thêm test cho quy tắc "user mới phải có required action".

Ngoài phạm vi:

- Đổi nhà cung cấp định danh, thêm đăng nhập xã hội.
- Đổi luồng OIDC PKCE ở frontend.
- Xoá tài khoản seed.

## 5. Tiêu chí chấp nhận

**AC-TD01.1 — Không còn mật khẩu cứng**
- Given mã nguồn sau thay đổi
- When tìm chuỗi mật khẩu mặc định trong `KeycloakSyncService`
- Then không còn hằng số mật khẩu; giá trị lấy từ cấu hình

**AC-TD01.2 — User mới phải đổi mật khẩu**
- Given admin tạo user mới
- When Keycloak tạo tài khoản
- Then credential có `temporary=true` và user có required action `UPDATE_PASSWORD`

**AC-TD01.3 — Seed chỉ dùng cho demo**
- Given `DEMO_MODE` không bật
- When sync tạo tài khoản cho user seed
- Then mật khẩu là ngẫu nhiên một lần và buộc đổi ở lần đăng nhập đầu

**AC-TD01.4 — Demo local vẫn dùng được**
- Given `DEMO_MODE=true` và profile `%dev`
- When đăng nhập bằng tài khoản seed
- Then đăng nhập được nhưng hệ thống ghi cảnh báo mật khẩu demo đang bật

**AC-TD01.5 — Có kiểm thử**
- Given bộ test backend
- When chạy `./gradlew test`
- Then có test kiểm `temporary`/required action và test cấu hình thiếu mật khẩu

## 6. Rủi ro và giảm thiểu

| Rủi ro | Giảm thiểu |
|--------|-----------|
| Demo tự động hỏng vì bắt đổi mật khẩu | Cờ `DEMO_MODE`, chỉ bật ở profile dev |
| Keycloak phiên bản cũ không nhận required action | Kiểm tra qua Admin API, log WARN và fail test tích hợp |
| Mật khẩu ngẫu nhiên không tới được người dùng | Giai đoạn 1 chỉ áp dụng cho user tạo qua admin; giai đoạn 2 mới gửi email |
| Thay đổi ảnh hưởng sync hàng loạt | Sync bỏ qua user đã có `keycloak_id`; chỉ áp dụng cho user mới |

## 7. Kiểm thử đề xuất

- `tests/luat/test_TD01_khong_con_mat_khau_cung.py`: đọc mã nguồn, chặn hằng số mật khẩu.
- Test tích hợp Quarkus: tạo user, gọi Keycloak Admin API, kiểm required action.
- Kiểm thủ công: đăng nhập tài khoản mới, xác nhận màn hình đổi mật khẩu.

## 8. Truy vết

- Tech debt: TD-01
- Yêu cầu: NFR-01 (bảo mật), NFR-06 (cấu hình theo môi trường)
- ADR: `adr/0002-xu-ly-phieu-dot1-TD-01.md`
- Log phiên: `log-tac-tu/phieu-dot1.md`
- Hạng mục Issues: `Xử lý TD-01 — bỏ mật khẩu seed mặc định` (milestone Gói 4)

## 9. Kết luận tiếp nhận

Phiếu hợp lệ, đủ nhỏ để làm trong một PR, không đổi schema, không đổi hợp đồng API. Chấp nhận xử
lý theo ADR-0002.

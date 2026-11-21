# Báo cáo Gói 4 — Mô hình lớp, trạng thái và xử lý phiếu đợt 1

Nhóm: N__10
Phụ trách: N10-C
Hạn: CN 08/11/2026 (23:59)
Thẻ: `goi-4`
Milestone Issues: `Gói 4`

## 1. Đã làm gì

1. Vẽ `diagrams/class.mmd` với 59 lớp: 39 lớp entity/value object, 13 lớp application layer,
   6 lớp AI, 1 lớp framework. Sơ đồ phủ đủ participant của cả 3 sequence diagram ở Gói 3.
2. Vẽ `diagrams/state.mmd` cho vòng đời `Order` (14 trạng thái, gồm cả nhánh hủy/trả hàng).
3. Vẽ thêm `diagrams/state-escrow.mmd` cho `EscrowRecord` và `diagrams/state-thanh-toan.mmd`
   cho `PaymentIntent` để không nhồi ba máy trạng thái vào một tệp.
4. Viết `docs/tu-danh-gia-M3.md`: bảng 59 lớp kèm nguồn, bảng đối chiếu quan hệ, bảng chuyển
   trạng thái, sai lệch đã biết và tự chấm theo tiêu chí M3.
5. Xử lý phiếu thay đổi đợt 1:
   - `phieu-thay-doi/dot1_N10_TD-01.md`
   - `log-tac-tu/phieu-dot1.md`
   - `adr/0002-xu-ly-phieu-dot1-TD-01.md`
6. Đối chiếu từng lớp với `extends` trong mã nguồn và từng chuyển trạng thái với
   `OrderResource.isAllowedStatusTransition`.

## 2. Ba quyết định thiết kế lớn nhất

1. **Sơ đồ lớp giữ đúng package thật, không vẽ lại theo ý tưởng.** Mọi lớp entity lấy từ
   `base/entity`; lớp application lấy từ `base/rest` và `base/service`; lớp AI lấy từ
   `ecommerce-chatbot`. Nhờ vậy participant trong sequence diagram đều hợp lệ và sơ đồ dùng được
   để đối chiếu mã, không chỉ để minh họa.
2. **Tách ba sơ đồ trạng thái theo aggregate.** `state.mmd` chỉ mô tả `Order`; escrow và
   `PaymentIntent` có tệp riêng. Một tệp Mermaid chỉ nên chứa một sơ đồ, và tách theo aggregate
   giúp mỗi vòng đời kiểm được độc lập.
3. **Chọn TD-01 cho phiếu đợt 1.** TD-01 tác động bảo mật, phạm vi hẹp, không đổi schema hay API,
   nên vừa xử lý được bằng một PR, vừa không làm nhiễu mô hình M3. Các TD khác (God controller,
   double-entry ledger, strategy gateway) cần thay đổi kiến trúc lớn hơn.

## 3. Thẩm định kết quả agent

### Lần 1 — Kiểm sơ đồ lớp với mã nguồn

- Kiểm bằng gì: grep `public class ... extends (BaseEntity|PanacheEntityBase)` trong
  `base/entity`, so từng dòng inheritance và quan hệ với `class.mmd`; đọc `Order`, `Product`,
  `Wallet`, `PayoutRequest`, `PaymentRefund` để kiểm khóa ngoại.
- Kết quả: agent gộp `OrderHistory` vào `BaseEntity` — **sai**, lớp này kế thừa
  `PanacheEntityBase`. Agent cũng ghi `RevenueAdjustment` tham gia giải phóng escrow — **sai**,
  lớp này chỉ dùng cho điều chỉnh doanh thu thủ công. Đã sửa cả hai.
- Kết luận: sai hai quan hệ; sửa xong sơ đồ mới khớp mã.

### Lần 2 — Kiểm trạng thái và tính nhất quán sequence

- Kiểm bằng gì: đọc `OrderResource.isAllowedStatusTransition` (dòng 1371-1400), `EscrowService`,
  `PaymentIntentService`; so danh sách participant trong `seq-*.mmd` với class diagram; kiểm
  `state.mmd` có đủ nhánh `PENDING_COMPLETION`, `CANCEL_REQUESTED`, `RETURN_REQUESTED`.
- Kết quả: agent bỏ `PENDING_COMPLETION` và để `DELIVERED → COMPLETED` trực tiếp — **sai**,
  mã nguồn tự chuyển `DELIVERED → PENDING_COMPLETION` rồi mới `COMPLETED`. Agent cũng dùng
  participant `RevenueAdjustment` trong sequence escrow — **sai**, không có trong luồng.
  Ngoài ra báo cáo Gói 3 ghi "72 giờ" trong khi mã dùng 7 ngày — **sai**, đã sửa toàn bộ tài liệu.
- Kết luận: sai ba điểm; sau khi sửa, 25 participant của 3 sequence đều có trong class diagram.

## 4. Xử lý phiếu thay đổi đợt 1

| Hạng mục | Tệp | Nội dung |
|----------|-----|----------|
| Phiếu | `phieu-thay-doi/dot1_N10_TD-01.md` | TD-01: bỏ mật khẩu seed mặc định, buộc đổi lần đầu |
| Log phiên | `log-tac-tu/phieu-dot1.md` | Đầu vào, 6 bước, 3 sai sót và cách sửa |
| ADR | `adr/0002-xu-ly-phieu-dot1-TD-01.md` | 4 phương án, chọn A, hệ quả và việc cần làm |

Trạng thái xử lý: phiếu đã tiếp nhận, ADR đã chốt phương án A, PR hiện thực chưa mở trong gói này.
Lý do: Gói 4 yêu cầu phiếu, log và ADR; việc hiện thực mã cần test tích hợp Keycloak và sẽ mở PR
riêng để không trộn thay đổi tài liệu với thay đổi hành vi đăng nhập.

Bằng chứng phiếu dựa trên mã thật:

- `KeycloakSyncService.java:61` — `DEV_PASSWORD = "password"`.
- `KeycloakSyncService.setPassword` — `cred.setTemporary(false)`.
- `V4__seed_users.sql` — user seed có `keycloak_id = NULL`.

### 4.1 Nội dung hạng mục Issues (milestone Gói 4)

**Tiêu đề:** Xử lý TD-01 — bỏ mật khẩu seed mặc định

Là quản trị nền tảng, tôi muốn tài khoản mới không dùng mật khẩu công khai để giảm rủi ro chiếm
tài khoản.

- Given admin tạo user mới
- When Keycloak tạo tài khoản
- Then credential có `temporary=true` và user có required action `UPDATE_PASSWORD`
- Given `DEMO_MODE` không bật
- When sync tạo tài khoản seed
- Then mật khẩu là ngẫu nhiên một lần và buộc đổi ở lần đăng nhập đầu
- Given mã nguồn sau thay đổi
- When tìm chuỗi mật khẩu mặc định trong `KeycloakSyncService`
- Then không còn hằng số mật khẩu

Truy vết: TD-01, NFR-01, NFR-06, ADR-0002. Cách làm: `agent`. Phụ trách: N10-A.

## 5. Retro ba dòng

1. Giữ: mỗi sơ đồ có bảng đối chiếu dòng mã nguồn ngay trong `docs/`.
2. Bỏ: suy đoán trạng thái theo tên gọi thay vì đọc bảng chuyển trạng thái.
3. Thử: thêm kiểm tự động participant ⊆ class diagram khi có `scripts/kiem_moc.py`.

## 6. Đối chiếu tiêu chí Gói 4

| Tiêu chí | Trạng thái | Bằng chứng |
|----------|------------|------------|
| `diagrams/class.mmd` | Đạt | 59 lớp, 25 participant sequence đều có mặt |
| `diagrams/state.mmd` | Đạt | 14 trạng thái Order |
| Mã khớp sơ đồ | Đạt sau khi sửa 5 lỗi | mục 3, `docs/tu-danh-gia-M3.md` mục 4-6 |
| `docs/tu-danh-gia-M3.md` | Đạt | bảng 59 lớp + tự chấm |
| Phiếu thay đổi đợt 1 | Đạt | `phieu-thay-doi/dot1_N10_TD-01.md` |
| Log phiên | Đạt | `log-tac-tu/phieu-dot1.md` |
| ADR nhắc mã phiếu | Đạt | `adr/0002-xu-ly-phieu-dot1-TD-01.md` |
| Hạng mục Issues milestone Gói 4 | Chờ thao tác | Nội dung sẵn ở mục 4.1; cần tạo trên GitHub Issues |

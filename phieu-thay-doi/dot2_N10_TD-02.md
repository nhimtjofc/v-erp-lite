# Phiếu thay đổi đợt 2 — TD-02

Phụ trách: N10-B
Đợt: 2
Mã phiếu: TD-02
Nguồn: danh mục nợ kỹ thuật `docs/ARCHITECTURE_ARC42.md` (TD-02). Kho không có `.se100/` nên nhóm mô phỏng phiếu này từ danh mục; thay bằng phiếu chính thức khi có.
Trạng thái: Đã tiếp nhận, chờ PR hiện thực

## 1. Bối cảnh

`OrderResource` đang là God Controller: vừa truy vấn, vừa kiểm quyền, vừa chuyển trạng thái, vừa hủy từng dòng, vừa hoàn tiền. Hai trang React lớn nhất cũng vượt 1.400 dòng. TD-02 trong `docs/ARCHITECTURE_ARC42.md` xếp mức P2 và đề xuất tách theo ngữ cảnh.

Phiếu này chỉ lấy một lát cắt nhỏ, có thể hoàn thành trong một PR: tách **luật chuyển trạng thái đơn** khỏi resource.

## 2. Hiện trạng và bằng chứng

| Bằng chứng | Vị trí |
|---|---|
| `OrderResource` dài 1.401 dòng, chứa nhiều nhóm trách nhiệm | `base/rest/OrderResource.java` |
| Bảng chuyển trạng thái nằm trong resource | `OrderResource.java:1371` (`isAllowedStatusTransition`) |
| Resource vừa kiểm tra trạng thái vừa ghi lịch sử | `OrderResource.java:800` |
| Trang chi tiết đơn 88 KB, 1.896 dòng | `frontend/src/pages/order-detail-page.tsx` |
| Trang giỏ/thanh toán 79 KB, 1.454 dòng | `frontend/src/pages/cart-checkout-page.tsx` |
| Chưa có test riêng cho luật chuyển trạng thái | `src/test/java/base/` đang trống |

Hệ quả: muốn thêm trạng thái mới phải sửa một tệp lớn, dễ ảnh hưởng endpoint khác; luật nghiệp vụ không kiểm được nếu không dựng cả resource.

## 3. Vấn đề

1. Luật chuyển trạng thái là tri thức nghiệp vụ nhưng bị chôn trong JAX-RS resource.
2. Không có điểm duy nhất để kiểm tra trạng thái hợp lệ, trạng thái kết thúc và alias legacy.
3. Không có test đơn vị cho luật này; thay đổi nhỏ cũng phải kiểm thủ công qua API.
4. Resource trộn truy vấn, quyền, chuyển trạng thái và hoàn tiền, khó review.

## 4. Phạm vi thay đổi

Trong phạm vi:

- Tạo `base/service/OrderStatusPolicy` (hoặc lớp tương đương) chứa:
  - bảng chuyển trạng thái hợp lệ;
  - danh sách trạng thái kết thúc;
  - alias legacy `PENDING`, `CONFIRMED`, `SHIPPING`, `SHIPPED`;
  - hàm `assertTransitionAllowed(oldStatus, newStatus)`.
- `OrderResource` gọi policy thay vì giữ bảng chuyển trạng thái nội bộ.
- Thêm test đơn vị cho các cặp hợp lệ, cặp bị chặn, alias legacy và nhánh `OWNER_DECLINED`.
- Ghi rõ trong `docs/tu-danh-gia-M3.md` rằng luật đã tách nhưng resource chưa tách hoàn toàn.

Ngoài phạm vi:

- Tách toàn bộ `OrderResource` thành ba resource.
- Tách các trang React.
- Đổi hợp đồng API, mã trạng thái hay schema.
- Sửa luồng escrow/hoàn tiền.

## 5. Tiêu chí chấp nhận

**AC-TD02.1 — Một nguồn luật duy nhất**
- Given mã nguồn sau thay đổi
- When tìm bảng chuyển trạng thái
- Then chỉ còn trong `OrderStatusPolicy`, không còn trong `OrderResource`

**AC-TD02.2 — Hành vi không đổi**
- Given một cặp trạng thái hợp lệ trước thay đổi
- When gọi endpoint chuyển trạng thái
- Then đơn chuyển thành công và ghi `OrderHistory` như cũ

**AC-TD02.3 — Cặp không hợp lệ bị chặn**
- Given đơn `COMPLETED`
- When yêu cầu chuyển về `PENDING_PAYMENT`
- Then trả lỗi 400 và không ghi `OrderHistory`

**AC-TD02.4 — Alias legacy vẫn được chấp nhận**
- Given dữ liệu cũ có `SHIPPING` hoặc `SHIPPED`
- When đọc hoặc chuyển trạng thái theo luật legacy
- Then policy xử lý như trước, không ném lỗi

**AC-TD02.5 — Có test**
- Given bộ test backend
- When chạy `./gradlew test`
- Then test của `OrderStatusPolicy` chạy và phủ ít nhất các nhánh trong `state.mmd`

## 6. Rủi ro và giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Đổi hành vi do chép sai bảng chuyển trạng thái | Test đối chiếu từng cặp với bảng cũ trước khi xóa |
| Ảnh hưởng nhánh hủy/trả hàng do `ReturnRefundService` đặt trạng thái trực tiếp | Giữ nguyên service; chỉ thay lớp kiểm tra trong resource |
| Tách quá mức gây thêm lớp không cần thiết | Chỉ tách policy, không tách resource trong phiếu này |
| Alias legacy bị bỏ sót | Đưa alias vào policy và test riêng |

## 7. Kiểm thử đề xuất

- `tests/luat/test_TD02_chuyen_trang_thai.py`: đọc bảng chuyển trạng thái, so với `diagrams/state.mmd`.
- Test đơn vị Java cho `OrderStatusPolicy`.
- Kiểm thủ công: tạo đơn, đi qua `PAID_ESCROW` → `OWNER_ACCEPTED` → `FULFILLING` → `DELIVERED` → `PENDING_COMPLETION` → `COMPLETED`.

## 8. Truy vết

- Tech debt: TD-02
- Yêu cầu: NFR-01, NFR-10
- ADR: `adr/0003-xu-ly-phieu-dot2-TD-02.md`
- Log phiên: `log-tac-tu/phieu-dot2.md`
- Hạng mục Issues: `Xử lý TD-02 — tách luật chuyển trạng thái đơn` (milestone Gói 5)

## 9. Kết luận tiếp nhận

Phiếu hợp lệ, phạm vi hẹp, không đổi API hay schema. Chấp nhận xử lý theo ADR-0003; PR hiện thực mở sau khi báo cáo Gói 5 được merge để không trộn tài liệu với thay đổi mã.

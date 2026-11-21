# ADR-0003 — Xử lý phiếu TD-02: tách luật chuyển trạng thái đơn

Phụ trách: N10-B
Trạng thái: Chấp nhận
Ngày: 15/11/2026

## Bối cảnh

Phiếu TD-02 chỉ ra `OrderResource` là God Controller. Tệp dài 1.401 dòng, chứa truy vấn, kiểm quyền, chuyển trạng thái, hủy từng dòng và hoàn tiền. Hai trang React lớn nhất cũng hơn 1.400 dòng.

Không thể tách toàn bộ trong một PR của Gói 5: thay đổi quá lớn, khó review, dễ đổi hành vi API. Cần chọn một lát cắt nhỏ, có giá trị, kiểm thử được.

## Quyết định

Chọn **A. Tách `OrderStatusPolicy`**:

- Chuyển bảng chuyển trạng thái, trạng thái kết thúc và alias legacy từ `OrderResource` sang một lớp policy trong `base.service`.
- `OrderResource` chỉ gọi policy; endpoint, mã lỗi và cách ghi `OrderHistory` giữ nguyên.
- Thêm test đơn vị cho policy; chưa tách resource, chưa tách trang React.
- Ghi phần còn lại của TD-02 vào nợ kỹ thuật để làm tiếp ở gói sau.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm | Kết luận |
|---|---|---|---|
| **A. Tách policy chuyển trạng thái** | Phạm vi nhỏ, không đổi API/schema; luật nghiệp vụ có một nguồn; test được độc lập | Resource vẫn lớn; chưa giải quyết truy vấn và hoàn tiền | **Chọn** |
| B. Tách `OrderResource` thành ba resource | Giảm kích thước tệp rõ rệt; ranh giới ngữ cảnh rõ | Rủi ro routing, quyền và hợp đồng API; cần nhiều PR và kiểm thử tích hợp | Loại trong đợt này |
| C. Không tách, chỉ thêm test | Ít thay đổi nhất | Giữ nguyên God Controller; test vẫn phải dựng resource | Loại |
| D. Viết lại order module theo DDD | Mô hình sạch, dài hạn | Quá lớn cho đồ án lite và thời gian còn lại | Loại |

## Hệ quả

### Tích cực

- Một nguồn sự thật cho luật chuyển trạng thái, khớp với `diagrams/state.mmd`.
- Có thể test các cặp trạng thái mà không cần dựng JAX-RS resource.
- Giảm nguy cơ sửa nhầm endpoint khi thêm trạng thái mới.
- Không đổi hợp đồng API, không cần migrate dữ liệu.

### Tiêu cực và rủi ro

- `OrderResource` vẫn còn lớn; phiếu chỉ giải quyết một phần TD-02.
- Nếu chép bảng chuyển trạng thái sai, hành vi đổi. Giảm thiểu: test đối chiếu từng cặp với bảng cũ trước khi xóa, và test theo `state.mmd`.
- `ReturnRefundService` đặt trạng thái trực tiếp, không đi qua policy. Giảm thiểu: giữ nguyên hành vi, ghi chú rõ; không gộp thay đổi này vào phiếu.

## Việc tiếp theo

- PR hiện thực `OrderStatusPolicy`, test đơn vị và cập nhật `OrderResource`.
- Sau khi ổn định, tách tiếp nhóm truy vấn và nhóm hoàn tiền thành resource riêng.
- Thêm test tích hợp cho luồng `PAID_ESCROW` đến `COMPLETED`.

## Tham chiếu

- `phieu-thay-doi/dot2_N10_TD-02.md`
- `log-tac-tu/phieu-dot2.md`
- `diagrams/state.mmd`
- `src/main/java/base/rest/OrderResource.java`
- `docs/ARCHITECTURE_ARC42.md` TD-02

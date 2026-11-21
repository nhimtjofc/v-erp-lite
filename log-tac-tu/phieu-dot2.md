# Log phiên — phiếu thay đổi đợt 2 (TD-02)

Phụ trách: N10-B
Đợt: 2
Mã phiếu: TD-02
Công cụ: agent trong IDE (không có `.se100/`; phiên mô phỏng theo danh mục TD-02)
Thời điểm: 15/11/2026

## 1. Đầu vào

- `docs/README.md` mục Gói 5 và mục phiếu thay đổi.
- `docs/ARCHITECTURE_ARC42.md` mục TD-02.
- `src/main/java/base/rest/OrderResource.java`.
- `diagrams/state.mmd`, `docs/tu-danh-gia-M3.md`.
- `frontend/src/pages/order-detail-page.tsx`, `frontend/src/pages/cart-checkout-page.tsx`.

## 2. Các bước

1. Đọc TD-02 và đo kích thước tệp: `OrderResource` 1.401 dòng, trang chi tiết đơn 1.896 dòng, trang giỏ/thanh toán 1.454 dòng. Kết luận không thể tách toàn bộ trong một PR.
2. Chọn lát cắt nhỏ nhất có giá trị: tách bảng chuyển trạng thái và alias legacy khỏi resource.
3. Đọc `OrderResource.isAllowedStatusTransition` (dòng 1371-1400) và `updateStatus` (dòng 800) để lấy đúng danh sách cặp trạng thái.
4. Đối chiếu với `diagrams/state.mmd`; phát hiện `OWNER_DECLINED` đang được vẽ là tự chuyển `REFUNDED`, trong khi `OrderFulfillmentService.declineAndRefund` chỉ đặt `OWNER_DECLINED`.
5. Kiểm `ReturnRefundService` để xác nhận các trạng thái `CANCEL_REQUESTED`, `CANCEL_APPROVED`, `RETURN_REQUESTED`, `RETURN_APPROVED`, `REFUNDED`.
6. Viết tiêu chí chấp nhận, rủi ro và kiểm thử cho lát cắt; ghi rõ phần ngoài phạm vi.
7. Trong lúc soạn tài liệu kiến trúc, đối chiếu `docker-compose.yml` và `application.properties`: phát hiện `AGENTS.md` ghi PostgreSQL, thực tế là MariaDB 11.4. Đã sửa `AGENTS.md` và dùng MariaDB trong C4.
8. Kiểm lại `docs/du-lieu.md` với `@Table` và migration: xác nhận đủ 40 bảng (37 bảng có entity, 3 bảng nối/legacy); sửa `OrderItem.lineTotalMinor` thành `subtotalAmountMinor` trong `class.mmd`.

## 3. Sai sót và cách sửa

| Sai sót | Phát hiện | Cách sửa |
|---|---|---|
| Định tách toàn bộ `OrderResource` trong một phiếu | Ước lượng 1.401 dòng và nhiều nhóm trách nhiệm | Thu hẹp còn tách `OrderStatusPolicy` |
| Vẽ `OWNER_DECLINED → REFUNDED` như tự động | Đọc `OrderFulfillmentService.declineAndRefund` | Đổi thành nét đứt, ghi chú hoàn tiền qua `PaymentRefundService` |
| C4 context ban đầu đặt trợ lý AI ra ngoài hệ thống | Đối chiếu container: chatbot là một phần của V-ERP Lite | Đưa chatbot vào boundary, chỉ để Gemini/Pinecone bên ngoài |
| `AGENTS.md` ghi PostgreSQL và cổng 5433 | Đọc `docker-compose.yml`, `application.properties` | Sửa thành MariaDB 11.4, cổng 3307 |
| `OrderItem` ghi `lineTotalMinor` | Đối chiếu `OrderItem.java` | Đổi thành `subtotalAmountMinor` |

## 4. Đầu ra

- `phieu-thay-doi/dot2_N10_TD-02.md`
- `adr/0003-xu-ly-phieu-dot2-TD-02.md`
- Mục 4 và 4.1 trong `bao-cao/goi5-kien-truc.md`
- Cập nhật `diagrams/state.mmd`, `docs/dac-ta-UC-02-xu-ly-don-giai-phong-escrow.md`, `docs/tu-danh-gia-M3.md`

## 5. Giới hạn của phiên

- Không có `.se100/` nên phiếu không phải bản phát hành chính thức của giảng viên; nội dung được mô phỏng từ TD-02.
- Không có PR hiện thực trong gói này; chỉ có phiếu, log và ADR.
- Không có URL deploy và không có review chéo từ kho khác tại thời điểm viết.

# Báo cáo Gói 5 — Kiến trúc, dữ liệu và xử lý phiếu đợt 2

Nhóm: N__10
Phụ trách: N10-C
Hạn: CN 22/11/2026 (23:59)
Thẻ: `goi-5`
Milestone Issues: `Gói 5`

## 1. Đã làm gì

1. Vẽ `diagrams/c4-context.mmd`: bốn nhóm người dùng, hệ thống V-ERP Lite và năm hệ thống ngoài (Keycloak, WooCommerce, ngân hàng/VietQR, cổng thanh toán, Gemini/Pinecone).
2. Vẽ `diagrams/c4-container.mmd`: web SPA, backend API, trợ lý AI, MariaDB; quan hệ REST, OIDC, webhook HMAC, JDBC và SDK.
3. Viết `docs/du-lieu.md`: 40 bảng, phân loại dữ liệu, quan hệ chính, vòng đời đặt hàng/escrow/payout, PII, lưu giữ và nợ kỹ thuật dữ liệu.
4. Viết `adr/0001-kien-truc-modular-monolith.md`: chọn modular monolith, so sánh bốn phương án.
5. Cập nhật `AGENTS.md`: sửa PostgreSQL thành MariaDB 11.4, cổng 3307; bổ sung WordPress 8090, AI 8000; trỏ tới C4 và tài liệu dữ liệu; bỏ email seed khỏi hướng dẫn.
6. Xử lý phiếu đợt 2:
   - `phieu-thay-doi/dot2_N10_TD-02.md`
   - `log-tac-tu/phieu-dot2.md`
   - `adr/0003-xu-ly-phieu-dot2-TD-02.md`
7. Đối chiếu C4 với `docker-compose.yml`, `application.properties`; đối chiếu danh mục bảng với `@Table` và migration; sửa các sai lệch ở mục 3.

## 2. Ba quyết định thiết kế lớn nhất

1. **Modular monolith, không microservices.** Escrow, ví và đơn hàng cần cùng một giao dịch CSDL; nhóm nhỏ và thời gian ngắn không đủ cho broker, saga và hạ tầng quan sát. Chi tiết ở ADR-0001.
2. **Trợ lý AI là container riêng, chỉ đọc.** FastAPI phù hợp hệ sinh thái Gemini/Pinecone; backend vẫn là nguồn sự thật. Tool chỉ gồm bốn thao tác đọc, danh tính lấy từ JWT, không nhận `tenant_id`/`user_id` từ model.
3. **Cách ly tenant bằng lọc thủ công.** Mỗi truy vấn đơn lẻ thêm `tenant_id`, với OWNER thêm `business_id`. Đây là quyết định có chủ ý để tránh Hibernate multi-tenancy và RLS khi chưa có hạ tầng kiểm thử; rủi ro được bù bằng quy tắc trong `AGENTS.md` và test NFR-01.

## 3. Thẩm định kết quả agent

### Lần 1 — Kiểm C4 với cấu hình chạy thật

- Kiểm bằng gì: đọc `docker-compose.yml`, `src/main/resources/application.properties`, `PaymentGatewayService`, `ecommerce-chatbot/README.md`; so từng container, cổng và giao thức với hai tệp C4.
- Kết quả: nguồn `AGENTS.md` cũ ghi PostgreSQL và cổng 5433 — **sai**, cấu hình thật là MariaDB 11.4 cổng 3307; đã sửa `AGENTS.md` trước khi chốt C4. Bản C4 đầu tiên đặt trợ lý AI ra ngoài hệ thống — **sai**, đây là container nội bộ; chỉ Gemini/Pinecone là ngoài. Đã sửa cả hai.
- Kết luận: sai một nguồn tài liệu và một ranh giới container; sau khi sửa, container khớp compose và application.properties.

### Lần 2 — Kiểm tài liệu dữ liệu với mã nguồn

- Kiểm bằng gì: grep `@Table(name=...)` trong `base/entity`, đối chiếu danh sách `CREATE TABLE` trong migration; đọc `OrderItem`, `OrderHistory`, `WalletTransaction`, `InboundEventLog`, `WebhookEvent`; so field trong `class.mmd`.
- Kết quả: danh mục 40 bảng khớp 37 bảng có entity và 3 bảng nối/legacy (`product_tags`, `carts`, `cart_items`). Agent ghi `OrderItem.lineTotalMinor` — **sai**, mã dùng `subtotalAmountMinor`; đã sửa. Agent vẽ `OWNER_DECLINED → REFUNDED` như tự động — **sai**, `declineAndRefund` giữ `OWNER_DECLINED`; đã đổi sang nét đứt và ghi chú.
- Kết luận: sai hai chi tiết mô hình; sau khi sửa, tên bảng và field chính khớp mã nguồn.

## 4. Xử lý phiếu thay đổi đợt 2

| Hạng mục | Tệp | Nội dung |
|---|---|---|
| Phiếu | `phieu-thay-doi/dot2_N10_TD-02.md` | TD-02: tách luật chuyển trạng thái khỏi `OrderResource` |
| Log phiên | `log-tac-tu/phieu-dot2.md` | Đầu vào, 8 bước, 5 sai sót và cách sửa |
| ADR | `adr/0003-xu-ly-phieu-dot2-TD-02.md` | 4 phương án, chọn A, hệ quả và việc cần làm |

Trạng thái: phiếu đã tiếp nhận, ADR chốt phương án A, PR hiện thực chưa mở. Lý do: Gói 5 yêu cầu phiếu, log và ADR; thay đổi mã cần test hồi quy luật trạng thái và nên tách khỏi PR tài liệu.

Giới hạn: kho không có `.se100/` nên phiếu đợt 2 được mô phỏng từ TD-02 trong `docs/ARCHITECTURE_ARC42.md`, không phải phiếu phát hành chính thức của giảng viên. Khi có phiếu chính thức, thay mã phiếu và đối chiếu lại phạm vi.

### 4.1 Nội dung hạng mục Issues (milestone Gói 5)

**Tiêu đề:** Xử lý TD-02 — tách luật chuyển trạng thái đơn

Là người bảo trì backend, tôi muốn luật chuyển trạng thái đơn nằm ở một lớp riêng để thêm trạng thái mới mà không sửa God Controller.

- Given mã nguồn sau thay đổi
- When tìm bảng chuyển trạng thái
- Then chỉ còn trong `OrderStatusPolicy`, không còn trong `OrderResource`
- Given đơn `COMPLETED`
- When yêu cầu chuyển về `PENDING_PAYMENT`
- Then trả lỗi 400 và không ghi `OrderHistory`
- Given dữ liệu cũ có `SHIPPING` hoặc `SHIPPED`
- When đọc hoặc chuyển trạng thái
- Then alias legacy vẫn được chấp nhận
- Given bộ test backend
- When chạy `./gradlew test`
- Then test của `OrderStatusPolicy` chạy và phủ các nhánh trong `state.mmd`

Truy vết: TD-02, NFR-01, NFR-10, ADR-0003. Cách làm: `agent`. Phụ trách: N10-B.

## 5. Trả lời review chéo

Chưa có báo cáo review chéo từ kho được phân công tại thời điểm viết, nên chưa thể trả lời từng phát hiện. Khi nhận báo cáo, nhóm sẽ xử lý theo thứ tự:

1. Đối chiếu từng phát hiện với tệp và dòng được trỏ; xác nhận hoặc phản biện bằng mã.
2. Với phát hiện đúng: mở hạng mục, sửa trong PR riêng, ghi `Đóng #`.
3. Với phát hiện không đúng: trả lời kèm bằng chứng dòng mã và giữ nguyên nếu không thuyết phục được.
4. Ghi lại kết quả vào báo cáo Gói 6 và `docs/trung-lap.md`.

Đây là phần còn thiếu của Gói 5; không đánh dấu đạt khi chưa có review thật.

## 6. Retro ba dòng

1. Giữ: mỗi sơ đồ kiến trúc đối chiếu với một tệp cấu hình chạy được, không vẽ theo trí nhớ.
2. Bỏ: tin vào `AGENTS.md` khi chưa đối chiếu `docker-compose.yml` và `application.properties`.
3. Thử: thêm kiểm tự động danh mục bảng từ `@Table` và migration vào `scripts/kiem_moc.py` khi có bộ kiểm.

## 7. Đối chiếu tiêu chí Gói 5

| Tiêu chí | Trạng thái | Bằng chứng |
|---|---|---|
| `diagrams/c4-context.mmd` | Đạt | 4 actor, 5 hệ thống ngoài |
| `diagrams/c4-container.mmd` | Đạt | 4 container, quan hệ và giao thức |
| `docs/du-lieu.md` | Đạt | 40 bảng, vòng đời, PII, lưu giữ |
| `adr/0001-*.md` | Đạt | 4 phương án, chọn modular monolith |
| `AGENTS.md` | Đạt | MariaDB, cổng, C4, dữ liệu |
| Phiếu đợt 2 | Đạt | `phieu-thay-doi/dot2_N10_TD-02.md` (mô phỏng do thiếu `.se100/`) |
| Log phiên | Đạt | `log-tac-tu/phieu-dot2.md` |
| ADR nhắc mã phiếu | Đạt | `adr/0003-xu-ly-phieu-dot2-TD-02.md` |
| Trả lời review chéo | Chưa đạt | Chưa nhận báo cáo từ kho được phân công |
| Hạng mục Issues milestone Gói 5 | Chờ thao tác | Nội dung sẵn ở mục 4.1 |

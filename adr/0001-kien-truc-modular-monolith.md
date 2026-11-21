# ADR-0001 — Kiến trúc modular monolith với lọc tenant thủ công

Phụ trách: N10-C
Trạng thái: Chấp nhận
Ngày: 15/11/2026

## Bối cảnh

V-ERP Lite là đồ án học phần, nhóm nhỏ, cần chạy được trên một máy và triển khai đơn giản. Nghiệp vụ gồm catalog, đơn hàng nhiều người bán, escrow, sổ cái ví, payout, đồng bộ WooCommerce và một trợ lý AI read-only.

Các lực lượng thiết kế:

- Dữ liệu tài chính cần nhất quán giao dịch và truy vết; không được chia nhỏ giao dịch sang nhiều dịch vụ khi chưa có hạ tầng.
- Mọi bảng nghiệp vụ đều theo tenant; yêu cầu NFR-01 đòi 100% truy vấn đơn lẻ phải lọc `tenant_id`.
- Nhóm cần đọc và sửa mã nhanh trong 14 tuần; số lượng thành viên nhỏ.
- Trợ lý AI dùng hệ sinh thái Python (FastAPI, Gemini, Pinecone), khác runtime với backend Java.
- Yêu cầu NFR-05 (khởi động nhanh, p95 thấp) và NFR-10 (schema qua Flyway) phù hợp với một tiến trình backend duy nhất.

## Quyết định

Chọn **modular monolith**:

1. Một ứng dụng Quarkus duy nhất cho toàn bộ nghiệp vụ, đóng gói theo package phẳng `base.entity`, `base.rest`, `base.service`, `base.security`.
2. Một cơ sở dữ liệu MariaDB 11.4; mọi thay đổi schema qua Flyway.
3. Cách ly tenant bằng cột `tenant_id` và lọc thủ công trong câu truy vấn, không dùng Hibernate multi-tenancy, không dùng Row-Level Security.
4. Trợ lý AI là container riêng (FastAPI) vì khác runtime, nhưng chỉ gọi 4 tool read-only của backend; backend vẫn là nguồn sự thật.
5. Giao tiếp nội bộ qua REST/JSON và OpenAPI; frontend dùng SDK sinh tự động.

Quyết định này được thể hiện trong `diagrams/c4-context.mmd` và `diagrams/c4-container.mmd`.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm | Kết luận |
|---|---|---|---|
| **A. Modular monolith + lọc tenant thủ công** | Một giao dịch CSDL cho escrow/ví; triển khai một artifact; nhóm nhỏ dễ sửa; đúng với mã hiện có | Phải kỷ luật thêm `tenant_id` ở mọi truy vấn; khó scale từng module; God controller dễ phình | **Chọn** |
| B. Microservices theo miền (order, payment, wallet, catalog) | Scale và cô lập lỗi độc lập; ranh giới miền rõ | Cần message broker, saga, hạ tầng quan sát; tăng độ phức tạp và chi phí học; không phù hợp "lite" | Loại |
| C. Modular monolith + Hibernate multi-tenancy hoặc RLS | Giảm rủi ro quên lọc tenant; ràng buộc ở tầng CSDL | Hibernate 3.x không hỗ trợ discriminator như mong muốn; RLS phức tạp với MariaDB và chưa có hạ tầng kiểm thử; thay đổi lớn so với mã hiện tại | Loại |
| D. Nhúng trợ lý AI vào backend Quarkus | Một runtime, một deploy | Hệ sinh thái LLM/vector mạnh hơn ở Python; trộn vòng đời và rủi ro bảo mật AI vào backend tài chính; khó cô lập tài nguyên | Loại |

## Hệ quả

### Tích cực

- Escrow, ví và đơn hàng nằm trong cùng một giao dịch CSDL, giảm nhu cầu saga và bù trừ.
- Một lệnh build, một artifact, một pipeline; phù hợp thời gian và nhân lực của nhóm.
- Ranh giới package và quy ước đặt tên `ThingResource`/`ThingService` dễ kiểm tra bằng đọc mã.
- Trợ lý AI có thể thay mô hình hoặc vector store mà không ảnh hưởng backend.

### Tiêu cực và rủi ro

- Quên `tenant_id` ở một endpoint là lỗi bảo mật. Giảm thiểu: quy tắc bắt buộc trong `AGENTS.md`, test theo NFR-01, review diff tập trung vào các truy vấn `find`.
- Một module lỗi có thể kéo toàn backend. Giảm thiểu: giới hạn tài nguyên, health check, log có mã lỗi.
- `OrderResource` và một số trang React đang quá lớn (TD-02). Giảm thiểu: tách dần theo ngữ cảnh, không gộp thêm logic mới vào tệp cũ.
- Sổ cái hiện là single-entry (TD-03), chưa đối ứng Nợ/Có. Đây là nợ kỹ thuật độc lập với quyết định monolith.
- MariaDB volume chưa có sao lưu tự động. Cần bổ sung trước khi chạy thật.

## Việc tiếp theo

- Thêm test hồi quy cho các truy vấn đơn lẻ theo tenant (NFR-01).
- Tách `OrderResource` theo ngữ cảnh khi có thời gian (TD-02).
- Chuyển sổ cái sang mô hình ghi kép khi nghiệp vụ tài chính mở rộng (TD-03).
- Bổ sung sao lưu và thử khôi phục định kỳ cho MariaDB.

## Tham chiếu

- `diagrams/c4-context.mmd`, `diagrams/c4-container.mmd`
- `docs/du-lieu.md`
- `docs/yeu-cau.md` NFR-01, NFR-05, NFR-10
- `AGENTS.md`
- `docs/ARCHITECTURE_ARC42.md` TD-02, TD-03

# Trợ lý AI Shopping - Thiết kế Bảo mật Toàn diện (Hardened AI Agent)

Tài liệu này mô tả chi tiết kiến trúc bảo mật của AI Shopping Assistant sau khi thực hiện hardening đạt 100% các tiêu chuẩn và nguyên tắc thiết kế trong tài liệu `intern-ai-agent-design.md`.

---

## 1. Kiến trúc Bảo mật (Security Architecture)

```
[Frontend ChatWidget] ──(message + Bearer JWT + session_id)──> [FastAPI Server:8000]
                                                                      │
                                                           [RequestContext] (ContextVar)
                                                                      │
                                                           [UnifiedAgent] (Bounded Loop)
                                                                      │
                        (Authorization: Bearer JWT) ──────────┼──────────> [Quarkus Backend:8081]
                                                              │            (Per-User Scoped)
                                                              ▼
                                                   [Deterministic Output Validator]
                                                    └─ 1-step No-tool Retry / Fallback
                                                              │
                                                   [Server-Owned SessionStore]
                                                    (Atomic Append on Valid Turn)
```

### Nguyên tắc Cốt lõi
*   **Identity nằm ngoài Model**: Toàn bộ định danh người dùng (`tenant`, `user_id`, `email`, `roles`) được giải mã và kiểm tra chữ ký RS256 từ JWT Keycloak, lưu trữ trong `RequestContext` và truyền ngầm qua `contextvars`. Model không nhận và không quyết định danh tính.
*   **Per-User Scoped Backend**: Backend Quarkus (`OrderResource.java`) kiểm tra quyền của token và tự động cô lập dữ liệu theo `(customer.id = :customerId OR createdBy = :userId)`.
*   **Bounded Tool Loop**: Vòng lặp công cụ giới hạn tối đa `max_steps = 5`, mỗi tool call nhận đúng 1 tool response, bọc bảo vệ chống crash khi model không đưa ra văn bản cuối.
*   **Deterministic Output Validation**: Trước khi gửi tin nhắn cho người dùng hoặc lưu vào session, câu trả lời bắt buộc phải qua 4 tầng kiểm tra tất định:
    1. `tech_leak`: Chặn rò rỉ mã lỗi, stack trace, đường dẫn file `.py`, `.java`.
    2. `instruction_leak`: Chặn tiết lộ prompt hệ thống hoặc câu lệnh ẩn.
    3. `action_claim`: Chặn tự nhận đã thực hiện thay đổi dữ liệu (hủy đơn, hoàn tiền, sửa đổi) khi hệ thống là read-only.
    4. `pii`: Chặn rò rỉ hoặc bịa đặt số điện thoại, email không có trong kết quả trả về từ công cụ.
*   **1-Step No-Tool Corrective Retry**: Khi phát hiện vi phạm, hệ thống gửi lời nhắc hiệu chỉnh cho model mà **không cấp quyền gọi tool**. Nếu thử lại vẫn vi phạm, hệ thống trả về `SAFE_FALLBACK` và hủy lượt lưu lịch sử dở dang.
*   **Privacy-Safe Telemetry**: Ghi nhận chỉ số hoạt động (thời gian xử lý, số lượt gọi từng tool, trạng thái lượt chat, số lần retry) mà **tuyệt đối không lưu trữ prompt, tin nhắn của người dùng, tham số hay dữ liệu trả về của tool**.

---

## 2. Danh sách Công cụ (MVP Read-Only Tools)

Hệ thống AI Assistant được đăng ký 4 công cụ (Tools) với schema cố định, chỉ nhận tham số ngữ nghĩa (Semantic Arguments):

1.  **`list_customer_orders(status_filter: Optional[str] = None) -> str`**:
    *   *Mô tả*: Lấy danh sách các đơn hàng gần đây của chính người dùng đang đăng nhập. Có thể lọc theo trạng thái (ví dụ: `SHIPPING`, `DELIVERED`, `PENDING_PAYMENT`, `PAID_ESCROW`, `FULFILLING`, `CANCELLED`, v.v.) hoặc để trống để xem tất cả đơn gần nhất.
    *   *Bảo mật*: Token JWT được inject tự động từ `RequestContext` để gọi `GET /api/orders?status=...`. Backend tự động lọc theo quyền sở hữu của user. Kết quả được định dạng ngắn gọn, loại bỏ mọi PII nhạy cảm.

2.  **`get_order_status(order_id: str) -> str`**:
    *   *Mô tả*: Tra cứu tình trạng chi tiết của một đơn hàng cụ thể theo mã đơn (ví dụ: `ORD-123`).
    *   *Bảo mật*: Nhận tham số phi danh tính `order_id`. Token JWT được gửi đến `/api/orders?search={order_id}`.

3.  **`get_total_products_count() -> str`**:
    *   *Mô tả*: Đếm tổng số lượng sản phẩm hiện có trong cửa hàng.
    *   *Bảo mật*: Không nhận tham số định danh. Sử dụng JWT gửi đến `/api/products` để lấy số lượng sản phẩm thuộc Tenant hiện tại.

4.  **`search_knowledge_base(query: str) -> str`**:
    *   *Mô tả*: Tìm kiếm thông tin sản phẩm (mô tả, giá cả, tồn kho) trên CSDL Vector (Pinecone) dựa trên từ khóa ngữ nghĩa.

---

## 3. Phân biệt Kiểm soát Cấu trúc và Hành vi (Structural vs. Behavioral Controls)

| Loại kiểm soát | Cơ chế triển khai | Mô tả & Tính bất khả xâm phạm |
|---|---|---|
| **Cấu trúc (Structural)** | JWT Verification & RequestContext | Xác thực chữ ký RS256 ngoài model, model không có quyền can thiệp vào định danh. |
| **Cấu trúc (Structural)** | Schema & Argument Security Guard | Schema không chứa `user_id`/`tenant_id`. Bộ lọc chặn ngay lập tức nếu đối số chứa từ khóa danh tính. |
| **Cấu trúc (Structural)** | Backend Isolation | `OrderResource.java` bắt buộc thêm điều kiện `tenantId` và `customerId/userId`. |
| **Cấu trúc (Structural)** | Server-Owned SessionStore | Lịch sử lưu trên server, scope theo `(tenant, user_id)`, kiểm soát đồng thời `turn()` chống race-condition. |
| **Cấu trúc (Structural)** | Output Validator & Fallback | Regex và chuỗi kiểm tra tất định độc lập với LLM; tự động fallback nếu model trả lời sai. |
| **Cấu trúc (Structural)** | Privacy Telemetry | Chỉ ghi nhận metric thời gian/số lượng; không có trường dữ liệu lưu trữ prompt hay PII. |
| **Hành vi (Behavioral)** | Static System Instruction | Cấu hình trong `UnifiedAgent` định hướng văn phong, cách hành văn lịch sự và nhắc nhở không bịa đặt. |

---

## 4. Hợp đồng API (API Contract)

### `POST /chat`
*   **Headers**:
    ```http
    Authorization: Bearer <Keycloak_OIDC_JWT>
    Content-Type: application/json
    ```
*   **Request Body** (Chỉ nhận tin nhắn và session_id, tuyệt đối không nhận lịch sử từ client):
    ```json
    {
      "message": "Tôi có những đơn hàng nào đang giao?",
      "session_id": "optional-session-id"
    }
    ```
*   **Response Body**:
    ```json
    {
      "response": "🤖 Dạ hiện tại bạn có 1 đơn hàng đang trên đường giao:<br>- Mã: ORD-001 | Trạng thái: SHIPPING | Tổng tiền: 250000 VND...",
      "session_id": "server-generated-session-id"
    }
    ```
*   **Mã lỗi HTTP**:
    *   `401 Unauthorized`: Token bị thiếu, hết hạn hoặc không hợp lệ.
    *   `409 Conflict`: Có lượt xử lý khác đang diễn ra đồng thời trên session này (`SessionBusy`).
    *   `404 Not Found`: Session không tồn tại hoặc thuộc quyền sở hữu của người khác (`SessionAccessDenied` được map sang 404 để chống tấn công dò quét ID).

---

## 5. Hướng dẫn Chạy Kiểm thử (Test Suite)

Để kiểm tra toàn bộ 48 bài kiểm thử bất biến bảo mật (Security Invariants & Hardening Tests) hoàn toàn offline:

```bash
cd ecommerce-chatbot
python run_tests.py
```

### Các nhóm kiểm thử tự động:
1. `test_agent_loop.py`: Kiểm thử tool `list_customer_orders`, Argument Security Guard, và Telemetry.
2. `test_session_store.py`: Kiểm thử quyền sở hữu session, append-only, giới hạn dung lượng, giải phóng lock khi exception, chống race condition đa luồng.
3. `test_output_validator.py`: Kiểm thử 4 tầng lọc rò rỉ mã lỗi, system prompt, claim hành động thay đổi dữ liệu, và PII giả mạo.
4. `test_request_context.py`: Kiểm thử bóc tách tenant/roles từ JWT và từ chối header không hợp lệ.

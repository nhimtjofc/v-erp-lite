# Trợ lý AI Shopping - Thiết kế Bảo mật (Hardening Agent)

Tài liệu này mô tả chi tiết kiến trúc bảo mật của AI Shopping Assistant sau khi thực hiện hardening theo tài liệu thiết kế `intern-ai-agent-design.md`.

## 1. Kiến trúc Bảo mật (Security Architecture)

```
[Frontend ChatWidget] ──(message + token + session_id)──> [FastAPI Server:8000]
                                                              │
                                                      [RequestContext] (ContextVar)
                                                              │
                                                      [UnifiedAgent] (Tool Loop)
                                                              │
                    (Authorization: Bearer JWT) ──────┴───────> [Quarkus Backend:8081]
                                                                (Per-User Auth Enforced)
```

Thay vì sử dụng một shared-secret không an toàn (`X-Chatbot-Key`) và bỏ qua việc xác thực quyền sở hữu dữ liệu của khách hàng, hệ thống đã được cải tiến để đạt được nguyên tắc **Identity nằm ngoài model**:
*   **JWT Token Forwarding**: ChatWidget trích xuất OIDC JWT token hiện tại của người dùng đang đăng nhập và truyền xuống FastAPI.
*   **RequestContext Propagation**: FastAPI xác thực chữ ký JWT, tạo ra `RequestContext` tin cậy, lưu vào biến `contextvars` cục bộ để tự động inject vào mọi lượt gọi API Backend.
*   **Per-User Scoped Backend**: Quarkus Backend tự động giải quyết email người dùng từ JWT và thực hiện lọc dữ liệu per-user (User A chỉ có thể tìm kiếm đơn hàng của chính mình).

---

## 2. Danh sách Công cụ (Tools List)

Hệ thống AI Assistant được đăng ký 3 công cụ (Tools) với schema cố định:

1.  **`get_order_status(order_id: str) -> str`**:
    *   *Mô tả*: Tra cứu tình trạng giao hàng và thanh toán của đơn hàng.
    *   *Bảo mật*: Nhận tham số phi danh tính (`order_id`). Token JWT của phiên hiện tại được lấy từ `RequestContext` cục bộ để gửi đến `/api/orders?search={order_id}`.
2.  **`get_total_products_count() -> str`**:
    *   *Mô tả*: Lấy tổng số lượng sản phẩm hiện có trong cửa hàng.
    *   *Bảo mật*: Không nhận tham số định danh. Sử dụng JWT gửi đến `/api/products` để lấy số lượng sản phẩm được phân quyền cho Tenant đó.
3.  **`search_knowledge_base(query: str) -> str`**:
    *   *Mô tả*: Tìm kiếm thông tin sản phẩm (mô tả, giá cả, tồn kho) trên CSDL Vector (Pinecone).

---

## 3. Phân biệt Kiểm soát Cấu trúc và Hành vi (Structural vs. Behavioral Controls)

### Kiểm soát Cấu trúc (Structural Controls - Non-bypassable)
Đây là các rào cản kỹ thuật cứng rắn ở cấp mã nguồn (code-level) mà mô hình AI không thể vượt qua:
*   **No Identity Parameters in Schema**: Schema của các công cụ tuyệt đối không chứa các tham số định danh như `userId`, `tenantId`, hay `customerEmail`.
*   **Argument Security Guard**: Bộ kiểm duyệt đầu vào (input parameter guard) sẽ chặn và hủy lệnh thực thi công cụ ngay lập tức nếu mô hình gửi tham số có chứa từ khóa nhạy cảm (`user`, `tenant`, `customer`, `account`).
*   **Server-Owned Session Store**: Lịch sử trò chuyện được lưu trữ hoàn toàn ở server (InMemorySessionStore) và scope theo `(tenant, user_id)` lấy từ JWT đã xác thực. Client không thể tiêm nhiễm (inject) tin nhắn hệ thống hoặc giả mạo lịch sử.
*   **Sanitized Error Responses**: Mọi lỗi hệ thống (exception/stack trace) được bắt giữ và làm sạch tại FastAPI, trả về thông báo lỗi chung dạng văn bản để ngăn ngừa rò rỉ cấu trúc ứng dụng.

### Kiểm soát Hành vi (Behavioral Controls - Prompt-based)
Các hướng dẫn hành vi giúp mô hình tương tác một cách tự nhiên và an toàn:
*   **System Instructions**: Prompt hệ thống cấu hình tĩnh tại `UnifiedAgent` hướng dẫn mô hình không được giả mạo thông tin nếu công cụ không tìm thấy dữ liệu, và từ chối cung cấp thông tin nhạy cảm của người dùng khác.

---

## 4. Hợp đồng API (API Contract)

### `POST /chat`
*   **Headers**:
    ```http
    Authorization: Bearer <JWT_Token>
    Content-Type: application/json
    ```
*   **Request Body**:
    ```json
    {
      "message": "Tra cứu đơn hàng giúp tôi",
      "session_id": "optional-session-id-here"
    }
    ```
*   **Response Body**:
    ```json
    {
      "response": "🤖 Đơn hàng của bạn đang được xử lý...",
      "session_id": "secure-server-generated-session-id"
    }
    ```
*   **Mã lỗi HTTP**:
    *   `401 Unauthorized`: Token bị thiếu hoặc không hợp lệ.
    *   `409 Conflict`: Có một lượt xử lý (turn) đang diễn ra đồng thời trên cùng một session.
    *   `404 Not Found`: Không tìm thấy session hoặc session thuộc sở hữu của người dùng khác (được trả về đồng nhất để tránh tấn công dò tìm ID).

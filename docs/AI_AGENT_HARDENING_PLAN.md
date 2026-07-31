# Kế hoạch Hardening AI Agent (theo brief `intern-ai-agent-design.md`)

> Mục tiêu: đưa chatbot hiện tại từ trạng thái **demo chạy được** lên **agent an toàn**
> đúng 5 nguyên tắc non-negotiable của brief. Trọng tâm: **identity nằm ngoài model**.
>
> Ngày tạo: 2026-07-27 · Nhánh: `develop`

---

## 1. Kiến trúc hiện tại

```
chat-widget.tsx  ──{message}──>  FastAPI /chat (port 8000)
                                      │  router_agent (Gemini phân loại intent 3 nhánh)
                                      ├─> stats_agent
                                      ├─> order_ops_agent  ──X-Chatbot-Key + X-Tenant-Code(hardcode)──> Quarkus /api/chatbot/* (8081)
                                      └─> retrieval_agent (Pinecone)
```

Đây là kiến trúc demo hoạt động được, nhưng vi phạm gần như toàn bộ nguyên tắc của brief.

## 2. Phân tích khoảng cách theo 5 nguyên tắc bắt buộc

| # | Nguyên tắc (brief) | Hiện trạng | Mức độ |
|---|---|---|---|
| **1** | Identity nằm ngoài model, mọi API scope theo org+user | ❌ `X-Tenant-Code: "v-erp-app"` **hardcode** trong mọi agent; **không có user identity**. Model nhận `order_id` từ người dùng và tra thẳng → bất kỳ ai cũng tra được đơn của bất kỳ ai | 🔴 Nghiêm trọng |
| **2** | Kết quả tool là dữ liệu không tin cậy; rule nghiệp vụ ở backend | ⚠️ Bảo mật PII chỉ dựa vào **câu prompt** (`order_ops_agent.py:73`) — đúng thứ brief cấm | 🔴 Nghiêm trọng |
| **3** | Prompt/schema ổn định, không cá nhân hóa | ⚠️ Prompt build lại mỗi request, không tách tầng ổn định; chưa dùng cache | 🟡 Trung bình |
| **4** | Session server-owned, append-only | ❌ Hoàn toàn không có session — mỗi request stateless | 🟠 Cao |
| **5** | Validate output trước khi trả | ❌ Không có; trả thẳng `response.text`, in cả stack trace ra UI (`server.py:70`) | 🟠 Cao |

**Vấn đề phụ:** secret `chatbot-secret-v1` hardcode trong source; router là if/else cứng (không phải bounded tool loop); `/chat` không xác thực; frontend không gửi token; port lệch (widget→8000, agent→8081).

### Rủi ro số 1: Cross-user data leak
Người dùng A gõ "đơn ORD-125" → `get_order_status` tra thẳng backend; backend (`ChatbotResource.java:58`) chỉ lọc `tenantId` chứ **không lọc theo user** → A xem được đơn của B. Chính là lỗi *"letting a tool accept a principal identifier"* trong Common Mistakes.

## 3. Phát hiện then chốt: pattern bảo mật ĐÃ tồn tại sẵn

`OrderResource.java` đã làm **đúng chính xác** rule #1 — dùng làm khuôn mẫu:

```java
// OrderResource:156, 194-196 — gold standard
@RolesAllowed({"admin","businessOwner","operator","customer","user"})
Customer c = Customer.find("email = ?1 and tenantId = ?2", caller.userEmail(), tenantCode)...
query.append(" AND (customer.id = :customerId OR createdBy = :userId)"); // scope theo USER
```

- `caller` = principal đã xác thực từ JWT (`caller.userEmail()`, `caller.tenantCode()`) — identity host-controlled, model không nhìn thấy.
- `getById` (315-329) kiểm tra `isOwner` trước khi trả đơn.
- Frontend đã có sẵn OIDC + `Authorization: Bearer` (`interceptors.ts:16`), token ở `localStorage.access_token`.

Ngược lại, `ChatbotResource.java` là con đường tắt không an toàn (chỉ lọc `tenantId`, shared-secret hardcode).

> **Quyết định thiết kế:** KHÔNG hardening `ChatbotResource`. **Xóa nó**, cho Python agent gọi thẳng
> các endpoint `/api/orders/*` đã xác thực bằng **JWT của user được forward xuống**. Backend tự
> động enforce quyền per-user → hiện thực hóa rule #1 gần như miễn phí.

## 4. Quyết định đã chốt

- **Mục tiêu:** Hardening bảo mật theo brief.
- **Auth:** Dùng JWT của user đang đăng nhập (OIDC access token), forward xuống toàn tuyến.

## 5. Kế hoạch triển khai theo bước

### Bước 1 — Forward JWT của user xuống toàn tuyến *(nền tảng, làm trước — chặn lỗ hổng cross-user)*
- [ ] `chat-widget.tsx:44`: gửi kèm `Authorization: Bearer ${localStorage.access_token}` (lấy giống `interceptors.ts`).
- [ ] FastAPI `/chat`: đọc header `Authorization`, verify chữ ký JWT với cùng OIDC issuer của Quarkus, dựng `RequestContext(email, tenant, roles)`. Từ chối nếu thiếu/không hợp lệ.
- [ ] **Test denial TRƯỚC khi đụng LLM**: `/chat` không token → 401; token org A hỏi đơn org B → rỗng; user A hỏi đơn user B → rỗng.

### Bước 2 — Tool nhận identity vô hình, bỏ mọi tham số danh tính
- [ ] `get_order_status(order_number)` giữ arg semantic; JWT inject từ `RequestContext`, không phải header hardcode.
- [ ] Xóa `X-Tenant-Code`/`X-Chatbot-Key` khỏi `order_ops_agent.py:31-34` và `stats_agent.py:25-28`.
- [ ] Đổi target sang endpoint đã auth (`/api/orders*`) thay vì `/api/chatbot/*`.
- [ ] **Xóa `ChatbotResource.java`** + dòng `chatbot.api-key` trong `application.properties`.

### Bước 3 — Bounded tool loop thay router if/else
- [ ] Gói 3 agent thành tool registry schema cố định + `MAX_STEPS`; mỗi tool_call ↔ đúng 1 tool_result.
- [ ] Guard: reject arg chứa `user/tenant/customer/account` hoặc field danh tính tương đương.
- [ ] Test bằng fake provider (scripted) trước khi nối Gemini thật.
- [ ] 🔴 **BẮT BUỘC (rò rỉ đang sống): sửa `server.py:70`** — handler `/chat` hiện `return {"response": f"❌ Lỗi Server: <pre>{tb}</pre>"}`, in nguyên traceback ra UI. Khi viết lại loop, thay bằng thông báo lỗi generic (không kèm `tb`/stacktrace); log chi tiết chỉ ở server-side. Validator Bước 5 (`output_validator`) sẽ bắt `tech_leak` như lớp lưới thứ hai, nhưng lỗi này phải được sửa tận gốc tại đây.

### Bước 4 — Session server-owned, append-only
- [ ] `/chat` nhận `{ session_id?, message }`, **không nhận history từ client**.
- [ ] Lưu history ở server theo `session_id`, scope theo owner, append-only; bound kích thước.
- [ ] Chặn turn đồng thời (concurrent) thay vì trộn lẫn. Turn lỗi không lưu history dở.

### Bước 5 — Output validation + dọn lỗi lộ thông tin
**Mảng A — validator độc lập ✅ ĐÃ XONG** (`mcp_server/output_validator.py`, `tests/test_output_validator.py`, 20/20 pass)
- [x] Check deterministic: `tech_leak` (traceback/tên file `.py`/`<pre>`), `instruction_leak` (marker hệ thống + `protected_fragments` truyền vào), `action_claim` (self-claim write kiểu "tôi đã hủy/hoàn tiền..." — không bắt nhầm status "đã giao/đã thanh toán"), `pii` (SĐT/email trong reply mà tool result không có).
- [x] `validate_reply(reply, tool_results, protected_fragments) -> ValidationResult`; hằng `SAFE_FALLBACK`. Không đụng model/`server.py`.

**Mảng B — nối vào luồng ⛔ CHỜ Bước 3** (đụng `server.py`/agents dev kia đang viết)
- [ ] `validate_reply` → 1 retry no-tool → `SAFE_FALLBACK`; validate **trước khi stream**.
- [ ] **Bỏ in stack trace ra UI** — xem mục 🔴 bắt buộc ở Bước 3 (`server.py:70`).
- [ ] Chuyển bảo mật PII từ prompt (`order_ops_agent.py:73`) sang backend (đã có `OrderStatusDto` + ownership check).

### Bước 6 — Dọn secret + tài liệu + demo (Definition of Done)
- [ ] Đưa mọi secret ra biến môi trường (`.env`, không commit).
- [ ] README: capabilities, API contract, tool list, threat model; phân biệt structural vs behavioral controls.
- [ ] Demo 3 kịch bản: lookup hợp lệ · chặn cross-user/cross-org · chặn injection từ tool result.
- [ ] Telemetry chỉ log metadata (không log prompt/PII/args/results).

## 6. Acceptance criteria (từ brief — checklist nghiệm thu)
- [ ] User chỉ lấy được dữ liệu thuộc org + quyền của mình.
- [ ] Tool result độc hại không gây truy cập backend trái phép.
- [ ] Tool schema không chứa field danh tính người gọi.
- [ ] Tool schema byte-identical giữa các org có capability khác nhau.
- [ ] Org context vắng mặt trong system block chung.
- [ ] Mỗi tool_call nhận đúng 1 result; arg sai không bao giờ tới application API.
- [ ] Turn đồng thời bị từ chối; turn lỗi không lưu history một phần.

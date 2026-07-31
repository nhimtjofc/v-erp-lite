# Tính năng Cài đặt (Settings) — Tài liệu chức năng chi tiết

Tài liệu mô tả chi tiết từng phần trong tính năng **Cài đặt** của V‑ERP Lite: mô hình dữ liệu, phân quyền, mẫu giao diện chung, và đặc tả từng trang con.

---

## 1. Tổng quan & kiến trúc

Cài đặt là một **nhóm menu (submenu)** trong sidebar `Cài đặt doanh nghiệp`, gồm 6 trang con:

| Trang | Route | Icon | Nhóm dữ liệu |
|---|---|---|---|
| Khu vực (Regional) | `/settings/regional` | 🌐 Globe | `regional` |
| Tiền tệ (Currency) | `/settings/currency` | 🪙 Coins | `currency` |
| Tính năng (Features) | `/settings/features` | 🔘 ToggleRight | `features` |
| Giao tiếp (Communication) | `/settings/communication` | 💬 MessageSquare | `communication` |
| Bảo mật (Security) | `/settings/security` | 🛡️ Shield | `security` |
| Tích hợp (Integrations) | `/settings/integrations` | 🔌 PlugZap | *(WooCommerce, xem §9)* |

> Nguồn: `frontend/src/app/nav-registry.ts` (mục `nav:settings` với `children`).
> Trang con render qua file route trong `frontend/src/routes/_authenticated/settings/*`, giao diện nằm ở `frontend/src/pages/tenant-settings/*`.

### Mô hình cài đặt 3 lớp (defaults ← tenant ← user)

Giá trị người dùng thực sự nhìn thấy = **effective settings**, được backend tính bằng cách **deep‑merge** theo thứ tự ưu tiên tăng dần:

```
Hardcoded defaults   (mặc định hệ thống)
        ⤺ ghi đè bởi
Tenant config        (cấu hình cấp doanh nghiệp — admin/businessOwner chỉnh)
        ⤺ ghi đè bởi
User overrides       (tùy chọn cá nhân của từng user)
        =
Effective settings   (giá trị cuối cùng áp dụng)
```

> Nguồn: `TenantConfigResource.getEffectiveSettings()` — *"deep‑merging hardcoded defaults ← tenant config ← this user's overrides"*.

5 trang con đầu (Regional/Currency/Features/Communication/Security) thao tác trên **lớp Tenant** (cấu hình chung doanh nghiệp). Mỗi trang khi hiển thị đều đọc thêm **effective** để gợi ý "giá trị mặc định đang áp dụng" cho các ô còn để trống.

---

## 2. Phân quyền (RBAC)

| Vai trò | Thấy menu Cài đặt? | Chỉnh sửa tenant settings? |
|---|---|---|
| `admin` | ✅ | ✅ |
| `businessOwner` | ✅ | ✅ |
| `operator` | ✅ | ❌ (chỉ đọc) |
| `customer` | ❌ (bị ẩn) | ❌ |

- Nhóm menu `businessAdmin` giới hạn `roles: ['admin','operator','businessOwner']`; mục Cài đặt còn `hideForRoles: ['customer']`.
- Trong từng trang, biến `isAdmin = role === 'admin' || role === 'businessOwner'` quyết định:
  - Có hiện **Badge "Admin"** cạnh tiêu đề hay không.
  - Có hiện **thanh lưu (Save/Reset)** hay không.
  - Mọi input có bị `disabled` hay không.
- `operator` thấy trang ở chế độ **chỉ đọc**, kèm dải cảnh báo màu hổ phách (`readOnlyBadge`).

**Phân quyền phía backend** (`TenantConfigResource`):

| Endpoint | Method | Roles cho phép |
|---|---|---|
| `/tenant-settings` | GET | admin, businessOwner, operator |
| `/tenant-settings` | PUT | admin, businessOwner |
| `/tenant-settings/effective` | GET | admin, operator, businessOwner, customer |
| `/tenant-settings/user` | GET | tất cả (kể cả customer) |
| `/tenant-settings/user` | PUT | tất cả (kể cả customer) |

> Ngay cả khi UI bị disable, backend vẫn là nơi chốt quyền: PUT tenant chỉ cho admin/businessOwner.

---

## 3. Mẫu giao diện & hành vi chung (5 trang tenant)

Cả 5 trang Regional/Currency/Features/Communication/Security dùng **chung một khuôn**:

1. **Header**: icon + tiêu đề (`sectionXxx`) + Badge "Admin" (nếu là admin).
2. **Read‑only notice**: dải cảnh báo hiện khi **không** phải admin.
3. **Message toast**: banner xanh (thành công) / đỏ (lỗi), **tự ẩn sau 4 giây**.
4. **Form fields**: các ô nhập theo từng nhóm (xem đặc tả bên dưới).
   - Ô trống sẽ hiển thị dòng phụ `Mặc định đang áp dụng: <giá trị effective>` để người dùng biết giá trị kế thừa.
5. **Sticky save bar** (chỉ admin): dính đáy màn hình, gồm nút **Reset** và **Save**.

**Cơ chế state & lưu:**

- Mỗi trang giữ 2 bản: `xxx` (đang chỉnh) và `savedXxx` (bản đã lưu gần nhất).
- `hasChanges = JSON.stringify(xxx) !== JSON.stringify(savedXxx)` → nút Save/Reset chỉ bật khi có thay đổi.
- **Reset**: khôi phục về `savedXxx` (hủy chỉnh sửa chưa lưu).
- **Save**: gọi `PUT /tenant-settings` với body `{ settings: { <nhóm>: <dữ liệu> } }`.
  - Thành công → cập nhật `savedXxx`, hiện toast "Đã lưu".
  - Lỗi → hiện toast "Có lỗi".
- Trong lúc đang gọi API: hiện chữ "Đang lưu…" và disable nút.

> ⚠️ **Ngữ nghĩa full‑replace**: PUT thay thế **toàn bộ** nhóm được gửi. UI chỉ gửi đúng nhóm của trang đang lưu (vd trang Regional chỉ gửi `{ regional }`), nên không đụng các nhóm khác.

---

## 4. Trang **Khu vực** (Regional)

Cấu hình định dạng vùng miền. Nhóm dữ liệu `regional`.

| Trường | Kiểu | Điều khiển | Lựa chọn / Ghi chú |
|---|---|---|---|
| `timezone` | string | Select | 13 múi giờ: UTC, America/*, Europe/*, Asia/Tokyo, Asia/Shanghai, **Asia/Singapore**, Australia/Sydney, Pacific/Auckland… |
| `language` | string | Select | en, **vi (Tiếng Việt)**, ja, zh, ko, de, fr |
| `dateFormat` | string | Select | `YYYY-MM-DD`, `DD/MM/YYYY`, `MM/DD/YYYY`, `DD.MM.YYYY` (kèm ví dụ minh hoạ) |
| `timeFormat` | string | Select | `24h` (14:30) hoặc `12h` (2:30 PM) |

**Hành vi đặc biệt:** khi lưu, nếu có `language`, UI **đổi ngôn ngữ ngay lập tức**:
`localStorage.setItem('language', ...)` + `i18n.changeLanguage(...)`.

> Nguồn: `regional-page.tsx`; hằng số trong `settings-shared.tsx` (`TIMEZONES`, `LANGUAGES`, `DATE_FORMATS`, `TIME_FORMATS`).

---

## 5. Trang **Tiền tệ** (Currency)

Cấu hình tiền tệ & năm tài chính. Nhóm dữ liệu `currency`.

| Trường | Kiểu | Điều khiển | Ghi chú |
|---|---|---|---|
| `code` | string | Select | USD, EUR, GBP, JPY, **VND (₫)**, SGD, AUD |
| `symbol` | string | Input text | Ký hiệu tiền tệ (vd `₫`, `$`). Có thể tự nhập tùy ý |
| `decimalPlaces` | number | Input number | Số chữ số thập phân, **min 0 – max 10** |
| `fiscalYearStart` | string | Input text | Ngày bắt đầu năm tài chính, định dạng **`MM-DD`** (vd `01-01`) |

> Lưu ý: `symbol` là ô tự do — chọn `code` **không** tự điền `symbol` (dù danh sách `CURRENCIES` có sẵn symbol gợi ý). Cần nhập/kiểm tra thủ công.
> Nguồn: `currency-page.tsx`; hằng số `CURRENCIES`.

---

## 6. Trang **Tính năng** (Features)

Bật/tắt các nhóm quyền năng lực cấp doanh nghiệp. Nhóm dữ liệu `features` (mỗi trường là boolean).

| Trường (key) | Ý nghĩa (mô tả i18n) |
|---|---|
| `canManageCustomers` | Quản lý khách hàng |
| `canManageBusiness` | Quản lý thông tin doanh nghiệp |
| `canManageUsers` | Quản lý người dùng |
| `canManageSettings` | Quản lý cài đặt |
| `canManageRoles` | Quản lý vai trò |

**Giao diện:**
- Mỗi tính năng là 1 hàng có **Switch** bật/tắt.
- Ô trống hiển thị `Mặc định đang áp dụng: Bật/Tắt` (từ effective).
- Badge **"Đã thay đổi"** xuất hiện cạnh Switch khi giá trị hiện tại khác bản đã lưu.
- Cuối trang có mục **`<details>` "Tính năng đang áp dụng"** — liệt kê trạng thái effective (Bật/Tắt) của cả 5 tính năng để đối chiếu.

> Nguồn: `features-page.tsx`; `FEATURE_KEYS`, `FEATURE_LABELS`.

---

## 7. Trang **Giao tiếp** (Communication)

Cấu hình mặc định cho email & thông báo. Nhóm dữ liệu `communication`.

| Trường | Kiểu | Điều khiển | Ghi chú |
|---|---|---|---|
| `defaultEmailFrom` | string | Input email | Địa chỉ gửi mặc định, vd `noreply@example.com` |
| `defaultEmailSignature` | string | Textarea (4 dòng) | Chữ ký email mặc định |
| `defaultNotificationChannel` | string | Select | `email` hoặc `in-app` |

> Nguồn: `communication-page.tsx`; `NOTIFICATION_CHANNELS`.

---

## 8. Trang **Bảo mật** (Security)

Chính sách bảo mật cấp doanh nghiệp. Nhóm dữ liệu `security`.

| Trường | Kiểu | Điều khiển | Ràng buộc |
|---|---|---|---|
| `minPasswordLength` | number | Input number | Độ dài mật khẩu tối thiểu, **min 4 – max 128** |
| `sessionTimeoutMinutes` | number | Input number | Thời gian hết phiên (phút), **min 1 – max 1440** |
| `maxLoginAttempts` | number | Input number | Số lần đăng nhập sai tối đa, **min 1 – max 100** |
| `requireMfa` | boolean | Switch | Bắt buộc xác thực đa yếu tố |

> Nguồn: `security-page.tsx`.
> Lưu ý: đây là **giá trị cấu hình/chính sách** lưu trong tenant settings; việc *thực thi* chính sách (khóa tài khoản, timeout phiên, MFA) do các module xác thực tương ứng đảm nhiệm.

---

## 9. Trang **Tích hợp** (Integrations)

Khác 5 trang trên — **không** thuộc mô hình tenant settings 3 lớp, mà là trung tâm tích hợp **WooCommerce**. Nhóm gồm 4 khối:

1. **Bộ chọn kênh (Business/Channel selector)** — `WooChannelSelector`
   - Chọn doanh nghiệp/kênh đồng bộ; lưu lựa chọn app‑wide qua store (`woo-channel.ts`, key `woo_selected_business_id`).
   - Option "Kênh mặc định của tenant" (rỗng = kênh mặc định).
   - Hiện badge trạng thái kết nối theo kênh đang chọn.

2. **Bảng kết nối & sức khỏe WooCommerce** — `WooCommerceIntegrationPanel`
   - Store URL (chỉ đọc).
   - 3 thẻ trạng thái: **Connection** (CONNECTED/CONFIGURED/DISCONNECTED/DISABLED/ERROR/NOT_CONFIGURED), **Health** (UP/DOWN/DEGRADED/UNKNOWN), **Secret** (đã cấu hình hay chưa).
   - Mốc thời gian: lần kiểm tra gần nhất, lần kết nối gần nhất.
   - Đếm: số entity đã map, số webhook event.
   - Nút **Refresh** để tải lại health.

3. **Tóm tắt đồng bộ Catalog** — `useGetCatalogSyncSummaryQuery`
   - Thanh tiến độ % đã đồng bộ; 3 chỉ số: tổng sản phẩm / đã sync / đang chờ.
   - Thời điểm sync gần nhất; nút Refresh.

4. **Liên kết nhanh**: thẻ đi tới **Đơn hàng** (`/orders`) và **Sản phẩm** (`/products`).

> Nguồn: `integrations-page.tsx`, `woocommerce-integration-panel.tsx`, `woo-channel-selector.tsx`, `shared/woo-channel.ts`.

### Nguyên tắc bảo mật secret (bắt buộc)
Frontend **chỉ** được thấy: `secretConfigured` (true/false), trạng thái connection/health, thời điểm kiểm tra/kết nối gần nhất. **Không** để lộ secret/HMAC trong bundle JS, localStorage, HTML, response GET, log hay Git.

---

## 10. Tham chiếu API

| Mục đích | Endpoint | Method |
|---|---|---|
| Đọc cấu hình cấp tenant | `/tenant-settings` | GET |
| Cập nhật cấu hình tenant (full‑replace) | `/tenant-settings` | PUT |
| Đọc **effective** (defaults←tenant←user) | `/tenant-settings/effective` | GET |
| Đọc override cá nhân của user | `/tenant-settings/user` | GET |
| Cập nhật override cá nhân (full‑replace) | `/tenant-settings/user` | PUT |
| Health WooCommerce (theo `businessId`) | *(WooCommerce health API)* | GET |
| Tóm tắt sync Catalog | *(Catalog sync summary API)* | GET |

**Body PUT** (cả tenant lẫn user): `{ "settings": { <nhóm>: { ... } } }` — record `ConfigRequest(Map<String,Object> settings)`.

**Hook frontend tương ứng** (hey‑api sinh tự động): `useGetTenantSettingsQuery`, `useGetEffectiveSettingsQuery`, `usePutTenantSettingsMutation`, `useMeQuery`.

---

## 11. Ghi chú & lưu ý triển khai

- **Toast tự ẩn 4s**: mọi thông báo save biến mất sau 4 giây (`setTimeout`).
- **Full‑replace theo nhóm**: chỉ gửi nhóm của trang đang lưu để tránh ghi đè nhóm khác; nếu về sau gộp nhiều nhóm vào 1 PUT cần cẩn thận merge.
- **`symbol` tiền tệ không auto theo `code`** — cân nhắc cải tiến để đồng bộ.
- **User overrides (`/tenant-settings/user`)** đã có ở backend (mọi role, kể cả customer) nhưng **6 trang UI hiện chỉ thao tác lớp tenant**; lớp user override mới chỉ dùng để tính effective. Đây là điểm mở rộng tiềm năng (trang "Tùy chọn cá nhân").
- **i18n**: nhãn lấy từ namespace `settings` và `common`; các key `settings:woocommerce.*` phục vụ trang Tích hợp — cần đảm bảo đủ ở cả `en` và `vi`.

---

*Cập nhật: 2026‑07‑17. Nguồn code: `frontend/src/pages/tenant-settings/*`, `frontend/src/app/nav-registry.ts`, `src/main/java/base/rest/TenantConfigResource.java`.*

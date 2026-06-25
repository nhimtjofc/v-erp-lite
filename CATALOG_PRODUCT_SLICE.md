# Catalog/Product Vertical Slice

## Mục tiêu

Slice này phát triển 2 mục trong Catalog Management:

- **Tạo và cấu hình sản phẩm:** Tên, mô tả chi tiết, image URL, SKU, trạng thái active.
- **Định giá:** Base price, sale price, sale start/end time.

**Phạm vi hiện tại:** Staff portal cho tenant staff (`admin`, `operator`, `businessOwner`). Storefront/customer mua hàng sẽ làm sau khi catalog ổn định.

## Đã làm

### Backend:

- Thêm migration `V9__catalog_tables.sql`.
- Thêm bảng `categories`.
- Thêm bảng `products`.
- Mỗi bảng có `tenant_id`, audit fields, `version`.
- Bảng `products` có: `sku`, `name`, `description`, `image_url`, `base_price`, `sale_price`, `sale_starts_at`, `sale_ends_at`, `is_active`.
- Thêm entity `Category`.
- Thêm entity `Product`.
- Thêm `CategoryResource` với CRUD và search theo tên.
- Thêm `ProductResource` với CRUD, search theo name/SKU, filter theo category.
- API có `@RolesAllowed({"admin", "operator", "businessOwner"})`.
- API luôn filter theo `caller.tenantCode()`.
- Khi gán category cho product, backend check category thuộc đúng tenant.

### Frontend:

- Thêm API client mong `frontend/src/shared/api/catalog.ts`.
- Thêm trang list product `/products`.
- Thêm form tạo product `/products/new`.
- Thêm form sửa/xóa product `/products/$id`.
- Thêm category mini-management trên trang Products để tạo/list category.
- Thêm Products vào sidebar navigation.
- Thêm i18n key `nav:products`.
- Router tree đã được generate lại qua frontend build.

## File nên stage cho commit này

Dùng danh sách này để tránh stage nhầm các file dirty khác trong worktree:

```powershell
git add CATALOG_PRODUCT_SLICE.md
git add src/main/resources/db/migration/V9__catalog_tables.sql
git add src/main/java/base/entity/Category.java
git add src/main/java/base/entity/Product.java
git add src/main/java/base/rest/CategoryResource.java
git add src/main/java/base/rest/ProductResource.java
git add frontend/src/shared/api/catalog.ts
git add frontend/src/pages/products-page.tsx
git add frontend/src/pages/product-form-page.tsx
git add frontend/src/routes/_authenticated/products.tsx
git add frontend/src/routes/_authenticated/products/index.tsx
git add frontend/src/routes/_authenticated/products/new.tsx
git add frontend/src/routes/_authenticated/products/\$id.tsx
git add frontend/src/app/nav-registry.ts
git add frontend/src/routeTree.gen.ts
git add src/main/resources/i18n/en/nav.json
git add src/main/resources/i18n/vi/nav.json
```

**Lưu ý:** Không stage các file generated SDK/OpenAPI hoặc file config đang dirty nếu không định đưa chúng vào commit này.

**Commit message:**
```text
feat: add catalog product management slice
```

## Cách test nhanh

### Bước 1: Compile backend

```powershell
.\gradlew.bat compileJava
```

**Kỳ vọng:** `BUILD SUCCESSFUL`.

### Bước 2: Build frontend

```powershell
cd frontend
npm.cmd run build
```

**Kỳ vọng:** TypeScript và Vite build pass.

### Bước 3: Chạy infrastructure

Từ root repo:

```powershell
docker compose up -d mariadb keycloak
```

Chờ MariaDB/Keycloak healthy. Nếu cần xem log:

```powershell
docker compose logs -f mariadb keycloak
```

### Bước 4: Chạy backend

Từ root repo:

```powershell
.\gradlew.bat quarkusDev
```

**Kỳ vọng:**
- Backend lắng nghe `http://localhost:8081/api`.
- Flyway chạy migration `V9__catalog_tables.sql` thành công.
- Nếu Keycloak chưa sẵn sàng, có thể sync Keycloak sau bằng endpoint sync/admin hiện có.

### Bước 5: Chạy frontend

Từ `frontend/`:

```powershell
npm.cmd run dev -- --host localhost --port 5174 --strictPort
```

Mở:

```text
http://localhost:5174/
```

Không dùng `127.0.0.1` nếu Keycloak chưa allow redirect URI đó.

### Bước 6: Test thủ công trên UI

Đăng nhập bằng account dev:

- **Email:** `admin@v-erp.com`
- **Password:** `password`

**Checklist kiểm tra:**

- [ ] Sidebar có menu `Sản phẩm`.
- [ ] Vào `/products`, trang hiển thị tiếng Việt với tiêu đề `Sản phẩm`.
- [ ] Tạo danh mục mới ở panel `Danh mục`.
- [ ] Bấm `Sản phẩm mới` để mở form tạo sản phẩm.
- [ ] Nhập đầy đủ các trường trên form:
  - SKU
  - Tên
  - Mô tả
  - URL hình ảnh
  - Danh mục
  - Giá gốc
  - Giá khuyến mãi
  - Bắt đầu khuyến mãi
  - Kết thúc khuyến mãi
  - Hoạt động
- [ ] Bấm `Lưu sản phẩm`, tạo thành công và redirect sang trang `Sửa sản phẩm`.
- [ ] Quay lại `/products`, sản phẩm hiển thị trong bảng.
- [ ] Tìm kiếm theo tên sản phẩm hoặc SKU bằng ô `Tìm theo sản phẩm hoặc SKU...`.
- [ ] Lọc theo danh mục bằng dropdown `Tất cả danh mục`.
- [ ] Sửa `Giá gốc` hoặc `Giá khuyến mãi`, rồi bấm `Lưu sản phẩm` để lưu lại.
- [ ] Bỏ trống SKU/Tên/Giá gốc, form phải hiện lỗi tiếng Việt: `Vui lòng nhập trường này.`
- [ ] Nhập giá âm, form phải hiện lỗi tiếng Việt: `Giá trị phải lớn hơn hoặc bằng 0.`
- [ ] Nhập `Kết thúc khuyến mãi` trước `Bắt đầu khuyến mãi`, form phải hiện lỗi tiếng Việt: `Thời gian kết thúc khuyến mãi phải sau thời gian bắt đầu.`
- [ ] Bấm `Xóa`, xác nhận xóa, sản phẩm biến mất khỏi danh sách.

### Bước 7: Kiểm tra database

Từ root repo:

```powershell
docker compose exec mariadb mariadb -uv-erp -pv-erp-secret v-erp-lite -e "SELECT tenant_id, name, is_active FROM categories;"
```

```powershell
docker compose exec mariadb mariadb -uv-erp -pv-erp-secret v-erp-lite -e "SELECT tenant_id, sku, name, base_price, sale_price, sale_starts_at, sale_ends_at, is_active FROM products;"
```

**Cần thấy:**
- `tenant_id` đúng tenant hiện tại, ví dụ `v-erp-app`.
- `base_price`, `sale_price`, sale time lưu đúng.
- `sku` unique theo tenant.




# Hướng dẫn chạy WordPress + WooCommerce ở local

Docker Compose sẽ tự động cài WordPress và kích hoạt đúng phiên bản WooCommerce đã được cố định. WordPress chạy tại `http://localhost:8090`; các container gọi V-ERP backend trên máy host qua `http://host.docker.internal:8081/api`.

## 1. Cấu hình secret HMAC

Mở PowerShell sẽ dùng để chạy cả Docker Compose và Quarkus, sau đó khai báo secret local:

```powershell
$env:WOOCOMMERCE_WEBHOOK_SECRETS_LOCAL = '<gia-tri-ngau-nhien-chi-dung-local>'
```

Không đưa giá trị này vào Git, JavaScript bundle, `localStorage`, biến môi trường frontend, HTML, log hoặc output của lệnh.

Hậu tố `LOCAL` tương ứng với `secret_reference=local` của sales channel trong môi trường dev. Mỗi channel reference phải có một biến môi trường server-side riêng; hệ thống cố ý không có secret fallback dùng chung toàn tenant.

## 2. Khởi động hệ thống

Từ thư mục root repo, khởi động hạ tầng:

```powershell
docker compose up -d
```

Khởi động backend:

```powershell
.\gradlew.bat quarkusDev
```

Mở terminal khác và khởi động frontend:

```powershell
cd frontend
npm run dev
```

Các địa chỉ cần dùng:

- Integration Settings: `http://localhost:5174/settings/regional`
- WordPress/WooCommerce: `http://localhost:8090`
- Trang quản trị WordPress: `http://localhost:8090/wp-admin`
- V-ERP backend: `http://localhost:8081/api`

## 3. Kiểm tra container và plugin

```powershell
docker compose ps --all
docker compose run --rm --entrypoint wp wordpress-setup plugin is-active woocommerce --path=/var/www/html
docker compose exec wordpress php -r "echo file_get_contents('http://host.docker.internal:8081/api/health');"
```

Kết quả mong đợi:

- `mariadb`, `wordpress-db` và `wordpress` ở trạng thái healthy.
- `wordpress-setup` kết thúc với `Exited (0)`.
- Lệnh `plugin is-active` thoát với mã 0.
- Backend health trả JSON chứa `"status":"UP"`.

## 4. Kiểm tra trên giao diện

Mở `http://localhost:8090/wp-admin/plugins.php` → đăng nhập bằng tài khoản local mặc định `admin` / `password` (hoặc giá trị `WORDPRESS_ADMIN_*` đã override) → nhìn thấy WooCommerce ở trạng thái **Active/Đang hoạt động**.

Mở `http://localhost:5174/settings/regional` → đăng nhập V-ERP → bấm **Kiểm tra lại** trong card WooCommerce → nhìn thấy store URL, health, trạng thái kết nối và `secretConfigured` dưới dạng đã/chưa cấu hình; không nhìn thấy secret thật.

## 5. Dừng hệ thống

```powershell
docker compose down
```

> **Cảnh báo:** `docker compose down -v` sẽ xóa cả dữ liệu WordPress và volume MariaDB hiện có của V-ERP. Chỉ dùng khi chủ động muốn reset toàn bộ dữ liệu local.

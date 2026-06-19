# Hướng Dẫn Chạy Hệ Thống Fullstack (V-ERP Lite — MariaDB & Zustand Edition)

Tài liệu này hướng dẫn chi tiết cách khởi động toàn bộ hệ thống fullstack bao gồm cơ sở dữ liệu MariaDB, Keycloak (Identity Provider), Quarkus Backend và React Vite Frontend.

## Điều kiện tiên quyết
- **Java**: JDK 25 (Khuyên dùng OpenJDK Temurin 25).
- **Node.js**: Phiên bản mới nhất hỗ trợ NPM.
- **Docker Desktop**: Đã cài đặt trên máy.

---

## Các bước thực hiện

### Bước 1: Khởi động Docker & Dịch vụ Cơ sở hạ tầng (MariaDB & Keycloak)
1. Mở ứng dụng **Docker Desktop** trên máy của bạn và đợi cho Docker Daemon khởi động hoàn tất.
2. Mở terminal tại thư mục gốc của dự án (`v-erp-lite-mariadb-zustand`) và chạy lệnh sau để khởi động MariaDB và Keycloak ở chế độ nền:
   ```bash
   docker compose up -d
   ```
3. Đợi cho đến khi các container chuyển sang trạng thái `Healthy` hoặc `Started`. MariaDB sẽ khởi chạy trên cổng **3307** để tránh xung đột với MariaDB cài đặt local nếu có.

### Bước 2: Khởi chạy Quarkus Backend
1. Đảm bảo cổng **8081** hiện đang trống. Nếu cổng này bị chiếm dụng bởi tiến trình Java chạy ẩn từ trước, bạn có thể giải phóng cổng bằng lệnh PowerShell sau:
   ```powershell
   Stop-Process -Id (Get-NetTCPConnection -LocalPort 8081).OwningProcess -Force
   ```
2. Chạy lệnh Gradle để khởi động Backend ở chế độ phát triển (Dev Mode):
   ```bash
   # Windows PowerShell / CMD
   .\gradlew quarkusDev
   
   # Linux / macOS
   ./gradlew quarkusDev
   ```
3. Đợi cho đến khi terminal hiển thị thông báo Quarkus đã sẵn sàng lắng nghe tại `http://0.0.0.0:8081`. Quá trình di chuyển (migration) cơ sở dữ liệu Flyway sẽ tự động chạy trên cơ sở dữ liệu MariaDB mới.

### Bước 3: Khởi chạy React Frontend (Tích hợp Zustand)
1. Mở một terminal mới, di chuyển vào thư mục `frontend` của dự án:
   ```bash
   cd frontend
   ```
2. Cài đặt các thư viện cần thiết bao gồm `zustand` (nếu chưa cài):
   ```bash
   npm install
   ```
3. Khởi chạy máy chủ phát triển của Vite:
   ```bash
   npm run dev
   ```
4. Frontend sẽ sẵn sàng hoạt động tại địa chỉ: `http://localhost:5174/`

---

## Đăng nhập & Sử dụng hệ thống

- **Truy cập ứng dụng**: Mở trình duyệt và truy cập `http://localhost:5174/`. Hệ thống sẽ tự động chuyển hướng sang Keycloak SSO thông qua Zustand Auth Store để đăng nhập.
- **Tài khoản đăng nhập Demo**:
  - Email: `admin@v-erp.com`
  - Mật khẩu: `password`
- **Keycloak Admin Console**: Truy cập `http://localhost:8181/`
  - Tài khoản quản trị: `admin`
  - Mật khẩu quản trị: `admin`

-----

# Fullstack Run Guide (V-ERP Lite — MariaDB & Zustand Edition)

This document provides step-by-step instructions on how to start the entire fullstack system, including the MariaDB database, Keycloak (Identity Provider), Quarkus Backend, and React Vite Frontend.

## Prerequisites
- **Java**: JDK 25 (OpenJDK Temurin 25 is recommended).
- **Node.js**: Latest version with NPM support.
- **Docker Desktop**: Installed and running on your system.

---

## Step-by-Step Instructions

### Step 1: Start Docker & Infrastructure Services (MariaDB & Keycloak)
1. Open the **Docker Desktop** application on your machine and wait for the Docker Daemon to fully initialize.
2. Open a terminal at the project root directory (`v-erp-lite-mariadb-zustand`) and run the following command to start MariaDB and Keycloak in the background:
   ```bash
   docker compose up -d
   ```
3. Wait until both containers are running and marked as `Healthy` or `Started`. MariaDB will expose port **3307** on the host.

### Step 2: Launch Quarkus Backend
1. Ensure port **8081** is free. If the port is occupied by a stale Java process from a previous run, you can release it using the following PowerShell command:
   ```powershell
   Stop-Process -Id (Get-NetTCPConnection -LocalPort 8081).OwningProcess -Force
   ```
2. Start the Backend in Dev Mode using Gradle:
   ```bash
   # Windows PowerShell / CMD
   .\gradlew quarkusDev
   
   # Linux / macOS
   ./gradlew quarkusDev
   ```
3. Wait until the terminal output shows Quarkus has successfully started and is listening on `http://0.0.0.0:8081`. Flyway migrations will run automatically on the new MariaDB database.

### Step 3: Launch React Frontend (Zustand Integrated)
1. Open a new terminal window and change directory to the `frontend` folder:
   ```bash
   cd frontend
   ```
2. Install the node packages including `zustand` (if not already done):
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
4. The frontend will be ready and running at: `http://localhost:5174/`

---

## Credentials & Access Info

- **Access the Application**: Open your browser and navigate to `http://localhost:5174/`. The app will automatically redirect you to Keycloak SSO via the global Zustand Auth Store for login.
- **Seeded User Account**:
  - Email: `admin@v-erp.com`
  - Password: `password`
- **Keycloak Admin Console**: Navigate to `http://localhost:8181/`
  - Username: `admin`
  - Password: `admin`

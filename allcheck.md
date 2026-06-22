# Hướng Dẫn Đọc Hiểu & Gỡ Lỗi Source Code (V-ERP Lite)

Tài liệu này phân tích cấu trúc, luồng hoạt động và phương pháp gỡ lỗi chi tiết của dự án **V-ERP Lite** (Bản chạy MariaDB và Zustand).

---

## 1. Bức Tranh Tổng Thể (High-Level Architecture)

Dự án là một hệ thống ERP đơn giản hóa (ERP Lite) được tổ chức theo mô hình **Client-Server độc lập** (Monorepo chứa 2 phần riêng biệt):

```text
v-erp-lite-mariadb-zustand/
├── docker-compose.yml       # Cấu hình container MariaDB (cổng 3307) & Keycloak (cổng 8181)
├── build.gradle             # Cấu hình dự án Quarkus Backend & các dependencies Java
├── src/main/                # Mã nguồn Quarkus Backend (Java 25)
│   ├── java/base/           # Package gốc của Backend
│   │   ├── entity/          # Các JPA Entities (Ánh xạ cơ sở dữ liệu thông qua Hibernate Panache)
│   │   ├── rest/            # Các JAX-RS Resources (Định nghĩa API REST endpoints)
│   │   ├── security/        # Keycloak OIDC Authentication & Tenant Resolver
│   │   └── service/         # Các dịch vụ xử lý logic (ví dụ: KeycloakSyncService)
│   └── resources/           # Cấu hình backend & database migrations
│       ├── application.properties  # File cấu hình chính của Quarkus
│       └── db/migration/    # Thư mục Flyway migrations (tạo và cập nhật cấu trúc cơ sở dữ liệu)
└── frontend/                # Mã nguồn React Frontend (React 19 + Vite 7 + TS)
    ├── package.json         # Danh sách thư viện frontend (Zustand, Tanstack, Base UI, Hey API)
    └── src/                 # Mã nguồn React
        ├── app/             # Entrypoint providers & Layouts
        ├── routes/          # TanStack Router file-based routing
        ├── pages/           # Các component trang chính
        └── shared/          # API generated, Zustand stores, hooks, UI primitives
```

### Các công nghệ và thư viện cốt lõi sử dụng:
- **Backend**:
  - **Quarkus (v3.33.1.1)**: Java framework hiệu năng cao, hỗ trợ Dev Mode live reload cực nhanh.
  - **Hibernate ORM with Panache**: Đơn giản hóa làm việc với DB qua Active Record pattern (các Entity kế thừa `PanacheEntityBase`).
  - **Flyway**: Quản lý phiên bản và migrate database tự động khi ứng dụng khởi chạy.
  - **Keycloak OIDC**: Bảo mật API endpoints với JWT tokens thông qua extension `quarkus-oidc`.
- **Frontend**:
  - **React 19 + TypeScript + Vite 7**: Môi trường phát triển và render phía client.
  - **Zustand**: Quản lý trạng thái toàn cục (Theme và Auth user) nhẹ nhàng, tối giản.
  - **TanStack Router**: File-based router cho React (quản lý route tự động thông qua cấu trúc file trong `src/routes`).
  - **TanStack Query**: Quản lý server state, caching, và đồng bộ dữ liệu.
  - **Hey API (openapi-ts)**: Tự động biên dịch đặc tả OpenAPI (`openapi-spec.yaml`) thành bộ SDK client (bao gồm đầy đủ kiểu dữ liệu TypeScript và các React Query hooks).

---

## 2. Điểm Bắt Đầu (Entry Point) của Chương Trình

Hiểu được ứng dụng bắt đầu từ đâu sẽ giúp bạn dễ dàng lần mò dấu vết (trace) khi hệ thống gặp lỗi hoặc khi cần mở rộng tính năng.

### A. Frontend (React Client)

Luồng khởi động của frontend diễn ra tuần tự qua các file sau:

1. **`frontend/index.html`**:
   - Đây là trang HTML duy nhất được Vite phục vụ khi trình duyệt truy cập `http://localhost:5174/`.
   - Điểm mấu chốt nằm ở dòng định nghĩa thẻ div chứa ứng dụng: `<div id="root"></div>` và liên kết script chạy mã nguồn: `<script type="module" src="/src/main.tsx"></script>`.

2. **[main.tsx](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/frontend/src/main.tsx)**:
   - File này nạp toàn bộ CSS (`index.css`), bộ dịch đa ngôn ngữ i18n, và đặc biệt là import `shared/api/interceptors` để cấu hình xử lý tự động đính kèm token hoặc làm mới token khi nhận lỗi 401.
   - Nhiệm vụ chính là gọi `ReactDOM.createRoot` để gắn (mount) ứng dụng React vào thẻ `<div id="root">` trong index.html, sau đó render component `<Providers />`.

3. **[providers.tsx](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/frontend/src/app/providers.tsx)**:
   - Cung cấp môi trường dữ liệu và định tuyến toàn cục:
     - **`<QueryClientProvider>` (TanStack Query)**: Quản lý bộ đệm (cache) dữ liệu tải từ server, cấu hình tự động thử lại (retry) và thời gian dữ liệu hết hạn (`staleTime: 30s`).
     - **`<RouterProvider>` (TanStack Router)**: Quản lý điều hướng URL.
   - **Tự động định tuyến (File-based routing)**: TanStack Router quét toàn bộ thư mục `frontend/src/routes/` và tự động sinh ra sơ đồ cây định tuyến trong file [routeTree.gen.ts](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/frontend/src/routeTree.gen.ts). Mỗi file trong `src/routes` tương ứng với một URL trên trình duyệt (ví dụ: `src/routes/_authenticated/customers/index.tsx` quản lý giao diện danh sách khách hàng).

4. **[__root.tsx](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/frontend/src/routes/__root.tsx)**:
   - Component cha ngoài cùng của hệ thống định tuyến, chứa thẻ `<Outlet />`. Thẻ này đóng vai trò như một placeholder động, nơi các component con của trang hiện tại (dashboard, profile, customers...) sẽ được hiển thị vào đó dựa theo URL hiện tại trên trình duyệt.

---

### B. Backend (Quarkus Server)

Khác với các ứng dụng Java truyền thống sử dụng hàm `public static void main()`, Quarkus hoạt động dựa trên cơ chế nạp thành phần động (IoC Container) và máy chủ HTTP hiệu năng cao xây dựng trên nền tảng **Netty/Vert.x**:

1. **Khởi Động & Lắng Nghe Cổng**:
   - Khi chạy lệnh `.\gradlew quarkusDev`, Quarkus bootstrap và đọc file cấu hình [application.properties](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/src/main/resources/application.properties).
   - Máy chủ HTTP mở cổng **8081** để tiếp nhận các cuộc gọi API từ frontend.

2. **Quét Annotation & Đón Nhận Request**:
   - Lớp nhận dữ liệu API đầu tiên là các **Resource class** nằm trong package `base.rest.*` được đánh dấu bằng `@Path` của Jakarta RESTful Web Services (JAX-RS).
   - Ví dụ: Yêu cầu API gửi tới `GET /api/customers` sẽ được tự động định tuyến đến phương thức `list()` của [CustomerResource.java](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/src/main/java/base/rest/CustomerResource.java) nhờ cấu hình `@Path("/customers")` ở cấp class và `@GET` ở cấp method.

3. **Vòng đời khởi động (Startup Lifecycle hooks)**:
   - Các class được đánh dấu `@Startup` (ví dụ: [KeycloakSyncService.java](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/src/main/java/base/service/KeycloakSyncService.java)) sẽ lập tức được khởi tạo ngay khi server bật lên.
   - Bạn cũng có thể bắt các sự kiện khởi động bằng cách lắng nghe sự kiện `StartupEvent`:
     ```java
     void onStart(@Observes StartupEvent ev) {
         // Logic khởi tạo chạy trước khi server chính thức đón nhận HTTP request
     }
     ```

---

## 3. Theo Dõi Luồng Thực Thi (Execution Flow Tracing)

Để hiểu sâu sắc cơ chế hoạt động, chúng ta hãy trace chi tiết **Luồng Tạo Mới Khách Hàng (Create Customer)** đi từ giao diện người dùng qua mạng, vào logic nghiệp vụ của Java và lưu xuống MariaDB:

### Giai đoạn 1: Kích hoạt Hành động trên Giao diện (UI Form Submit)
- Người dùng truy cập trang tạo mới khách hàng và điền thông tin. Mọi thay đổi trên ô nhập liệu được đồng bộ vào React State `form` thông qua hàm `handleChange` trong [customer-form-page.tsx](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/frontend/src/pages/customer-form-page.tsx).
- Khi nhấn nút "Create Customer", sự kiện `onSubmit` gọi hàm `handleSubmit(e)`.
- Hàm `handleSubmit` kích hoạt cuộc gọi API bất đồng bộ:
  ```typescript
  const customer = await createMutation.mutateAsync({ body: form })
  ```
  `createMutation` ở đây được sinh ra từ React Query hook `useCreate2Mutation()`. Hook này bọc bên trong một cuộc gọi API định nghĩa tự động của Hey API, chịu trách nhiệm thực hiện một HTTP request.

### Giai đoạn 2: Xử lý Phía Client & Đính kèm Xác thực (Interceptor & Network)
- Trước khi gói tin HTTP rời trình duyệt, **API Interceptor** (`frontend/src/shared/api/interceptors.ts`) sẽ can thiệp:
  - Nó đọc token hiện tại từ Zustand Auth Store `useAuthStore` (hoặc trực tiếp từ `localStorage` khóa `access_token`).
  - Đính kèm token này vào HTTP Header: `Authorization: Bearer <access_token>`.
- Gói tin HTTP POST mang payload JSON (chứa thông tin tên, email, điện thoại khách hàng) được gửi tới URL: `/api/customers`.
- *Lưu ý*: Trong môi trường phát triển (dev environment), Vite dev server (cổng 5174) nhận request và proxy nó sang Quarkus backend (cổng 8081), hoặc gọi trực tiếp tới cổng 8081 nếu biến môi trường `VITE_API_BASE_URL` trỏ tới `http://localhost:8081`.

### Giai đoạn 3: Tiếp nhận Request & Kiểm tra Bảo mật ở Backend (REST Endpoint)
- Gói tin mạng đến cổng 8081 của Quarkus.
- Bộ lọc bảo mật của Quarkus OIDC (`quarkus-oidc`) chặn request để xác thực:
  - Giải mã và kiểm tra chữ ký số của JWT token bằng cách liên hệ với Keycloak (chạy ở cổng 8181).
  - Trích xuất các vai trò (roles) của người dùng từ token.
- Lớp `CustomerResource` kiểm tra phân quyền thông qua annotation `@RolesAllowed({"admin", "operator", "businessOwner"})`. Nếu người dùng có quyền hợp lệ, request được đi tiếp.
- `CallerContext` (được inject thông qua `@Inject CallerContext caller`) giải mã thông tin tenant từ token JWT để lấy ra `tenantCode` và `userId` của tài khoản đang đăng nhập.

### Giai đoạn 4: Thực thi Logic Nghiệp vụ & Giao dịch Database (JPA Transaction & Active Record)
- Phương thức `create(Customer customer)` trong [CustomerResource.java](file:///d:/v-erp-lite-khoa%20%281%29/v-erp-lite-mariadb-zustand/src/main/java/base/rest/CustomerResource.java) được chạy:
  ```java
  @POST
  @Transactional
  public Response create(Customer customer) {
      customer.setId(UUID.randomUUID());              // Tạo UUID ngẫu nhiên cho khách hàng
      customer.setTenantId(caller.tenantCode());      // Gán tenantId lấy từ token bảo mật
      customer.setCreatedBy(caller.userId());         // Ghi nhận ID người thực hiện
      customer.persist();                             // Lưu trữ xuống DB
      ...
  }
  ```
  - **`@Transactional`**: Annotation này định nghĩa ranh giới giao dịch (Database Transaction). Nếu có bất kỳ lỗi Runtime Exception nào xảy ra trong hàm này, toàn bộ các thao tác ghi vào DB sẽ bị hủy bỏ (Rollback). Nếu thành công, giao dịch sẽ được commit.
  - **`customer.persist()`**: Nhờ Hibernate Panache (Active Record pattern), đối tượng `Customer` tự tích hợp các hàm làm việc với DB. Lệnh `persist()` yêu cầu Hibernate biên dịch entity này thành câu lệnh SQL INSERT:
    ```sql
    INSERT INTO customer (id, tenant_id, first_name, last_name, email, phone, address, created_at, created_by, version)
    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, 0);
    ```
  - Giao dịch được commit và SQL INSERT được thực thi trên cơ sở dữ liệu **MariaDB** chạy tại cổng **3307**.

### Giai đoạn 5: Phản hồi Phía Client & Đồng bộ Trạng thái (UI Invalidation & Navigation)
- Backend trả về HTTP `201 Created` kèm theo dữ liệu khách hàng vừa tạo dưới dạng JSON (bao gồm cả ID mới sinh).
- Phía Frontend, sau khi nhận kết quả thành công từ `await createMutation.mutateAsync(...)`:
  - Gọi `queryClient.invalidateQueries({ queryKey: ['list2'] })`: Yêu cầu React Query đánh dấu dữ liệu danh sách khách hàng cũ trong cache là lỗi thời (stale), kích hoạt việc gọi lại API tải danh sách để cập nhật giao diện mới nhất.
  - Gọi `router.navigate({ to: '/customers/$id', params: { id: customer.id } })`: Chuyển hướng người dùng sang trang hiển thị chi tiết khách hàng vừa tạo.

---

## 4. Chạy Thử và Gỡ Lỗi (Dynamic Analysis & Debugging)

Để xem code thực sự chạy thế nào ở runtime, chúng ta có thể áp dụng các kỹ thuật debug sau:

### A. Gỡ lỗi Backend (Quarkus Java)
1. **Debugger Port (5005)**:
   - Khi chạy backend ở chế độ phát triển bằng lệnh `.\gradlew quarkusDev`, Quarkus tự động mở một cổng gỡ lỗi Java Debug Wire Protocol (JDWP) tại **cổng 5005** (nếu không bị trùng cổng).
   - Bạn chỉ cần mở VS Code / IntelliJ IDEA, cấu hình một trình debug **Remote JVM Debug** kết nối tới `localhost:5005` và đặt các điểm dừng (breakpoints) tại các file Resource (ví dụ: dòng 50 của `CustomerResource.java`).
2. **In log ra Terminal**:
   - Sử dụng thư viện logging chuẩn đã được cài đặt sẵn. Ví dụ trong Java:
     ```java
     import org.jboss.logging.Logger;
     
     public class CustomerResource {
         private static final Logger LOG = Logger.getLogger(CustomerResource.class);
         
         @POST
         public Response create(Customer customer) {
             LOG.infof("Đang tạo khách hàng mới với email: %s", customer.getEmail());
             ...
         }
     }
     ```
   - Log này sẽ hiển thị trực tiếp trên terminal đang chạy Quarkus Dev Mode.

### B. Gỡ lỗi Frontend (React Client)
1. **React Developer Tools / Console Logs**:
   - Sử dụng các lệnh log tiêu chuẩn trong component:
     ```typescript
     console.log('Form data trước khi submit:', form);
     ```
   - Sử dụng tab **Console** và **Sources** trong Chrome DevTools để đặt breakpoint dòng code Javascript trong trình duyệt.
2. **React Query Devtools**:
   - Thư viện TanStack Query lưu trữ server state trong bộ nhớ đệm. Bạn có thể kiểm tra trạng thái cache, thời gian hết hạn (stale), và các câu query đang chạy bằng cách thêm component devtools hoặc xem các hoạt động mạng trong tab **Network** của DevTools.

# Hướng dẫn Showcase Storefront và Thanh toán — V-ERP Lite

Tài liệu này dùng để trình diễn phiên bản hiện tại của nhánh `develop`. Các luồng đã hoàn thiện trên giao diện và các phần cần minh họa qua API được tách rõ để nội dung trình bày chính xác.

## 1. Kịch bản tổng thể

Câu chuyện nên trình bày theo thứ tự sau:

1. Khách hàng duyệt storefront V-ERP, chọn sản phẩm/biến thể, thu thập voucher và checkout.
2. Backend tự tính lại giá, giảm giá, phí vận chuyển, thuế và tồn kho; sau đó giữ tồn kho và tạo đơn hàng.
3. API thanh toán tạo `PaymentIntent` có idempotency và hoàn tất thanh toán qua mock gateway.
4. Thanh toán thành công tạo khoản escrow cho người bán và ghi nhận hoa hồng nền tảng.
5. Nhân viên xử lý đơn theo luồng `PENDING -> CONFIRMED -> SHIPPED -> COMPLETED`.
6. Khi đơn hoàn tất, tiền escrow được chuyển từ số dư chờ sang số dư khả dụng của người bán.
7. Người bán gửi yêu cầu rút tiền; quản trị viên phê duyệt hoặc từ chối.
8. Phần tích hợp WooCommerce thể hiện health check, trạng thái đồng bộ catalog, nhận đơn bằng HMAC, mapping, reconcile và chống xử lý webhook trùng lặp.

Lưu ý quan trọng về phạm vi: storefront React đã có luồng thanh toán dành cho người mua, gồm chọn phương thức, tạo `PaymentIntent`, QR mock, polling trạng thái, kết quả thành công/thất bại và thử lại. Gateway hiện vẫn là mock phục vụ demo, chỉ được backend bật trong profile dev/test và mặc định tắt ở production; chưa kết nối nhà cung cấp tài chính thật. WordPress/WooCommerce local và adapter phía ERP đã được triển khai, nhưng nhánh hiện tại chưa có custom WordPress plugin để tự động đưa sản phẩm ERP lên storefront WooCommerce.

## 2. Địa chỉ và tài khoản

| Mục đích | Địa chỉ | Tài khoản |
|---|---|---|
| Ứng dụng V-ERP | `http://localhost:5174` | Xem danh sách bên dưới |
| Backend health | `http://localhost:8081/api/health` | Không cần đăng nhập |
| OpenAPI/Swagger UI | `http://localhost:8081/api/q/swagger-ui` | API bảo vệ cần Bearer token |
| Keycloak | `http://localhost:8181` | Admin console: `admin` / `admin` |
| WordPress/WooCommerce | `http://localhost:8090` | `admin` / `password` |

Các tài khoản V-ERP được seed đều dùng mật khẩu `password` sau khi đồng bộ lúc khởi động:

| Tài khoản | Vai trò | Phần demo phù hợp |
|---|---|---|
| `admin@v-erp.com` | Admin | Storefront, sản phẩm, đơn hàng, voucher, duyệt payout |
| `operator@v-erp.com` | Operator | Xử lý đơn hàng |
| `owner@v-erp.com` | Chủ doanh nghiệp | Cài đặt WooCommerce, ví người bán, yêu cầu payout |

Có thể tạo tài khoản người mua từ tab **Đăng ký**. Nên dùng email dễ nhớ như `showcase.buyer@v-erp.com` và mật khẩu ít nhất tám ký tự.

## 3. Chuẩn bị từ tối hôm trước

Thực hiện trước ngày thuyết trình:

1. Merge đúng nhánh và kiểm tra commit sẽ trình diễn.
2. Chạy toàn bộ hệ thống một lần để Docker image và dependency đã được tải sẵn.
3. Tạo tài khoản người mua và xác nhận đăng nhập thành công.
4. Diễn tập một luồng tạo đơn và thanh toán hoàn chỉnh.
5. Tắt chế độ sleep của laptop và chuẩn bị sẵn các cửa sổ terminal.

Các lệnh kiểm tra:

```powershell
git status --short --branch
.\gradlew.bat test
cd frontend
npm.cmd run build
```

Kết quả mong đợi: worktree sạch, backend báo `BUILD SUCCESSFUL`, frontend build thành công.

### Tùy chọn reset dữ liệu sạch

Chỉ thực hiện từ tối hôm trước nếu dữ liệu demo hiện tại bị lỗi hoặc quá lộn xộn. Lệnh này xóa vĩnh viễn volume MariaDB và WordPress local:

```powershell
docker compose down -v
docker compose up -d
```

Không reset volume ngay trước buổi trình bày vì WordPress setup và Docker image có thể mất thời gian.

## 4. Khởi động hệ thống

Mở Docker Desktop trước, sau đó dùng ba cửa sổ PowerShell.

### Terminal 1 — Hạ tầng

Chạy từ thư mục gốc repository:

```powershell
$env:WOOCOMMERCE_WEBHOOK_SECRETS_LOCAL = 'showcase-local-only-change-me'
docker compose up -d
docker compose ps --all
```

Đợi `mariadb`, `wordpress-db` và `wordpress` chuyển sang trạng thái healthy. `wordpress-setup` cuối cùng phải có trạng thái `Exited (0)`.

### Terminal 2 — Backend

Chạy từ thư mục gốc repository:

```powershell
$env:WOOCOMMERCE_WEBHOOK_SECRETS_LOCAL = 'showcase-local-only-change-me'
.\gradlew.bat quarkusDev
```

Đợi Quarkus lắng nghe tại cổng `8081`. Flyway migration và đồng bộ tài khoản Keycloak tự chạy khi khởi động.

Kiểm tra health trong một terminal khác:

```powershell
Invoke-RestMethod http://localhost:8081/api/health
```

Kết quả mong đợi: response có trạng thái `UP`.

### Terminal 3 — Frontend

```powershell
cd frontend
npm.cmd run dev
```

Mở `http://localhost:5174` và đăng nhập.

### Kiểm tra WordPress nếu cần

```powershell
docker compose run --rm --entrypoint wp wordpress-setup plugin is-active woocommerce --path=/var/www/html
docker compose exec wordpress php -r "echo file_get_contents('http://host.docker.internal:8081/api/health');"
```

## 5. Kịch bản trình diễn chính — ưu tiên giao diện

### Phần A — Giới thiệu storefront

Đăng nhập bằng tài khoản người mua. Có thể dùng `admin@v-erp.com` nếu muốn hạn chế việc đổi tài khoản lúc bắt đầu.

Mở **Shop/Cửa hàng** và trình diễn:

- sản phẩm seed thuộc nhiều doanh nghiệp;
- tìm kiếm và lọc theo danh mục;
- hình ảnh, giá khuyến mãi, tồn kho và biến thể;
- khu vực voucher và thao tác thu thập voucher;
- thay đổi số lượng trong giỏ và giới hạn theo tồn kho.

Sản phẩm nên dùng cho demo thanh toán: **Cờ Tỷ Phú Catan**. Sản phẩm này thuộc **Cửa hàng Board Game**, doanh nghiệp đã được liên kết với ví người bán được seed. Nhờ đó thanh toán ở phần sau sẽ tạo được escrow cho người bán.

Lời dẫn gợi ý: “Trình duyệt không tự quyết định số tiền cuối cùng. Checkout chỉ gửi ID sản phẩm và số lượng; backend tải lại dữ liệu, xác thực tồn kho, tính giá, giảm giá, vận chuyển và thuế, sau đó giữ tồn kho.”

### Phần B — Checkout và voucher

1. Thêm **Cờ Tỷ Phú Catan** vào giỏ với số lượng **2**.
2. Thu thập và áp dụng `WELCOME10`. Voucher yêu cầu đơn tối thiểu 5.000 minor ($50), nên hai sản phẩm Catan ($70) bảo đảm đủ điều kiện.
3. Mở checkout.
4. Kiểm tra họ tên, số điện thoại, địa chỉ và ghi chú giao hàng.
5. Xác nhận đặt hàng.
6. Ứng dụng tự chuyển sang `/orders/payment?orderId=<UUID>` để bắt đầu thanh toán.

Sau khi thanh toán, mở trang chi tiết đơn và trình diễn:

- mã đơn và trạng thái `PENDING`;
- thông tin khách hàng và giao nhận;
- sản phẩm cùng tổng tiền do server tính;
- thông tin voucher/giảm giá;
- timeline lịch sử đơn hàng.
- card thanh toán tách biệt với trạng thái xử lý đơn, gồm số tiền, mã `PaymentIntent` và trạng thái giao dịch.

### Phần C — Quản lý sản phẩm và đơn hàng

Thực hiện **mục 6 — Thanh toán** ngay sau Phần B khi vẫn đang ở tài khoản người mua. Sau khi thanh toán thành công mới quay lại Phần C và đổi sang tài khoản nhân viên.

Đăng nhập bằng `admin@v-erp.com` hoặc `operator@v-erp.com`.

Mở **Products/Sản phẩm** và trình diễn:

- sản phẩm, biến thể, SKU, giá, tồn kho, lượng đã giữ và cảnh báo sắp hết hàng;
- tổng quan trạng thái đồng bộ WooCommerce;
- khả năng tạo và chỉnh sửa sản phẩm.

Mở **Orders/Đơn hàng** và trình diễn:

- các tab trạng thái và thống kê đơn;
- tìm kiếm/lọc;
- đơn vừa tạo;
- lịch sử và các bước chuyển trạng thái hợp lệ.

Chưa chuyển đơn mới sang `COMPLETED`. Phần thanh toán cần đưa tiền vào escrow trước.

## 6. Trình diễn thanh toán và escrow

### Luồng chính trên giao diện người mua

Ngay sau checkout, ứng dụng mở màn hình **Hoàn tất thanh toán**. Trình diễn theo đúng thứ tự dưới đây để người xem thấy cả nhánh lỗi và nhánh thành công:

1. Chỉ ra tóm tắt đơn hàng, tổng tiền do backend trả về và ba cam kết: kiểm tra lại đơn/số tiền, hạn `PaymentIntent` 30 phút và idempotency chống giao dịch trùng.
2. Chỉ ra **MockPay QR** là phương thức đang hoạt động. **VNPay** và **Thẻ quốc tế** được khóa với nhãn **Sắp hỗ trợ**, không nên nói đây là gateway thật.
3. Bấm **Tiếp tục thanh toán**. Hệ thống tạo hoặc tái sử dụng `PaymentIntent`, chuyển trạng thái sang `PROCESSING`, tạo đúng một mã giao dịch mock và hiển thị QR.
4. Giải thích màn hình đang polling backend mỗi hai giây; trạng thái hiển thị không phải biến cục bộ giả lập ở trình duyệt.
5. Bấm **Mô phỏng thất bại**. Cho người xem thấy màn hình kết quả thất bại và đơn vẫn ở trạng thái `PENDING`.
6. Bấm **Thử thanh toán lại**. Hệ thống tạo một `PaymentIntent` mới với idempotency key mới rồi hiển thị QR mới.
7. Bấm **Mô phỏng thành công**. Cho người xem thấy màn hình thành công, số tiền và mã đơn.
8. Bấm **Xem chi tiết đơn hàng**. Card **Thanh toán** phải hiển thị **Đã thanh toán**, số tiền và mã `PaymentIntent`; trạng thái xử lý đơn vẫn là `PENDING`.
9. Bấm refresh để chứng minh trạng thái thanh toán được đọc lại từ backend và không mất khi tải lại trang.

Lời dẫn gợi ý: “Trạng thái đơn hàng và trạng thái thanh toán là hai state machine độc lập. Thanh toán thành công chưa có nghĩa là đơn đã giao; nhân viên vẫn phải xác nhận, giao và hoàn tất đơn.”

### Kiểm chứng kỹ thuật tùy chọn qua API

Phần này không bắt buộc khi thời gian ngắn. Nếu muốn trình diễn lại toàn bộ API tạo/thanh toán, hãy tạo một đơn `PENDING` thứ hai; đơn đã thanh toán trên giao diện sẽ được backend bảo vệ khỏi thanh toán trùng.

Mở một cửa sổ PowerShell mới. Các lệnh sau lấy access token admin qua direct-access grant của Keycloak.

```powershell
$api = 'http://localhost:8081/api'
$tokenResponse = Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:8181/realms/v-erp-lite/protocol/openid-connect/token' `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
        client_id = 'v-erp-app'
        grant_type = 'password'
        username = 'admin@v-erp.com'
        password = 'password'
        scope = 'openid'
    }
$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
```

Dán UUID của đơn vừa sao chép từ trình duyệt:

```powershell
$orderId = 'DAN-UUID-DON-HANG-VAO-DAY'
```

### Tạo PaymentIntent có idempotency

```powershell
$idempotencyKey = "showcase-$([guid]::NewGuid())"
$intent = Invoke-RestMethod `
    -Method Post `
    -Uri "$api/payment-intents" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{
        orderId = $orderId
        idempotencyKey = $idempotencyKey
    } | ConvertTo-Json)
$intent | Format-List id, orderId, amountMinor, currency, status, idempotencyKey, expiresAt
```

Trạng thái ban đầu mong đợi: `REQUIRES_PAYMENT_METHOD`.

Giải thích: gửi lại request với cùng idempotency key sẽ trả về intent hiện có thay vì tạo bản ghi trùng.

### Khởi tạo mock gateway

```powershell
$gateway = Invoke-RestMethod `
    -Method Post `
    -Uri "$api/payments/mock/initiate" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{ paymentIntentId = $intent.id } | ConvertTo-Json)
$gateway | Format-List gateway, gatewayTransactionId, paymentUrl, qrCodeContent, expiresAt
```

Phần này chứng minh lớp gateway có thể trả về URL thanh toán/nội dung QR mà chưa cần kết nối nhà cung cấp tài chính thật.

### Hoàn tất thanh toán

```powershell
$paymentResult = Invoke-RestMethod `
    -Method Post `
    -Uri "$api/payments/mock/$($intent.id)/complete" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{ gatewayTransactionId = $gateway.gatewayTransactionId } | ConvertTo-Json)
$paymentResult | Format-List

$paidIntent = Invoke-RestMethod `
    -Uri "$api/payment-intents/$($intent.id)" `
    -Headers $headers
$paidIntent | Format-List id, status, amountMinor, currency

$transactions = Invoke-RestMethod `
    -Uri "$api/payment-intents/$($intent.id)/transactions" `
    -Headers $headers
$transactions | Format-Table gateway, gatewayTransactionId, amountMinor, currency, status
```

Kết quả mong đợi: intent có trạng thái `SUCCEEDED` và có một mock transaction thành công.

### Chứng minh escrow và hoa hồng

ID của Cửa hàng Board Game là `b1000000-0000-0000-0000-000000000001`.

```powershell
$sellerWallets = @(Invoke-RestMethod `
    -Uri "$api/wallets?ownerType=SELLER&ownerId=b1000000-0000-0000-0000-000000000001&currency=USD" `
    -Headers $headers)
$sellerUsdWallet = $sellerWallets | Select-Object -First 1
$sellerUsdWallet | Format-List

Invoke-RestMethod `
    -Uri "$api/wallets/$($sellerUsdWallet.id)/transactions" `
    -Headers $headers | Format-Table type, transactionType, amountMinor, referenceType, description

$platformWallets = @(Invoke-RestMethod `
    -Uri "$api/wallets?ownerType=PLATFORM&ownerId=00000000-0000-0000-0000-000000000000&currency=USD" `
    -Headers $headers)
$platformWallets | Format-Table ownerType, availableBalanceMinor, pendingBalanceMinor, currency
```

Kết quả kế toán mong đợi:

- phần tiền ròng của người bán nằm trong `pendingBalanceMinor`;
- hoa hồng nền tảng là 5% và được ghi vào ví platform;
- mọi số tiền được lưu bằng đơn vị nhỏ nhất dạng số nguyên, không dùng số thực;
- ledger chỉ cho phép ghi thêm và mỗi dòng liên kết với đơn hàng.

### Hoàn tất đơn và giải ngân escrow

Quay lại trang chi tiết đơn và chuyển lần lượt:

1. `PENDING -> CONFIRMED`
2. `CONFIRMED -> SHIPPED`
3. `SHIPPED -> COMPLETED`

Thêm ghi chú ngắn ở mỗi bước. Sau đó tải lại ví người bán bằng PowerShell:

```powershell
$sellerUsdWallet = Invoke-RestMethod `
    -Uri "$api/wallets/$($sellerUsdWallet.id)" `
    -Headers $headers
$sellerUsdWallet | Format-List availableBalanceMinor, pendingBalanceMinor, currency
```

Kết quả mong đợi: tiền của người bán chuyển từ số dư chờ sang số dư khả dụng. Timeline ghi lại mọi lần đổi trạng thái; khi hoàn tất, hệ thống cũng cập nhật tổng chi tiêu, điểm và hạng thành viên của khách.

## 7. Trình diễn ví và payout trên giao diện

Đây là luồng tài chính riêng có giao diện đầy đủ, sử dụng ví VND đã được seed.

1. Đăng xuất và đăng nhập `owner@v-erp.com` / `password`.
2. Mở **Wallet/Ví**.
3. Trình diễn số dư khả dụng `5.000.000 VND` và lịch sử giao dịch chỉ ghi thêm.
4. Chọn chức năng yêu cầu rút tiền.
5. Yêu cầu một khoản nhỏ, ví dụ `100000 VND`, cùng thông tin ngân hàng demo.
6. Trình diễn payout mới có trạng thái `PENDING`.
7. Đăng xuất và đăng nhập `admin@v-erp.com` / `password`.
8. Mở **Payouts**.
9. Trình diễn bộ lọc chờ duyệt/đã duyệt/từ chối.
10. Phê duyệt yêu cầu và nhập lý do.
11. Quay lại ví người bán để cho thấy số dư khả dụng đã giảm và có ledger entry cho payout.

Có thể giải thích rằng phê duyệt payout sử dụng transaction, kiểm tra số dư và locking để tránh rút quá số dư khi có nhiều request đồng thời.

## 8. Trình diễn tích hợp WooCommerce

Đăng nhập `owner@v-erp.com`, sau đó mở **Settings -> Regional**.

Tại card WooCommerce, bấm **Kiểm tra lại/Refresh** và trình diễn:

- store URL `http://localhost:8090`;
- trạng thái channel và health;
- secret HMAC phía server đã được cấu hình hay chưa;
- số lượng entity đã mapping và webhook event;
- thời điểm kiểm tra/kết nối gần nhất;
- không có secret thật bị trả về trình duyệt.

Mở `http://localhost:8090/wp-admin/plugins.php` và cho thấy WooCommerce đang active. Có thể mở storefront WordPress ở tab khác để minh họa đích tích hợp local, nhưng không nói rằng sản phẩm ERP đã tự động được publish: custom WordPress connector chưa có trong nhánh này.

Có thể mô tả chính xác adapter backend đã triển khai:

- catalog change feed có scope theo tenant và sales channel;
- upsert mapping ID ngoài cho sản phẩm/biến thể;
- xác thực HMAC cho webhook đơn hàng từ WooCommerce;
- backend tự xác thực lại giá và tồn kho;
- pessimistic locking khi giữ tồn kho;
- stable semantic idempotency key để chống webhook lặp;
- endpoint reconcile/retry cho đơn đồng bộ lỗi;
- ánh xạ trạng thái đơn WooCommerce và V-ERP.

## 9. Phương án dự phòng — tạo đơn bằng API

Nếu checkout trên trình duyệt gặp sự cố, chạy đoạn dưới sau khi đã tạo `$api` và `$headers` ở phần 6. Lệnh sẽ tạo đơn cho Cửa hàng Board Game bằng khách hàng seed.

```powershell
$order = Invoke-RestMethod `
    -Method Post `
    -Uri "$api/orders/checkout" `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{
        customerId = '50000000-0000-0000-0000-000000000001'
        shippingName = 'Nguyen Van An'
        shippingPhone = '0901234567'
        shippingAddress = '123 Le Loi, District 1, Ho Chi Minh City'
        shippingNote = 'Don hang du phong cho showcase'
        items = @(
            @{
                productId = '30000000-0000-0000-0000-000000000101'
                productVariantId = $null
                quantity = 1
            }
        )
    } | ConvertTo-Json -Depth 6)
$orderId = $order.id
$order | Format-List id, orderNumber, status, finalAmountMinor, currency
```

Sau đó có thể mở trực tiếp `http://localhost:5174/orders/payment?orderId=<UUID>` để tiếp tục trên giao diện khi trình duyệt cũng đang đăng nhập bằng `admin@v-erp.com`. Nếu trình duyệt đang đăng nhập một buyer khác, hãy dùng `customerId` đúng của buyer đó; backend sẽ từ chối truy cập đơn không thuộc người mua. Hoặc tiếp tục từ mục **Tạo PaymentIntent có idempotency** nếu đang kiểm chứng API.

## 10. Xử lý sự cố

### Đăng nhập bị chuyển hướng lặp

- Xác nhận frontend đang dùng Keycloak cổng `8181` qua file `.env` đã commit.
- Xóa local storage/session của `localhost:5174`, sau đó đăng nhập lại.
- Xác nhận Keycloak truy cập được tại `http://localhost:8181`.

### Backend không kết nối được database

```powershell
docker compose ps --all
docker compose logs mariadb --tail 100
```

Đợi MariaDB healthy rồi khởi động lại Quarkus.

### WordPress chưa sẵn sàng

```powershell
docker compose logs wordpress --tail 100
docker compose logs wordpress-setup --tail 100
docker compose up -d wordpress wordpress-setup
```

### Token hết hạn khi demo API

Chạy lại riêng đoạn lấy token ở đầu phần 6 để làm mới `$headers`.

### PaymentIntent từ chối đơn hàng

Dùng một đơn mới ở trạng thái `PENDING`. Đơn đã thanh toán, hoàn tất hoặc hủy không thể bắt đầu luồng thanh toán bình thường. Đơn phải chứa sản phẩm liên kết với một doanh nghiệp; sản phẩm Board Game được đề xuất đáp ứng điều kiện này.

### Cổng đang bị chiếm

```powershell
Get-NetTCPConnection -LocalPort 8081,5174,8181,8090 -ErrorAction SilentlyContinue |
    Select-Object LocalPort, State, OwningProcess
```

Chỉ dừng process cũ mà bạn nhận diện được, sau đó khởi động lại service tương ứng.

## 11. Kịch bản rút gọn trong 5 phút

Nếu thời gian bị rút ngắn:

1. Mở **Shop**, thêm Catan và checkout.
2. Trình diễn đơn vừa tạo và tồn kho đã được giữ.
3. Trên màn hình QR, mô phỏng thất bại một lần, thử lại rồi mô phỏng thành công; mở chi tiết đơn để chỉ ra trạng thái **Đã thanh toán**.
4. Chuyển đơn sang `COMPLETED`, chạy lại truy vấn ví và cho thấy tiền chuyển từ pending sang available.
5. Mở **Wallet** của owner và **Payouts** của admin.
6. Kết thúc tại **Settings -> Regional -> WooCommerce** và trình bày các đảm bảo kỹ thuật của integration.

## 12. Những điều không nên khẳng định khi hỏi đáp

Không nên nói rằng:

- hệ thống đã kết nối Stripe, VNPay, MoMo hoặc ngân hàng thật; gateway hiện tại là mock;
- sản phẩm ERP tự động xuất hiện trong storefront WordPress local;
- refund/return hoặc quyết toán marketplace nhiều người bán đã sẵn sàng production;
- tài khoản demo, wildcard CORS hoặc HMAC local là cấu hình production.

Tóm tắt an toàn: “Nhóm đã hoàn thành storefront, checkout, màn hình thanh toán người mua bằng mock gateway, vòng đời đơn hàng, payment/ledger/escrow/payout và adapter phía ERP cho WooCommerce. Phần còn lại để productize là tích hợp nhà cung cấp thanh toán thật, xử lý callback/webhook production và custom WordPress connector.”

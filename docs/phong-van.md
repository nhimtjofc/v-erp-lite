# Biên bản phỏng vấn — Vòng 1

Phụ trách: N10-B
Thời gian: Tuần 5–6 (2026-10-05 → 2026-10-16)
Hình thức: Phỏng vấn bán cấu trúc, ghi chú ẩn danh.

> Ghi chú phương pháp: đây là biên bản tổng hợp phục vụ môn học, nội dung được tái dựng từ
> tài liệu dự án và các buổi trao đổi nội bộ. Người tham gia được gọi bằng mã bút danh,
> không ghi họ tên/MSSV/email. Mọi phát biểu dưới đây là quan điểm của người được phỏng vấn,
> không phải yêu cầu chính thức cho tới khi được chốt ở `docs/yeu-cau.md`.

## 1. Người tham gia

| Mã | Vai trò | Bối cảnh |
|----|---------|----------|
| P-01 | Chủ shop nhỏ (5–10 nhân viên) | Bán hàng đa kênh, có cửa hàng online và bán qua mạng xã hội |
| P-02 | Nhân viên vận hành | Xử lý đơn, đóng gói, bàn giao vận chuyển hằng ngày |
| P-03 | Admin nền tảng | Quản lý nhiều tenant, duyệt payout và thanh toán gói |
| P-04 | Khách mua quen | Mua hàng online 2–3 lần/tháng, quan tâm trạng thái đơn |
| P-05 | Kế toán nội bộ | Đối soát doanh thu, ví người bán và hoa hồng sàn |

## 2. Phỏng vấn P-01 — Chủ shop

**Hỏi: Hiện tại anh/chị quản lý sản phẩm và tồn kho bằng gì?**
Trả lời: Bảng tính và sổ tay. Khi có đơn từ nhiều kênh, dễ bán quá số lượng vì không cập
nhật kịp. Cần một nơi duy nhất để biết còn bao nhiêu hàng.

**Hỏi: Điều gì khiến anh/chị mất thời gian nhất khi có đơn mới?**
Trả lời: Phải tự nhắn khách xác nhận, tự kiểm tra tiền đã vào chưa. Nếu hệ thống giữ tiền
và chỉ chuyển khi giao xong thì yên tâm hơn.

**Hỏi: Anh/chị có nhiều cửa hàng không?**
Trả lời: Có 2 cửa hàng, muốn tách doanh thu và nhân viên theo từng cửa hàng. Không muốn
nhân viên cửa hàng này xem đơn của cửa hàng kia.

**Hỏi: Về phí sàn và rút tiền?**
Trả lời: Muốn biết rõ mỗi đơn bị trừ bao nhiêu phí, tiền còn chờ là bao nhiêu, khi nào
rút được. Rút tiền nên tự động nếu đủ ngưỡng, nhưng vẫn cần admin duyệt khi bất thường.

**Insight**
- Ưu tiên: chống bán quá tồn, tách dữ liệu theo cửa hàng, minh bạch phí và số dư.
- Nỗi đau: quy trình thủ công, thiếu tin cậy vào dòng tiền.

## 3. Phỏng vấn P-02 — Nhân viên vận hành

**Hỏi: Một ngày anh/chị xử lý bao nhiêu đơn và qua mấy bước?**
Trả lời: 30–50 đơn. Bước hiện tại: xem đơn, xác nhận với chủ, đóng gói, giao vận chuyển,
cập nhật trạng thái. Bước xác nhận với chủ hay bị chậm.

**Hỏi: Khi chủ shop không phản hồi đơn thì sao?**
Trả lời: Đơn treo, khách hỏi nhiều. Cần hạn chót để tự động xử lý hoặc nhắc.

**Hỏi: Anh/chị cần xem những thông tin gì trên màn hình đơn?**
Trả lời: Mã đơn, khách, danh sách sản phẩm, trạng thái thanh toán, trạng thái giao, nút
cập nhật trạng thái. Không cần xem số dư ví.

**Insight**
- Ưu tiên: cập nhật trạng thái nhanh, có deadline quyết định của chủ shop.
- Nỗi đau: thiếu thông tin thanh toán ngay trên đơn.

## 4. Phỏng vấn P-03 — Admin nền tảng

**Hỏi: Công việc quản trị nào chiếm nhiều thời gian?**
Trả lời: Duyệt payout ngoại lệ và đối soát thanh toán gói. Nếu có hàng đợi và duyệt theo
lô thì đỡ hơn nhiều.

**Hỏi: Khi một tenant không đóng phí gói thì xử lý thế nào?**
Trả lời: Không xóa dữ liệu. Chỉ khóa ghi, cho xem và xuất dữ liệu. Khi gia hạn thì mở lại.

**Hỏi: Lo ngại bảo mật lớn nhất?**
Trả lời: Nhân viên một tenant xem được dữ liệu tenant khác; hoặc chatbot AI lộ thông tin
khách. Cần chặn ở backend, không chỉ ở giao diện.

**Insight**
- Ưu tiên: duyệt theo lô, khóa ghi thay vì xóa, cô lập tenant.
- Nỗi đau: thao tác thủ công trên nhiều màn hình.

## 5. Phỏng vấn P-04 — Khách mua

**Hỏi: Anh/chị muốn biết gì sau khi đặt hàng?**
Trả lời: Đơn đã thanh toán chưa, ai đang giao, bao giờ tới, nếu không nhận được thì báo ở đâu.

**Hỏi: Anh/chị có dùng chat hỗ trợ không?**
Trả lời: Có, nhưng ngại gõ mã đơn. Muốn hỏi "đơn của tôi đang giao không" và nhận câu trả
lời đúng đơn của mình, không thấy đơn người khác.

**Hỏi: Khi trả hàng thì sao?**
Trả lời: Muốn trả từng món, biết tiền hoàn về đâu và bao lâu.

**Insight**
- Ưu tiên: tra cứu đơn nhanh, đúng quyền, hoàn tiền rõ ràng.
- Nỗi đau: phải nhớ mã đơn; sợ lộ thông tin khi chat.

## 6. Phỏng vấn P-05 — Kế toán nội bộ

**Hỏi: Anh/chị đối soát doanh thu và ví như thế nào?**
Trả lời: Đối chiếu sổ phụ với sao kê. Cần sổ cái chỉ ghi thêm, có mốc kiểm tra, không sửa
lịch sử.

**Hỏi: Điều gì là bất biến với anh/chị?**
Trả lời: Tổng tiền vào/ra phải khớp; không có giao dịch mồ côi; mọi điều chỉnh phải có
bản ghi riêng, không sửa dòng cũ.

**Insight**
- Ưu tiên: append-only, checkpoint đối soát, tiền minor units.
- Nỗi đau: sổ cũ bị sửa tay, khó truy vết.

## 7. Tổng hợp nỗi đau → yêu cầu

| Nỗi đau | Yêu cầu | Mã |
|---------|---------|----|
| Bán quá tồn do nhiều kênh | Giữ tồn kho khi đặt, khóa bi quan | FR-03 |
| Không tin tưởng dòng tiền | Escrow + sổ cái append-only + checkpoint | FR-08, 12, 13, 16, 19 |
| Đơn treo vì chủ shop chậm | Deadline quyết định + scheduler | FR-10, 13 |
| Nhân viên xem chéo cửa hàng | Phân quyền theo `business_id` | NFR-01 |
| Duyệt payout thủ công | Hàng đợi ngoại lệ + duyệt lô | FR-17, 18 |
| Tenant hết hạn bị mất dữ liệu | Read-only thay vì xóa | FR-25 |
| AI lộ PII / vượt quyền | Identity ngoài model + validator | FR-29..32 |
| Khách phải nhớ mã đơn | AI tra cứu theo phiên, tool danh sách đơn | FR-30 |

## 8. Câu hỏi chưa được trả lời (chuyển sang vòng 2)

1. Thời gian giữ escrow 7 ngày có phù hợp với mọi ngành hàng không?
2. Tỷ lệ hoa hồng 5% có cần cấu hình theo tenant/gói không?
3. Khi trả hàng một phần, phí sàn hoàn lại thế nào?
4. Có cần thông báo qua email/SMS ngoài notification trong app không?
5. Dữ liệu tenant hết hạn được lưu bao lâu trước khi lưu trữ lạnh?

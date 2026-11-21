# Câu hỏi thường gặp của khách hàng

Phụ trách: N10-C
Cập nhật: Vòng 1 (Gói 2)

Tài liệu tổng hợp câu hỏi thực tế của khách mua và câu trả lời chuẩn, dùng để:
- viết kịch bản demo và test hội thoại cho trợ lý AI;
- kiểm tra use case tra cứu đơn hàng (UC-03);
- đào tạo nhân viên hỗ trợ.

Mỗi câu hỏi gắn mã yêu cầu/use case tương ứng. Câu trả lời phản ánh đúng hành vi hiện
tại của hệ thống; phần chưa hỗ trợ được ghi rõ là "ngoài phạm vi".

## 1. Đặt hàng và thanh toán

**Q1. Tôi đặt hàng xong nhưng chưa thấy trừ tiền, đơn có được giữ không?**
Có. Đơn ở trạng thái `PENDING_PAYMENT` và tồn kho được tạm giữ. Bạn có 30 phút để hoàn tất
thanh toán; quá hạn đơn sẽ hết hiệu lực và tồn kho được trả lại. (FR-06, FR-07)

**Q2. Tôi lỡ đóng trang thanh toán, có phải đặt lại từ đầu?**
Không cần nếu đơn còn hạn. Mở lại màn hình thanh toán của đơn để tiếp tục hoặc thử lại;
hệ thống không tạo giao dịch trùng nhờ idempotency key. (FR-07, FR-09)

**Q3. Tôi bấm thanh toán hai lần thì có bị trừ tiền hai lần không?**
Không. Mỗi lần thanh toán dùng cùng idempotency key cho đơn, nên bản ghi thứ hai bị bỏ qua.
(FR-09, NFR-04)

**Q4. Tôi thanh toán bằng QR được không?**
Được. Hệ thống sinh mã VietQR (EMVCo) cho đơn; bạn quét và chờ màn hình cập nhật kết quả.
(FR-07)

**Q5. Đơn của tôi có nhiều người bán thì thanh toán thế nào?**
Giỏ hàng được tách theo từng người bán; mỗi người bán có đơn riêng và mỗi đơn có tiền giữ
escrow riêng. (FR-06, FR-08)

**Q6. Dùng voucher ở đâu và điều kiện là gì?**
Nhập mã ở bước checkout. Voucher có thể yêu cầu giá trị đơn tối thiểu, thời gian hiệu lực
và giới hạn lượt dùng; hệ thống kiểm tra lại ở server. (FR-05)

## 2. Theo dõi đơn và escrow

**Q7. Tiền của tôi đang ở đâu sau khi thanh toán?**
Tiền được giữ ở ví nền tảng dưới dạng escrow `HELD`. Người bán chưa nhận được tiền cho tới
khi bạn xác nhận nhận hàng hoặc hết thời hạn khiếu nại. (FR-08, FR-12)

**Q8. Khi nào người bán nhận được tiền?**
Khi bạn bấm "Đã nhận hàng" (`COMPLETED`), hoặc sau 7 ngày kể từ khi escrow được giữ mà không có khiếu
nại. Hệ thống trừ hoa hồng sàn 5% rồi mới cộng vào số dư khả dụng của người bán.
(FR-12, FR-13, FR-15)

**Q9. Tôi có thể xem lịch sử trạng thái đơn không?**
Có. Trang chi tiết đơn hiển thị timeline từ `OrderHistory`, gồm trạng thái cũ, trạng thái
mới, thời điểm và người thao tác. (FR-11)

**Q10. Đơn đang giao quá lâu, tôi hỏi ai?**
Bạn có thể hỏi trợ lý AI trong cửa hàng: "Đơn của tôi đang giao không?" Trợ lý chỉ trả về
đơn thuộc quyền của bạn. (FR-30, UC-03)

## 3. Hủy, trả hàng, hoàn tiền

**Q11. Tôi muốn hủy đơn thì làm thế nào?**
Khi đơn còn ở giai đoạn cho phép, bạn gửi yêu cầu hủy. Chủ shop duyệt hủy; tiền escrow
được hoàn và tồn kho được trả lại. (FR-14)

**Q12. Tôi chỉ muốn trả một món trong đơn được không?**
Được. Hệ thống hỗ trợ trả hàng theo từng item, tính tiền hoàn theo item và cập nhật escrow
tương ứng. (FR-14)

**Q13. Tiền hoàn về đâu và bao lâu?**
Tiền hoàn về ví/kênh thanh toán của bạn theo bản ghi hoàn tiền; thời gian tùy kênh. Trong
hệ thống demo, hoàn tiền được ghi nhận ngay vào sổ cái. (FR-14, FR-16)

**Q14. Chủ shop từ chối đơn của tôi thì sao?**
Đơn chuyển `OWNER_DECLINED` và tiền escrow được hoàn lại cho bạn; tồn kho được trả.
(FR-10, FR-14)

## 4. Gói thuê bao (dành cho chủ shop)

**Q15. Tôi được dùng thử gói BASE bao lâu?**
14 ngày kể từ khi bắt đầu dùng thử. Hết thời gian dùng thử, tenant chuyển sang trạng thái
hết hạn và chỉ đọc cho tới khi gia hạn. (FR-23, FR-25)

**Q16. Tôi nâng cấp lên PREMIUM giữa kỳ thì tính tiền thế nào?**
Hệ thống tính phần chênh lệch theo số ngày còn lại của kỳ hiện tại (prorated), làm tròn lên.
(FR-23)

**Q17. Gói BASE và PREMIUM khác nhau gì?**
BASE có catalog, khách hàng, đơn hàng, tồn kho, thanh toán online. PREMIUM thêm voucher,
báo cáo, nhiều cửa hàng, cộng tác nhóm, bán hàng đa kênh và ví/payout. (FR-24)

**Q18. Hết hạn gói thì dữ liệu của tôi có bị xóa không?**
Không. Tenant bị khóa ghi (read-only), dữ liệu vẫn xem và xuất được; gia hạn sẽ mở lại.
(FR-25)

## 5. Trợ lý AI

**Q19. Trợ lý AI có thể hủy đơn hoặc hoàn tiền giúp tôi không?**
Không. Trợ lý chỉ tra cứu thông tin (read-only), không tạo/sửa/xóa dữ liệu và không tự nhận
đã thực hiện thay đổi. (FR-30, FR-31)

**Q20. Trợ lý AI có thấy đơn của người khác không?**
Không. Định danh của bạn lấy từ phiên đăng nhập và được gửi ngầm tới backend; câu trả lời
chỉ chứa dữ liệu bạn có quyền xem. (FR-29, NFR-01)

**Q21. Tôi có thể hỏi trợ lý những gì?**
Ví dụ: "Tôi có những đơn nào đang giao?", "Đơn ORD-123 của tôi sao rồi?", "Cửa hàng có bao
nhiêu sản phẩm?", "Tìm áo thun màu đỏ". (FR-30)

**Q22. Trợ lý AI có lưu lại cuộc trò chuyện không?**
Có, lịch sử lưu ở server theo phiên và theo quyền sở hữu. Client không được gửi lịch sử
lên. (FR-28)

**Q23. Nếu trợ lý gặp lỗi thì sao?**
Bạn nhận thông báo chung "hệ thống đang bận, thử lại sau", không thấy chi tiết kỹ thuật.
(FR-32, NFR-09)

## 6. Bán hàng đa kênh

**Q24. Đơn từ WooCommerce có tự về hệ thống không?**
Có, qua webhook có chữ ký HMAC. Đơn trùng lặp bị bỏ qua, không tạo đơn thứ hai. (FR-20, 21)

**Q25. Nếu webhook gửi lại nhiều lần thì có bị nhân đôi đơn?**
Không. Hệ thống khử trùng theo `gateway + event_id` và ánh xạ ID đơn ngoài. (FR-21, NFR-06)

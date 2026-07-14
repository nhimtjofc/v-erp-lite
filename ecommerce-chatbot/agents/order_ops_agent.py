import sys
import os

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import os
import google.generativeai as genai

# 1. Khai báo Hàm Công Cụ (Tool) cho AI
def get_order_status(order_id: str) -> str:
    """Tra cứu tình trạng giao hàng và thanh toán của một đơn hàng cụ thể.
    
    Args:
        order_id: Mã đơn hàng cần tra cứu (ví dụ: 'ORD-123', '123')
    """
    print(f"\n[Function Call] AI tu dong kich hoat he thong: GET /api/orders/{order_id}...")
    
    # Mô phỏng API trả về từ Database ERP thực tế
    mock_orders = {
        "123": {"status": "Đang giao hàng 🚚", "total": "2,500,000 VND", "expected_delivery": "Ngày mai"},
        "456": {"status": "Đã hủy ❌", "total": "350,000 VND", "reason": "Khách hàng yêu cầu hủy"},
        "789": {"status": "Đã hoàn thành ✅", "total": "850,000 VND", "delivery_date": "Hôm qua"}
    }
    
    # Lọc bỏ các ký tự thừa
    clean_id = order_id.replace("ORD-", "").strip()
    
    if clean_id in mock_orders:
        return str(mock_orders[clean_id])
    else:
        return f"Không tìm thấy đơn hàng nào có mã {order_id} trong hệ thống ERP."

class OrderOpsAgent:
    """Xử lý tra cứu đơn hàng bằng Gemini Function Calling."""
    
    def __init__(self):
        gemini_key = os.environ.get("GEMINI_API_KEY")
        genai.configure(api_key=gemini_key)
        
        # 2. Bơm Tool vào não bộ Gemini
        self.model = genai.GenerativeModel(
            model_name='gemini-2.5-flash',
            tools=[get_order_status]
        )
        
    def handle_query(self, query: str) -> str:
        """Xử lý câu hỏi của khách hàng."""
        pass
        
        # 3. Kích hoạt tính năng cho phép AI tự động chạy hàm khi cần thiết
        chat = self.model.start_chat(enable_automatic_function_calling=True)
        
        prompt = f"""
        Bạn là nhân viên hỗ trợ khách hàng của V-ERP Lite. 
        Nhiệm vụ: Tư vấn tình trạng đơn hàng cho khách.
        
        Bắt buộc: Nếu khách có cung cấp mã đơn hàng, bạn PHẢI gọi công cụ 'get_order_status'.
        Tuyệt đối không tự bịa ra thông tin nếu công cụ báo không tìm thấy.
        Hãy trả lời bằng tiếng Việt, thân thiện, lịch sự và thêm emoji vui nhộn.
        
        Câu hỏi của khách: "{query}"
        """
        
        try:
            response = chat.send_message(prompt)
            return response.text.strip()
        except Exception as e:
            return f"Dạ hệ thống tra cứu đơn hàng đang bảo trì, anh/chị thông cảm thử lại sau nhé. (Lỗi: {e})"

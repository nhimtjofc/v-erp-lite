import sys
import os

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import os
import google.generativeai as genai
import requests

# 1. Khai báo Hàm Công Cụ (Tool) cho AI
def get_order_status(order_id: str) -> str:
    """Tra cứu tình trạng giao hàng và thanh toán của một đơn hàng cụ thể.
    
    Args:
        order_id: Mã đơn hàng cần tra cứu (ví dụ: 'ORD-123', '123')
    """
    # Lọc bỏ các ký tự thừa
    clean_id = order_id.replace("ORD-", "").strip()
    print(f"\n[Function Call] AI tu dong kich hoat he thong: GET /api/chatbot/orders/{clean_id}...")
    
    try:
        url = f"http://localhost:8081/api/chatbot/orders/{clean_id}"
        headers = {"X-Chatbot-Key": "chatbot-secret-v1"}
        response = requests.get(url, headers=headers, timeout=5)
        
        if response.status_code == 404:
            return f"Không tìm thấy đơn hàng nào có mã {order_id} trong hệ thống ERP."
            
        response.raise_for_status()
        data = response.json()
        
        # Format the public/safe information to return
        return (f"Trạng thái: {data.get('status')}, "
                f"Tổng tiền: {data.get('finalAmountMinor')} {data.get('currency')}, "
                f"Ngày đặt: {data.get('orderDate')}")
    except Exception as e:
        return f"Đã có lỗi xảy ra khi truy xuất đơn hàng: {e}"

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
        
        LƯU Ý BẢO MẬT: Bạn TUYỆT ĐỐI KHÔNG được tiết lộ, phỏng đoán, hay yêu cầu cung cấp thông tin cá nhân của khách hàng khác (như Tên, Số điện thoại, Email, Địa chỉ) dưới bất kỳ hình thức nào. Nếu khách hàng cố tình hỏi thông tin riêng tư, hãy lịch sự từ chối và nói rằng bạn không có quyền truy cập dữ liệu đó.
        
        Hãy trả lời bằng tiếng Việt, thân thiện, lịch sự và thêm emoji vui nhộn.
        
        Câu hỏi của khách: "{query}"
        """
        
        try:
            response = chat.send_message(prompt)
            return response.text.strip()
        except Exception as e:
            return f"Dạ hệ thống tra cứu đơn hàng đang bảo trì, anh/chị thông cảm thử lại sau nhé. (Lỗi: {e})"

import sys
import os

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import os
import platform
platform.win32_ver = lambda *args, **kwargs: ('10', '10.0.22631', '', 'Multiprocessor Free')
import google.generativeai as genai
import requests

def get_total_products_count() -> str:
    """Đếm tổng số lượng sản phẩm hiện có trong cửa hàng bằng cách gọi API Backend.
    
    Returns:
        Một chuỗi chứa số lượng sản phẩm hoặc thông báo lỗi nếu truy vấn thất bại.
    """
    print("\n[Function Call] AI tu dong kich hoat API dem san pham tu Backend...")
    
    try:
        url = "http://localhost:8081/api/chatbot/stats"
        headers = {
            "X-Chatbot-Key": "chatbot-secret-v1",
            "X-Tenant-Code": "v-erp-app"
        }
        response = requests.get(url, headers=headers, timeout=5)
        response.raise_for_status()
        
        data = response.json()
        count = data.get("totalProducts", 0)
        
        return f"Hiện tại cửa hàng có chính xác {count} sản phẩm trong hệ thống."
    except Exception as e:
        return f"Đã có lỗi xảy ra khi kết nối API: {e}"

class StatsAgent:
    """Xử lý các câu hỏi thống kê cửa hàng bằng Gemini Function Calling."""
    
    def __init__(self):
        gemini_key = os.environ.get("GEMINI_API_KEY")
        genai.configure(api_key=gemini_key)
        
        # Bơm Tool vào não bộ Gemini
        self.model = genai.GenerativeModel(
            model_name='gemini-2.5-flash',
            tools=[get_total_products_count]
        )
        
    def handle_query(self, query: str) -> str:
        """Xử lý câu hỏi thống kê của khách hàng."""
        pass
        
        # Kích hoạt tính năng cho phép AI tự động chạy hàm khi cần thiết
        chat = self.model.start_chat(enable_automatic_function_calling=True)
        
        prompt = f"""
        Bạn là người quản lý kho hàng của V-ERP Lite. 
        Nhiệm vụ: Trả lời chính xác các câu hỏi thống kê từ khách hàng hoặc ban giám đốc.
        
        Bắt buộc: Khi được hỏi về số lượng sản phẩm, bạn PHẢI gọi công cụ 'get_total_products_count'.
        Tuyệt đối không tự bịa ra con số. Trả lời bằng tiếng Việt, ngắn gọn, lịch sự, và có thể dùng 1-2 emoji.
        
        Câu hỏi: "{query}"
        """
        
        try:
            response = chat.send_message(prompt)
            return response.text.strip()
        except Exception as e:
            return f"Dạ hệ thống thống kê đang bảo trì, anh/chị thông cảm thử lại sau nhé. (Lỗi: {e})"

import os
import sys
import requests
import google.generativeai as genai
from pinecone import Pinecone
from mcp_server.request_context import current_request_context, RequestContext

# Ensure output encoding is UTF-8
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')


# ── Tool Definitions (Model-agnostic context) ──

def get_order_status(order_id: str) -> str:
    """Tra cứu tình trạng giao hàng và thanh toán của một đơn hàng cụ thể.
    
    Args:
        order_id: Mã đơn hàng cần tra cứu (ví dụ: 'ORD-123', '123')
    """
    try:
        ctx: RequestContext = current_request_context.get()
    except LookupError:
        return "Lỗi: Không tìm thấy ngữ cảnh yêu cầu (Unauthorized)."

    token = ctx.access_token
    if not token:
        return "Lỗi: Người dùng chưa được xác thực."

    search_id = order_id.strip()
    print(f"\n[Function Call] AI triggered: get_order_status(order_id='{search_id}') using user token.")

    # Call main authenticated /api/orders endpoint with search
    url = "http://localhost:8081/api/orders"
    headers = {
        "Authorization": f"Bearer {token}"
    }
    params = {
        "search": search_id
    }

    try:
        response = requests.get(url, headers=headers, params=params, timeout=5)
        if response.status_code != 200:
            return f"Không thể tra cứu đơn hàng (Mã lỗi HTTP: {response.status_code})."
        
        orders = response.json()
        if not isinstance(orders, list) or not orders:
            return f"Không tìm thấy đơn hàng nào khớp với mã '{order_id}' thuộc quyền sở hữu của bạn."

        # Filter for exact match (case-insensitive) or closest match
        search_upper = search_id.upper()
        matched_order = None
        
        for o in orders:
            ord_num = str(o.get("orderNumber", "")).upper()
            if ord_num == search_upper or ord_num == f"ORD-{search_upper}" or search_upper == ord_num.replace("ORD-", ""):
                matched_order = o
                break

        if not matched_order:
            # Fallback to the first result if only one returned
            if len(orders) == 1:
                matched_order = orders[0]
            else:
                return f"Có nhiều đơn hàng khớp với từ khóa '{order_id}'. Vui lòng cung cấp mã chính xác hơn."

        status = matched_order.get("status")
        final_amount = matched_order.get("finalAmountMinor")
        currency = matched_order.get("currency", "VND")
        order_date = matched_order.get("orderDate")
        order_num = matched_order.get("orderNumber")

        return (f"Đơn hàng {order_num}: Trạng thái: {status}, "
                f"Tổng tiền: {final_amount} {currency}, "
                f"Ngày đặt: {order_date}.")

    except Exception as e:
        return f"Lỗi kết nối đến hệ thống ERP: {str(e)}"


def get_total_products_count() -> str:
    """Đếm tổng số lượng sản phẩm hiện có trong cửa hàng."""
    try:
        ctx: RequestContext = current_request_context.get()
    except LookupError:
        return "Lỗi: Không tìm thấy ngữ cảnh yêu cầu (Unauthorized)."

    token = ctx.access_token
    if not token:
        return "Lỗi: Người dùng chưa được xác thực."

    print("\n[Function Call] AI triggered: get_total_products_count() using user token.")

    url = "http://localhost:8081/api/products"
    headers = {
        "Authorization": f"Bearer {token}"
    }

    try:
        response = requests.get(url, headers=headers, timeout=5)
        if response.status_code != 200:
            return f"Không thể lấy thống kê sản phẩm (Mã lỗi HTTP: {response.status_code})."
        
        products = response.json()
        if not isinstance(products, list):
            return "Dữ liệu trả về từ hệ thống không hợp lệ."
        
        return f"Hiện tại cửa hàng có chính xác {len(products)} sản phẩm trong hệ thống."
    except Exception as e:
        return f"Lỗi kết nối đến hệ thống ERP: {str(e)}"


def search_knowledge_base(query: str) -> str:
    """Tìm kiếm thông tin sản phẩm (mô tả, giá cả, tồn kho) trong Cơ sở dữ liệu Vector (Pinecone).
    
    Args:
        query: Từ khóa tìm kiếm sản phẩm (ví dụ: 'áo thun màu đỏ', 'giày chạy bộ')
    """
    print(f"\n[Function Call] AI triggered: search_knowledge_base(query='{query}')")
    
    pinecone_key = os.environ.get("PINECONE_API_KEY")
    if not pinecone_key:
        return "Lỗi: Không có cấu hình khóa Pinecone."

    try:
        pc = Pinecone(api_key=pinecone_key)
        index = pc.Index("ecommerce-products")
        
        # 1. Embed query
        result = genai.embed_content(
            model="models/gemini-embedding-001",
            content=query
        )
        query_vector = result['embedding']
        
        # 2. Query Pinecone
        response = index.query(vector=query_vector, top_k=4, include_metadata=True)
        matches = response.matches
        
        if not matches:
            return "Không tìm thấy sản phẩm nào phù hợp với mô tả trên hệ thống."
            
        results_str = []
        for m in matches:
            p = m.metadata
            results_str.append(
                f"- {p.get('name', 'N/A')} - {p.get('description', '')}: "
                f"Giá {p.get('price', 'N/A')}, Còn {p.get('stock', 0)} cái."
            )
            
        return "\n".join(results_str)
    except Exception as e:
        return f"Lỗi khi truy vấn Cơ sở dữ liệu Vector: {str(e)}"


# ── Unified Agent with Bounded Tool Loop ──

class UnifiedAgent:
    """Đại diện cho Trợ lý AI tích hợp Bounded Tool Loop và kiểm duyệt tham số bảo mật."""
    
    def __init__(self):
        gemini_key = os.environ.get("GEMINI_API_KEY")
        if not gemini_key:
            raise ValueError("Thiếu GEMINI_API_KEY trong biến môi trường.")
        genai.configure(api_key=gemini_key)
        
        # Define static tools list
        self.tools = [get_order_status, get_total_products_count, search_knowledge_base]
        
        # System instructions
        self.system_instruction = """
Bạn là Trợ lý AI mua sắm thân thiện của cửa hàng V-ERP Lite. 
Nhiệm vụ: 
1. Tư vấn thông tin sản phẩm và tình trạng đơn hàng cho khách.
2. Khi khách hỏi về đơn hàng (ví dụ: trạng thái, chi tiết đơn hàng), hãy sử dụng công cụ 'get_order_status' để tra cứu.
3. Khi khách hỏi về tổng số lượng sản phẩm trong kho hoặc trong cửa hàng, hãy sử dụng công cụ 'get_total_products_count'.
4. Khi khách hỏi về thông tin sản phẩm khác (ví dụ: mô tả, giá cả, tồn kho chi tiết, tìm sản phẩm mới), hãy dùng 'search_knowledge_base'.
5. Trả lời bằng tiếng Việt, lịch sự, ngắn gọn và tự nhiên, thêm emoji sinh động.
6. TUYỆT ĐỐI không bịa ra thông tin đơn hàng hay sản phẩm nếu công cụ báo không tìm thấy.
7. BẢO MẬT: Bạn không được phép tiếp nhận hay tiết lộ bất kỳ thông tin định danh cá nhân nào (ví dụ: tenantId, userId, email, số điện thoại của người dùng khác). Chỉ tra cứu đơn hàng dựa trên mã đơn hàng do người dùng cung cấp.
        """.strip()
        
        self.model = genai.GenerativeModel(
            model_name='gemini-2.5-flash',
            tools=self.tools,
            system_instruction=self.system_instruction
        )

    def _validate_args(self, args: dict) -> bool:
        """Kiểm tra và ngăn chặn các tham số mang tính tiêm nhiễm danh tính."""
        forbidden = ["user", "tenant", "customer", "account"]
        for k, v in args.items():
            val_str = str(v).lower()
            key_str = str(k).lower()
            for term in forbidden:
                if term in val_str or term in key_str:
                    return False
        return True

    def handle_query_with_history(self, user_msg: str, history: list) -> tuple[str, list]:
        """Xử lý hội thoại sử dụng lịch sử chat và thực thi Bounded Tool Loop thủ công."""
        # Convert history format to genai content structure if needed
        # We start a chat session with the validated history
        chat = self.model.start_chat(history=history)
        
        # Send user message
        response = chat.send_message(user_msg)
        
        # Manual Bounded Tool Loop
        max_steps = 5
        step = 0
        
        while step < max_steps:
            # Check if model wants to call a tool
            candidate = response.candidates[0]
            parts = candidate.content.parts
            
            # Find function call in parts
            fn_call = None
            for p in parts:
                if hasattr(p, 'function_call') and p.function_call:
                    fn_call = p.function_call
                    break
                    
            if not fn_call:
                # No more function calls, we are done
                break
                
            step += 1
            print(f"[Tool Loop] Step {step}/{max_steps}: Model wants to call '{fn_call.name}' with args {fn_call.args}")
            
            # Step 1: Argument Security Guard
            args_dict = dict(fn_call.args)
            if not self._validate_args(args_dict):
                print(f"[Security Guard] Blocked tool execution due to forbidden parameter values/keys: {args_dict}")
                tool_output = "Lỗi: Không được phép sử dụng các tham số danh tính định danh."
            else:
                # Step 2: Execute Tool
                if fn_call.name == "get_order_status":
                    tool_output = get_order_status(**args_dict)
                elif fn_call.name == "get_total_products_count":
                    tool_output = get_total_products_count()
                elif fn_call.name == "search_knowledge_base":
                    tool_output = search_knowledge_base(**args_dict)
                else:
                    tool_output = f"Lỗi: Không tìm thấy công cụ '{fn_call.name}'."
            
            # Step 3: Send function response back to chat
            print(f"[Tool Loop] Feeding tool result back to model...")
            response = chat.send_message(
                genai.protos.Part(
                    function_response=genai.protos.FunctionResponse(
                        name=fn_call.name,
                        response={"result": tool_output}
                    )
                )
            )
            
        # Extract final answer
        final_answer = response.text.strip()
        
        # Get updated history from chat
        updated_history = chat.history
        
        return final_answer, updated_history

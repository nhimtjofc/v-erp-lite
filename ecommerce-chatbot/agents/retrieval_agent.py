import sys
import os

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import os
import google.generativeai as genai
from pinecone import Pinecone
from dotenv import load_dotenv

load_dotenv()

class RetrievalAgent:
    """Handles product discovery by querying Vector DB and generating responses."""
    
    def __init__(self):
        gemini_key = os.environ.get("GEMINI_API_KEY")
        if gemini_key:
            genai.configure(api_key=gemini_key)
        self.model = genai.GenerativeModel('gemini-2.5-flash')
        
        self.pinecone_api_key = os.environ.get("PINECONE_API_KEY")
        try:
            self.pc = Pinecone(api_key=self.pinecone_api_key)
            self.index = self.pc.Index("ecommerce-products")
        except Exception as e:
            self.index = None
            
    def _search_db(self, query):
        """Thuật toán tìm kiếm Vector Database."""
        if not self.index:
            return []


        
        # 1. Biến câu hỏi của khách thành Vector
        result = genai.embed_content(
            model="models/gemini-embedding-001",
            content=query
        )
        query_vector = result['embedding']
        
        # 2. Tìm kiếm top 4 sản phẩm gần nhất trong Pinecone
        response = self.index.query(vector=query_vector, top_k=4, include_metadata=True)
        
        # 3. Lấy phần metadata ra để đưa cho AI
        results = [match.metadata for match in response.matches]
        return results

    def handle_query(self, query):
        """Lấy dữ liệu và nhờ AI tạo câu trả lời tư vấn."""
        try:
            retrieved_products = self._search_db(query)
            
            if not retrieved_products:
                return "Dạ hiện tại em không tìm thấy dữ liệu nào phù hợp với yêu cầu của anh/chị trên hệ thống Pinecone ạ."
                
            # Format bối cảnh từ dữ liệu trả về của Pinecone
            context_str = "\n".join([f"- {p['name']} - {p.get('description', '')}: Giá {p.get('price', 'N/A')}, Còn {p.get('stock', 0)} cái." for p in retrieved_products])
            
            prompt = f"""
            Bạn là chuyên viên tư vấn bán hàng cực kỳ chuyên nghiệp và thân thiện.
            Dựa vào DỮ LIỆU TỪ PINECONE CLOUD sau đây, hãy trả lời câu hỏi của khách hàng.
            Quy tắc:
            - KHÔNG được bịa ra sản phẩm không có trong danh sách.
            - Trả lời ngắn gọn, tự nhiên, thêm emoji cho sinh động.
            
            [DỮ LIỆU ĐƯỢC KÉO TỪ PINECONE]:
            {context_str}
            
            [CÂU HỎI CỦA KHÁCH]: "{query}"
            """
            
            response = self.model.generate_content(prompt)
            return response.text.strip()
        except Exception as e:
            return f"Xin lỗi, tôi đang gặp trục trặc khi truy xuất Vector DB hoặc Não bộ: {str(e)}"

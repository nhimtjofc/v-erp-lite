import os
import time
from pinecone import Pinecone, ServerlessSpec
from dotenv import load_dotenv
import google.generativeai as genai

load_dotenv()

def sync_products_to_vector_db():
    print("🚀 BẮT ĐẦU ĐỒNG BỘ DỮ LIỆU TỪ ERP VÀO PINECONE VECTOR DB...")
    
    api_key = os.environ.get("PINECONE_API_KEY")
    if not api_key or api_key == "YOUR_PINECONE_API_KEY_HERE":
        print("⚠️ Lỗi: Không tìm thấy PINECONE_API_KEY hợp lệ.")
        return

    # Khởi tạo Gemini Embedding
    gemini_key = os.environ.get("GEMINI_API_KEY")
    genai.configure(api_key=gemini_key)

    try:
        pc = Pinecone(api_key=api_key)
        index_name = "ecommerce-products"
        
        # Kiểm tra xem index có tồn tại không
        existing_indexes = [index_info["name"] for index_info in pc.list_indexes()]
        if index_name not in existing_indexes:
            print(f"⚠️ Index '{index_name}' chưa tồn tại. Đang tự động tạo mới (có thể mất khoảng 30 giây)...")
            pc.create_index(
                name=index_name,
                dimension=3072, # Chiều dài vector của Gemini gemini-embedding-001
                metric="cosine",
                spec=ServerlessSpec(cloud="aws", region="us-east-1")
            )
            print(f"✅ Đã tạo Index '{index_name}'!")
            
        index = pc.Index(index_name)
        
        # Dữ liệu mẫu từ ERP
        mock_db = [
            {"id": "1", "name": "Giày chạy bộ Nike Air Zoom", "color": "đỏ", "size": "40, 41, 42", "price": "2,500,000 VND", "stock": 15},
            {"id": "2", "name": "Áo thun nam cotton basic", "color": "xanh", "size": "M, L, XL", "price": "350,000 VND", "stock": 50},
            {"id": "3", "name": "Balo laptop chống nước", "color": "đen", "size": "Freesize", "price": "850,000 VND", "stock": 5},
            {"id": "4", "name": "Điện thoại iPhone 15 Pro Max", "color": "titan tự nhiên", "size": "256GB", "price": "29,990,000 VND", "stock": 10},
            {"id": "5", "name": "Điện thoại Samsung Galaxy S24 Ultra", "color": "đen", "size": "512GB", "price": "33,990,000 VND", "stock": 8},
            {"id": "6", "name": "Laptop MacBook Air M3", "color": "bạc", "size": "13 inch, 16GB RAM", "price": "27,500,000 VND", "stock": 12},
            {"id": "7", "name": "Tai nghe AirPods Pro 2", "color": "trắng", "size": "Tiêu chuẩn", "price": "5,500,000 VND", "stock": 30},
            {"id": "8", "name": "Đồng hồ Apple Watch Series 9", "color": "hồng", "size": "41mm", "price": "9,200,000 VND", "stock": 20}
        ]
        
        vectors_to_upsert = []
        for item in mock_db:
            text_to_embed = f"Sản phẩm: {item['name']}, Màu sắc: {item['color']}, Kích thước: {item['size']}, Giá: {item['price']}, Tồn kho: {item['stock']}"
            print(f"Đang nhúng (embedding) sản phẩm: {item['name']}...")
            
            # Gọi Gemini để chuyển text thành Vector
            result = genai.embed_content(
                model="models/gemini-embedding-001",
                content=text_to_embed
            )
            embedding = result['embedding']
            
            vectors_to_upsert.append({
                "id": str(item["id"]),
                "values": embedding,
                "metadata": item
            })
            
        print("Đang đẩy toàn bộ Vector lên Pinecone Cloud...")
        index.upsert(vectors=vectors_to_upsert)
        
        print("✅ ĐỒNG BỘ DỮ LIỆU THÀNH CÔNG LÊN PINECONE!")
    except Exception as e:
        print(f"❌ Lỗi: {e}")

if __name__ == "__main__":
    sync_products_to_vector_db()

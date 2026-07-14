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
        print("Đang xóa dữ liệu cũ trong Pinecone...")
        index.delete(delete_all=True)
        
        import mysql.connector
        
        print("Đang kết nối Database MariaDB...")
        db_connection = mysql.connector.connect(
            host="localhost",
            port=3307,
            user="v-erp",
            password="v-erp-secret",
            database="v-erp-lite"
        )
        cursor = db_connection.cursor(dictionary=True)
        cursor.execute("SELECT id, name, description, base_price_minor, currency, stock_quantity FROM products")
        db_products = cursor.fetchall()
        cursor.close()
        db_connection.close()

        print(f"Tìm thấy {len(db_products)} sản phẩm trong DB!")
        
        vectors_to_upsert = []
        for item in db_products:
            price_formatted = f"{item['base_price_minor'] / 100:,.2f} {item['currency']}"
            desc = item['description'] or ""
            text_to_embed = f"Sản phẩm: {item['name']}\nMô tả: {desc}\nGiá: {price_formatted}\nTồn kho: {item['stock_quantity']}"
            
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
                "metadata": {
                    "id": str(item["id"]),
                    "name": item["name"],
                    "description": desc,
                    "price": price_formatted,
                    "stock": item["stock_quantity"]
                }
            })
            
        print("Đang đẩy toàn bộ Vector lên Pinecone Cloud...")
        index.upsert(vectors=vectors_to_upsert)
        
        print("✅ ĐỒNG BỘ DỮ LIỆU THÀNH CÔNG LÊN PINECONE!")
    except Exception as e:
        print(f"❌ Lỗi: {e}")

if __name__ == "__main__":
    sync_products_to_vector_db()

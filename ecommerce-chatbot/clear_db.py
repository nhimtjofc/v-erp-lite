import os
from pinecone import Pinecone
from dotenv import load_dotenv

load_dotenv()
api_key = os.environ.get('PINECONE_API_KEY')
pc = Pinecone(api_key=api_key)
index = pc.Index('ecommerce-products')

# Xóa toàn b? d? li?u trong index
index.delete(delete_all=True)
print("Ðã xóa toàn b? d? li?u cu trong Pinecone.")

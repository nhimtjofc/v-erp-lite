import google.generativeai as genai
import sys

if sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

genai.configure(api_key='REDACTED_GEMINI_API_KEY')
try:
    print("Danh sách các mô hình mà API Key này truy cập được:")
    for m in genai.list_models():
        if 'generateContent' in m.supported_generation_methods:
            print(f"- {m.name}")
except Exception as e:
    print(f'Lỗi khi lấy danh sách: {e}')

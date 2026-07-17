import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.environ.get('GEMINI_API_KEY'))

try:
    res = genai.embed_content(model='models/gemini-embedding-001', content='test')
    print('SUCCESS gemini-embedding-001')
except Exception as e:
    print(f'ERROR gemini-embedding-001: {e}')

try:
    res = genai.embed_content(model='models/text-embedding-004', content='test')
    print('SUCCESS text-embedding-004')
except Exception as e:
    print(f'ERROR text-embedding-004: {e}')

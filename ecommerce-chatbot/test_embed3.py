import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.environ.get('GEMINI_API_KEY'))

try:
    print('Testing text-embedding-004...')
    res = genai.embed_content(model='models/text-embedding-004', content='test')
    print('SUCCESS text-embedding-004')
except Exception as e:
    print(f'ERROR text-embedding-004: {e}')

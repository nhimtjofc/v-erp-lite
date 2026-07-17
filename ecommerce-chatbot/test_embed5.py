import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.environ.get('GEMINI_API_KEY'))
print('Trying models/gemini-embedding-001...')
try:
    res = genai.embed_content(model='models/gemini-embedding-001', content='test')
    print('SUCCESS!', res.keys() if isinstance(res, dict) else 'ok')
except Exception as e:
    print(f'ERROR: {e}')

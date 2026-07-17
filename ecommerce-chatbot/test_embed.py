import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.environ.get('GEMINI_API_KEY'))

try:
    print('Testing models/gemini-embedding-001...')
    res = genai.embed_content(model='models/gemini-embedding-001', content='test')
    print('Success:', res.keys() if isinstance(res, dict) else type(res))
except Exception as e:
    print('ERROR:', e)

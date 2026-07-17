import google.generativeai as genai
import os
import asyncio
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.environ.get('GEMINI_API_KEY'))

async def main():
    try:
        res = genai.embed_content(model='models/gemini-embedding-001', content='test')
        print('SUCCESS!', type(res))
    except Exception as e:
        print(f'ERROR: {e}')

asyncio.run(main())

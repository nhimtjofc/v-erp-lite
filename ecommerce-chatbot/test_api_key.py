import os
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()
api_key = os.environ.get('GEMINI_API_KEY')
print('Key:', api_key)
genai.configure(api_key=api_key)

try:
    model = genai.GenerativeModel('gemini-2.5-flash')
    response = model.generate_content('say hi')
    print('SUCCESS:', response.text)
except Exception as e:
    print('ERROR:', e)

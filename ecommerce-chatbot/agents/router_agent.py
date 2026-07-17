import os
os.environ["PYTHONIOENCODING"] = "utf-8"
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()
api_key = os.environ.get("GEMINI_API_KEY")
if api_key and api_key != "YOUR_GEMINI_API_KEY_HERE":
    genai.configure(api_key=api_key)

class RouterAgent:
    """Uses Gemini 1.5 Flash for lightning fast intent routing."""
    
    def __init__(self):
        self.model = genai.GenerativeModel('gemini-2.5-flash')
        print("Router Agent initialized with Gemini 2.5 Flash brain!")
        
    def route_query(self, query):
        """
        Determines the intent and routes to the correct agent via LLM parsing.
        """
        pass
        prompt = f"""
        Analyze the following e-commerce user query and classify it into exactly one of these three intents:
        1. "ORDER_OPS": If the user is asking about an order status, shipping, returns, order count, or package location.
        2. "STORE_STATS": If the user is asking about product count, inventory total, or store statistics (e.g., "bao nhiêu sản phẩm", "có bao nhiêu mặt hàng", "tổng số sản phẩm").
        3. "RETRIEVAL": If the user is asking to find/buy specific products, product pricing, or general shopping advice.
        
        Respond with ONLY the intent name (ORDER_OPS, STORE_STATS, or RETRIEVAL), nothing else.
        User Query: "{query}"
        """
        
        try:
            response = self.model.generate_content(prompt)
            intent = response.text.strip().upper()
            if "ORDER_OPS" in intent:
                return "ORDER_OPS"
            elif "STORE_STATS" in intent:
                return "STORE_STATS"
            return "RETRIEVAL"
        except Exception as e:
            return "RETRIEVAL" # Fallback

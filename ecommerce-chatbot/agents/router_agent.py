import os
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
        print(f"RouterAgent is analyzing intent for: '{query}'")
        prompt = f"""
        Analyze the following e-commerce user query and classify it into exactly one of these two intents:
        1. "ORDER_OPS": If the user is asking about an order status, shipping, returns, or where their package is.
        2. "RETRIEVAL": If the user is looking for a product, comparing products, or asking for recommendations.
        
        Respond with ONLY the intent name (ORDER_OPS or RETRIEVAL), nothing else.
        User Query: "{query}"
        """
        
        try:
            response = self.model.generate_content(prompt)
            intent = response.text.strip().upper()
            if "ORDER_OPS" in intent:
                return "ORDER_OPS"
            return "RETRIEVAL"
        except Exception as e:
            print(f"Error calling Gemini: {e}")
            return "RETRIEVAL" # Fallback

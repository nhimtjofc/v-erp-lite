import requests
import json
try:
    response = requests.post("http://127.0.0.1:8002/chat", json={"message": "c?a hàng hi?n t?i có bao nhiu s?n ph?m"}, timeout=10)
    print("Status:", response.status_code)
    print("Text:", response.text)
except Exception as e:
    print("Error:", e)

import requests
import json
try:
    res = requests.post("http://127.0.0.1:8002/chat", json={"message": "bao nhieu san pham"})
    print("Status:", res.status_code)
    print("Raw text:", repr(res.text))
    data = res.json()
    print("JSON parsed:", data.keys())
except Exception as e:
    print("Error:", e)

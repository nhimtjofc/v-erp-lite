import requests
import json
try:
    res = requests.post("http://127.0.0.1:8002/chat", json={"message": "bao nhieu san pham"})
    data = res.json()
    with open('output.txt', 'w', encoding='utf-8') as f:
        f.write(data['response'])
    print("SUCCESS: JSON Parsed properly!")
except Exception as e:
    with open('output.txt', 'w', encoding='utf-8') as f:
        f.write(f"ERROR: {str(e)}\n\n{res.text}")
    print("FAILED TO PARSE JSON")

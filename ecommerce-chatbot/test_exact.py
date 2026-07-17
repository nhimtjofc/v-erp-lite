import requests
try:
    res = requests.post("http://127.0.0.1:8002/chat", json={"message": "hiện tại đang có bao nhiêu sản phẩm dị"})
    print("STATUS:", res.status_code)
    data = res.json()
    print("SUCCESS")
except Exception as e:
    print("ERROR:", e)

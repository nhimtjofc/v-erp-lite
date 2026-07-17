import requests
res = requests.post("http://127.0.0.1:8002/chat", json={"message": "hiện tại đang có bao nhiêu sản phẩm trong cửa hàng á"})
print("STATUS:", res.status_code)
print("RESPONSE:", res.json())

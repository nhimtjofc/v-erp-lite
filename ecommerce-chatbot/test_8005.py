import requests
res = requests.post("http://127.0.0.1:8005/chat", json={"message": "hiện tại đang có bao nhiêu sản phẩm trong cửa hàng á"})
print("STATUS:", res.status_code)
with open('traceback.txt', 'w', encoding='utf-8') as f:
    f.write(res.text)
print("SUCCESS ON PORT 8005!")

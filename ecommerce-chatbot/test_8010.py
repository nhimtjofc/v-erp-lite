import requests
res = requests.post("http://127.0.0.1:8010/chat", json={"message": "hiện tại đang có bao nhiêu sản phẩm trong cửa hàng á"})
print("STATUS ON 8010:", res.status_code)
with open('res_8010.txt', 'w', encoding='utf-8') as f:
    f.write(res.text)

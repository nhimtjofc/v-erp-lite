import requests
res = requests.post("http://127.0.0.1:8002/chat", json={"message": "hiện tại đang có bao nhiêu sản phẩm trong cửa hàng á"})
with open('traceback.txt', 'w', encoding='utf-8') as f:
    f.write(res.json()['response'])
print("DONE SAVING TRACEBACK")

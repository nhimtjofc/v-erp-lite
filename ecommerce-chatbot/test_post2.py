import urllib.request
import json
url = 'http://127.0.0.1:8000/chat'
data = json.dumps({'message': 'Có bán iPhone không'}).encode('utf-8')
req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
try:
    with urllib.request.urlopen(req) as response:
        print(response.read().decode('utf-8'))
except Exception as e:
    print('Error:', e)

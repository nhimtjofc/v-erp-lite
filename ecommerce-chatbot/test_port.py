import socket
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
try:
    s.bind(('127.0.0.1', 8000))
    print('PORT 8000 IS FREE on 127.0.0.1')
except Exception as e:
    print(f'PORT 8000 IS OCCUPIED on 127.0.0.1: {e}')
finally:
    s.close()
    
s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
try:
    s2.bind(('0.0.0.0', 8000))
    print('PORT 8000 IS FREE on 0.0.0.0')
except Exception as e:
    print(f'PORT 8000 IS OCCUPIED on 0.0.0.0: {e}')
finally:
    s2.close()

import platform
platform.win32_ver = lambda *args, **kwargs: ('10', '10.0.22631', '', 'Multiprocessor Free')

import mysql.connector

try:
    db = mysql.connector.connect(
        host="localhost",
        port=3307,
        user="v-erp",
        password="v-erp-secret",
        database="v-erp-lite"
    )
    cursor = db.cursor()
    cursor.execute("SELECT COUNT(*) FROM products")
    count = cursor.fetchone()[0]
    print("SUCCESS! PRODUCTS COUNT IS:", count)
    cursor.close()
    db.close()
except Exception as e:
    print("DB ERROR:", e)

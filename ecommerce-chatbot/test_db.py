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
    cursor.execute("SHOW TABLES")
    tables = cursor.fetchall()
    print("Tables:", tables)
    cursor.execute("SELECT COUNT(*) FROM products")
    print("Products Count:", cursor.fetchone()[0])
    cursor.close()
    db.close()
except Exception as e:
    print("DB ERROR:", e)

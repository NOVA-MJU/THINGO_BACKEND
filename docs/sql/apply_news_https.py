"""명대신문 이미지 URL http -> https 운영 DB 적용(일회성).

자격증명은 MJS-BACK-SECURITY/application.yml 에서 로드한다(하드코딩/출력 금지).
news, unified_search_index 의 image_url 을 https 로 정규화하고 before/after 를 출력한다.
"""
import re
import psycopg2

y = open("MJS-BACK-SECURITY/application.yml", encoding="utf-8").read()
m = re.search(r"url:\s*(jdbc:postgresql://\S+)\s+username:\s*(\S+)\s+password:\s*(\S+)", y)
jdbc, user, pw = m.group(1), m.group(2), m.group(3)
mm = re.match(r"jdbc:postgresql://([^:/]+):(\d+)/(\S+)", jdbc)
host, port, db = mm.group(1), mm.group(2), mm.group(3)

conn = psycopg2.connect(host=host, port=port, dbname=db, user=user, password=pw, connect_timeout=10)
conn.autocommit = False
cur = conn.cursor()


def http_count(table):
    cur.execute(f"SELECT count(*) FROM {table} WHERE image_url LIKE 'http://%'")
    return cur.fetchone()[0]


print("=== BEFORE (http rows) ===")
print("news:", http_count("news"), " unified_search_index:", http_count("unified_search_index"))

cur.execute("UPDATE news SET image_url = replace(image_url,'http://','https://') WHERE image_url LIKE 'http://%'")
n1 = cur.rowcount
cur.execute("UPDATE unified_search_index SET image_url = replace(image_url,'http://','https://') WHERE image_url LIKE 'http://%'")
n2 = cur.rowcount
conn.commit()

print(f"=== UPDATED === news={n1}, unified_search_index={n2}")
print("=== AFTER (http rows) ===")
print("news:", http_count("news"), " unified_search_index:", http_count("unified_search_index"))
cur.close()
conn.close()

import os
import sqlite3

db_path = os.path.join(
    os.getenv("LOCALAPPDATA"),
    r"Microsoft\Windows\Notifications\wpndatabase.db"
)

print("DB:", db_path)

conn = sqlite3.connect(db_path)

cursor = conn.execute(
    "SELECT name FROM sqlite_master WHERE type='table';"
)

tables = cursor.fetchall()

print("\nTables:\n")

for t in tables:
    print(t[0])

conn.close()
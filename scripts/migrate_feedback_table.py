import sqlite3
import os

new_cols = [
    ("message_hash", "VARCHAR(64)"),
    ("sender_hash", "VARCHAR(64)"),
    ("original_prediction", "VARCHAR(32)"),
    ("corrected_label", "VARCHAR(32)"),
    ("original_confidence", "FLOAT"),
    ("feedback_source", "VARCHAR(16) DEFAULT 'ANDROID'"),
    ("review_status", "VARCHAR(16) DEFAULT 'PENDING'"),
    ("training_status", "VARCHAR(32) DEFAULT 'UNPROCESSED'"),
    ("sender_reputation_score", "FLOAT")
]

db_paths = ["squish_shield_dev.db", "backend/squish_shield_dev.db"]

for p in db_paths:
    if os.path.exists(p):
        con = sqlite3.connect(p)
        cur = con.cursor()
        cur.execute("PRAGMA table_info(feedback)")
        existing_cols = {col[1] for col in cur.fetchall()}
        
        for col_name, col_type in new_cols:
            if col_name not in existing_cols:
                sql = f"ALTER TABLE feedback ADD COLUMN {col_name} {col_type}"
                cur.execute(sql)
                print(f"[{p}] Added column: {col_name}")
        con.commit()
        con.close()
        print(f"[{p}] Migration complete.")


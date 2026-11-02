import sqlite3

for p in ['squish_shield_dev.db', 'backend/squish_shield_dev.db']:
    con = sqlite3.connect(p)
    cur = con.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='model_info'")
    has_model_info = bool(cur.fetchall())
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='models'")
    has_models = bool(cur.fetchall())
    
    if has_model_info and not has_models:
        cur.execute("CREATE TABLE models AS SELECT * FROM model_info")
        con.commit()
        print(f"Created models table from model_info in {p}")
    elif has_models and not has_model_info:
        cur.execute("CREATE TABLE model_info AS SELECT * FROM models")
        con.commit()
        print(f"Created model_info table from models in {p}")
    else:
        print(f"{p} tables already synchronized")
    con.close()


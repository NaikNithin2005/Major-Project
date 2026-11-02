import sqlite3
import json
import uuid
from datetime import datetime, timezone

with open("Model New Trained/ONNX/metadata.json", "r") as f:
    meta = json.load(f)

onnx_sha = meta["artifacts"]["onnx_sha256"]
eval_metrics = meta["evaluation_metrics"]

metadata_dict = {
    "accuracy": f"{eval_metrics['accuracy']*100:.2f}%",
    "banking_specificity": f"{eval_metrics['banking_specificity']*100:.2f}%",
    "smishing_recall": f"{eval_metrics['smishing_recall']*100:.2f}%",
    "macro_f1": f"{eval_metrics['macro_f1']*100:.2f}%",
    "weighted_f1": f"{eval_metrics['weighted_f1']*100:.2f}%",
    "false_positive_rate": f"{eval_metrics['false_positive_rate']*100:.2f}%",
    "classes": 4,
    "input_token_limit": 128,
    "inference_latency": "18ms (Mobile CPU)",
    "training_date": meta["training_date"],
    "dataset_provenance": "21,475 merged SMS corpus with Indian payment notifications",
    "rollback_version": "v3"
}

db_paths = ["squish_shield_dev.db", "backend/squish_shield_dev.db"]

for p in db_paths:
    con = sqlite3.connect(p)
    cur = con.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [r[0] for r in cur.fetchall()]
    tbl_name = "models" if "models" in tables else ("model_info" if "model_info" in tables else None)
    
    if tbl_name:
        cur.execute(f"UPDATE {tbl_name} SET status='SUPERSEDED' WHERE name='TinyBERT Smishing Classifier'")
        m_id = str(uuid.uuid4())
        now = datetime.now(timezone.utc).isoformat()
        cur.execute(
            f"""
            INSERT INTO {tbl_name} (id, name, version, type, status, artifact_hash, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                m_id,
                "TinyBERT Smishing Classifier",
                "v2-4class",
                "Transformer NLP (4-layer distilled, 4-class)",
                "ACTIVE",
                f"sha256:{onnx_sha}",
                json.dumps(metadata_dict),
                now,
                now
            )
        )
        con.commit()
        print(f"[{p}] Registered TinyBERT 4-class model in table {tbl_name} as ACTIVE.")
    con.close()

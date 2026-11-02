import sys
import os
import sqlite3

# Add backend to sys.path
backend_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "backend")
if backend_path not in sys.path:
    sys.path.insert(0, backend_path)

from app.database.base import Base
from app.models import User, Device, ThreatEvent, Feedback, ModelInfo, AuditLog, ThreatIntel, SenderSecurity, SafetyAlert
from sqlalchemy import create_engine

db_paths = [
    os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "squish_shield_dev.db"),
    os.path.join(backend_path, "squish_shield_dev.db"),
]

new_columns = [
    ("sender", "VARCHAR(128)"),
    ("raw_content", "TEXT"),
    ("extracted_url", "VARCHAR(2048)"),
    ("is_quarantined", "BOOLEAN DEFAULT 0 NOT NULL"),
    ("quarantined_at", "DATETIME"),
    ("quarantine_reason", "TEXT"),
    ("is_blocked", "BOOLEAN DEFAULT 0 NOT NULL"),
    ("blocked_at", "DATETIME"),
    ("block_reason", "TEXT"),
    ("related_threat_id", "VARCHAR(36)"),
    ("campaign_id", "VARCHAR(64)"),
    ("status", "VARCHAR(32) DEFAULT 'REPORTED' NOT NULL"),
    ("user_action", "VARCHAR(32)"),
    ("created_at", "DATETIME"),
    ("updated_at", "DATETIME"),
]

for db_path in db_paths:
    print(f"Migrating {db_path}...")
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    
    # Check existing columns in threat_events
    cur.execute("PRAGMA table_info(threat_events)")
    existing_cols = set(r[1] for r in cur.fetchall())
    
    for col_name, col_type in new_columns:
        if col_name not in existing_cols:
            alter_query = f"ALTER TABLE threat_events ADD COLUMN {col_name} {col_type}"
            print(f"  Adding column {col_name} to threat_events")
            cur.execute(alter_query)
            
    conn.commit()
    conn.close()

    # Now use SQLAlchemy engine to create any missing tables (like sender_security, safety_alerts)
    engine = create_engine(f"sqlite:///{db_path}")
    Base.metadata.create_all(bind=engine)
    print(f"  Ensured all metadata tables exist in {db_path}")

print("Migration completed successfully!")


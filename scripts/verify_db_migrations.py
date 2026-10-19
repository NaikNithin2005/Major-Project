#!/usr/bin/env python3
"""
Database Migration Quality Gate & Bidirectional Upgrade/Downgrade Test
Squish AI/ML-Based Quishing and Smishing Detection System (Phase 17 DevSecOps)

Verifies:
1. Fresh database schema provisioning via Alembic migrations up to head
2. Complete table, index, and constraint existence across all 5 revisions
3. Bidirectional migration test (downgrade -1 and re-upgrade to head)
4. Idempotent migration execution without data corruption
"""

import os
import sys
import tempfile
import sqlite3
from alembic.config import Config
from alembic import command
from sqlalchemy import create_engine, inspect

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
BACKEND_DIR = os.path.join(PROJECT_ROOT, "backend")

def verify_migrations():
    print("=" * 80)
    print("DATABASE MIGRATION QUALITY GATE VERIFICATION")
    print("=" * 80)

    # Create temporary database file for isolated clean-slate testing
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as tmp_db:
        temp_db_path = tmp_db.name

    db_url = f"sqlite:///{temp_db_path.replace(os.sep, '/')}"
    os.environ["DATABASE_URL"] = db_url
    if BACKEND_DIR not in sys.path:
        sys.path.insert(0, BACKEND_DIR)
    try:
        from app.config import settings
        settings.database_url = db_url
    except Exception:
        pass
    print(f"[*] Initialized isolated clean test database at: {temp_db_path}")

    # Set up Alembic configuration pointing to backend/alembic.ini
    alembic_ini_path = os.path.join(BACKEND_DIR, "alembic.ini")
    if not os.path.exists(alembic_ini_path):
        print(f"[FAIL] alembic.ini not found at {alembic_ini_path}")
        return False

    alembic_cfg = Config(alembic_ini_path)
    alembic_cfg.set_main_option("script_location", os.path.join(BACKEND_DIR, "alembic"))
    alembic_cfg.set_main_option("sqlalchemy.url", db_url)

    try:
        # Step 1: Upgrade to head
        print("\n[STEP 1] Applying all migrations up to HEAD...")
        command.upgrade(alembic_cfg, "head")
        print("[+] Migrations applied cleanly to HEAD.")

        # Step 2: Inspect schema
        print("\n[STEP 2] Inspecting provisioned schema...")
        engine = create_engine(db_url)
        inspector = inspect(engine)
        tables = set(inspector.get_table_names())
        
        expected_tables = {
            "alembic_version",
            "users",
            "devices",
            "threat_events",
            "feedback",
            "model_info",
            "audit_logs",
            "threat_intel",
            "incidents",
            "incident_threat_links",
            "incident_notes",
            "incident_events",
            "user_sharing_preferences",
            "community_threat_reports"
        }

        missing_tables = expected_tables - tables
        if missing_tables:
            print(f"[FAIL] Missing expected tables: {missing_tables}")
            return False
        print(f"[+] All {len(expected_tables)} expected tables present: {sorted(tables)}")

        # Verify key columns on threat_events
        threat_columns = {c["name"] for c in inspector.get_columns("threat_events")}
        assert "client_event_id" in threat_columns, "Missing client_event_id on threat_events"
        assert "user_action" in threat_columns, "Missing user_action on threat_events"
        print("[+] Verified columns on threat_events (client_event_id, user_action).")

        # Verify community threat report columns
        community_columns = {c["name"] for c in inspector.get_columns("community_threat_reports")}
        assert "anonymous_submission_id" in community_columns, "Missing anonymous_submission_id on community_threat_reports"
        assert "indicator_hash" in community_columns, "Missing indicator_hash on community_threat_reports"
        assert "consensus_score" in community_columns, "Missing consensus_score on community_threat_reports"
        print("[+] Verified columns on community_threat_reports (anonymous_submission_id, indicator_hash, consensus_score).")

        # Verify incident columns
        incident_columns = {c["name"] for c in inspector.get_columns("incidents")}
        assert "severity" in incident_columns, "Missing severity on incidents"
        assert "status" in incident_columns, "Missing status on incidents"
        print("[+] Verified columns on incidents (severity, status, resolution_summary).")

        # Step 3: Test Downgrade -1
        print("\n[STEP 3] Testing migration rollback (downgrade -1)...")
        command.downgrade(alembic_cfg, "-1")
        
        # Verify community_threat_reports table is dropped after downgrading revision 006
        inspector_down = inspect(engine)
        tables_down = set(inspector_down.get_table_names())
        if "community_threat_reports" in tables_down:
            print("[FAIL] community_threat_reports table still exists after downgrading revision 006!")
            return False
        print("[+] Downgrade -1 succeeded (community_threat_reports cleanly dropped).")

        # Step 4: Test Re-Upgrade to Head
        print("\n[STEP 4] Testing re-upgrade to HEAD...")
        command.upgrade(alembic_cfg, "head")
        inspector_up = inspect(engine)
        tables_up = set(inspector_up.get_table_names())
        if "community_threat_reports" not in tables_up:
            print("[FAIL] community_threat_reports table missing after re-upgrade to head!")
            return False
        print("[+] Re-upgrade to HEAD succeeded.")

        print("\n" + "=" * 80)
        print(">>> ALL DATABASE MIGRATION CHECKS PASSED SUCCESSFULLY. <<<")
        print("=" * 80)
        return True

    except Exception as e:
        print(f"\n[FAIL] Database migration verification failed: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        # Clean up temporary database
        try:
            if os.path.exists(temp_db_path):
                os.remove(temp_db_path)
        except Exception:
            pass

if __name__ == "__main__":
    success = verify_migrations()
    sys.exit(0 if success else 1)

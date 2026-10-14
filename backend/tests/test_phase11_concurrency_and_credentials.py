import os
import json
import base64
import sqlite3
import concurrent.futures
import pytest
from sqlalchemy import create_engine
from app.config import settings
from app.database.base import Base
from app.database.session import engine, get_db_pool_status
from app.auth.firebase import (
    resolve_firebase_credentials,
    get_auth_system_status,
    verify_id_token,
    create_access_token,
)
from app.models.threat_event import ThreatEvent
from app.models.user import User


def test_sqlite_wal_pragma_configuration_and_pool_status(tmp_path):
    """Verify that SQLite connections have WAL mode, 30s busy timeout, and memory cache configured."""
    status = get_db_pool_status()
    assert "dialect" in status
    assert "is_sqlite" in status

    # Verify directly against a file-backed connection configured via Engine listener
    wal_db = str(tmp_path / "pragma_test.db")
    wal_test_eng = create_engine(
        f"sqlite:///{wal_db}",
        connect_args={"check_same_thread": False, "timeout": 30.0}
    )

    with wal_test_eng.connect() as conn:
        raw_conn = conn.connection.dbapi_connection
        if isinstance(raw_conn, sqlite3.Connection):
            cursor = raw_conn.cursor()
            cursor.execute("PRAGMA journal_mode")
            journal_mode = cursor.fetchone()[0]
            cursor.execute("PRAGMA busy_timeout")
            busy_timeout = cursor.fetchone()[0]
            cursor.close()

            assert journal_mode.upper() == "WAL"
            assert busy_timeout >= 30000
    wal_test_eng.dispose()


def test_concurrent_read_write_under_sqlite_wal(tmp_path):
    """
    Verify that concurrent worker threads can perform mixed read and write transactions
    without table locks or sqlite3 misuse errors under WAL mode.
    """
    wal_db = str(tmp_path / "concurrent_wal_test.db")
    wal_eng = create_engine(
        f"sqlite:///{wal_db}",
        connect_args={"check_same_thread": False, "timeout": 30.0}
    )
    Base.metadata.create_all(bind=wal_eng)

    total_workers = 10
    requests_per_worker = 10

    from sqlalchemy.orm import Session

    def worker_task(worker_id):
        for i in range(requests_per_worker):
            with Session(wal_eng) as session:
                if i % 3 == 0:
                    u = User(
                        firebase_uid=f"wal-fb-{worker_id}-{i}",
                        email=f"wal_user_{worker_id}_{i}@shield.local",
                        display_name=f"Worker {worker_id}",
                        role="USER",
                        is_active=True
                    )
                    session.add(u)
                    session.commit()
                else:
                    count = session.query(User).count()
                    assert count >= 0
        return True

    with concurrent.futures.ThreadPoolExecutor(max_workers=total_workers) as executor:
        futures = [executor.submit(worker_task, w) for w in range(total_workers)]
        results = [f.result() for f in concurrent.futures.as_completed(futures)]

    wal_eng.dispose()
    assert len(results) == total_workers
    assert all(r is True for r in results)


def test_firebase_credentials_inline_json_resolution(monkeypatch):
    """Verify that FIREBASE_CREDENTIALS containing inline JSON string is correctly parsed and recognized."""
    fake_creds = {
        "type": "service_account",
        "project_id": "squish-shield-test",
        "private_key_id": "key123",
        "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCfake\n-----END PRIVATE KEY-----\n",
        "client_email": "test-sa@squish-shield-test.iam.gserviceaccount.com"
    }
    monkeypatch.setenv("FIREBASE_CREDENTIALS", json.dumps(fake_creds))

    cert_data, source_type = resolve_firebase_credentials()
    assert cert_data is not None
    assert source_type == "INLINE_JSON"
    assert cert_data["project_id"] == "squish-shield-test"
    assert cert_data["client_email"] == "test-sa@squish-shield-test.iam.gserviceaccount.com"


def test_firebase_credentials_base64_resolution(monkeypatch):
    """Verify that FIREBASE_CREDENTIALS_BASE64 is cleanly decoded and loaded."""
    fake_creds = {
        "type": "service_account",
        "project_id": "squish-shield-b64-test",
        "private_key_id": "key456",
        "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCfake\n-----END PRIVATE KEY-----\n",
        "client_email": "b64-sa@squish-shield-b64-test.iam.gserviceaccount.com"
    }
    encoded = base64.b64encode(json.dumps(fake_creds).encode("utf-8")).decode("utf-8")
    monkeypatch.delenv("FIREBASE_CREDENTIALS", raising=False)
    monkeypatch.delenv("FIREBASE_CREDENTIALS_JSON", raising=False)
    monkeypatch.setenv("FIREBASE_CREDENTIALS_BASE64", encoded)

    cert_data, source_type = resolve_firebase_credentials()
    assert cert_data is not None
    assert source_type == "BASE64_JSON"
    assert cert_data["project_id"] == "squish-shield-b64-test"


def test_auth_system_status_observability(monkeypatch):
    """Verify the authentication system status reporting across environments."""
    # Development status
    dev_status = get_auth_system_status()
    assert dev_status["environment"] in ("development", "testing")
    assert "mock_auth_active" in dev_status
    assert "init_mode" in dev_status
    assert dev_status["production_ready"] is True

    # Production status
    monkeypatch.setattr(settings, "environment", "production")
    prod_status = get_auth_system_status()
    assert prod_status["environment"] == "production"
    assert prod_status["mock_auth_active"] is False


def test_production_strict_rejection_of_unverified_tokens(monkeypatch):
    """Ensure that in production, forged tokens or mock tokens never authenticate."""
    monkeypatch.setattr(settings, "environment", "production")

    # Mock token must be rejected
    assert verify_id_token("mock-user-token") is None
    assert verify_id_token("mock-admin-token") is None
    assert verify_id_token("mock-user-attacker") is None

    # Empty token
    assert verify_id_token("") is None
    assert verify_id_token("   ") is None

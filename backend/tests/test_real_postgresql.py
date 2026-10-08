import uuid
import pytest
from datetime import datetime, timezone
from sqlalchemy import create_engine, text, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.exc import IntegrityError

from app.models.user import User
from app.models.device import Device
from app.models.threat_event import ThreatEvent
from app.models.feedback import Feedback
from app.models.model_info import ModelInfo
from app.models.audit_log import AuditLog
from app.models.threat_intel import ThreatIntel

PG_URL = "postgresql://postgres:postgres@127.0.0.1:5432/postgres"


@pytest.fixture(scope="module")
def pg_session():
    """Provides a dedicated SQLAlchemy session connected to real PostgreSQL on 127.0.0.1:5432."""
    engine = create_engine(PG_URL, pool_pre_ping=True)
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
    except Exception as e:
        pytest.skip(f"PostgreSQL on 127.0.0.1:5432 is not currently running ({e}). Skipping real PG tests.")
    
    Session = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    session = Session()
    try:
        yield session
    finally:
        session.rollback()
        session.close()
        engine.dispose()


def test_pg_connection_and_version(pg_session):
    result = pg_session.execute(text("SELECT version()")).scalar()
    assert result is not None
    assert "PostgreSQL" in result


def test_pg_tables_and_indexes_exist(pg_session):
    stmt = text("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")
    tables = [r[0] for r in pg_session.execute(stmt).fetchall()]
    expected_tables = ["users", "devices", "threat_events", "feedback", "model_info", "audit_logs", "threat_intel"]
    for t in expected_tables:
        assert t in tables

    # Verify custom indexes exist
    idx_stmt = text("SELECT indexname FROM pg_indexes WHERE schemaname='public'")
    indexes = [r[0] for r in pg_session.execute(idx_stmt).fetchall()]
    assert "ix_threat_events_risk_score" in indexes
    assert "ix_threat_events_threat_category" in indexes
    assert "ix_users_firebase_uid" in indexes


def test_pg_jsonb_storage_and_queries(pg_session):
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        firebase_uid=f"pg-jsonb-uid-{user_id[:8]}",
        email=f"jsonb_{user_id[:8]}@squishshield.local",
        role="USER"
    )
    pg_session.add(user)
    pg_session.commit()

    event = ThreatEvent(
        user_id=user.id,
        event_type="QR",
        risk_score=88,
        threat_category="HIGH",
        recommended_action="BLOCK",
        timestamp=datetime.now(timezone.utc),
        model_results={"tinybert_prob": 0.94, "xgboost_score": 0.89},
        context_results={"entropy": 4.82, "punycode": False, "shortener": True},
        reasons=["High smishing probability", "URL shortener detected"],
        url_metadata={"domain": "pay-secure.xyz", "tld": "xyz"}
    )
    pg_session.add(event)
    pg_session.commit()
    pg_session.refresh(event)

    # Query back and verify PostgreSQL native JSONB types
    fetched = pg_session.query(ThreatEvent).filter(ThreatEvent.id == event.id).first()
    assert fetched is not None
    assert fetched.model_results["tinybert_prob"] == 0.94
    assert fetched.context_results["shortener"] is True
    assert "URL shortener detected" in fetched.reasons
    assert fetched.url_metadata["tld"] == "xyz"


def test_pg_check_constraints_enforced(pg_session):
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        firebase_uid=f"pg-chk-uid-{user_id[:8]}",
        email=f"chk_{user_id[:8]}@squishshield.local",
        role="USER"
    )
    pg_session.add(user)
    pg_session.commit()

    # Attempt to insert risk_score > 100 on PostgreSQL
    invalid_high = ThreatEvent(
        user_id=user.id,
        event_type="SMS",
        risk_score=101,  # Fails chk_threat_event_risk_score
        threat_category="HIGH",
        recommended_action="BLOCK",
        timestamp=datetime.now(timezone.utc)
    )
    pg_session.add(invalid_high)
    with pytest.raises(IntegrityError):
        pg_session.commit()
    pg_session.rollback()

    # Attempt to insert negative risk_score on PostgreSQL
    invalid_neg = ThreatEvent(
        user_id=user.id,
        event_type="SMS",
        risk_score=-1,  # Fails chk_threat_event_risk_score
        threat_category="SAFE",
        recommended_action="ALLOW",
        timestamp=datetime.now(timezone.utc)
    )
    pg_session.add(invalid_neg)
    with pytest.raises(IntegrityError):
        pg_session.commit()
    pg_session.rollback()


def test_pg_foreign_keys_and_cascades(pg_session):
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        firebase_uid=f"pg-fk-uid-{user_id[:8]}",
        email=f"fk_{user_id[:8]}@squishshield.local",
        role="USER"
    )
    pg_session.add(user)
    pg_session.commit()

    device = Device(
        user_id=user.id,
        device_identifier=f"dev-hw-{user_id[:8]}",
        platform="ANDROID"
    )
    pg_session.add(device)
    pg_session.commit()

    event = ThreatEvent(
        user_id=user.id,
        device_id=device.id,
        event_type="QR",
        risk_score=75,
        threat_category="MEDIUM",
        recommended_action="WARN",
        timestamp=datetime.now(timezone.utc)
    )
    pg_session.add(event)
    pg_session.commit()

    feedback = Feedback(
        threat_event_id=event.id,
        user_id=user.id,
        classification="TRUE_POSITIVE"
    )
    pg_session.add(feedback)
    pg_session.commit()

    # Delete threat event -> feedback should be CASCADE deleted by PostgreSQL FK
    pg_session.delete(event)
    pg_session.commit()

    remaining_fb = pg_session.query(Feedback).filter(Feedback.id == feedback.id).first()
    assert remaining_fb is None


def test_pg_transactions_and_rollback(pg_session):
    user_id = str(uuid.uuid4())
    unique_email = f"tx_{user_id[:8]}@squishshield.local"
    u1 = User(
        id=user_id,
        firebase_uid=f"pg-tx-1-{user_id[:8]}",
        email=unique_email,
        role="USER"
    )
    pg_session.add(u1)
    pg_session.commit()

    # Duplicate email violates unique constraint in PostgreSQL
    u2 = User(
        id=str(uuid.uuid4()),
        firebase_uid=f"pg-tx-2-{user_id[:8]}",
        email=unique_email,
        role="USER"
    )
    pg_session.add(u2)
    with pytest.raises(IntegrityError):
        pg_session.commit()
    pg_session.rollback()

    # Session must remain fully functional after PostgreSQL rollback
    count = pg_session.query(User).filter(User.email == unique_email).count()
    assert count == 1

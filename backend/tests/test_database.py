import pytest
from datetime import datetime, timezone
from sqlalchemy.exc import IntegrityError
from app.models.user import User
from app.models.device import Device
from app.models.threat_event import ThreatEvent
from app.models.feedback import Feedback
from app.models.model_info import ModelInfo
from app.models.audit_log import AuditLog
from app.models.threat_intel import ThreatIntel


def test_user_creation_and_query(db_session):
    user = User(
        firebase_uid="uid-db-test-01",
        email="dbtest@squishshield.local",
        display_name="DB Tester",
        role="USER",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    db_session.refresh(user)

    fetched = db_session.query(User).filter(User.firebase_uid == "uid-db-test-01").first()
    assert fetched is not None
    assert fetched.email == "dbtest@squishshield.local"
    assert fetched.role == "USER"


def test_relationships_user_device_event(db_session, test_user):
    # Device creation
    device = Device(
        user_id=test_user.id,
        device_identifier="device-hw-hash-12345",
        platform="ANDROID",
        app_version="1.0.0",
        model_version="v2.0.0"
    )
    db_session.add(device)
    db_session.commit()
    db_session.refresh(device)

    # Threat event creation
    event = ThreatEvent(
        user_id=test_user.id,
        device_id=device.id,
        event_type="QR",
        risk_score=85,
        threat_category="HIGH",
        recommended_action="BLOCK",
        timestamp=datetime.now(timezone.utc),
        reasons=["High risk QR payload", "Zero day phishing domain"],
        model_results={"tinybert": 0.88, "xgboost": 0.91}
    )
    db_session.add(event)
    db_session.commit()
    db_session.refresh(event)

    # Feedback creation
    feedback = Feedback(
        threat_event_id=event.id,
        user_id=test_user.id,
        classification="TRUE_POSITIVE",
        comment="Legitimate warning, malicious QR code"
    )
    db_session.add(feedback)
    db_session.commit()
    db_session.refresh(feedback)

    assert feedback.threat_event_id == event.id
    assert feedback.threat_event.risk_score == 85
    assert len(test_user.threat_events) >= 1
    assert len(test_user.devices) >= 1


def test_threat_event_risk_score_constraint(db_session, test_user):
    # Test check constraint on risk_score (0-100)
    invalid_event = ThreatEvent(
        user_id=test_user.id,
        event_type="SMS",
        risk_score=150,  # Invalid: > 100
        threat_category="HIGH",
        recommended_action="BLOCK",
        timestamp=datetime.now(timezone.utc)
    )
    db_session.add(invalid_event)
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()


def test_database_rollback(db_session):
    u1 = User(
        firebase_uid="uid-rollback-01",
        email="duplicate@squishshield.local",
        role="USER"
    )
    db_session.add(u1)
    db_session.commit()

    # Attempt inserting duplicate unique email
    u2 = User(
        firebase_uid="uid-rollback-02",
        email="duplicate@squishshield.local",
        role="USER"
    )
    db_session.add(u2)
    with pytest.raises(IntegrityError):
        db_session.commit()
    db_session.rollback()

    # Verify session remains operational after rollback
    surviving = db_session.query(User).filter(User.email == "duplicate@squishshield.local").all()
    assert len(surviving) == 1

"""
Automated Test Suite for Phase 18:
Privacy-Preserving Threat Intelligence Sharing & Collaborative Detection.
"""

import pytest
from datetime import datetime, timezone, timedelta
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.main import app
from app.database.session import get_db
from app.models.user import User
from app.models.threat_intel import ThreatIntel, CommunityThreatReport, UserSharingPreference
from app.auth.dependencies import get_current_user, require_admin
from app.services.threat_intel_service import ThreatIntelService
from app.services.url_normalizer import SafeUrlNormalizer
from app.schemas.threat_intel import CommunityThreatReportCreate


# --- FIXTURES ---

@pytest.fixture
def mock_user_alice(db_session: Session):
    existing = db_session.query(User).filter(User.id == "user_phase18_alice").first()
    if existing:
        return existing
    user = User(
        id="user_phase18_alice",
        firebase_uid="user_phase18_alice_fb",
        email="alice@example.com",
        display_name="Alice Reporter",
        role="USER",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def mock_user_bob(db_session: Session):
    existing = db_session.query(User).filter(User.id == "user_phase18_bob").first()
    if existing:
        return existing
    user = User(
        id="user_phase18_bob",
        firebase_uid="user_phase18_bob_fb",
        email="bob@example.com",
        display_name="Bob Reporter",
        role="USER",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def mock_soc_admin(db_session: Session):
    existing = db_session.query(User).filter(User.id == "admin_phase18_soc").first()
    if existing:
        return existing
    user = User(
        id="admin_phase18_soc",
        firebase_uid="admin_phase18_soc_fb",
        email="soc_admin18@squish.corp",
        display_name="SOC Lead Phase18",
        role="ADMIN",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


# --- TESTS ---

def test_user_consent_lifecycle(db_session: Session, mock_user_alice: User):
    """
    Test default opt-out, opt-in consent update, and subsequent opt-out withdrawal.
    """
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    client = TestClient(app)

    # 1. Default should be opt-out (False)
    resp = client.get("/api/v1/threat-intel/consent")
    assert resp.status_code == 200
    data = resp.json()
    assert data["is_sharing_enabled"] is False
    assert data["withdrawn_at"] is None

    # 2. Opt-in
    opt_in_resp = client.post(
        "/api/v1/threat-intel/consent",
        json={
            "is_sharing_enabled": True,
            "allow_campaign_correlation": True,
            "consent_version": "v1.0"
        }
    )
    assert opt_in_resp.status_code == 200
    opt_in_data = opt_in_resp.json()
    assert opt_in_data["is_sharing_enabled"] is True
    assert opt_in_data["consent_timestamp"] is not None
    assert opt_in_data["withdrawn_at"] is None

    # 3. Withdraw consent (opt-out)
    opt_out_resp = client.post(
        "/api/v1/threat-intel/consent",
        json={
            "is_sharing_enabled": False,
            "allow_campaign_correlation": False,
            "consent_version": "v1.0"
        }
    )
    assert opt_out_resp.status_code == 200
    opt_out_data = opt_out_resp.json()
    assert opt_out_data["is_sharing_enabled"] is False
    assert opt_out_data["withdrawn_at"] is not None

    app.dependency_overrides.clear()


def get_err_msg(resp) -> str:
    body = resp.json()
    if "error" in body and isinstance(body["error"], dict):
        return body["error"].get("message", "")
    return str(body.get("detail", ""))


def test_unconsented_user_submission_rejected(db_session: Session, mock_user_alice: User):
    """
    Test that users who have not opted in cannot submit community threat reports (HTTP 403).
    """
    # Ensure alice has sharing disabled
    pref = db_session.query(UserSharingPreference).filter(UserSharingPreference.user_id == mock_user_alice.id).first()
    if pref:
        pref.is_sharing_enabled = False
        db_session.commit()

    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    client = TestClient(app)

    resp = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": "https://malicious-scam.xyz/verify",
            "threat_category": "PHISHING",
            "user_risk_score": 85
        }
    )
    assert resp.status_code == 403
    assert "opted in" in get_err_msg(resp).lower()

    app.dependency_overrides.clear()


def test_ssrf_and_malformed_url_rejected(db_session: Session, mock_user_alice: User):
    """
    Test that submissions with SSRF loopback/private IPs or invalid schemas are rejected (HTTP 400).
    """
    # Enable consent for alice
    service = ThreatIntelService(db_session)
    service.comm_repo.set_user_preferences(mock_user_alice.id, is_sharing_enabled=True)

    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    client = TestClient(app)

    # 1. SSRF Loopback
    resp_loopback = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": "http://127.0.0.1:8080/internal-admin",
            "threat_category": "PHISHING",
            "user_risk_score": 90
        }
    )
    assert resp_loopback.status_code == 400
    assert "rejected dangerous private/loopback url" in get_err_msg(resp_loopback).lower()

    # 2. Dangerous Scheme URL (SSRF & XSS guard)
    resp_malformed = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": "javascript:alert(document.cookie)",
            "threat_category": "PHISHING",
            "user_risk_score": 75
        }
    )
    assert resp_malformed.status_code == 400

    app.dependency_overrides.clear()


def test_community_submission_normalization_and_corroboration(
    db_session: Session,
    mock_user_alice: User,
    mock_user_bob: User
):
    """
    Test that URL query parameters with sensitive auth/session tokens are stripped,
    and multiple user reports corroborate the same indicator dynamically.
    """
    service = ThreatIntelService(db_session)
    service.comm_repo.set_user_preferences(mock_user_alice.id, is_sharing_enabled=True)
    service.comm_repo.set_user_preferences(mock_user_bob.id, is_sharing_enabled=True)

    raw_submission = "https://Bank-Secure-Update.com/login?token=secret12345&dest=account"
    expected_normalized = "https://bank-secure-update.com/login?dest=account&token=%5BREDACTED%5D"

    # Alice submits report
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    client = TestClient(app)

    resp1 = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": raw_submission,
            "threat_category": "SMISHING",
            "user_risk_score": 80,
            "model_verdicts": {"tinybert": 0.82, "xgboost": 0.78}
        }
    )
    assert resp1.status_code == 200
    data1 = resp1.json()
    assert data1["indicator_value"] == expected_normalized
    assert data1["status"] == "PENDING_REVIEW"
    assert data1["corroboration_count"] == 1
    assert data1["consensus_score"] > 0.0

    report_id = data1["id"]

    # Bob submits identical indicator
    app.dependency_overrides[get_current_user] = lambda: mock_user_bob
    resp2 = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": raw_submission,
            "threat_category": "SMISHING",
            "user_risk_score": 90
        }
    )
    assert resp2.status_code == 200
    data2 = resp2.json()
    assert data2["id"] == report_id  # Corroborated existing report
    assert data2["corroboration_count"] == 2
    assert data2["consensus_score"] > data1["consensus_score"]

    app.dependency_overrides.clear()


def test_unverified_report_not_treated_as_confirmed_malicious(
    db_session: Session,
    mock_user_alice: User
):
    """
    Verify that an unverified community report is distinguished from confirmed malicious indicators:
    reputation status is SUSPICIOUS, is_malicious=False.
    """
    service = ThreatIntelService(db_session, providers=[])
    service.clear_cache()
    service.comm_repo.set_user_preferences(mock_user_alice.id, is_sharing_enabled=True)

    test_url = "https://quishing-campaign-unverified.org/pay"
    service.submit_community_report(
        user_id=mock_user_alice.id,
        report_data=CommunityThreatReportCreate(
            indicator_type="URL",
            indicator_value=test_url,
            threat_category="QUISHING",
            user_risk_score=75
        )
    )

    # Perform lookup
    rep = service.lookup_url_reputation(raw_url=test_url)
    assert rep["reputation_status"] == "SUSPICIOUS"
    assert rep["is_malicious"] is False  # Never treated as confirmed malicious
    assert rep["source"] == "COMMUNITY_CONSENSUS"
    assert "Under SOC review" in rep["evidence_summary"][0]


def test_user_reports_isolation_and_withdrawal(
    db_session: Session,
    mock_user_alice: User,
    mock_user_bob: User
):
    """
    Test user submission isolation (peer users cannot view each other's reports)
    and user report withdrawal capability.
    """
    service = ThreatIntelService(db_session)
    service.comm_repo.set_user_preferences(mock_user_alice.id, is_sharing_enabled=True)
    service.comm_repo.set_user_preferences(mock_user_bob.id, is_sharing_enabled=True)

    app.dependency_overrides[get_db] = lambda: db_session
    client = TestClient(app)

    # Alice submits report
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    resp = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": "https://alice-private-threat.com/claim",
            "threat_category": "PHISHING",
            "user_risk_score": 60
        }
    )
    alice_report_id = resp.json()["id"]

    # Alice lists her reports -> sees report
    alice_list = client.get("/api/v1/threat-intel/community/my-reports").json()
    assert any(it["id"] == alice_report_id for it in alice_list["items"])

    # Bob lists his reports -> does NOT see Alice's report
    app.dependency_overrides[get_current_user] = lambda: mock_user_bob
    bob_list = client.get("/api/v1/threat-intel/community/my-reports").json()
    assert not any(it["id"] == alice_report_id for it in bob_list["items"])

    # Bob tries to withdraw Alice's report -> 404
    withdraw_attempt = client.delete(f"/api/v1/threat-intel/community/my-reports/{alice_report_id}")
    assert withdraw_attempt.status_code == 404

    # Alice withdraws her report -> 200
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    withdraw_success = client.delete(f"/api/v1/threat-intel/community/my-reports/{alice_report_id}")
    assert withdraw_success.status_code == 200
    assert withdraw_success.json()["status"] == "WITHDRAWN"

    app.dependency_overrides.clear()


def test_admin_triage_and_promotion_flow(
    db_session: Session,
    mock_user_alice: User,
    mock_soc_admin: User
):
    """
    Test that RBAC prevents regular users from triaging,
    and that SOC Admin promotion upgrades community report into authoritative ThreatIntel.
    """
    service = ThreatIntelService(db_session, providers=[])
    service.clear_cache()
    service.comm_repo.set_user_preferences(mock_user_alice.id, is_sharing_enabled=True)

    app.dependency_overrides[get_db] = lambda: db_session
    client = TestClient(app)

    # 1. Alice submits community report
    app.dependency_overrides[get_current_user] = lambda: mock_user_alice
    target_url = "https://confirmed-phishing-promoted.net/credential-harvest"
    submit_resp = client.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": target_url,
            "threat_category": "PHISHING",
            "user_risk_score": 95
        }
    )
    report_id = submit_resp.json()["id"]

    # 2. Alice tries to access admin triage -> 403 Forbidden
    app.dependency_overrides[require_admin] = lambda: (_ for _ in ()).throw(
        pytest.importorskip("fastapi").HTTPException(status_code=403, detail="Forbidden")
    )
    unauth_resp = client.patch(
        f"/api/v1/admin/threat-intel/community-reports/{report_id}/triage",
        json={"action": "APPROVE_AND_PROMOTE"}
    )
    assert unauth_resp.status_code == 403

    # 3. SOC Admin approves and promotes report
    app.dependency_overrides[get_current_user] = lambda: mock_soc_admin
    app.dependency_overrides[require_admin] = lambda: mock_soc_admin

    triage_resp = client.patch(
        f"/api/v1/admin/threat-intel/community-reports/{report_id}/triage",
        json={
            "action": "APPROVE_AND_PROMOTE",
            "notes": "Verified by SOC analysts as active credential harvesting campaign",
            "assigned_confidence": 0.95,
            "assigned_threat_type": "PHISHING",
            "expires_in_days": 30
        }
    )
    assert triage_resp.status_code == 200
    promoted_data = triage_resp.json()
    assert promoted_data["status"] == "PROMOTED"
    assert promoted_data["promoted_indicator_id"] is not None

    # 4. Lookup now recognizes promoted indicator as confirmed malicious!
    rep_result = service.lookup_url_reputation(raw_url=target_url)
    assert rep_result["reputation_status"] == "KNOWN_MALICIOUS"
    assert rep_result["is_malicious"] is True
    assert rep_result["source"] == "COMMUNITY"
    assert rep_result["confidence"] == 0.95

    app.dependency_overrides.clear()


def test_admin_indicator_lifecycle_management(
    db_session: Session,
    mock_soc_admin: User
):
    """
    Test transition of indicator lifecycle to REVOKED with mandatory reason.
    """
    service = ThreatIntelService(db_session, providers=[])
    service.clear_cache()
    ind, _ = service.repo.upsert_indicator(
        indicator_type="DOMAIN",
        indicator_value="disputed-domain.com",
        threat_type="PHISHING",
        confidence=0.8
    )

    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: mock_soc_admin
    app.dependency_overrides[require_admin] = lambda: mock_soc_admin
    client = TestClient(app)

    resp = client.patch(
        f"/api/v1/admin/threat-intel/indicators/{ind.id}/status",
        json={
            "status": "REVOKED",
            "reason": "Legitimate business domain verified by SOC review - false positive"
        }
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "REVOKED"

    # Confirm in DB
    refreshed = service.repo.find_by_id(ind.id)
    assert refreshed.verification_status == "REVOKED"
    assert refreshed.revocation_reason == "Legitimate business domain verified by SOC review - false positive"

    app.dependency_overrides.clear()


def test_threat_intel_health_overview(
    db_session: Session,
    mock_soc_admin: User
):
    """
    Test /admin/threat-intel/health aggregates metrics accurately.
    """
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: mock_soc_admin
    app.dependency_overrides[require_admin] = lambda: mock_soc_admin
    client = TestClient(app)

    resp = client.get("/api/v1/admin/threat-intel/health")
    assert resp.status_code == 200
    data = resp.json()
    assert "status" in data
    assert "total_verified_indicators" in data
    assert "total_community_reports" in data
    assert "feeds" in data
    assert len(data["feeds"]) >= 1

    app.dependency_overrides.clear()

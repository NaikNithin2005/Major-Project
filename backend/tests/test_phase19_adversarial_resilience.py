"""
Phase 19 Backend Adversarial Security & API Resilience Test Suite.
Validates authentication tampering, role escalation, cross-user isolation,
schema violations, oversized requests, SSRF injection, data poisoning resistance,
and query parameter credential scrubbing.
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session
from app.main import app
from app.database.session import get_db
from app.models.user import User
from app.services.threat_intel_service import ThreatIntelService


@pytest.fixture
def test_alice(db_session: Session):
    user = db_session.query(User).filter(User.id == "user_phase19_alice").first()
    if not user:
        user = User(
            id="user_phase19_alice",
            firebase_uid="user_phase19_alice_fb",
            email="alice19@squish.corp",
            display_name="Alice Reporter",
            role="USER",
            is_active=True
        )
        db_session.add(user)
        db_session.commit()
    return user


@pytest.fixture
def test_bob(db_session: Session):
    user = db_session.query(User).filter(User.id == "user_phase19_bob").first()
    if not user:
        user = User(
            id="user_phase19_bob",
            firebase_uid="user_phase19_bob_fb",
            email="bob19@squish.corp",
            display_name="Bob Reporter",
            role="USER",
            is_active=True
        )
        db_session.add(user)
        db_session.commit()
    return user


# ==============================================================================
# 1. AUTHENTICATION TAMPERING & ROLE ESCALATION
# ==============================================================================

def test_invalid_and_tampered_auth_tokens(client: TestClient):
    """Verify forged, missing, or malformed auth headers are rejected with 401."""
    # Invalid Bearer signature
    res1 = client.get("/api/v1/threat-intel/consent", headers={"Authorization": "Bearer forged-tampered-token"})
    assert res1.status_code == 401

    # Missing Bearer prefix
    res2 = client.get("/api/v1/threat-intel/consent", headers={"Authorization": "Token mock-user-token"})
    assert res2.status_code == 401

    # Empty auth header
    res3 = client.get("/api/v1/threat-intel/consent", headers={"Authorization": ""})
    assert res3.status_code == 401


def test_privilege_escalation_guards(
    db_session: Session,
    test_alice: User
):
    """Verify regular user (Role.USER) cannot access administrator SOC endpoints (HTTP 403)."""
    from app.auth.dependencies import get_current_user
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        # Attempting to access admin threat intel feeds
        res1 = client.get("/api/v1/admin/threat-intel/feeds")
        assert res1.status_code == 403

        # Attempting to access admin community reports triage queue
        res2 = client.get("/api/v1/admin/threat-intel/community-reports")
        assert res2.status_code == 403

        # Attempting to modify indicator verification status
        res3 = client.patch(
            "/api/v1/admin/threat-intel/indicators/fake-indicator-id/status",
            json={"status": "VERIFIED", "reason": "Unauthorized escalation attempt"}
        )
        assert res3.status_code == 403
    finally:
        app.dependency_overrides.clear()


# ==============================================================================
# 2. CROSS-USER ISOLATION & RECORD TAMPERING
# ==============================================================================

def test_cross_user_isolation_and_tampering(
    db_session: Session,
    test_alice: User,
    test_bob: User
):
    """Verify User B cannot delete or modify User A's community threat reports."""
    from app.auth.dependencies import get_current_user
    service = ThreatIntelService(db_session)
    service.comm_repo.set_user_preferences(test_alice.id, is_sharing_enabled=True)
    service.comm_repo.set_user_preferences(test_bob.id, is_sharing_enabled=True)

    # Alice submits a community report
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client_alice = TestClient(app)

    submit_res = client_alice.post(
        "/api/v1/threat-intel/community/submit",
        json={
            "indicator_type": "URL",
            "indicator_value": "http://user-a-target.xyz/login",
            "threat_category": "PHISHING",
            "user_risk_score": 95
        }
    )
    assert submit_res.status_code == 200
    report_id = submit_res.json().get("id")
    assert report_id is not None

    # Bob attempts to delete Alice's report
    app.dependency_overrides[get_current_user] = lambda: test_bob
    client_bob = TestClient(app)

    tamper_res = client_bob.delete(f"/api/v1/threat-intel/community/my-reports/{report_id}")
    # Must reject with 404, enforcing strict ownership isolation
    assert tamper_res.status_code in (404, 403)

    # Alice can legitimately delete their own report
    app.dependency_overrides[get_current_user] = lambda: test_alice
    legit_delete = client_alice.delete(f"/api/v1/threat-intel/community/my-reports/{report_id}")
    assert legit_delete.status_code == 200

    app.dependency_overrides.clear()


# ==============================================================================
# 3. SCHEMA VIOLATIONS & OVERSIZED REQUESTS
# ==============================================================================

def test_malformed_json_and_schema_violations(
    db_session: Session,
    test_alice: User
):
    """Verify malformed requests or schema type violations return 422 Unprocessable Entity."""
    from app.auth.dependencies import get_current_user
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        # Missing required 'url' field in lookup
        res1 = client.post("/api/v1/threat-intel/lookup", json={})
        assert res1.status_code == 422

        # Wrong data type (integer instead of string)
        res2 = client.post("/api/v1/threat-intel/lookup", json={"url": 12345})
        assert res2.status_code == 422

        # Invalid user_risk_score out of range (greater than 100)
        res3 = client.post(
            "/api/v1/threat-intel/community/submit",
            json={
                "indicator_type": "URL",
                "indicator_value": "http://bad.xyz",
                "threat_category": "PHISHING",
                "user_risk_score": 150
            }
        )
        assert res3.status_code == 422
    finally:
        app.dependency_overrides.clear()


def test_oversized_request_handling(
    db_session: Session,
    test_alice: User
):
    """Verify oversized URLs are handled safely without crashing the service."""
    from app.auth.dependencies import get_current_user
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        oversized_url = "http://phish.xyz/" + ("a" * 8000)
        res = client.post("/api/v1/threat-intel/lookup", json={"url": oversized_url})
        assert res.status_code in (200, 400, 422)
    finally:
        app.dependency_overrides.clear()


# ==============================================================================
# 4. SSRF & DANGEROUS SCHEME INJECTION IN API
# ==============================================================================

def test_ssrf_injection_in_lookup_api(
    db_session: Session,
    test_alice: User
):
    """Verify SSRF attempts through lookup API are intercepted and flagged as hazards."""
    from app.auth.dependencies import get_current_user
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        ssrf_payloads = [
            "http://127.0.0.1:8000/internal",
            "http://169.254.169.254/latest/meta-data",
            "http://0x7f.0.0.1/admin"
        ]
        for target in ssrf_payloads:
            res = client.post("/api/v1/threat-intel/lookup", json={"url": target})
            assert res.status_code == 200
            data = res.json()
            assert data.get("is_ssrf_risk") is True
            assert data.get("ssrf_reason") is not None
    finally:
        app.dependency_overrides.clear()


def test_dangerous_scheme_rejection_in_api(
    db_session: Session,
    test_alice: User
):
    """Verify dangerous non-HTTP schemes are intercepted and flagged."""
    from app.auth.dependencies import get_current_user
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        dangerous = "javascript:alert(document.cookie)"
        res = client.post("/api/v1/threat-intel/lookup", json={"url": dangerous})
        assert res.status_code == 200
        data = res.json()
        assert data.get("is_ssrf_risk") is True
        assert "Dangerous protocol" in str(data.get("ssrf_reason", ""))
    finally:
        app.dependency_overrides.clear()


# ==============================================================================
# 5. PRIVACY CREDENTIAL SCRUBBING
# ==============================================================================

def test_sensitive_query_parameter_scrubbing(
    db_session: Session,
    test_alice: User
):
    """Verify sensitive tokens and credentials in query parameters are redacted."""
    from app.auth.dependencies import get_current_user
    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        sensitive_url = "https://legit-portal.com/auth?token=secret_token_12345&password=mypassword&user=alice"
        res = client.post("/api/v1/threat-intel/lookup", json={"url": sensitive_url})
        assert res.status_code == 200
        norm_url = res.json().get("normalized_url", "")
        assert "secret_token_12345" not in norm_url
        assert "mypassword" not in norm_url
        assert "%5BREDACTED%5D" in norm_url or "[REDACTED]" in norm_url
    finally:
        app.dependency_overrides.clear()


# ==============================================================================
# 6. DATA POISONING & REPUTATION INTEGRITY
# ==============================================================================

def test_data_poisoning_unverified_report_isolation(
    db_session: Session,
    test_alice: User
):
    """Verify user community reports remain UNVERIFIED and do not artificially mark target as authoritative."""
    from app.auth.dependencies import get_current_user
    service = ThreatIntelService(db_session)
    service.comm_repo.set_user_preferences(test_alice.id, is_sharing_enabled=True)

    app.dependency_overrides[get_db] = lambda: db_session
    app.dependency_overrides[get_current_user] = lambda: test_alice
    client = TestClient(app)

    try:
        target = "http://fresh-unverified-site.xyz/login"

        # Submit community report
        sub_res = client.post(
            "/api/v1/threat-intel/community/submit",
            json={
                "indicator_type": "URL",
                "indicator_value": target,
                "threat_category": "PHISHING",
                "user_risk_score": 85
            }
        )
        assert sub_res.status_code == 200

        # Immediately lookup indicator
        lookup_res = client.post("/api/v1/threat-intel/lookup", json={"url": target})
        assert lookup_res.status_code == 200
        data = lookup_res.json()

        # Unverified community report MUST NOT promote reputation status to authoritative KNOWN_MALICIOUS
        assert data.get("reputation_status") != "KNOWN_MALICIOUS"
    finally:
        app.dependency_overrides.clear()

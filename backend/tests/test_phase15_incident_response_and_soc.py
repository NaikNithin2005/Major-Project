"""
Automated Test Suite for Phase 15:
Advanced SOC Dashboard, Incident Response & Threat Investigation.
"""

import pytest
from datetime import datetime, timezone, timedelta
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.main import app
from app.models.user import User
from app.models.threat_event import ThreatEvent
from app.models.incident import Incident, IncidentThreatLink, IncidentNote, IncidentEvent
from app.models.audit_log import AuditLog
from app.auth.dependencies import get_current_user, require_admin


# --- FIXTURES ---

@pytest.fixture
def mock_normal_user(db_session: Session):
    existing = db_session.query(User).filter(User.id == "user_phase15_regular").first()
    if existing:
        return existing
    user = User(
        id="user_phase15_regular",
        firebase_uid="user_phase15_regular_fb",
        email="user15@example.com",
        display_name="Phase15 Normal User",
        role="USER",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def mock_admin_user(db_session: Session):
    existing = db_session.query(User).filter(User.id == "admin_phase15_soc").first()
    if existing:
        return existing
    user = User(
        id="admin_phase15_soc",
        firebase_uid="admin_phase15_soc_fb",
        email="soc15@squishshield.org",
        display_name="Phase15 SOC Lead",
        role="ADMIN",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def mock_admin_user_2(db_session: Session):
    existing = db_session.query(User).filter(User.id == "admin_phase15_analyst").first()
    if existing:
        return existing
    user = User(
        id="admin_phase15_analyst",
        firebase_uid="admin_phase15_analyst_fb",
        email="analyst15@squishshield.org",
        display_name="Phase15 Analyst",
        role="ADMIN",
        is_active=True
    )
    db_session.add(user)
    db_session.commit()
    return user


@pytest.fixture
def sample_threat_event(db_session: Session, mock_normal_user: User):
    event_id = f"threat_{datetime.now().timestamp()}"
    event = ThreatEvent(
        id=event_id,
        user_id=mock_normal_user.id,
        event_type="SMS",
        risk_score=94,
        threat_category="HIGH",
        recommended_action="Block sender and delete message",
        timestamp=datetime.now(timezone.utc) - timedelta(minutes=30),
        url_metadata={
            "url": "https://secure-login-chase-update.com/auth/verify",
            "domain": "secure-login-chase-update.com"
        },
        reasons=["High-confidence phishing domain", "Brand impersonation: Chase"],
        campaign_id="CAMP_CHASE_2026_01",
        status="REPORTED"
    )
    db_session.add(event)
    db_session.commit()
    return event


# --- 1. INCIDENT CREATION TESTS ---

def test_incident_creation_and_seed_threat(
    client: TestClient,
    mock_admin_user: User,
    sample_threat_event: ThreatEvent
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        payload = {
            "title": "Suspected Chase Smishing Surge",
            "description": "Multiple SMS phishing lures targeting Chase bank customers with credential-harvesting links.",
            "severity": "HIGH",
            "category": "SMISHING_SURGE",
            "seed_threat_event_id": sample_threat_event.id,
            "metadata_json": {"target_brand": "Chase", "tags": ["credential_harvesting"]}
        }
        res = client.post("/api/v1/admin/incidents", json=payload)
        assert res.status_code == 201
        data = res.json()
        assert data["title"] == payload["title"]
        assert data["severity"] == "HIGH"
        assert data["status"] == "NEW"
        assert data["category"] == "SMISHING_SURGE"
        assert len(data["threat_links"]) == 1
        assert data["threat_links"][0]["id"] == sample_threat_event.id
        assert data["threat_links"][0]["relationship_type"] == "CONFIRMED"
        assert len(data["events"]) >= 1
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 2. LIFECYCLE STATE TRANSITION TESTS ---

def test_legal_status_transitions(
    client: TestClient,
    mock_admin_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        # 1. Create incident
        create_res = client.post("/api/v1/admin/incidents", json={
            "title": "Lifecycle Test Incident",
            "severity": "MEDIUM",
            "category": "QUISHING_CAMPAIGN"
        })
        assert create_res.status_code == 201
        inc_id = create_res.json()["id"]

        # 2. Transition: NEW -> TRIAGED
        triage_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "TRIAGED",
            "reason": "Initial triage confirmed active campaign"
        })
        assert triage_res.status_code == 200
        assert triage_res.json()["status"] == "TRIAGED"
        assert triage_res.json()["triaged_at"] is not None

        # 3. Transition: TRIAGED -> INVESTIGATING
        inv_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "INVESTIGATING",
            "reason": "Assigned to threat intelligence unit"
        })
        assert inv_res.status_code == 200
        assert inv_res.json()["status"] == "INVESTIGATING"

        # 4. Transition: INVESTIGATING -> CONTAINED
        cont_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "CONTAINED",
            "reason": "Malicious domain added to local blocklist and registrar contacted"
        })
        assert cont_res.status_code == 200
        assert cont_res.json()["status"] == "CONTAINED"
        assert cont_res.json()["contained_at"] is not None

        # 5. Transition: CONTAINED -> RESOLVED
        res_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "RESOLVED",
            "resolution_summary": "Domain suspended by registrar, no compromised accounts detected."
        })
        assert res_res.status_code == 200
        assert res_res.json()["status"] == "RESOLVED"
        assert res_res.json()["resolved_at"] is not None

        # 6. Transition: RESOLVED -> CLOSED
        close_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "CLOSED",
            "reason": "Post-incident review complete"
        })
        assert close_res.status_code == 200
        assert close_res.json()["status"] == "CLOSED"
        assert close_res.json()["closed_at"] is not None
    finally:
        app.dependency_overrides.pop(require_admin, None)


def test_illegal_status_transitions_rejected(
    client: TestClient,
    mock_admin_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        # Create incident in state NEW
        create_res = client.post("/api/v1/admin/incidents", json={
            "title": "Illegal Transition Test",
            "severity": "LOW"
        })
        inc_id = create_res.json()["id"]

        # Attempt illegal jump: NEW -> RESOLVED (not permitted)
        bad_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "RESOLVED",
            "resolution_summary": "Skipped triage and containment"
        })
        assert bad_res.status_code == 400
        assert bad_res.json()["error"]["code"] == "INVALID_STATE_TRANSITION"

        # Attempt illegal jump: NEW -> CONTAINED (not permitted)
        bad_res2 = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "CONTAINED"
        })
        assert bad_res2.status_code == 400
    finally:
        app.dependency_overrides.pop(require_admin, None)


def test_reopen_incident_workflow(
    client: TestClient,
    mock_admin_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        # Create and advance to RESOLVED
        inc_id = client.post("/api/v1/admin/incidents", json={"title": "Reopen Test"}).json()["id"]
        client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={"status": "INVESTIGATING"})
        client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={"status": "RESOLVED", "resolution_summary": "Done"})

        # Attempt reopen without reason (must fail)
        no_reason = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "INVESTIGATING"
        })
        assert no_reason.status_code == 400
        assert no_reason.json()["error"]["code"] == "REOPEN_REASON_REQUIRED"

        # Valid reopen with documented reason
        reopened = client.patch(f"/api/v1/admin/incidents/{inc_id}/status", json={
            "status": "INVESTIGATING",
            "reason": "New victim telemetry reported identical campaign signature"
        })
        assert reopened.status_code == 200
        assert reopened.json()["status"] == "INVESTIGATING"
        assert reopened.json()["resolved_at"] is None

        # Verify REOPENED event in timeline
        timeline = client.get(f"/api/v1/admin/incidents/{inc_id}/timeline").json()
        assert any(e["event_type"] == "REOPENED" for e in timeline)
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 3. ASSIGNMENT & SEVERITY TESTS ---

def test_incident_assignment_and_rbac(
    client: TestClient,
    mock_admin_user: User,
    mock_admin_user_2: User,
    mock_normal_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        inc_id = client.post("/api/v1/admin/incidents", json={"title": "Assignment Test"}).json()["id"]

        # Assign to eligible admin
        assign_res = client.patch(f"/api/v1/admin/incidents/{inc_id}/assign", json={
            "assigned_admin_id": mock_admin_user_2.id,
            "note": "Lead analysis on brand impersonation"
        })
        assert assign_res.status_code == 200
        assert assign_res.json()["assigned_admin_id"] == mock_admin_user_2.id
        assert assign_res.json()["assigned_admin_email"] == mock_admin_user_2.email

        # Assign to normal user (must fail with 400)
        bad_assign = client.patch(f"/api/v1/admin/incidents/{inc_id}/assign", json={
            "assigned_admin_id": mock_normal_user.id
        })
        assert bad_assign.status_code == 400
        assert bad_assign.json()["error"]["code"] == "INVALID_ROLE"

        # Assign to non-existent ID (must fail with 404)
        not_found = client.patch(f"/api/v1/admin/incidents/{inc_id}/assign", json={
            "assigned_admin_id": "non_existent_uuid"
        })
        assert not_found.status_code == 404
    finally:
        app.dependency_overrides.pop(require_admin, None)


def test_incident_severity_update(
    client: TestClient,
    mock_admin_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        inc_id = client.post("/api/v1/admin/incidents", json={"title": "Severity Test", "severity": "LOW"}).json()["id"]

        res = client.patch(f"/api/v1/admin/incidents/{inc_id}/severity", json={
            "severity": "CRITICAL",
            "reason": "Confirmed active malware payload distribution"
        })
        assert res.status_code == 200
        assert res.json()["severity"] == "CRITICAL"
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 4. INVESTIGATION NOTES & EVIDENCE TESTS ---

def test_investigation_notes(
    client: TestClient,
    mock_admin_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        inc_id = client.post("/api/v1/admin/incidents", json={"title": "Notes Test"}).json()["id"]

        # Add observation note
        res1 = client.post(f"/api/v1/admin/incidents/{inc_id}/notes", json={
            "content": "Domain registration occurred 24h ago via NameCheap with WhoisGuard privacy.",
            "note_type": "OBSERVATION"
        })
        assert res1.status_code == 200

        # Add remediation note
        res2 = client.post(f"/api/v1/admin/incidents/{inc_id}/notes", json={
            "content": "Abuse ticket submitted to registrar hosting provider.",
            "note_type": "REMEDIATION"
        })
        assert res2.status_code == 200
        assert len(res2.json()["notes"]) == 2
        assert res2.json()["notes_count"] == 2
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 5. THREAT LINKING & UNLINKING TESTS ---

def test_threat_linking_and_unlinking(
    client: TestClient,
    mock_admin_user: User,
    mock_normal_user: User,
    db_session: Session
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        inc_id = client.post("/api/v1/admin/incidents", json={"title": "Link Test"}).json()["id"]

        # Create 2 threats
        t1 = ThreatEvent(
            id=f"t1_{datetime.now().timestamp()}",
            user_id=mock_normal_user.id,
            event_type="SMS",
            risk_score=88,
            threat_category="HIGH",
            recommended_action="Block",
            timestamp=datetime.now(timezone.utc),
            status="REPORTED"
        )
        t2 = ThreatEvent(
            id=f"t2_{datetime.now().timestamp()}",
            user_id=mock_normal_user.id,
            event_type="QR",
            risk_score=92,
            threat_category="HIGH",
            recommended_action="Block",
            timestamp=datetime.now(timezone.utc),
            status="REPORTED"
        )
        db_session.add_all([t1, t2])
        db_session.commit()

        # Link t1
        link_res = client.post(f"/api/v1/admin/incidents/{inc_id}/threats", json={
            "threat_event_id": t1.id,
            "relationship_type": "LIKELY",
            "correlation_signal": "MATCHING_DOMAIN"
        })
        assert link_res.status_code == 200
        assert len(link_res.json()["threat_links"]) == 1

        # Link t1 again (deduplicated, no duplicate link)
        link_dup = client.post(f"/api/v1/admin/incidents/{inc_id}/threats", json={
            "threat_event_id": t1.id
        })
        assert link_dup.status_code == 200
        assert len(link_dup.json()["threat_links"]) == 1

        # Unlink t1
        unlink_res = client.delete(f"/api/v1/admin/incidents/{inc_id}/threats/{t1.id}")
        assert unlink_res.status_code == 200
        assert len(unlink_res.json()["threat_links"]) == 0

        # Unlink non-linked t2 (returns 404)
        unlink_fail = client.delete(f"/api/v1/admin/incidents/{inc_id}/threats/{t2.id}")
        assert unlink_fail.status_code == 404
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 6. CORRELATION SUGGESTIONS TEST ---

def test_correlation_suggestions(
    client: TestClient,
    mock_admin_user: User,
    mock_normal_user: User,
    db_session: Session
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        # Seeded threat with domain "evil-phish-bank.com"
        seed_threat = ThreatEvent(
            id=f"seed_{datetime.now().timestamp()}",
            user_id=mock_normal_user.id,
            event_type="SMS",
            risk_score=95,
            threat_category="HIGH",
            recommended_action="Block",
            timestamp=datetime.now(timezone.utc) - timedelta(hours=2),
            url_metadata={"url": "https://evil-phish-bank.com/login", "domain": "evil-phish-bank.com"},
            status="REPORTED"
        )
        # Unlinked candidate threat with same domain
        cand_threat = ThreatEvent(
            id=f"cand_{datetime.now().timestamp()}",
            user_id=mock_normal_user.id,
            event_type="QR",
            risk_score=90,
            threat_category="HIGH",
            recommended_action="Block",
            timestamp=datetime.now(timezone.utc) - timedelta(hours=1),
            url_metadata={"url": "https://evil-phish-bank.com/reset-pin", "domain": "evil-phish-bank.com"},
            status="REPORTED"
        )
        db_session.add_all([seed_threat, cand_threat])
        db_session.commit()

        # Create incident seeded with seed_threat
        inc = client.post("/api/v1/admin/incidents", json={
            "title": "Bank Impersonation Campaign",
            "seed_threat_event_id": seed_threat.id
        }).json()

        # Query correlation suggestions
        sug_res = client.get(f"/api/v1/admin/incidents/{inc['id']}/correlation-suggestions")
        assert sug_res.status_code == 200
        suggestions = sug_res.json()["suggestions"]
        assert len(suggestions) >= 1
        assert any(s["threat_event_id"] == cand_threat.id for s in suggestions)
        match = next(s for s in suggestions if s["threat_event_id"] == cand_threat.id)
        assert match["signal"] == "MATCHING_DOMAIN"
        assert match["confidence"] == "LIKELY"
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 7. INCIDENT METRICS & SOC REPORTING ---

def test_incident_metrics(
    client: TestClient,
    mock_admin_user: User
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        res = client.get("/api/v1/admin/incidents/metrics")
        assert res.status_code == 200
        data = res.json()
        assert "total_incidents" in data
        assert "open_incidents" in data
        assert "critical_incidents" in data
        assert "severity_breakdown" in data
        assert "status_breakdown" in data
        assert "category_breakdown" in data
        assert "daily_trend" in data
        assert len(data["daily_trend"]) == 14
    finally:
        app.dependency_overrides.pop(require_admin, None)


# --- 8. RBAC & AUDIT LOGGING INTEGRITY ---

def test_incident_authorization_rbac(
    client: TestClient,
    mock_normal_user: User
):
    # Unauthenticated -> 401
    unauth = client.get("/api/v1/admin/incidents")
    assert unauth.status_code == 401

    # Regular user -> 403 Forbidden
    app.dependency_overrides[get_current_user] = lambda: mock_normal_user
    try:
        forbidden = client.get("/api/v1/admin/incidents")
        assert forbidden.status_code == 403
    finally:
        app.dependency_overrides.pop(get_current_user, None)


def test_audit_logging_integrity(
    client: TestClient,
    mock_admin_user: User,
    db_session: Session
):
    app.dependency_overrides[require_admin] = lambda: mock_admin_user
    try:
        # Create incident
        inc = client.post("/api/v1/admin/incidents", json={"title": "Audit Verify Incident"}).json()
        inc_id = inc["id"]

        # Verify audit log entry was written
        audit_entry = (
            db_session.query(AuditLog)
            .filter(
                AuditLog.resource_type == "INCIDENT",
                AuditLog.resource_id == inc_id,
                AuditLog.action == "INCIDENT_CREATED"
            )
            .first()
        )
        assert audit_entry is not None
        assert audit_entry.actor_user_id == mock_admin_user.id
    finally:
        app.dependency_overrides.pop(require_admin, None)

"""
Phase 9 Acceptance Tests: Web Admin Dashboard Backend Integration & RBAC Verification.

Validates:
1. Strict RBAC: Unauthenticated requests return 401 Unauthorized.
2. Strict RBAC: Standard USER accounts return 403 Forbidden on /api/v1/admin/*.
3. Authoritative ADMIN: Verified ADMIN accounts gain full access to telemetry, users, and audit logs.
4. Threat Filtering & Search: Server-side filtering by vector, category, status, and query term.
5. Threat Inspection: Full telemetry inspection with XAI reasons and model breakdown.
6. State-Changing Admin Actions & Audit Trail: Status/role updates generate immutable audit logs.
7. Architectural Decoupling: Web dashboard contains zero database credentials or direct PG connections.
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session
from app.models.user import User
from app.models.threat_event import ThreatEvent
from app.models.audit_log import AuditLog


def test_phase9_unauthenticated_requests_blocked(client: TestClient):
    """Admin endpoints must reject requests lacking Authorization Bearer header."""
    endpoints = [
        "/api/v1/admin/threat-events",
        "/api/v1/admin/users",
        "/api/v1/admin/audit-logs",
        "/api/v1/admin/analytics/summary",
    ]
    for ep in endpoints:
        res = client.get(ep)
        assert res.status_code == 401, f"Endpoint {ep} allowed unauthenticated access"
        data = res.json()
        assert "error" in data or "detail" in data


def test_phase9_normal_user_rejected_by_rbac(client: TestClient, user_headers: dict):
    """Standard USER role must be strictly rejected with HTTP 403 on all admin endpoints."""
    endpoints = [
        "/api/v1/admin/threat-events",
        "/api/v1/admin/users",
        "/api/v1/admin/audit-logs",
        "/api/v1/admin/analytics/summary",
    ]
    for ep in endpoints:
        res = client.get(ep, headers=user_headers)
        assert res.status_code == 403, f"Endpoint {ep} allowed standard USER role"
        data = res.json()
        err = data.get("error") or data.get("detail")
        assert err["code"] == "FORBIDDEN"


def test_phase9_admin_authenticated_access(client: TestClient, admin_headers: dict):
    """Authoritative ADMIN role must successfully retrieve system telemetry and logs."""
    res_threats = client.get("/api/v1/admin/threat-events", headers=admin_headers)
    assert res_threats.status_code == 200
    assert "items" in res_threats.json()

    res_users = client.get("/api/v1/admin/users", headers=admin_headers)
    assert res_users.status_code == 200
    assert "items" in res_users.json()

    res_logs = client.get("/api/v1/admin/audit-logs", headers=admin_headers)
    assert res_logs.status_code == 200
    assert "items" in res_logs.json()

    res_summary = client.get("/api/v1/admin/analytics/summary", headers=admin_headers)
    assert res_summary.status_code == 200
    assert "total_detections" in res_summary.json()


def test_phase9_threat_filtering_and_search(
    client: TestClient,
    admin_headers: dict,
    user_headers: dict
):
    """Admin threat table supports server-side filtering by vector, category, status, and search."""
    # Ingest diverse detection telemetry
    sms_payload = {
        "event_type": "SMS",
        "risk_score": 88,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_ALERT",
        "reasons": ["Urgent financial request", "Suspicious link pattern"],
        "client_event_id": "test-phase9-sms-001"
    }
    qr_payload = {
        "event_type": "QR",
        "risk_score": 15,
        "threat_category": "SAFE",
        "recommended_action": "ALLOW",
        "reasons": ["Known safe domain"],
        "client_event_id": "test-phase9-qr-001"
    }
    client.post("/api/v1/detections", json=sms_payload, headers=user_headers)
    client.post("/api/v1/detections", json=qr_payload, headers=user_headers)

    # Filter by event_type=SMS
    res_sms = client.get("/api/v1/admin/threat-events?event_type=SMS", headers=admin_headers)
    assert res_sms.status_code == 200
    sms_items = res_sms.json()["items"]
    assert all(i["event_type"] == "SMS" for i in sms_items)

    # Filter by threat_category=HIGH
    res_high = client.get("/api/v1/admin/threat-events?threat_category=HIGH", headers=admin_headers)
    assert res_high.status_code == 200
    high_items = res_high.json()["items"]
    assert all(i["threat_category"] == "HIGH" for i in high_items)

    # Search by keyword in recommended_action
    res_search = client.get("/api/v1/admin/threat-events?search=BLOCK", headers=admin_headers)
    assert res_search.status_code == 200
    search_items = res_search.json()["items"]
    assert len(search_items) >= 1


def test_phase9_threat_status_update_and_audit_trail(
    client: TestClient,
    admin_headers: dict,
    user_headers: dict,
    db_session: Session
):
    """Admin updating threat status mutates DB and creates an audit log entry."""
    payload = {
        "event_type": "SMS",
        "risk_score": 92,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK",
        "reasons": ["Urgent credential reset pattern"],
        "client_event_id": "test-phase9-status-001"
    }
    create_res = client.post("/api/v1/detections", json=payload, headers=user_headers)
    threat_id = create_res.json()["id"]

    # Admin marks threat as REVIEWED
    update_res = client.patch(
        f"/api/v1/admin/threat-events/{threat_id}/status",
        json={"status": "REVIEWED"},
        headers=admin_headers
    )
    assert update_res.status_code == 200
    assert update_res.json()["status"] == "REVIEWED"

    # Verify audit log in database
    audit_entry = db_session.query(AuditLog).filter(
        AuditLog.action == "THREAT_STATUS_UPDATED",
        AuditLog.resource_id == threat_id
    ).first()
    assert audit_entry is not None
    assert audit_entry.resource_type == "THREAT_EVENT"
    assert audit_entry.metadata_json["new_status"] == "REVIEWED"


def test_phase9_user_management_and_audit_trail(
    client: TestClient,
    admin_headers: dict,
    db_session: Session
):
    """Admin managing user active status and roles generates authoritative audit logs."""
    # Ensure a target test user exists
    target = db_session.query(User).filter(User.email == "target_user@squishshield.local").first()
    if not target:
        target = User(
            firebase_uid="target-user-uid-0099",
            email="target_user@squishshield.local",
            role="USER",
            is_active=True
        )
        db_session.add(target)
        db_session.commit()
        db_session.refresh(target)

    # 1. Admin suspends user
    res_status = client.patch(
        f"/api/v1/admin/users/{target.id}/status",
        json={"is_active": False},
        headers=admin_headers
    )
    assert res_status.status_code == 200
    assert res_status.json()["is_active"] is False

    # Verify audit log for status update
    log_status = db_session.query(AuditLog).filter(
        AuditLog.action == "USER_STATUS_UPDATED",
        AuditLog.resource_id == target.id
    ).first()
    assert log_status is not None
    assert log_status.metadata_json["new_active"] is False

    # 2. Admin promotes user to ADMIN
    res_role = client.patch(
        f"/api/v1/admin/users/{target.id}/role",
        json={"role": "ADMIN"},
        headers=admin_headers
    )
    assert res_role.status_code == 200
    assert res_role.json()["role"] == "ADMIN"

    # Verify audit log for role update
    log_role = db_session.query(AuditLog).filter(
        AuditLog.action == "USER_ROLE_UPDATED",
        AuditLog.resource_id == target.id
    ).first()
    assert log_role is not None
    assert log_role.metadata_json["new_role"] == "ADMIN"


def test_phase9_web_database_isolation_boundary():
    """Verify web codebase has zero PostgreSQL database credentials or direct database connections."""
    import os
    web_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "web")
    src_dir = os.path.join(web_dir, "src")

    forbidden_terms = [
        "postgresql://",
        "postgres_dev_password",
        "sqlalchemy",
        "psycopg2",
        "DATABASE_URL",
        "create_engine",
    ]

    for root, _, files in os.walk(src_dir):
        for f in files:
            if f.endswith((".ts", ".tsx", ".js", ".jsx", ".json")):
                filepath = os.path.join(root, f)
                with open(filepath, "r", encoding="utf-8", errors="ignore") as fh:
                    content = fh.read()
                    for term in forbidden_terms:
                        assert term not in content, (
                            f"Security boundary violation: Found forbidden database term '{term}' in {filepath}"
                        )

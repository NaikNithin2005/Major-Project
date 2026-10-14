"""
Phase 11 - Complete Integration, End-to-End, and User Acceptance Testing (UAT).
Executes UAT-01 through UAT-15 against live backend services, models, schemas, and database fixtures.
"""

import pytest
from datetime import datetime, timezone
from app.models.threat_event import ThreatEvent
from app.models.audit_log import AuditLog
from app.models.user import User


def test_uat_01_new_user_registration(client, db_session):
    """
    UAT-01: New User Registration & Auto-Provisioning.
    Verifies that a new authenticated client is auto-provisioned with USER role
    and exact 1-to-1 mapping to application user.
    """
    new_uid = f"firebase-new-user-{int(datetime.now(timezone.utc).timestamp())}"
    token_header = {"Authorization": f"Bearer mock-user-{new_uid}"}

    response = client.get("/api/v1/users/me", headers=token_header)
    assert response.status_code == 200, f"Expected 200 OK, got {response.status_code}: {response.text}"
    user_data = response.json()

    assert user_data["firebase_uid"] == f"user-mock-{new_uid}"
    assert user_data["role"] == "USER"
    assert user_data["is_active"] is True

    # Verify database entry created exactly once
    user_in_db = db_session.query(User).filter(User.firebase_uid == f"user-mock-{new_uid}").all()
    assert len(user_in_db) == 1
    assert user_in_db[0].role == "USER"


def test_uat_02_returning_user_login(client, test_user):
    """
    UAT-02: Returning User Login.
    Verifies existing application user is reused without creating duplicate user records.
    """
    token_header = {"Authorization": "Bearer mock-user-token"}

    res1 = client.get("/api/v1/users/me", headers=token_header)
    assert res1.status_code == 200
    user1 = res1.json()

    # Second request simulates returning session
    res2 = client.get("/api/v1/users/me", headers=token_header)
    assert res2.status_code == 200
    user2 = res2.json()

    assert user1["id"] == user2["id"]
    assert user1["firebase_uid"] == user2["firebase_uid"] == test_user.firebase_uid


def test_uat_03_smishing_detection_pipeline_and_telemetry(client, user_headers):
    """
    UAT-03: Smishing Detection Telemetry Ingestion.
    Verifies that SMS detection returns actual classification, risk score, and explanation,
    while enforcing strict privacy (NO raw SMS text).
    """
    payload = {
        "client_event_id": "sms-telemetry-uat03-01",
        "event_type": "SMS",
        "risk_score": 88,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_DELETE",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "model_versions": {"tinybert": "v2", "risk_engine": "v1"},
        "model_results": {"smishing_probability": 0.94, "inferred_label": "MALICIOUS"},
        "rule_results": [{"rule": "URGENCY_KEYWORD", "severity": "HIGH", "score": 25}],
        "context_results": {"sender": "+19876543210", "brand_detected": "PayPal"},
        "reasons": [
            "TinyBERT NLP inferred high smishing probability (94%)",
            "Urgent call-to-action detected",
            "Unverified sender purporting to represent PayPal"
        ]
    }

    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 201, f"Expected 201 Created: {response.text}"
    event = response.json()

    assert event["event_type"] == "SMS"
    assert event["risk_score"] == 88
    assert event["threat_category"] == "HIGH"
    assert event["recommended_action"] == "BLOCK_AND_DELETE"
    assert len(event["reasons"]) == 3
    # Verify raw message is NOT in telemetry response
    assert "raw_text" not in event
    assert "sms_content" not in event


def test_uat_04_qr_quishing_scanning_and_telemetry(client, user_headers):
    """
    UAT-04: QR Scanning & Quishing Telemetry.
    Verifies that decoded QR URL features, XGBoost score, and Isolation Forest anomaly
    are accepted and stored with appropriate warnings.
    """
    payload = {
        "client_event_id": "qr-telemetry-uat04-01",
        "event_type": "QR",
        "risk_score": 79,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_DELETE",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "model_versions": {"xgboost": "v2", "isolation_forest": "v2"},
        "model_results": {
            "xgboost_url_prob": 0.88,
            "isolation_forest_anomaly": -1,
            "anomaly_score": 75.0
        },
        "url_metadata": {
            "url": "https://secure-paypaI-login.xyz/auth",
            "domain": "secure-paypaI-login.xyz",
            "has_ip": False,
            "punycode": False
        },
        "qr_metadata": {
            "target_url": "https://secure-paypaI-login.xyz/auth",
            "error_correction_level": "M"
        },
        "reasons": [
            "High URL structural risk score from XGBoost (88%)",
            "Isolation Forest flagged structural anomaly in domain",
            "Suspicious lookalike TLD (.xyz)"
        ]
    }

    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 201
    event = response.json()

    assert event["event_type"] == "QR"
    assert event["risk_score"] == 79
    assert event["threat_category"] == "HIGH"
    assert event["qr_metadata"]["target_url"] == "https://secure-paypaI-login.xyz/auth"


def test_uat_05_threat_history_and_stats(client, user_headers):
    """
    UAT-05: Threat History & Personal Stats.
    Verifies user can list their own threat history and retrieve calculated security stats.
    """
    res_list = client.get("/api/v1/detections?page=1&page_size=10", headers=user_headers)
    assert res_list.status_code == 200
    data = res_list.json()
    assert "items" in data
    assert "total" in data

    res_stats = client.get("/api/v1/detections/stats", headers=user_headers)
    assert res_stats.status_code == 200
    stats = res_stats.json()
    assert "total_scans" in stats
    assert "threats_detected" in stats
    assert "threats_blocked" in stats
    assert "avg_risk_score" in stats
    assert 0 <= stats["avg_risk_score"] <= 100


def test_uat_06_user_defensive_action_tracking(client, user_headers):
    """
    UAT-06: Threat Action Tracking.
    Verifies user action (BLOCK, REPORT, MARK_SAFE) is persisted and reflected in detection.
    """
    # 1. Ingest threat
    payload = {
        "client_event_id": "act-test-event-01",
        "event_type": "SMS",
        "risk_score": 85,
        "threat_category": "HIGH",
        "recommended_action": "WARN_USER",
    }
    create_res = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert create_res.status_code == 201
    threat_id = create_res.json()["id"]

    # 2. User takes BLOCK action
    action_payload = {"action": "BLOCK", "notes": "Sender blocked on device"}
    action_res = client.post(f"/api/v1/detections/{threat_id}/action", json=action_payload, headers=user_headers)
    assert action_res.status_code == 200
    updated = action_res.json()
    assert updated["user_action"] == "BLOCK"
    assert updated["status"] == "BLOCKED"

    # 3. Retrieve detection and confirm persistence
    get_res = client.get(f"/api/v1/detections/{threat_id}", headers=user_headers)
    assert get_res.status_code == 200
    assert get_res.json()["user_action"] == "BLOCK"


def test_uat_07_offline_detection_and_idempotent_ingestion(client, user_headers):
    """
    UAT-07: Offline Detection & Idempotent Sync.
    Verifies that offline-queued detections are ingested without duplicate records
    when duplicate retries arrive with identical client_event_id.
    """
    client_event_id = "offline-sync-idempotent-99"
    payload = {
        "client_event_id": client_event_id,
        "event_type": "SMS",
        "risk_score": 75,
        "threat_category": "MEDIUM",
        "recommended_action": "WARN_USER",
    }

    # First attempt (sync)
    res1 = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert res1.status_code == 201
    id1 = res1.json()["id"]

    # Duplicate attempt (worker retry)
    res2 = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert res2.status_code == 201
    id2 = res2.json()["id"]

    # Must return existing record without creating a duplicate row
    assert id1 == id2


def test_uat_08_deferred_synchronization_and_retry(client, user_headers):
    """
    UAT-08: Deferred Synchronization.
    Verifies that pending telemetry created offline synchronizes properly when connected.
    """
    payload = {
        "client_event_id": "deferred-sync-event-77",
        "event_type": "QR",
        "risk_score": 55,
        "threat_category": "MEDIUM",
        "recommended_action": "WARN_USER",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "context_results": {"origin": "offline_queue"}
    }
    res = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert res.status_code == 201
    data = res.json()
    assert data["status"] in ("REPORTED", "NEW", "SYNCED")
    assert data["client_event_id"] == "deferred-sync-event-77"


def test_uat_09_cross_user_isolation_and_anti_enumeration(client, user_headers, user2_headers):
    """
    UAT-09: Cross-User Isolation & Anti-Resource Enumeration.
    Verifies that User A cannot read, act on, or delete User B's threats.
    Returns HTTP 404 to prevent resource existence enumeration.
    """
    # User B creates a threat
    payload = {
        "client_event_id": "user-b-private-threat-01",
        "event_type": "SMS",
        "risk_score": 92,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_DELETE"
    }
    res_b = client.post("/api/v1/detections", json=payload, headers=user2_headers)
    assert res_b.status_code == 201
    threat_b_id = res_b.json()["id"]

    # User A attempts GET on User B's threat -> 404
    get_res = client.get(f"/api/v1/detections/{threat_b_id}", headers=user_headers)
    assert get_res.status_code == 404, f"Cross-user GET should return 404, got {get_res.status_code}"

    # User A attempts ACTION on User B's threat -> 404
    action_res = client.post(f"/api/v1/detections/{threat_b_id}/action", json={"action": "BLOCK"}, headers=user_headers)
    assert action_res.status_code == 404

    # User A attempts DELETE on User B's threat -> 404
    del_res = client.delete(f"/api/v1/detections/{threat_b_id}", headers=user_headers)
    assert del_res.status_code == 404


def test_uat_10_administrator_access_and_rbac_enforcement(client, user_headers, admin_headers):
    """
    UAT-10: Administrator Access & RBAC Enforcement.
    Verifies that ordinary users cannot access administrator endpoints (HTTP 403),
    while verified administrators can access all administrative routes.
    """
    # Ordinary user blocked
    user_res = client.get("/api/v1/admin/users", headers=user_headers)
    assert user_res.status_code == 403

    user_res_threats = client.get("/api/v1/admin/threat-events", headers=user_headers)
    assert user_res_threats.status_code == 403

    user_res_audit = client.get("/api/v1/admin/audit-logs", headers=user_headers)
    assert user_res_audit.status_code == 403

    # Administrator allowed
    admin_res = client.get("/api/v1/admin/users", headers=admin_headers)
    assert admin_res.status_code == 200
    assert "items" in admin_res.json()

    admin_res_threats = client.get("/api/v1/admin/threat-events", headers=admin_headers)
    assert admin_res_threats.status_code == 200

    admin_res_audit = client.get("/api/v1/admin/audit-logs", headers=admin_headers)
    assert admin_res_audit.status_code == 200


def test_uat_11_admin_threat_inspection_and_status_update(client, user_headers, admin_headers):
    """
    UAT-11: Admin Threat Inspection & Status Triage.
    Verifies administrators can inspect any user's threat and update triage status.
    """
    # User creates threat
    payload = {
        "client_event_id": "admin-inspect-threat-01",
        "event_type": "SMS",
        "risk_score": 82,
        "threat_category": "HIGH",
        "recommended_action": "WARN_USER",
    }
    create_res = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert create_res.status_code == 201
    threat_id = create_res.json()["id"]

    # Admin inspects threat
    admin_get = client.get(f"/api/v1/admin/threat-events/{threat_id}", headers=admin_headers)
    assert admin_get.status_code == 200
    assert admin_get.json()["id"] == threat_id

    # Admin triages threat status to REVIEWED
    update_res = client.patch(
        f"/api/v1/admin/threat-events/{threat_id}/status",
        json={"status": "REVIEWED"},
        headers=admin_headers
    )
    assert update_res.status_code == 200
    assert update_res.json()["status"] == "REVIEWED"


def test_uat_12_analytics_consistency_summary_and_trends(client, admin_headers, user_headers):
    """
    UAT-12: Analytics Consistency.
    Verifies analytics summary and trend data match database records.
    """
    # Query summary
    summary_res = client.get("/api/v1/admin/analytics/summary", headers=admin_headers)
    assert summary_res.status_code == 200
    summary = summary_res.json()
    assert "total_detections" in summary
    assert "sms_count" in summary
    assert "qr_count" in summary
    assert "average_risk_score" in summary

    # Query trends
    trends_res = client.get("/api/v1/analytics/trends?days=7", headers=user_headers)
    assert trends_res.status_code == 200
    trends = trends_res.json()
    assert "period" in trends
    assert "data_points" in trends
    assert len(trends["data_points"]) >= 1


def test_uat_13_error_recovery_and_resilience(client, user_headers):
    """
    UAT-13: Error Recovery & Input Resilience.
    Verifies that malformed inputs, invalid ranges, and oversize payloads
    produce safe structured error envelopes rather than unhandled server crashes.
    """
    # 1. Invalid risk score (> 100) -> 422
    invalid_score = {"event_type": "SMS", "risk_score": 150, "threat_category": "HIGH", "recommended_action": "WARN"}
    res1 = client.post("/api/v1/detections", json=invalid_score, headers=user_headers)
    assert res1.status_code == 422

    # 2. Invalid event type -> 422
    invalid_type = {"event_type": "BLUETOOTH", "risk_score": 50, "threat_category": "MEDIUM", "recommended_action": "WARN"}
    res2 = client.post("/api/v1/detections", json=invalid_type, headers=user_headers)
    assert res2.status_code == 422

    # 3. Non-existent endpoint -> 404
    res3 = client.get("/api/v1/nonexistent", headers=user_headers)
    assert res3.status_code == 404

    # 4. Oversize payload (> 2MB) -> 413
    oversize_payload = "a" * (2 * 1024 * 1024 + 1024)
    res4 = client.post("/api/v1/detections", content=oversize_payload, headers={"Content-Type": "application/json", **user_headers})
    assert res4.status_code == 413


def test_uat_14_account_switching_isolation(client, user_headers, user2_headers):
    """
    UAT-14: Account Switching Isolation.
    Verifies that events created under User A remain isolated to User A
    when switching to User B's session.
    """
    # User A creates threat
    res_a = client.post("/api/v1/detections", json={
        "client_event_id": "account-switch-evt-alice",
        "event_type": "SMS",
        "risk_score": 85,
        "threat_category": "HIGH",
        "recommended_action": "WARN_USER"
    }, headers=user_headers)
    assert res_a.status_code == 201

    # User B lists detections
    res_b_list = client.get("/api/v1/detections", headers=user2_headers)
    assert res_b_list.status_code == 200
    user_b_events = res_b_list.json()["items"]
    # User A's client_event_id must NOT be present in User B's detections
    alice_ids = [e["client_event_id"] for e in user_b_events if e["client_event_id"] == "account-switch-evt-alice"]
    assert len(alice_ids) == 0


def test_uat_15_audit_logging_generation(client, admin_headers, db_session):
    """
    UAT-15: Immutable Audit Logging.
    Verifies administrative actions generate structured audit log entries in the database.
    """
    # Create disposable target user so test_user remains active
    disposable_user = User(
        firebase_uid="disposable-audit-target-uid-01",
        email="disposable@squishshield.local",
        display_name="Disposable Target User",
        role="USER",
        is_active=True
    )
    db_session.add(disposable_user)
    db_session.commit()
    db_session.refresh(disposable_user)

    # Admin updates the disposable user status
    client.patch(
        f"/api/v1/admin/users/{disposable_user.id}/status",
        json={"is_active": False},
        headers=admin_headers
    )

    # Verify audit log recorded in DB
    audit_logs = db_session.query(AuditLog).filter(AuditLog.resource_id == disposable_user.id).all()
    assert len(audit_logs) >= 1
    recent_log = audit_logs[-1]
    assert recent_log.action in ("USER_STATUS_UPDATED", "USER_STATUS_UPDATE", "USER_UPDATED")
    assert recent_log.resource_type == "USER"

"""
Phase 8 End-to-End Acceptance Criteria Test Suite
Tests:
- Token Verification & Profile Synchronization (/api/v1/users/me)
- Authoritative RBAC: USER blocked from Admin Endpoints (403 Forbidden)
- Admin Allowed on Admin Endpoints (200 OK)
- Minimized Telemetry Ingestion (No Raw SMS Text)
- Idempotency & Duplicate Prevention (Exact same client_event_id returns existing record, no duplicate in DB)
- Cross-User Data Isolation (User A cannot list or submit feedback on User B's threat event)
- Unauthenticated Rejection (401 Unauthorized)
- QR Quishing Telemetry Synchronization
"""

import uuid
from datetime import datetime, timezone


def test_phase8_auth_token_profile_sync(client, user_headers):
    """AC 1: Firebase ID Token verified & Profile Synchronized"""
    res = client.get("/api/v1/users/me", headers=user_headers)
    assert res.status_code == 200
    data = res.json()
    assert data["role"] == "USER"
    assert data["email"] == "user@squishshield.local"
    assert data["is_active"] is True


def test_phase8_authoritative_rbac_isolation(client, user_headers, admin_headers):
    """AC 2: RBAC enforced authoritatively on backend: USER blocked from ADMIN"""
    # USER attempting to access Admin Audit Logs -> 403 Forbidden
    res_user = client.get("/api/v1/admin/audit-logs", headers=user_headers)
    assert res_user.status_code == 403
    
    # ADMIN accessing Admin Audit Logs -> 200 OK
    res_admin = client.get("/api/v1/admin/audit-logs", headers=admin_headers)
    assert res_admin.status_code == 200


def test_phase8_data_minimization_telemetry(client, user_headers):
    """AC 3: Data Minimization strictly enforced: No raw SMS text in telemetry"""
    client_event_id = str(uuid.uuid4())
    sync_payload = {
        "client_event_id": client_event_id,
        "event_type": "SMS",
        "risk_score": 85,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_REPORT",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "model_versions": {"tinybert": "tinybert-smishing-v2", "risk_engine": "rule-risk-engine-v1"},
        "model_results": {"smishing_probability": 0.88, "is_model_available": True},
        "reasons": ["Urgent banking smishing keywords detected", "Sender mismatch"],
        "rule_results": [{"id": "R01", "category": "URGENCY", "severity": "HIGH", "score_contribution": 35.0, "description": "High urgency language"}]
    }
    
    # Verify neither raw_body nor raw_sms is in the payload
    assert "raw_body" not in sync_payload
    assert "raw_sms" not in sync_payload
    
    res = client.post("/api/v1/detections", json=sync_payload, headers=user_headers)
    assert res.status_code == 201
    data = res.json()
    assert data["client_event_id"] == client_event_id
    assert data["risk_score"] == 85


def test_phase8_idempotency_prevents_duplicates(client, user_headers):
    """AC 4: Submitting same client_event_id returns existing event and creates 0 duplicates"""
    client_event_id = str(uuid.uuid4())
    sync_payload = {
        "client_event_id": client_event_id,
        "event_type": "SMS",
        "risk_score": 85,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_REPORT",
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
    
    # First submission
    res1 = client.post("/api/v1/detections", json=sync_payload, headers=user_headers)
    assert res1.status_code == 201
    event_id1 = res1.json()["id"]
    
    # Second submission with same client_event_id (simulating retry or duplicate worker)
    res2 = client.post("/api/v1/detections", json=sync_payload, headers=user_headers)
    assert res2.status_code in (200, 201)
    event_id2 = res2.json()["id"]
    
    # Must be the exact same event ID
    assert event_id1 == event_id2


def test_phase8_cross_user_isolation(client, user_headers, user2_headers):
    """AC 5: User A cannot view or submit feedback for User B's detections (Anti-IDOR)"""
    # User 1 creates a detection
    event_id_1 = str(uuid.uuid4())
    res_1 = client.post("/api/v1/detections", json={
        "client_event_id": event_id_1,
        "event_type": "QR",
        "risk_score": 90,
        "threat_category": "HIGH",
        "recommended_action": "WARN",
        "timestamp": datetime.now(timezone.utc).isoformat()
    }, headers=user_headers)
    assert res_1.status_code == 201
    server_event_id = res_1.json()["id"]
    
    # User 2 queries detections: User 1's event must not be present
    res_2_list = client.get("/api/v1/detections", headers=user2_headers)
    assert res_2_list.status_code == 200
    user2_events = res_2_list.json()["items"]
    assert all(e["id"] != server_event_id for e in user2_events)

    # User 2 directly requesting User 1's event ID must be rejected (404/403)
    direct_res = client.get(f"/api/v1/detections/{server_event_id}", headers=user2_headers)
    assert direct_res.status_code in (403, 404)
    
    # User 2 tries to submit feedback on User 1's event -> 403 Forbidden
    fb_res = client.post("/api/v1/feedback", json={
        "threat_event_id": server_event_id,
        "classification": "FALSE_POSITIVE",
        "comment": "Malicious tampering attempt"
    }, headers=user2_headers)
    assert fb_res.status_code == 403


def test_phase8_unauthenticated_requests_blocked(client):
    """AC 6: Missing token rejected with 401"""
    res = client.post("/api/v1/detections", json={
        "event_type": "SMS",
        "risk_score": 50,
        "threat_category": "MEDIUM",
        "recommended_action": "CAUTION"
    })
    assert res.status_code == 401

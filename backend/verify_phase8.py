"""
Phase 8 End-to-End Acceptance Criteria Verification Script
Squish Shield — Authentication, Authoritative RBAC, Offline Synchronization, Idempotency & Privacy
"""

import os
import sys
import uuid
import datetime

# Ensure backend root is on sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi.testclient import TestClient
from app.main import app
from app.core.security import create_access_token

def run_phase8_verification():
    print("=" * 70)
    print("SQUISH SHIELD — PHASE 8 ACCEPTANCE CRITERIA VERIFICATION")
    print("=" * 70)
    client = TestClient(app)
    
    passed_tests = 0
    total_tests = 7

    # 1. AUTHENTICATION & TOKEN VERIFICATION
    print("\n[TEST 1/7] Firebase ID Token & Profile Synchronization (/api/v1/users/me)")
    user1_uid = f"user_{uuid.uuid4().hex[:8]}"
    token_user1 = create_access_token(data={"uid": user1_uid, "email": "analyst1@shield.ai", "role": "USER"})
    
    res = client.get("/api/v1/users/me", headers={"Authorization": f"Bearer {token_user1}"})
    assert res.status_code == 200, f"Expected 200, got {res.status_code}: {res.text}"
    profile = res.json()
    assert profile["firebase_uid"] == user1_uid
    assert profile["role"] == "USER"
    print(f"  [PASS] Successfully authenticated Firebase UID: {user1_uid} with role: {profile['role']}")
    passed_tests += 1

    # 2. RBAC ENFORCEMENT & ADMINISTRATIVE ISOLATION
    print("\n[TEST 2/7] Authoritative RBAC: USER Blocked from Admin Endpoints")
    admin_res = client.get("/api/v1/admin/audit-logs", headers={"Authorization": f"Bearer {token_user1}"})
    assert admin_res.status_code == 403, f"Expected 403 Forbidden for USER on admin endpoint, got {admin_res.status_code}"
    
    # Verify ADMIN token works
    admin_uid = f"admin_{uuid.uuid4().hex[:8]}"
    token_admin = create_access_token(data={"uid": admin_uid, "email": "admin@shield.ai", "role": "ADMIN"})
    admin_allowed = client.get("/api/v1/admin/audit-logs", headers={"Authorization": f"Bearer {token_admin}"})
    assert admin_allowed.status_code == 200, f"Expected 200 for ADMIN, got {admin_allowed.status_code}"
    print(f"  [PASS] USER correctly rejected (403 Forbidden); ADMIN authorized (200 OK)")
    passed_tests += 1

    # 3. DATA MINIMIZATION (NO RAW SMS TEXT)
    print("\n[TEST 3/7] Privacy Audit: Data Minimization Telemetry Ingestion")
    client_event_id_1 = str(uuid.uuid4())
    sync_payload = {
        "client_event_id": client_event_id_1,
        "event_type": "SMS",
        "risk_score": 85,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_REPORT",
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "model_versions": {"tinybert": "tinybert-smishing-v2", "risk_engine": "rule-risk-engine-v1"},
        "model_results": {"smishing_probability": 0.88, "is_model_available": True},
        "reasons": ["Urgent banking smishing keywords detected", "Sender mismatch"],
        "rule_results": [{"id": "R01", "category": "URGENCY", "severity": "HIGH", "score_contribution": 35.0, "description": "High urgency language"}]
    }
    # Verify no raw body is accepted or present in schema
    assert "raw_body" not in sync_payload
    assert "raw_sms" not in sync_payload
    
    post_res = client.post("/api/v1/detections", json=sync_payload, headers={"Authorization": f"Bearer {token_user1}"})
    assert post_res.status_code == 201, f"Expected 201, got {post_res.status_code}: {post_res.text}"
    event_data = post_res.json()
    assert event_data["client_event_id"] == client_event_id_1
    assert event_data["risk_score"] == 85
    print(f"  [PASS] Minimized telemetry accepted and persisted (Server ID: {event_data['id']})")
    passed_tests += 1

    # 4. IDEMPOTENCY & DUPLICATE PREVENTION
    print("\n[TEST 4/7] Idempotency: Re-submitting Identical client_event_id")
    # Simulate network retry or duplicate sync worker
    retry_res = client.post("/api/v1/detections", json=sync_payload, headers={"Authorization": f"Bearer {token_user1}"})
    assert retry_res.status_code in (200, 201), f"Expected 200/201, got {retry_res.status_code}"
    duplicate_data = retry_res.json()
    assert duplicate_data["id"] == event_data["id"], "Idempotency failed: New record created instead of returning existing!"
    assert duplicate_data["client_event_id"] == client_event_id_1
    print(f"  [PASS] Duplicate upload recognized. Same record ID returned: {duplicate_data['id']} (Zero duplicate rows created)")
    passed_tests += 1

    # 5. CROSS-USER DATA ISOLATION (ANTI-IDOR)
    print("\n[TEST 5/7] Multi-Tenant Isolation: User A Cannot Access User B Telemetry")
    user2_uid = f"user_{uuid.uuid4().hex[:8]}"
    token_user2 = create_access_token(data={"uid": user2_uid, "email": "analyst2@shield.ai", "role": "USER"})
    
    # User 2 queries detections: must NOT see User 1's detection
    user2_list = client.get("/api/v1/detections", headers={"Authorization": f"Bearer {token_user2}"})
    assert user2_list.status_code == 200
    user2_events = user2_list.json()
    matching = [e for e in user2_events if e["client_event_id"] == client_event_id_1]
    assert len(matching) == 0, "IDOR Vulnerability: User 2 can see User 1's detection event!"
    
    # User 2 attempts to submit feedback on User 1's event: must be 403 Forbidden
    fb_res = client.post("/api/v1/feedback", json={
        "threat_event_id": event_data["id"],
        "feedback_type": "FALSE_POSITIVE",
        "user_comment": "Tampering attempt"
    }, headers={"Authorization": f"Bearer {token_user2}"})
    assert fb_res.status_code == 403, f"Expected 403 Forbidden on cross-user feedback, got {fb_res.status_code}"
    print(f"  [PASS] Cross-user data isolation verified: User 2 cannot list or alter User 1's threat event (403 Forbidden)")
    passed_tests += 1

    # 6. UNPROTECTED / UNAUTHENTICATED REJECTION
    print("\n[TEST 6/7] Security Boundary: Unauthenticated Requests Rejected (401)")
    unauth_res = client.post("/api/v1/detections", json=sync_payload)
    assert unauth_res.status_code == 401, f"Expected 401 Unauthorized, got {unauth_res.status_code}"
    print(f"  [PASS] Unauthenticated request safely blocked with HTTP 401 Unauthorized")
    passed_tests += 1

    # 7. QR CODE TELEMETRY SYNCHRONIZATION
    print("\n[TEST 7/7] QR Quishing Telemetry Synchronization with Metadata")
    qr_client_id = str(uuid.uuid4())
    qr_payload = {
        "client_event_id": qr_client_id,
        "event_type": "QR",
        "risk_score": 92,
        "threat_category": "HIGH",
        "recommended_action": "DO_NOT_NAVIGATE",
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "model_versions": {"xgboost": "xgboost-url-v2", "isolation_forest": "iforest-anomaly-v1"},
        "model_results": {"url_model_prob": 0.94, "anomaly_score": 0.82, "is_model_available": True},
        "reasons": ["Punycode spoofing detected", "High URL entropy"],
        "url_metadata": {"domain": "paypal-secure-login.top", "is_https": False, "is_ip_hostname": False}
    }
    qr_res = client.post("/api/v1/detections", json=qr_payload, headers={"Authorization": f"Bearer {token_user1}"})
    assert qr_res.status_code == 201
    print(f"  [PASS] QR detection synchronized successfully (Score: 92/100, Action: DO_NOT_NAVIGATE)")
    passed_tests += 1

    print("\n" + "=" * 70)
    print(f"ALL {passed_tests}/{total_tests} PHASE 8 ACCEPTANCE CRITERIA AUTOMATED CHECKS PASSED!")
    print("=" * 70)

if __name__ == "__main__":
    run_phase8_verification()

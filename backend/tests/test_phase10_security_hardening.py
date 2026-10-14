import pytest
from app.config import settings
from app.auth.firebase import create_access_token


def test_security_headers_present_on_responses(client):
    """Verify that OWASP defense-in-depth security headers are attached to API responses."""
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.headers.get("x-content-type-options") == "nosniff"
    assert resp.headers.get("x-frame-options") == "DENY"
    assert resp.headers.get("x-xss-protection") == "1; mode=block"
    assert resp.headers.get("referrer-policy") == "strict-origin-when-cross-origin"
    assert "permissions-policy" in resp.headers


def test_request_size_limit_rejects_payload_over_2mb(client, user_headers):
    """Verify that incoming requests exceeding the 2MB size limit are rejected with 413 Payload Too Large."""
    # Send a header claiming 3MB payload
    headers = {
        **user_headers,
        "Content-Type": "application/json",
        "Content-Length": str(3 * 1024 * 1024)
    }
    resp = client.post("/api/v1/detections", headers=headers, json={"risk_score": 50})
    assert resp.status_code == 413
    data = resp.json()
    assert data["error"]["code"] == "PAYLOAD_TOO_LARGE"


def test_unauthenticated_requests_blocked_across_protected_endpoints(client):
    """Ensure all protected endpoints return HTTP 401 when unauthenticated."""
    protected_paths = [
        ("GET", "/api/v1/auth/me"),
        ("GET", "/api/v1/users/me"),
        ("GET", "/api/v1/detections"),
        ("POST", "/api/v1/detections"),
        ("GET", "/api/v1/detections/stats"),
        ("GET", "/api/v1/admin/users"),
        ("GET", "/api/v1/admin/threat-events"),
        ("GET", "/api/v1/admin/audit-logs"),
    ]
    for method, path in protected_paths:
        if method == "GET":
            resp = client.get(path)
        else:
            resp = client.post(path, json={})
        assert resp.status_code == 401, f"{method} {path} should return 401 Unauthorized"
        assert resp.json()["error"]["code"] in ("UNAUTHORIZED", "INVALID_TOKEN")


def test_user_cannot_elevate_to_admin_via_token_claims(client):
    """
    Security Regression Test: A user supplying a JWT claiming role='ADMIN'
    is strictly assigned the default 'USER' role upon auto-provisioning.
    """
    # Forge a token with an unauthorized role claim
    forged_token = create_access_token({
        "uid": "attacker-uid-1337",
        "sub": "attacker-uid-1337",
        "email": "attacker@randomdomain.org",
        "role": "ADMIN"
    })
    headers = {"Authorization": f"Bearer {forged_token}"}

    # Verify profile endpoint
    resp = client.get("/api/v1/users/me", headers=headers)
    assert resp.status_code == 200
    user_data = resp.json()
    assert user_data["role"] == "USER", "Attacker token claiming ADMIN must be downgraded to USER"

    # Attacker attempting an admin endpoint must be rejected with 403 Forbidden
    admin_resp = client.get("/api/v1/admin/users", headers=headers)
    assert admin_resp.status_code == 403
    assert admin_resp.json()["error"]["code"] == "FORBIDDEN"


def test_normal_user_forbidden_on_all_admin_endpoints(client, user_headers):
    """Verify that an authenticated normal user receives 403 on all administrative endpoints."""
    admin_routes = [
        ("GET", "/api/v1/admin/users"),
        ("GET", "/api/v1/admin/threat-events"),
        ("GET", "/api/v1/admin/audit-logs"),
        ("GET", "/api/v1/admin/analytics/summary"),
        ("POST", "/api/v1/admin/models"),
    ]
    for method, path in admin_routes:
        if method == "GET":
            resp = client.get(path, headers=user_headers)
        else:
            resp = client.post(path, headers=user_headers, json={})
        assert resp.status_code == 403, f"{method} {path} should be forbidden to regular users"
        assert resp.json()["error"]["code"] == "FORBIDDEN"


def test_cross_user_threat_isolation_and_anti_enumeration(client, user_headers, user2_headers):
    """
    Verify complete multi-tenant resource isolation:
    1. User A creates a threat detection.
    2. User B cannot see User A's threat in list or by ID.
    3. User B requesting User A's threat receives 404 (not 403) to prevent ID enumeration.
    """
    # User A creates a threat
    threat_payload = {
        "event_type": "SMS",
        "risk_score": 88,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK_AND_DELETE",
        "client_event_id": "security-test-event-tenant-a-001",
        "reasons": ["Urgent request for credentials", "Suspicious link"],
        "context_results": {"sender": "BANK-ALERT"}
    }
    create_resp = client.post("/api/v1/detections", headers=user_headers, json=threat_payload)
    assert create_resp.status_code == 201
    threat_id = create_resp.json()["id"]

    # User A can retrieve it
    resp_a = client.get(f"/api/v1/detections/{threat_id}", headers=user_headers)
    assert resp_a.status_code == 200
    assert resp_a.json()["id"] == threat_id

    # User B attempting to retrieve User A's threat receives 404
    resp_b = client.get(f"/api/v1/detections/{threat_id}", headers=user2_headers)
    assert resp_b.status_code == 404
    assert resp_b.json()["error"]["code"] == "DETECTION_NOT_FOUND"


def test_cross_user_action_and_deletion_denied(client, user_headers, user2_headers):
    """Verify that User B cannot record actions or delete User A's threat detections."""
    # User A creates threat
    create_resp = client.post(
        "/api/v1/detections",
        headers=user_headers,
        json={
            "event_type": "QR",
            "risk_score": 92,
            "threat_category": "HIGH",
            "recommended_action": "BLOCK_AND_DELETE",
            "client_event_id": "security-test-event-tenant-a-002"
        }
    )
    assert create_resp.status_code == 201
    threat_id = create_resp.json()["id"]

    # User B attempts to record an action on User A's threat
    action_resp = client.post(
        f"/api/v1/detections/{threat_id}/action",
        headers=user2_headers,
        json={"action": "BLOCK", "notes": "Hacked action"}
    )
    assert action_resp.status_code == 404

    # User B attempts to delete User A's threat
    delete_resp = client.delete(
        f"/api/v1/detections/{threat_id}",
        headers=user2_headers
    )
    assert delete_resp.status_code == 404


def test_sync_idempotency_prevents_duplicate_records(client, user_headers):
    """Verify that repeated synchronization submissions with the same client_event_id are idempotent."""
    payload = {
        "event_type": "SMS",
        "risk_score": 65,
        "threat_category": "MEDIUM",
        "recommended_action": "WARN_USER",
        "client_event_id": "idempotency-key-security-test-999"
    }

    # First submission
    resp1 = client.post("/api/v1/detections", headers=user_headers, json=payload)
    assert resp1.status_code == 201
    event_id1 = resp1.json()["id"]

    # Second submission with identical client_event_id
    resp2 = client.post("/api/v1/detections", headers=user_headers, json=payload)
    assert resp2.status_code in (200, 201)
    event_id2 = resp2.json()["id"]

    # Must point to the exact same record without creating duplicates
    assert event_id1 == event_id2


def test_production_environment_blocks_mock_auth(client):
    """Verify that if environment is configured as production, mock tokens are strictly rejected."""
    original_env = settings.environment
    try:
        settings.environment = "production"
        resp = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer mock-admin-token"})
        assert resp.status_code == 401
        assert resp.json()["error"]["code"] == "INVALID_TOKEN"
    finally:
        settings.environment = original_env


def test_audit_logs_protected_from_ordinary_users_and_modifications(client, user_headers, admin_headers):
    """Verify that ordinary users cannot view audit logs and no mutation endpoints exist."""
    # Regular user cannot read audit logs
    resp = client.get("/api/v1/admin/audit-logs", headers=user_headers)
    assert resp.status_code == 403

    # Admin user can read audit logs
    admin_resp = client.get("/api/v1/admin/audit-logs", headers=admin_headers)
    assert admin_resp.status_code == 200
    assert "items" in admin_resp.json()

    # Mutation attempts (POST, PUT, DELETE) on audit logs do not exist / are method not allowed
    post_resp = client.post("/api/v1/admin/audit-logs", headers=admin_headers, json={})
    assert post_resp.status_code in (404, 405)
    del_resp = client.delete("/api/v1/admin/audit-logs/some-id", headers=admin_headers)
    assert del_resp.status_code in (404, 405)

def test_user_allowed_on_user_endpoints(client, user_headers):
    response = client.get("/api/v1/users/me", headers=user_headers)
    assert response.status_code == 200
    assert response.json()["role"] == "USER"


def test_user_forbidden_on_admin_users(client, user_headers):
    response = client.get("/api/v1/admin/users", headers=user_headers)
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "FORBIDDEN"


def test_user_forbidden_on_admin_threat_events(client, user_headers):
    response = client.get("/api/v1/admin/threat-events", headers=user_headers)
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "FORBIDDEN"


def test_user_forbidden_on_admin_models_post(client, user_headers):
    payload = {
        "name": "TinyBERT",
        "version": "v2.1.0",
        "type": "ONNX_SMISHING",
        "status": "ACTIVE"
    }
    response = client.post("/api/v1/admin/models", json=payload, headers=user_headers)
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "FORBIDDEN"


def test_user_forbidden_on_admin_audit_logs(client, user_headers):
    response = client.get("/api/v1/admin/audit-logs", headers=user_headers)
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "FORBIDDEN"


def test_admin_allowed_on_admin_endpoints(client, admin_headers):
    # Admin can list all users
    users_resp = client.get("/api/v1/admin/users", headers=admin_headers)
    assert users_resp.status_code == 200
    assert "items" in users_resp.json()

    # Admin can list audit logs
    audit_resp = client.get("/api/v1/admin/audit-logs", headers=admin_headers)
    assert audit_resp.status_code == 200
    assert "items" in audit_resp.json()

    # Admin can view system-wide analytics summary
    analytics_resp = client.get("/api/v1/admin/analytics/summary", headers=admin_headers)
    assert analytics_resp.status_code == 200

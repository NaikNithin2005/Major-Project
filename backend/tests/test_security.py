def test_sqli_payload_in_query_params(client, user_headers):
    # Attempt SQL injection via query parameters
    sqli_payload = "' OR '1'='1' --"
    response = client.get(f"/api/v1/detections?threat_category={sqli_payload}", headers=user_headers)
    # Must succeed cleanly (parameterized query) with 0 matches, never 500 or DB crash
    assert response.status_code == 200
    assert response.json()["total"] == 0


def test_privilege_escalation_attempt_on_profile_update(client, user_headers):
    # User attempts mass-assignment / privilege escalation by sending role=ADMIN
    payload = {
        "display_name": "Hacker Attempt",
        "role": "ADMIN",
        "firebase_uid": "admin-compromised",
        "is_active": True
    }
    response = client.patch("/api/v1/users/me", json=payload, headers=user_headers)
    assert response.status_code == 200
    data = response.json()
    assert data["display_name"] == "Hacker Attempt"
    # Role must still be USER!
    assert data["role"] == "USER"
    assert data["firebase_uid"] == "user-mock-uid-0001"


def test_malformed_token_rejection(client):
    headers = {"Authorization": "Bearer not-a-valid-jwt-token"}
    response = client.get("/api/v1/users/me", headers=headers)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "INVALID_TOKEN"


def test_missing_auth_header_rejection(client):
    response = client.get("/api/v1/users/me")
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHORIZED"


def test_error_envelope_does_not_leak_internals(client):
    # 404 error envelope structure
    response = client.get("/api/v1/detections/00000000-0000-0000-0000-000000000000")
    assert response.status_code == 401  # Requires auth first
    data = response.json()
    assert "error" in data
    assert "code" in data["error"]
    assert "message" in data["error"]
    # Ensure no internal path or credentials leaked
    body_text = response.text.lower()
    assert "c:\\" not in body_text
    assert "traceback" not in body_text
    assert "select " not in body_text

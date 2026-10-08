def test_auth_me_no_token(client):
    response = client.get("/api/v1/auth/me")
    assert response.status_code == 401
    data = response.json()
    assert data["error"]["code"] == "UNAUTHORIZED"


def test_auth_me_invalid_token(client):
    response = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer invalid-token-string"})
    assert response.status_code == 401
    data = response.json()
    assert data["error"]["code"] == "INVALID_TOKEN"


def test_auth_me_valid_user(client, user_headers):
    response = client.get("/api/v1/auth/me", headers=user_headers)
    assert response.status_code == 200
    data = response.json()
    assert data["email"] == "user@squishshield.local"
    assert data["role"] == "USER"
    assert data["is_active"] is True


def test_auth_verify_endpoint(client, user_headers):
    response = client.post("/api/v1/auth/verify", headers=user_headers)
    assert response.status_code == 200
    data = response.json()
    assert data["valid"] is True
    assert data["user"]["email"] == "user@squishshield.local"


def test_auto_provisioning_new_user(client):
    # 'mock-user-brandnew' should auto-provision a new user record
    headers = {"Authorization": "Bearer mock-user-brandnew"}
    response = client.get("/api/v1/auth/me", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["firebase_uid"] == "user-mock-brandnew"
    assert data["email"] == "user_brandnew@squishshield.local"
    assert data["role"] == "USER"

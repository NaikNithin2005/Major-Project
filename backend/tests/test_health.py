def test_read_root(client):
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "squish-shield-backend"
    assert "phase" in data


def test_health_check(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "squish-shield-backend"


def test_db_health_check(client):
    response = client.get("/health/db")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["database"] == "connected"


def test_openapi_schema(client):
    response = client.get("/openapi.json")
    assert response.status_code == 200
    schema = response.json()
    assert schema["info"]["title"] == "Squish Shield Backend API"
    assert "/api/v1/auth/verify" in schema["paths"]
    assert "/api/v1/detections" in schema["paths"]
    assert "/api/v1/feedback" in schema["paths"]
    assert "/api/v1/analytics/summary" in schema["paths"]
    assert "/api/v1/models" in schema["paths"]
    assert "/api/v1/admin/users" in schema["paths"]


def test_docs_page(client):
    response = client.get("/docs")
    assert response.status_code == 200

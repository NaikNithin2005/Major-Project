def test_validation_risk_score_negative(client, user_headers):
    payload = {
        "event_type": "SMS",
        "risk_score": -5,
        "threat_category": "SAFE",
        "recommended_action": "ALLOW"
    }
    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 422
    data = response.json()
    assert data["error"]["code"] == "VALIDATION_ERROR"


def test_validation_risk_score_over_100(client, user_headers):
    payload = {
        "event_type": "QR",
        "risk_score": 105,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK"
    }
    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_validation_invalid_event_type(client, user_headers):
    payload = {
        "event_type": "BLUETOOTH",
        "risk_score": 50,
        "threat_category": "MEDIUM",
        "recommended_action": "WARN"
    }
    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_validation_invalid_category(client, user_headers):
    payload = {
        "event_type": "QR",
        "risk_score": 50,
        "threat_category": "EXTREME",
        "recommended_action": "BLOCK"
    }
    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_validation_invalid_feedback_classification(client, user_headers):
    payload = {
        "threat_event_id": "test-uuid-1234",
        "classification": "UNSURE_RESULT",
        "comment": "Testing invalid classification"
    }
    response = client.post("/api/v1/feedback", json=payload, headers=user_headers)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"

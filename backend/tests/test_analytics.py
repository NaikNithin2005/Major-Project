def test_analytics_summary_calculation(client, user_headers):
    # Ingest 3 detections for user: 20 (SAFE, SMS), 80 (HIGH, QR), 50 (MEDIUM, SMS)
    detections = [
        {"event_type": "SMS", "risk_score": 20, "threat_category": "SAFE", "recommended_action": "ALLOW"},
        {"event_type": "QR", "risk_score": 80, "threat_category": "HIGH", "recommended_action": "BLOCK"},
        {"event_type": "SMS", "risk_score": 50, "threat_category": "MEDIUM", "recommended_action": "WARN"},
    ]
    for d in detections:
        client.post("/api/v1/detections", json=d, headers=user_headers)

    response = client.get("/api/v1/analytics/summary", headers=user_headers)
    assert response.status_code == 200
    data = response.json()
    assert data["total_detections"] >= 3
    assert data["safe_count"] >= 1
    assert data["high_count"] >= 1
    assert data["medium_count"] >= 1
    assert data["sms_count"] >= 2
    assert data["qr_count"] >= 1
    assert data["average_risk_score"] > 0


def test_analytics_trends(client, user_headers):
    response = client.get("/api/v1/analytics/trends?days=7", headers=user_headers)
    assert response.status_code == 200
    data = response.json()
    assert "period" in data
    assert "data_points" in data
    assert "category_distribution" in data
    assert "channel_distribution" in data

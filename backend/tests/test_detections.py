def test_create_qr_detection(client, user_headers):
    payload = {
        "event_type": "QR",
        "risk_score": 82,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK",
        "model_versions": {"xgboost": "v2.0.0", "isolation_forest": "v2.0.0"},
        "model_results": {"xgboost_prob": 0.88, "anomaly_score": -0.65},
        "rule_results": [{"rule": "SUSPICIOUS_TLD", "fired": True}],
        "context_results": {"entropy": 4.5, "is_shortened": True},
        "reasons": ["Shortened URL detected", "High probability phishing pattern"],
        "url_metadata": {"domain": "bit.ly", "scheme": "https"}
    }
    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 201
    data = response.json()
    assert data["event_type"] == "QR"
    assert data["risk_score"] == 82
    assert data["threat_category"] == "HIGH"
    assert "id" in data


def test_create_sms_detection_data_minimization(client, user_headers):
    # Verify minimized telemetry: only assessment features transmitted, no raw sms text
    payload = {
        "event_type": "SMS",
        "risk_score": 90,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK",
        "model_versions": {"tinybert": "v2.0.0"},
        "model_results": {"tinybert_prob": 0.94},
        "reasons": ["Urgent financial action demanded", "Unverified sender ID"]
    }
    response = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert response.status_code == 201
    data = response.json()
    assert data["event_type"] == "SMS"
    assert data["risk_score"] == 90
    assert "raw_sms_text" not in data


def test_detection_user_isolation(client, user_headers, user2_headers, admin_headers):
    # User 1 creates a detection
    payload = {
        "event_type": "QR",
        "risk_score": 45,
        "threat_category": "MEDIUM",
        "recommended_action": "WARN",
        "reasons": ["New domain without reputation"]
    }
    create_resp = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert create_resp.status_code == 201
    event_id = create_resp.json()["id"]

    # User 1 can view it
    get_resp1 = client.get(f"/api/v1/detections/{event_id}", headers=user_headers)
    assert get_resp1.status_code == 200
    assert get_resp1.json()["id"] == event_id

    # User 2 CANNOT view User 1's detection (returns 404 to avoid leaking existence)
    get_resp2 = client.get(f"/api/v1/detections/{event_id}", headers=user2_headers)
    assert get_resp2.status_code == 404

    # Admin CAN view User 1's detection
    get_admin = client.get(f"/api/v1/detections/{event_id}", headers=admin_headers)
    assert get_admin.status_code == 200
    assert get_admin.json()["id"] == event_id


def test_list_detections_pagination_and_filter(client, user_headers):
    # Submit multiple detections
    for score, cat in [(10, "SAFE"), (30, "LOW"), (85, "HIGH")]:
        client.post(
            "/api/v1/detections",
            json={
                "event_type": "QR",
                "risk_score": score,
                "threat_category": cat,
                "recommended_action": "ALLOW" if score < 50 else "BLOCK"
            },
            headers=user_headers
        )

    # Filter by category HIGH
    response = client.get("/api/v1/detections?threat_category=HIGH", headers=user_headers)
    assert response.status_code == 200
    data = response.json()
    assert all(item["threat_category"] == "HIGH" for item in data["items"])


def test_detection_idempotency_prevents_duplicate_records(client, user_headers):
    # Testing Phase 8 requirement 20: duplicate client_event_id returns existing event without creating duplicate
    client_event_id = "test-client-event-12345"
    payload = {
        "client_event_id": client_event_id,
        "event_type": "SMS",
        "risk_score": 88,
        "threat_category": "HIGH",
        "recommended_action": "BLOCK",
        "reasons": ["Detected urgent financial smishing heuristic"]
    }

    # 1. First upload
    first_resp = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert first_resp.status_code == 201
    first_data = first_resp.json()
    first_id = first_data["id"]
    assert first_data["client_event_id"] == client_event_id

    # 2. Second upload with same client_event_id (simulating retry / duplicate worker)
    second_resp = client.post("/api/v1/detections", json=payload, headers=user_headers)
    assert second_resp.status_code == 201
    second_data = second_resp.json()
    # Must return the SAME event id
    assert second_data["id"] == first_id
    assert second_data["client_event_id"] == client_event_id

    # 3. Verify database count only incremented by 1
    list_resp = client.get("/api/v1/detections", headers=user_headers)
    assert list_resp.status_code == 200
    matching = [d for d in list_resp.json()["items"] if d.get("client_event_id") == client_event_id]
    assert len(matching) == 1


def test_user_stats(client, user_headers):
    # Submit one SAFE and one HIGH detection
    client.post(
        "/api/v1/detections",
        json={"event_type": "QR", "risk_score": 10, "threat_category": "SAFE", "recommended_action": "ALLOW"},
        headers=user_headers
    )
    client.post(
        "/api/v1/detections",
        json={"event_type": "SMS", "risk_score": 90, "threat_category": "HIGH", "recommended_action": "BLOCK"},
        headers=user_headers
    )

    stats_resp = client.get("/api/v1/detections/stats", headers=user_headers)
    assert stats_resp.status_code == 200
    stats = stats_resp.json()
    assert stats["total_scans"] >= 2
    assert stats["threats_detected"] >= 1
    assert stats["safe_detections"] >= 1
    assert stats["avg_risk_score"] > 0


def test_user_action_tracking_and_cross_user_isolation(client, user_headers, user2_headers):
    # 1. User 1 creates a detection
    create_resp = client.post(
        "/api/v1/detections",
        json={"event_type": "QR", "risk_score": 75, "threat_category": "HIGH", "recommended_action": "BLOCK"},
        headers=user_headers
    )
    assert create_resp.status_code == 201
    event_id = create_resp.json()["id"]

    # 2. User 1 takes BLOCK action
    action_resp = client.post(
        f"/api/v1/detections/{event_id}/action",
        json={"action": "BLOCK", "notes": "Sender confirmed malicious"},
        headers=user_headers
    )
    assert action_resp.status_code == 200
    data = action_resp.json()
    assert data["user_action"] == "BLOCK"
    assert data["status"] == "BLOCKED"

    # 3. User 2 tries to act on User 1's detection -> 404
    action_u2 = client.post(
        f"/api/v1/detections/{event_id}/action",
        json={"action": "MARK_SAFE"},
        headers=user2_headers
    )
    assert action_u2.status_code == 404

    # 4. User 2 tries to delete User 1's detection -> 404
    del_u2 = client.delete(f"/api/v1/detections/{event_id}", headers=user2_headers)
    assert del_u2.status_code == 404

    # 5. User 1 deletes their detection -> 200
    del_u1 = client.delete(f"/api/v1/detections/{event_id}", headers=user_headers)
    assert del_u1.status_code == 200
    assert del_u1.json()["success"] is True

    # 6. Verify detection is gone
    get_resp = client.get(f"/api/v1/detections/{event_id}", headers=user_headers)
    assert get_resp.status_code == 404


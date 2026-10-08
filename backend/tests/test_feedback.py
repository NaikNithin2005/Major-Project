def test_submit_feedback_success(client, user_headers):
    # First create a detection
    det_resp = client.post(
        "/api/v1/detections",
        json={
            "event_type": "SMS",
            "risk_score": 85,
            "threat_category": "HIGH",
            "recommended_action": "BLOCK"
        },
        headers=user_headers
    )
    event_id = det_resp.json()["id"]

    # Submit feedback
    feedback_payload = {
        "threat_event_id": event_id,
        "classification": "TRUE_POSITIVE",
        "comment": "Accurate detection of bank impersonation smishing."
    }
    fb_resp = client.post("/api/v1/feedback", json=feedback_payload, headers=user_headers)
    assert fb_resp.status_code == 201
    data = fb_resp.json()
    assert data["threat_event_id"] == event_id
    assert data["classification"] == "TRUE_POSITIVE"


def test_submit_feedback_nonexistent_event(client, user_headers):
    feedback_payload = {
        "threat_event_id": "nonexistent-uuid-000",
        "classification": "FALSE_POSITIVE",
        "comment": "This event doesn't exist"
    }
    fb_resp = client.post("/api/v1/feedback", json=feedback_payload, headers=user_headers)
    assert fb_resp.status_code == 404
    assert fb_resp.json()["error"]["code"] in ("NOT_FOUND", "EVENT_NOT_FOUND")


def test_submit_feedback_other_user_event_forbidden(client, user_headers, user2_headers):
    # User 1 creates an event
    det_resp = client.post(
        "/api/v1/detections",
        json={
            "event_type": "QR",
            "risk_score": 92,
            "threat_category": "HIGH",
            "recommended_action": "BLOCK"
        },
        headers=user_headers
    )
    event_id = det_resp.json()["id"]

    # User 2 tries to submit feedback for User 1's event
    fb_resp = client.post(
        "/api/v1/feedback",
        json={
            "threat_event_id": event_id,
            "classification": "FALSE_POSITIVE",
            "comment": "Attacking user 1 event"
        },
        headers=user2_headers
    )
    assert fb_resp.status_code == 403
    assert fb_resp.json()["error"]["code"] == "FORBIDDEN"


def test_list_feedback(client, user_headers):
    resp = client.get("/api/v1/feedback", headers=user_headers)
    assert resp.status_code == 200
    assert "items" in resp.json()
    assert "total" in resp.json()

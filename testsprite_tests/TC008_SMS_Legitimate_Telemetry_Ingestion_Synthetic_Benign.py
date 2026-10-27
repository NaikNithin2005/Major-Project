import requests
import uuid
import sys

BASE_URL = "http://localhost:8000"
HEADERS = {
    "Authorization": "Bearer dev-mock-token",
    "Content-Type": "application/json"
}
TIMEOUT = 30


def test_sms_legitimate_telemetry_ingestion_synthetic_benign():
    detection_id = None
    # Optional quick liveness check to ensure service reachable
    try:
        resp = requests.get(f"{BASE_URL}/health/live", timeout=TIMEOUT)
        resp.raise_for_status()
    except requests.exceptions.RequestException as e:
        raise AssertionError(f"Health check failed or service not reachable: {e}")

    payload = {
        "device_detection_id": f"synthetic-benign-{uuid.uuid4()}",
        "threat_type": "sms",
        "risk_score": 12,
        "threat_level": "LOW",
        "sender_or_source": "Clinic Appointments",
        "risk_reasons": [
            "appointment_reminder",
            "benign_language",
            "low_risk_score"
        ]
    }

    try:
        # Create detection
        post_url = f"{BASE_URL}/api/v1/v1/detections"
        r = requests.post(post_url, json=payload, headers=HEADERS, timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        raise AssertionError(f"Request to create detection failed: {e}")

    # Validate creation response
    assert r.status_code == 201, f"Expected 201 Created, got {r.status_code}, body: {r.text}"
    try:
        created = r.json()
    except ValueError:
        raise AssertionError(f"Response JSON decode failed. Status: {r.status_code}, Body: {r.text}")

    # Extract detection id from likely fields
    detection_id = None
    for key in ("id", "detection_id", "detectionId", "uuid"):
        if isinstance(created, dict) and key in created:
            detection_id = str(created[key])
            break
    # If not found, check nested object
    if detection_id is None and isinstance(created, dict):
        # sometimes returned under "data"
        data = created.get("data") if isinstance(created.get("data"), dict) else None
        if data:
            for key in ("id", "detection_id", "detectionId", "uuid"):
                if key in data:
                    detection_id = str(data[key])
                    break

    assert detection_id is not None and detection_id != "", f"Created detection response missing identifier: {created}"

    # If the create response returned the saved fields, validate risk_score persisted
    returned_risk = None
    if isinstance(created, dict):
        # direct risk_score
        if "risk_score" in created:
            returned_risk = created.get("risk_score")
        else:
            # maybe nested under data
            data = created.get("data") if isinstance(created.get("data"), dict) else {}
            returned_risk = data.get("risk_score", returned_risk)

    assert returned_risk == 12, f"Expected returned risk_score == 12, got {returned_risk}. Full response: {created}"

    # Fetch the created detection to verify persistence of metadata
    try:
        get_url = f"{BASE_URL}/api/v1/v1/detections/{detection_id}"
        gr = requests.get(get_url, headers=HEADERS, timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        raise AssertionError(f"Request to retrieve detection {detection_id} failed: {e}")

    assert gr.status_code == 200, f"Expected 200 when retrieving created detection, got {gr.status_code}, body: {gr.text}"
    try:
        rec = gr.json()
    except ValueError:
        raise AssertionError(f"GET detection JSON decode failed. Status: {gr.status_code}, Body: {gr.text}")

    # Determine where the record fields are located
    record = rec if isinstance(rec, dict) and ("risk_score" in rec or "device_detection_id" in rec) else rec.get("data") if isinstance(rec, dict) else None
    assert isinstance(record, dict), f"Unexpected GET record format: {rec}"

    # Validate persisted fields
    assert str(record.get("device_detection_id")) == payload["device_detection_id"], f"device_detection_id mismatch: expected {payload['device_detection_id']}, got {record.get('device_detection_id')}"
    assert int(record.get("risk_score")) == 12, f"risk_score mismatch or not persisted as 12: got {record.get('risk_score')}"
    assert record.get("threat_type") == payload["threat_type"], f"threat_type mismatch: expected {payload['threat_type']}, got {record.get('threat_type')}"
    # threat_level and sender_or_source presence
    assert record.get("threat_level") == payload["threat_level"], f"threat_level mismatch: expected {payload['threat_level']}, got {record.get('threat_level')}"
    assert record.get("sender_or_source") == payload["sender_or_source"], f"sender_or_source mismatch: expected {payload['sender_or_source']}, got {record.get('sender_or_source')}"
    # risk_reasons should be a superset (order may vary)
    returned_reasons = record.get("risk_reasons") or []
    assert isinstance(returned_reasons, list), f"risk_reasons expected list, got: {type(returned_reasons)}"
    for reason in payload["risk_reasons"]:
        assert reason in returned_reasons, f"Expected reason '{reason}' in persisted risk_reasons {returned_reasons}"

    # Optionally check that raw text is not stored if policy says so; but PRD for benign doesn't require raw text check.
    return True

    # Note: cleanup handled in finally below


if __name__ == "__main__":
    detection_id_local = None
    try:
        # Run the test
        test_sms_legitimate_telemetry_ingestion_synthetic_benign()
    except AssertionError as ae:
        print(f"TEST FAILED: {ae}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"UNEXPECTED ERROR: {e}", file=sys.stderr)
        sys.exit(2)
    else:
        print("TEST PASSED")
    finally:
        # Attempt best-effort cleanup if detection was created and an ID is discoverable via a last-known method.
        # Try to discover the most recent synthetic detection by listing (best-effort), then delete it.
        try:
            # List detections and find one that matches the synthetic prefix
            list_url = f"{BASE_URL}/api/v1/v1/detections"
            lr = requests.get(list_url, headers=HEADERS, timeout=TIMEOUT)
            if lr.status_code == 200:
                try:
                    listing = lr.json()
                except ValueError:
                    listing = None
                found_id = None
                if isinstance(listing, dict):
                    items = listing.get("items") or listing.get("detections") or listing.get("data") or listing
                    # items might be dict or list
                    if isinstance(items, dict):
                        # maybe pagination wrapper
                        items = items.get("items") or items.get("results") or []
                    if isinstance(items, list):
                        for it in items:
                            ddid = it.get("device_detection_id") or it.get("deviceDetectionId") or it.get("device_detection")
                            if isinstance(ddid, str) and ddid.startswith("synthetic-benign-"):
                                found_id = it.get("id") or it.get("detection_id") or it.get("detectionId")
                                if found_id:
                                    break
                if found_id:
                    detection_id_local = str(found_id)
                    # attempt delete
                    del_url = f"{BASE_URL}/api/v1/v1/detections/{detection_id_local}"
                    try:
                        dr = requests.delete(del_url, headers=HEADERS, timeout=TIMEOUT)
                        # Accept 200/202/204 as successful cleanup; otherwise ignore
                        if dr.status_code not in (200, 202, 204):
                            # best-effort: not fatal
                            pass
                    except requests.exceptions.RequestException:
                        pass
        except Exception:
            # swallow all cleanup exceptions to not mask test outcome
            pass
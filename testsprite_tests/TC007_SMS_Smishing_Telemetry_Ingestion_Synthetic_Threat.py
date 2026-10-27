import requests
import uuid
import json
import sys
from typing import Any, Dict, Optional

BASE_URL = "http://localhost:8000"
AUTH_TOKEN = "Bearer dev-mock-token"
TIMEOUT = 30  # seconds
HEADERS = {
    "Authorization": AUTH_TOKEN,
    "Content-Type": "application/json",
    "Accept": "application/json",
}

def _find_key_recursive(obj: Any, key: str) -> Optional[Any]:
    if isinstance(obj, dict):
        if key in obj:
            return obj[key]
        for v in obj.values():
            found = _find_key_recursive(v, key)
            if found is not None:
                return found
    elif isinstance(obj, list):
        for item in obj:
            found = _find_key_recursive(item, key)
            if found is not None:
                return found
    return None

def _key_exists_recursive(obj: Any, key: str) -> bool:
    if isinstance(obj, dict):
        if key in obj:
            return True
        return any(_key_exists_recursive(v, key) for v in obj.values())
    if isinstance(obj, list):
        return any(_key_exists_recursive(i, key) for i in obj)
    return False

def test_tc007_sms_smishing_ingestion():
    url = f"{BASE_URL}/api/v1/v1/detections"
    # Synthetic payload (no raw text included)
    device_detection_id = str(uuid.uuid4())
    payload = {
        "device_detection_id": device_detection_id,
        "threat_type": "sms",
        "risk_score": 88,
        "threat_level": "HIGH",
        "sender_or_source": "+15551234567",
        "risk_reasons": [
            "urgent-account-action",
            "spoofed-sender-pattern"
        ]
    }

    created_id = None
    try:
        resp = requests.post(url, headers=HEADERS, data=json.dumps(payload), timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Request to ingest detection failed: {e}") from e

    # Validate status code
    if resp.status_code != 201:
        # include body for debugging
        raise AssertionError(f"Expected status 201 Created, got {resp.status_code}. Response body: {resp.text}")

    # Parse JSON
    try:
        body = resp.json()
    except ValueError as e:
        raise AssertionError(f"Response is not valid JSON: {e}. Raw body: {resp.text}") from e

    # Ensure the persisted record includes the device_detection_id and risk_score
    found_device_id = _find_key_recursive(body, "device_detection_id")
    assert found_device_id == device_detection_id, f"Persisted device_detection_id mismatch (expected {device_detection_id}, got {found_device_id})"

    found_risk_score = _find_key_recursive(body, "risk_score")
    assert found_risk_score == 88, f"Persisted risk_score mismatch (expected 88, got {found_risk_score})"

    # Ensure threat metadata fields exist
    required_fields = ["threat_type", "threat_level", "sender_or_source", "risk_reasons"]
    for f in required_fields:
        val = _find_key_recursive(body, f)
        assert val is not None, f"Expected field '{f}' to be present in response JSON"

    # Ensure raw text is NOT persisted anywhere
    raw_text_present = _key_exists_recursive(body, "raw_text")
    assert not raw_text_present, "raw_text field was found in persisted detection, but it must NOT be stored"

    # Try to extract an identifier for cleanup (commonly 'id' or 'detection_id')
    for id_key in ("id", "detection_id", "event_id", "uuid"):
        iid = _find_key_recursive(body, id_key)
        if iid:
            created_id = str(iid)
            break

    # If no ID found, attempt to look for a URL/location header
    if not created_id:
        location = resp.headers.get("Location")
        if location:
            # try to parse the last path segment as id
            try:
                created_id = location.rstrip("/").split("/")[-1]
            except Exception:
                created_id = None

    # Final assertion: ensure we have an id to reference (best-effort)
    assert created_id is not None, f"Could not determine created detection ID from response. Body keys: {list(body.keys())}"

    print(f"TC007 passed: created detection id {created_id} with risk_score 88 and no raw_text persisted.")

    # cleanup in finally via deletion below
    try:
        # Attempt to delete the created resource if the API supports it.
        delete_url = f"{BASE_URL}/api/v1/v1/detections/{created_id}"
        try:
            del_resp = requests.delete(delete_url, headers=HEADERS, timeout=TIMEOUT)
        except requests.RequestException as e:
            # Non-fatal cleanup error
            print(f"Warning: delete request failed for id {created_id}: {e}", file=sys.stderr)
            return

        # Accept 200/202/204 or 404 (already gone) as acceptable cleanup outcomes
        if del_resp.status_code not in (200, 202, 204, 404):
            print(f"Warning: unexpected delete status {del_resp.status_code} for id {created_id}. Response: {del_resp.text}", file=sys.stderr)
    finally:
        # nothing more to do; real cleanup attempted above
        pass

if __name__ == "__main__":
    test_tc007_sms_smishing_ingestion()
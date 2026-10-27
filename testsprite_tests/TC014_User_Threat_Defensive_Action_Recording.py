import requests
import uuid
import sys

BASE_URL = "http://127.0.0.1:8000"
AUTH_HEADER = {"Authorization": "Bearer dev-mock-token", "Content-Type": "application/json"}
TIMEOUT = 30


def _extract_detection_id(json_obj):
    """
    Try common keys to extract the created detection id from response JSON.
    """
    if not isinstance(json_obj, dict):
        return None
    candidates = ["id", "detection_id", "detectionId", "uuid", "event_id", "threat_id"]
    for k in candidates:
        if k in json_obj and (isinstance(json_obj[k], (str, int))):
            return str(json_obj[k])
    # fallback: scan nested dicts for first key that endswith 'id'
    for k, v in json_obj.items():
        if isinstance(k, str) and k.lower().endswith("id") and isinstance(v, (str, int)):
            return str(v)
    return None


def test_record_defensive_action_TC014():
    """
    TC014: Verify that POST /api/v1/v1/detections/{detection_id}/action records a defensive user mitigation action
    (BLOCK_SENDER or COPY_SAFE_LINK) and returns HTTP 200.
    """
    session = requests.Session()
    session.headers.update(AUTH_HEADER)

    detection_id = None
    created = False

    # 1) Create a synthetic detection resource to act upon
    create_url = f"{BASE_URL}/api/v1/v1/detections"
    device_detection_id = f"test-device-{uuid.uuid4().hex}"
    payload = {
        "device_detection_id": device_detection_id,
        "threat_type": "SMS_SMISHING",
        "risk_score": 75,
        "threat_level": "HIGH",
        "sender_or_source": "+15555550123",
        "risk_reasons": ["urgent_account_action", "suspicious_link_pattern"]
    }

    try:
        try:
            resp = session.post(create_url, json=payload, timeout=TIMEOUT)
        except requests.RequestException as e:
            raise AssertionError(f"Request to create detection failed: {e}")

        assert resp is not None, "No response received when creating detection"
        assert resp.status_code in (200, 201), f"Unexpected status creating detection: {resp.status_code}, body: {resp.text}"

        try:
            resp_json = resp.json()
        except ValueError:
            raise AssertionError(f"Create detection response is not valid JSON: {resp.text}")

        detection_id = _extract_detection_id(resp_json)
        assert detection_id, f"Could not extract detection id from create response JSON: {resp_json}"
        created = True

        # 2) Post defensive action (BLOCK_SENDER) to the detection
        action_url = f"{BASE_URL}/api/v1/v1/detections/{detection_id}/action"
        action_payload = {"action": "BLOCK_SENDER"}
        try:
            action_resp = session.post(action_url, json=action_payload, timeout=TIMEOUT)
        except requests.RequestException as e:
            raise AssertionError(f"Request to post detection action failed: {e}")

        assert action_resp is not None, "No response received when posting action"
        assert action_resp.status_code == 200, f"Expected 200 when posting action, got {action_resp.status_code}, body: {action_resp.text}"

        # Verify response JSON includes the recorded action
        try:
            action_json = action_resp.json()
        except ValueError:
            raise AssertionError(f"Action response is not valid JSON: {action_resp.text}")

        action_found = False

        def _search_for_action(obj):
            nonlocal action_found
            if action_found:
                return
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if isinstance(v, str) and v.upper() == "BLOCK_SENDER":
                        action_found = True
                        return
                    _search_for_action(v)
            elif isinstance(obj, list):
                for item in obj:
                    _search_for_action(item)

        _search_for_action(action_json)
        assert action_found, f"Action response JSON does not show recorded action 'BLOCK_SENDER': {action_json}"

        # 3) Retrieve the detection to confirm the action is reflected in the record
        get_url = f"{BASE_URL}/api/v1/v1/detections/{detection_id}"
        try:
            get_resp = session.get(get_url, timeout=TIMEOUT)
        except requests.RequestException as e:
            raise AssertionError(f"Request to get detection failed: {e}")

        assert get_resp is not None, "No response received when retrieving detection"
        assert get_resp.status_code == 200, f"Expected 200 when retrieving detection, got {get_resp.status_code}, body: {get_resp.text}"

        try:
            get_json = get_resp.json()
        except ValueError:
            raise AssertionError(f"Get detection response is not valid JSON: {get_resp.text}")

        # Ensure recorded action is present in the detection record
        action_present = False
        _search = lambda o: None  # placeholder to reuse above search
        # reuse search_for_action logic
        def _search_for_action_in_get(obj):
            nonlocal action_present
            if action_present:
                return
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if isinstance(v, str) and v.upper() == "BLOCK_SENDER":
                        action_present = True
                        return
                    _search_for_action_in_get(v)
            elif isinstance(obj, list):
                for item in obj:
                    _search_for_action_in_get(item)

        _search_for_action_in_get(get_json)
        assert action_present, f"Retrieved detection does not include 'BLOCK_SENDER' action: {get_json}"

        print("TC014 passed: defensive action recorded and reflected in detection record.")

    finally:
        # Cleanup: attempt to delete the created detection if possible
        if created and detection_id:
            delete_url = f"{BASE_URL}/api/v1/v1/detections/{detection_id}"
            try:
                del_resp = session.delete(delete_url, timeout=TIMEOUT)
                # Accept 200/204/404/405 as OK cleanup outcomes (some APIs may not implement delete)
                if del_resp.status_code not in (200, 204, 404, 405):
                    print(f"Warning: unexpected status when deleting detection {detection_id}: {del_resp.status_code}, body: {del_resp.text}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"Warning: failed to delete detection {detection_id}: {e}", file=sys.stderr)


if __name__ == "__main__":
    test_record_defensive_action_TC014()
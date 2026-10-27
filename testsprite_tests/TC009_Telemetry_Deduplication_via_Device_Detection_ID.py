import requests
import uuid
import sys
import time

BASE_URL = "http://127.0.0.1:8000"
AUTH_HEADER = {"Authorization": "Bearer dev-mock-token", "Content-Type": "application/json", "Accept": "application/json"}
TIMEOUT = 30


def _extract_list_from_get_response(json_data):
    # Heuristically extract list of detections from response JSON
    if json_data is None:
        return []
    if isinstance(json_data, list):
        return json_data
    # common patterns
    for key in ("items", "detections", "results", "data", "payload"):
        if isinstance(json_data, dict) and key in json_data and isinstance(json_data[key], list):
            return json_data[key]
    # If response is dict of many entries, try to find nested list
    if isinstance(json_data, dict):
        for v in json_data.values():
            if isinstance(v, list):
                return v
    return []


def _extract_id_from_post_response(json_data):
    if not isinstance(json_data, dict):
        return None
    # common id keys
    for key in ("id", "detection_id", "detectionId", "uuid"):
        if key in json_data:
            return json_data[key]
    # maybe nested under "data"
    if "data" in json_data and isinstance(json_data["data"], dict):
        for key in ("id", "detection_id", "detectionId", "uuid"):
            if key in json_data["data"]:
                return json_data["data"][key]
    return None


def test_telemetry_deduplication_device_detection_id():
    # Preflight readiness check
    try:
        ready_resp = requests.get(f"{BASE_URL}/health/ready", timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        raise AssertionError(f"Readiness probe failed to reach {BASE_URL}/health/ready: {e}")
    if ready_resp.status_code != 200:
        raise AssertionError(f"Service not ready (status {ready_resp.status_code}): {ready_resp.text}")

    device_detection_id = f"test-did-{uuid.uuid4()}"
    payload = {
        "device_detection_id": device_detection_id,
        "threat_type": "sms_smishing",
        "risk_score": 42,
        "threat_level": "MEDIUM",
        "sender_or_source": "+15555550123",
        "risk_reasons": ["suspicious_link", "urgent_call_to_action"]
    }

    detection_id = None

    try:
        # First submission
        try:
            resp1 = requests.post(f"{BASE_URL}/api/v1/v1/detections", json=payload, headers=AUTH_HEADER, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"First POST /detections request failed: {e}")

        if resp1.status_code not in (200, 201):
            raise AssertionError(f"Expected 200 or 201 from first POST, got {resp1.status_code}: {resp1.text}")

        try:
            resp1_json = resp1.json()
        except ValueError:
            resp1_json = None

        detection_id = _extract_id_from_post_response(resp1_json)

        # Second submission with the same device_detection_id
        try:
            resp2 = requests.post(f"{BASE_URL}/api/v1/v1/detections", json=payload, headers=AUTH_HEADER, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"Second POST /detections request failed: {e}")

        if resp2.status_code not in (200, 201):
            raise AssertionError(f"Expected 200 or 201 from second POST, got {resp2.status_code}: {resp2.text}")

        # Allow some eventual consistency briefly
        time.sleep(0.5)

        # Retrieve list of detections for current user
        try:
            list_resp = requests.get(f"{BASE_URL}/api/v1/v1/detections", headers=AUTH_HEADER, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"GET /detections request failed: {e}")

        if list_resp.status_code != 200:
            raise AssertionError(f"Expected 200 from GET /detections, got {list_resp.status_code}: {list_resp.text}")

        try:
            list_json = list_resp.json()
        except ValueError:
            list_json = None

        detections_list = _extract_list_from_get_response(list_json)

        # Count entries that match our device_detection_id
        matches = []
        for item in detections_list:
            if not isinstance(item, dict):
                continue
            # possible key names
            ddid = None
            for k in ("device_detection_id", "deviceDetectionId", "device_detectionId"):
                if k in item:
                    ddid = item[k]
                    break
            # try nested data
            if ddid is None and "data" in item and isinstance(item["data"], dict):
                for k in ("device_detection_id", "deviceDetectionId"):
                    if k in item["data"]:
                        ddid = item["data"][k]
                        break
            if ddid == device_detection_id:
                matches.append(item)

        assert len(matches) == 1, f"Expected exactly 1 detection with device_detection_id {device_detection_id}, found {len(matches)}. Matches: {matches}"

        # If we extracted detection_id earlier, ensure it corresponds to the matched record
        if detection_id is not None:
            matched_ids = []
            for m in matches:
                for key in ("id", "detection_id", "detectionId", "uuid"):
                    if key in m:
                        matched_ids.append(m[key])
                        break
                else:
                    # maybe nested
                    if "data" in m and isinstance(m["data"], dict):
                        for key in ("id", "detection_id", "detectionId"):
                            if key in m["data"]:
                                matched_ids.append(m["data"][key])
                                break
            assert detection_id in matched_ids, f"Returned detection id {detection_id} not found among matched records ids {matched_ids}"

        print("TC009 passed: submitting the same device_detection_id is idempotent (no duplicates created).")

    finally:
        # Attempt cleanup if we have a detection id
        if detection_id:
            try:
                del_resp = requests.delete(f"{BASE_URL}/api/v1/v1/detections/{detection_id}", headers=AUTH_HEADER, timeout=TIMEOUT)
                # Accept common outcomes: 200/204 (deleted), 404 (already gone), 405 (method not allowed)
                if del_resp.status_code not in (200, 204, 404, 405):
                    print(f"Warning: unexpected status when deleting detection {detection_id}: {del_resp.status_code} - {del_resp.text}", file=sys.stderr)
            except requests.exceptions.RequestException as e:
                print(f"Warning: failed to cleanup detection {detection_id}: {e}", file=sys.stderr)


if __name__ == "__main__":
    test_telemetry_deduplication_device_detection_id()
import requests
import sys

BASE_URL = "http://127.0.0.1:8000"
HEADERS = {
    "Authorization": "Bearer dev-mock-token",
    "Content-Type": "application/json",
    "Accept": "application/json",
}
TIMEOUT = 30


def _extract_id(obj):
    """
    Try common keys to extract a resource id from a response JSON object.
    """
    if not isinstance(obj, dict):
        return None
    for key in ("id", "feedback_id", "feedbackId", "detection_id", "detectionId", "resource_id", "resourceId"):
        if key in obj:
            return obj[key]
    # fallback: if object is like {'data': {...}}
    data = obj.get("data") if isinstance(obj.get("data"), dict) else None
    if data:
        for key in ("id", "feedback_id", "detection_id"):
            if key in data:
                return data[key]
    return None


def test_post_feedback_submission():
    detection_id = None
    feedback_id = None

    # Step 1: Create a synthetic detection so we have a valid threat_event_id to reference
    detection_payload = {
        "device_detection_id": "synthetic-device-001-tc017",
        "threat_type": "SMS_SMISHING",
        "risk_score": 42,
        "threat_level": "MEDIUM",
        "sender_or_source": "sms:+15555550123",
        "risk_reasons": ["contains_suspicious_link", "urgent_action_requested"]
    }

    try:
        try:
            resp = requests.post(
                f"{BASE_URL}/api/v1/v1/detections",
                headers=HEADERS,
                json=detection_payload,
                timeout=TIMEOUT,
            )
        except requests.RequestException as e:
            raise AssertionError(f"Failed to POST detection: {e}")

        if resp.status_code not in (200, 201):
            raise AssertionError(f"Unexpected status creating detection: {resp.status_code}, body: {resp.text}")

        try:
            resp_json = resp.json()
        except ValueError:
            raise AssertionError(f"Detection creation response is not valid JSON: {resp.text}")

        detection_id = _extract_id(resp_json)
        assert detection_id is not None, f"Could not extract detection id from response: {resp_json}"

        # Step 2: Submit feedback referencing the created detection
        feedback_payload = {
            "threat_event_id": int(detection_id) if isinstance(detection_id, (int, str)) and str(detection_id).isdigit() else detection_id,
            "feedback_type": "FALSE_POSITIVE",
            "comments": "Synthetic test submission: marking as false positive for TC017"
        }

        try:
            fb_resp = requests.post(
                f"{BASE_URL}/api/v1/v1/feedback",
                headers=HEADERS,
                json=feedback_payload,
                timeout=TIMEOUT,
            )
        except requests.RequestException as e:
            raise AssertionError(f"Failed to POST feedback: {e}")

        # Validate expected 201 Created
        assert fb_resp.status_code == 201, f"Expected 201 Created for feedback, got {fb_resp.status_code}, body: {fb_resp.text}"

        try:
            fb_json = fb_resp.json()
        except ValueError:
            raise AssertionError(f"Feedback response is not valid JSON: {fb_resp.text}")

        # Extract feedback id if provided
        feedback_id = _extract_id(fb_json)

        # Basic assertions on response content
        # Ensure the response references the correct threat_event_id and feedback_type
        if isinstance(fb_json, dict):
            # Check threat_event_id present and matches
            returned_threat_event_id = fb_json.get("threat_event_id") or (fb_json.get("data") or {}).get("threat_event_id")
            if returned_threat_event_id is not None:
                assert str(returned_threat_event_id) == str(feedback_payload["threat_event_id"]), (
                    f"Returned threat_event_id mismatch: expected {feedback_payload['threat_event_id']}, got {returned_threat_event_id}"
                )
            # Check feedback_type present
            returned_feedback_type = fb_json.get("feedback_type") or (fb_json.get("data") or {}).get("feedback_type")
            if returned_feedback_type is not None:
                assert returned_feedback_type == feedback_payload["feedback_type"], (
                    f"Returned feedback_type mismatch: expected {feedback_payload['feedback_type']}, got {returned_feedback_type}"
                )

        # If we reach here, test passed
        print("TC017: feedback creation returned 201 and response validated successfully.")

    finally:
        # Cleanup: attempt to delete feedback and detection if created. Ignore errors but log if unable.
        session = requests.Session()
        # Attempt to delete feedback
        if feedback_id is not None:
            try:
                delete_fb = session.delete(
                    f"{BASE_URL}/api/v1/v1/feedback/{feedback_id}",
                    headers=HEADERS,
                    timeout=TIMEOUT,
                )
                # Accept common success codes; if endpoint not implemented, it's acceptable to receive 404/405
                if delete_fb.status_code not in (200, 202, 204, 404, 405, 401):
                    print(f"Warning: Unexpected status deleting feedback {feedback_id}: {delete_fb.status_code}, body: {delete_fb.text}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"Warning: Exception when deleting feedback {feedback_id}: {e}", file=sys.stderr)

        # Attempt to delete detection
        if detection_id is not None:
            try:
                delete_det = session.delete(
                    f"{BASE_URL}/api/v1/v1/detections/{detection_id}",
                    headers=HEADERS,
                    timeout=TIMEOUT,
                )
                if delete_det.status_code not in (200, 202, 204, 404, 405, 401):
                    print(f"Warning: Unexpected status deleting detection {detection_id}: {delete_det.status_code}, body: {delete_det.text}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"Warning: Exception when deleting detection {detection_id}: {e}", file=sys.stderr)


if __name__ == "__main__":
    test_post_feedback_submission()
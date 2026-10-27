import requests
import json
import sys

BASE_URL = "http://127.0.0.1:8000"
DETECTIONS_PATH = "/api/v1/v1/detections"
FULL_URL = BASE_URL.rstrip("/") + DETECTIONS_PATH
HEADERS = {
    "Authorization": "Bearer dev-mock-token",
    "Content-Type": "application/json",
}

TIMEOUT = 30.0


def _assert_structured_validation_error(resp):
    """
    Assert that response is a structured validation error (typical FastAPI/Pydantic shape).
    Expects JSON body with 'detail' as a list of error objects containing at least 'loc' and 'msg'.
    """
    try:
        body = resp.json()
    except ValueError:
        raise AssertionError(f"Response is not valid JSON (status {resp.status_code}): {resp.text}")

    if not isinstance(body, dict):
        raise AssertionError(f"Validation error response JSON is not an object: {body}")

    detail = body.get("detail")
    if not isinstance(detail, list) or len(detail) == 0:
        raise AssertionError(f"Expected 'detail' as non-empty list in validation response, got: {body}")

    # Each detail item should include location and message keys typical of validation errors
    first = detail[0]
    if not isinstance(first, dict):
        raise AssertionError(f"Validation detail entries expected to be objects, got: {first}")

    if "loc" not in first or "msg" not in first:
        raise AssertionError(f"Validation detail missing expected keys 'loc'/'msg': {first}")

    # Basic sanity: loc should be a list or tuple describing field path
    if not isinstance(first["loc"], (list, tuple)):
        raise AssertionError(f"Validation detail 'loc' expected to be list/tuple, got: {first['loc']}")

    # If we reach here, treat as acceptable structured validation error
    return body


def test_invalid_payload_and_schema_validation_handling():
    session = requests.Session()

    # Case 1: Malformed JSON (syntactically invalid)
    malformed_body = '{"device_detection_id": "dev-malformed-1", "threat_type": "SMS_PHISH", "risk_score": 85'  # missing closing brace
    try:
        resp = session.post(FULL_URL, headers=HEADERS, data=malformed_body, timeout=TIMEOUT)
    except requests.RequestException as exc:
        raise AssertionError(f"Request failed for malformed JSON case: {exc}") from exc

    # Expect 422 Unprocessable Entity with structured validation error details per test case spec
    assert resp.status_code == 422, f"Malformed JSON: expected status 422, got {resp.status_code}. Body: {resp.text}"
    _assert_structured_validation_error(resp)

    # Case 2: Missing required fields (omit risk_score and others)
    missing_fields_payload = {
        "device_detection_id": "dev-missing-1",
        "threat_type": "SMS_PHISH",
        # risk_score omitted intentionally
        "threat_level": "HIGH",
        "sender_or_source": "+10000000000",
        "risk_reasons": ["suspicious_link"]
    }
    try:
        resp = session.post(FULL_URL, headers=HEADERS, json=missing_fields_payload, timeout=TIMEOUT)
    except requests.RequestException as exc:
        raise AssertionError(f"Request failed for missing fields case: {exc}") from exc

    assert resp.status_code == 422, f"Missing fields: expected status 422, got {resp.status_code}. Body: {resp.text}"
    _assert_structured_validation_error(resp)

    # Case 3: Out-of-bound risk_score (e.g., much larger than 0-100)
    out_of_bounds_payload = {
        "device_detection_id": "dev-outofbound-1",
        "threat_type": "SMS_PHISH",
        "risk_score": 1000,  # intentionally invalid/out-of-range
        "threat_level": "CRITICAL",
        "sender_or_source": "+19998887777",
        "risk_reasons": ["phishing_url", "urgent_action_required"]
    }
    try:
        resp = session.post(FULL_URL, headers=HEADERS, json=out_of_bounds_payload, timeout=TIMEOUT)
    except requests.RequestException as exc:
        raise AssertionError(f"Request failed for out-of-bound risk_score case: {exc}") from exc

    assert resp.status_code == 422, f"Out-of-bound risk_score: expected status 422, got {resp.status_code}. Body: {resp.text}"
    _assert_structured_validation_error(resp)

    # If all assertions passed
    print("TC012 passed: Malformed JSON, missing required fields, and out-of-bound risk scores returned structured 422 validation errors.")


if __name__ == "__main__":
    try:
        test_invalid_payload_and_schema_validation_handling()
    except AssertionError as e:
        print("TC012 failed:", e)
        sys.exit(1)
    except Exception as e:
        print("TC012 encountered an unexpected error:", e)
        sys.exit(2)
    sys.exit(0)
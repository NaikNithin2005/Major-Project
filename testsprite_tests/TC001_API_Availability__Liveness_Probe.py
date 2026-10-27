import requests
import sys

BASE_ENDPOINT = "http://localhost:8000"
AUTH_HEADER = {"Authorization": "Bearer dev-mock-token", "Accept": "application/json"}
TIMEOUT = 30


def test_tc001_api_availability_liveness_probe():
    """
    TC001 - API Availability & Liveness Probe
    Verify GET /health/live returns HTTP 200 with status 'alive' and service metadata indicating process health.
    """
    url = f"{BASE_ENDPOINT}/health/live"

    try:
        resp = requests.get(url, headers=AUTH_HEADER, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"HTTP request to {url} failed: {e}")

    assert resp.status_code == 200, f"Expected HTTP 200 from {url}, got {resp.status_code}. Response text: {resp.text}"

    try:
        payload = resp.json()
    except ValueError:
        raise AssertionError(f"Response from {url} is not valid JSON. Raw response: {resp.text}")

    assert isinstance(payload, dict), f"Expected JSON object from {url}, got {type(payload)}"

    # Liveness status check: expect a 'status' field with value containing 'alive' (case-insensitive)
    status_val = payload.get("status") or payload.get("state") or payload.get("liveness")
    assert status_val is not None, f"No liveness status field found in response JSON: keys={list(payload.keys())}"
    try:
        status_str = str(status_val)
    except Exception:
        raise AssertionError(f"Liveness status value is not convertible to string: {status_val}")
    assert "alive" in status_str.lower(), f"Expected liveness status to indicate 'alive', got: {status_str}"

    # Service metadata check: ensure presence of at least two common metadata fields indicating process/service health
    metadata_candidates = [
        "service",
        "service_name",
        "name",
        "version",
        "release",
        "build_version",
        "uptime",
        "started_at",
        "start_time",
        "process",
        "pid",
    ]
    present = [k for k in metadata_candidates if k in payload]
    assert len(present) >= 2, (
        "Expected at least two service metadata fields in liveness response. "
        f"Found {len(present)} matching keys: {present}. Full response: {payload}"
    )

    # Optional: if uptime is present, ensure it's non-empty / non-negative if numeric
    if "uptime" in payload:
        uptime_val = payload["uptime"]
        # Accept numeric or non-empty string
        if isinstance(uptime_val, (int, float)):
            assert uptime_val >= 0, f"Expected non-negative uptime, got {uptime_val}"
        else:
            assert str(uptime_val).strip() != "", f"Expected non-empty uptime string, got: {uptime_val}"

    print("TC001 passed: /health/live returned HTTP 200 with 'alive' status and service metadata.")


if __name__ == "__main__":
    try:
        test_tc001_api_availability_liveness_probe()
    except AssertionError as e:
        print(f"TC001 failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC001 encountered an unexpected error: {e}")
        sys.exit(2)
    sys.exit(0)
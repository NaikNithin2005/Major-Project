import requests
import sys

BASE_URL = "http://localhost:8000"
HEADERS = {
    "Authorization": "Bearer dev-mock-token",
    "Accept": "application/json"
}
TIMEOUT = 30


def test_root_service_info_versioning():
    url = f"{BASE_URL}/"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"HTTP request to {url} failed: {e}")

    # Validate status code
    assert resp.status_code == 200, f"Expected HTTP 200 from {url}, got {resp.status_code}. Response text: {resp.text}"

    # Validate JSON body
    try:
        body = resp.json()
    except ValueError:
        raise AssertionError(f"Response from {url} is not valid JSON. Raw text: {resp.text}")

    assert isinstance(body, dict), f"Expected JSON object at root, got {type(body)}"

    # Heuristic checks for required semantic fields:
    name_keys = ["service_name", "name", "service", "project"]
    version_keys = ["release_version", "release", "version"]
    phase_keys = ["operational_phase", "phase", "stage", "status", "environment"]

    def has_nonempty_string(keys):
        for k in keys:
            if k in body and isinstance(body[k], str) and body[k].strip() != "":
                return True, k, body[k]
        return False, None, None

    ok_name, name_key, name_val = has_nonempty_string(name_keys)
    ok_version, ver_key, ver_val = has_nonempty_string(version_keys)
    ok_phase, phase_key, phase_val = has_nonempty_string(phase_keys)

    assert ok_name, f"Root JSON missing a non-empty service name field. Checked keys: {name_keys}. Response: {body}"
    assert ok_version, f"Root JSON missing a non-empty release/version field. Checked keys: {version_keys}. Response: {body}"
    assert ok_phase, f"Root JSON missing a non-empty operational phase field. Checked keys: {phase_keys}. Response: {body}"

    # Optionally, assert returned values are reasonable (synthetic data only)
    assert isinstance(name_val, str) and len(name_val) <= 200, "Service name looks invalid"
    assert isinstance(ver_val, str) and len(ver_val) <= 100, "Version value looks invalid"
    assert isinstance(phase_val, str) and len(phase_val) <= 100, "Operational phase value looks invalid"

    print("TC003 passed: Root service information contains service name, release/version, and operational phase.")


if __name__ == "__main__":
    try:
        test_root_service_info_versioning()
    except AssertionError as e:
        print(f"TC003 FAILED: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC003 ERROR: Unexpected exception: {e}")
        sys.exit(2)
    sys.exit(0)
import requests
import sys
import time

BASE_URL = "http://127.0.0.1:8000"
AUTH_HEADER = "Bearer dev-mock-admin-token"
TIMEOUT = 30

def test_tc015_soc_incident_creation_by_admin():
    incident_id = None
    url = f"{BASE_URL}/api/v1/v1/admin/incidents"
    headers = {
        "Authorization": AUTH_HEADER,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    payload = {
        "title": "Synthetic Critical Incident - TC015",
        "severity": "CRITICAL",
        "summary": "Synthetic summary for test TC015: admin created incident for verification."
    }

    try:
        # Create incident
        try:
            resp = requests.post(url, headers=headers, json=payload, timeout=TIMEOUT)
        except requests.exceptions.RequestException as e:
            raise AssertionError(f"Request to create incident failed: {e}")

        # Validate creation response
        assert resp.status_code == 201, f"Expected 201 Created, got {resp.status_code}: {resp.text}"

        try:
            data = resp.json()
        except ValueError:
            raise AssertionError(f"Response is not valid JSON: {resp.text}")

        # Extract incident id from common possible fields
        for key in ("id", "incident_id", "incidentId"):
            if key in data:
                incident_id = data[key]
                break

        # Assert response contains expected fields
        assert isinstance(data, dict), "Response JSON is not an object"
        assert data.get("title") == payload["title"], f"Title mismatch: expected '{payload['title']}', got '{data.get('title')}'"
        assert data.get("severity") == payload["severity"], f"Severity mismatch: expected '{payload['severity']}', got '{data.get('severity')}'"
        assert "summary" in data and payload["summary"] in data.get("summary", ""), "Summary missing or does not contain expected text"

    finally:
        # Cleanup: attempt to delete the created incident if we have an id
        if incident_id:
            delete_url = f"{url}/{incident_id}"
            try:
                del_resp = requests.delete(delete_url, headers=headers, timeout=TIMEOUT)
                # Accept common successful deletion responses; if not supported, do not fail the test cleanup
                if del_resp.status_code not in (200, 202, 204):
                    # If deletion not allowed or endpoint missing, attempt to mark as resolved via PATCH if supported
                    if del_resp.status_code in (401, 403):
                        # Can't delete due to auth/permission; raise to highlight cleanup issue
                        raise AssertionError(f"Failed to delete incident {incident_id}: {del_resp.status_code} {del_resp.text}")
                    # Otherwise, ignore non-successful deletion attempts
            except requests.exceptions.RequestException:
                # Ignore cleanup network errors
                pass

if __name__ == "__main__":
    try:
        test_tc015_soc_incident_creation_by_admin()
        print("TC015 passed: SOC Incident Creation by Admin")
    except AssertionError as e:
        print(f"TC015 failed: {e}")
        sys.exit(1)
    except Exception as exc:
        print(f"TC015 encountered an unexpected error: {exc}")
        sys.exit(2)
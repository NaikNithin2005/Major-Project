import requests
from requests.exceptions import RequestException, HTTPError

BASE_URL = "http://127.0.0.1:8000"
AUTH_HEADER = {"Authorization": "Bearer dev-mock-admin-token", "Content-Type": "application/json"}
TIMEOUT = 30

def _extract_incident_id(obj):
    if not isinstance(obj, dict):
        raise AssertionError("Invalid create response JSON structure")
    for key in ("id", "incident_id", "incidentId", "uuid", "uid"):
        if key in obj:
            return obj[key]
    # nested under 'incident'
    if "incident" in obj and isinstance(obj["incident"], dict):
        for key in ("id", "incident_id", "incidentId"):
            if key in obj["incident"]:
                return obj["incident"][key]
    raise AssertionError(f"Could not locate incident id in response: {obj}")

def _extract_status(obj):
    if not isinstance(obj, dict):
        return None
    if "status" in obj:
        return obj["status"]
    if "incident" in obj and isinstance(obj["incident"], dict) and "status" in obj["incident"]:
        return obj["incident"]["status"]
    return None

def test_incident_lifecycle_transition():
    incident_id = None
    try:
        # Ensure service is up
        try:
            r = requests.get(f"{BASE_URL}/health/live", timeout=TIMEOUT)
            r.raise_for_status()
        except RequestException as e:
            raise AssertionError(f"Health check failed: {e}")

        # 1) Create new incident as admin
        create_payload = {
            "title": "Synthetic test incident - lifecycle TC016",
            "severity": "CRITICAL",
            "summary": "Synthetic incident created for lifecycle transition testing"
        }
        try:
            r = requests.post(f"{BASE_URL}/api/v1/v1/admin/incidents", headers=AUTH_HEADER, json=create_payload, timeout=TIMEOUT)
        except RequestException as e:
            raise AssertionError(f"Failed to create incident: {e}")

        assert r.status_code == 201, f"Expected 201 Created for incident creation, got {r.status_code}, body: {r.text}"
        created_json = r.json()
        incident_id = _extract_incident_id(created_json)

        # 2) Confirm initial status is OPEN via GET
        try:
            r = requests.get(f"{BASE_URL}/api/v1/v1/admin/incidents/{incident_id}", headers=AUTH_HEADER, timeout=TIMEOUT)
        except RequestException as e:
            raise AssertionError(f"Failed to fetch created incident: {e}")

        assert r.status_code == 200, f"Expected 200 fetching incident, got {r.status_code}"
        status = _extract_status(r.json())
        assert status is not None, f"Incident GET response missing status: {r.json()}"
        assert str(status).upper() == "OPEN", f"Expected initial status OPEN, got {status}"

        patch_url = f"{BASE_URL}/api/v1/v1/admin/incidents/{incident_id}/status"

        # 3) Invalid transition: OPEN -> RESOLVED should be rejected with 400
        try:
            r = requests.patch(patch_url, headers=AUTH_HEADER, json={"status": "RESOLVED"}, timeout=TIMEOUT)
        except RequestException as e:
            raise AssertionError(f"PATCH request failed for invalid transition: {e}")
        assert r.status_code == 400, f"Expected 400 for invalid transition OPEN->RESOLVED, got {r.status_code}, body: {r.text}"

        # 4) Valid transition: OPEN -> INVESTIGATING
        try:
            r = requests.patch(patch_url, headers=AUTH_HEADER, json={"status": "INVESTIGATING"}, timeout=TIMEOUT)
        except RequestException as e:
            raise AssertionError(f"PATCH request failed for OPEN->INVESTIGATING: {e}")
        assert r.status_code == 200, f"Expected 200 for OPEN->INVESTIGATING, got {r.status_code}, body: {r.text}"
        new_status = _extract_status(r.json())
        assert new_status is not None and str(new_status).upper() == "INVESTIGATING", f"Expected status INVESTIGATING after transition, got {new_status}"

        # 5) Optionally assert illegal backward transition is rejected (INVESTIGATING -> OPEN)
        try:
            r = requests.patch(patch_url, headers=AUTH_HEADER, json={"status": "OPEN"}, timeout=TIMEOUT)
        except RequestException as e:
            raise AssertionError(f"PATCH request failed for INVESTIGATING->OPEN: {e}")
        assert r.status_code == 400, f"Expected 400 for illegal transition INVESTIGATING->OPEN, got {r.status_code}, body: {r.text}"

        # 6) Valid transition: INVESTIGATING -> RESOLVED
        try:
            r = requests.patch(patch_url, headers=AUTH_HEADER, json={"status": "RESOLVED"}, timeout=TIMEOUT)
        except RequestException as e:
            raise AssertionError(f"PATCH request failed for INVESTIGATING->RESOLVED: {e}")
        assert r.status_code == 200, f"Expected 200 for INVESTIGATING->RESOLVED, got {r.status_code}, body: {r.text}"
        final_status = _extract_status(r.json())
        assert final_status is not None and str(final_status).upper() == "RESOLVED", f"Expected final status RESOLVED, got {final_status}"

    finally:
        # Cleanup: attempt to delete the created incident if ID is known.
        if incident_id:
            try:
                del_url = f"{BASE_URL}/api/v1/v1/admin/incidents/{incident_id}"
                resp = requests.delete(del_url, headers=AUTH_HEADER, timeout=TIMEOUT)
                # Accept 200/204 as successful deletes; otherwise ignore (some implementations may not support DELETE)
                if resp.status_code not in (200, 204):
                    # If DELETE not supported, try to set status to RESOLVED (already done) and leave it.
                    pass
            except RequestException:
                pass

if __name__ == "__main__":
    test_incident_lifecycle_transition()
    print("TC016: SOC Incident Lifecycle Transition test completed.")
import requests
import sys

BASE_URL = "http://localhost:8000"
AUTH_HEADERS = {"Authorization": "Bearer dev-mock-token"}
TIMEOUT = 30


def test_rbac_admin_protection_TC006():
    try:
        # Sanity: liveness probe
        resp = requests.get(f"{BASE_URL}/health/live", timeout=TIMEOUT)
        assert resp.status_code == 200, f"Liveness probe failed: {resp.status_code} - {resp.text}"

        # Sanity: readiness probe
        resp = requests.get(f"{BASE_URL}/health/ready", timeout=TIMEOUT)
        assert resp.status_code == 200, f"Readiness probe failed: {resp.status_code} - {resp.text}"

        # Test cases: standard user attempting to access admin endpoints
        checks = [
            ("GET", "/api/v1/v1/admin/incidents"),
            ("POST", "/api/v1/v1/admin/operations/verify-models"),
        ]

        for method, path in checks:
            url = f"{BASE_URL}{path}"
            if method == "GET":
                r = requests.get(url, headers=AUTH_HEADERS, timeout=TIMEOUT)
            elif method == "POST":
                # No payload required per PRD for verify-models
                r = requests.post(url, headers=AUTH_HEADERS, timeout=TIMEOUT)
            else:
                raise AssertionError(f"Unsupported method in test definition: {method}")

            # Expect 403 Forbidden for non-admin user
            if r.status_code != 403:
                # If we got 401 or 200 or anything else, fail the test with details
                raise AssertionError(
                    f"RBAC check failed for {method} {path}: expected 403, got {r.status_code}. Response body: {r.text}"
                )

        print("TC006 passed: non-admin access to admin endpoints correctly rejected with 403 Forbidden.")

    except requests.exceptions.RequestException as e:
        raise AssertionError(f"HTTP request error during test execution: {e}")


if __name__ == "__main__":
    try:
        test_rbac_admin_protection_TC006()
        sys.exit(0)
    except AssertionError as e:
        print(f"TC006 FAILED: {e}")
        sys.exit(1)
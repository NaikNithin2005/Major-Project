import requests
import sys

BASE_URL = "http://127.0.0.1:8000"
TIMEOUT = 10

def test_unauthenticated_request_rejection_TC005():
    """
    TC005 - Unauthenticated Request Rejection
    Verify that protected endpoints return 401 Unauthorized when called without Authorization
    header or with an invalid token.
    Endpoints tested:
      - GET /api/v1/v1/auth/me
      - GET /api/v1/v1/detections
      - POST /api/v1/v1/detections (ingest) with synthetic payload
    """
    endpoints = {
        "auth_me_get": ( "GET", f"{BASE_URL}/api/v1/v1/auth/me" ),
        "detections_get": ( "GET", f"{BASE_URL}/api/v1/v1/detections" ),
        "detections_post": ( "POST", f"{BASE_URL}/api/v1/v1/detections" ),
    }

    invalid_auth_header = {"Authorization": "Bearer invalid-mock-token"}
    no_auth_headers = {}

    # synthetic payload for POST /detections (schema from PRD)
    synthetic_detection = {
        "device_detection_id": "synthetic-unauth-test-0001",
        "threat_type": "SMS",
        "risk_score": 50,
        "threat_level": "MEDIUM",
        "sender_or_source": "unknown",
        "risk_reasons": ["synthetic-test"]
    }

    # helper to perform request and assert 401
    def perform_and_assert_401(method, url, headers=None, json_payload=None):
        try:
            if method == "GET":
                resp = requests.get(url, headers=headers, timeout=TIMEOUT)
            elif method == "POST":
                resp = requests.post(url, headers=headers, json=json_payload, timeout=TIMEOUT)
            else:
                raise ValueError(f"Unsupported method {method}")

        except requests.RequestException as e:
            raise AssertionError(f"HTTP request to {url} failed with exception: {e}")

        # Expect 401 Unauthorized for unauthenticated/invalid token requests
        assert resp.status_code == 401, (
            f"Expected 401 Unauthorized for {method} {url} with headers={headers}, "
            f"but got {resp.status_code}. Response body: {resp.text}"
        )

    # 1) auth/me without Authorization header -> 401
    perform_and_assert_401(endpoints["auth_me_get"][0], endpoints["auth_me_get"][1], headers=no_auth_headers)

    # 2) auth/me with invalid token -> 401
    perform_and_assert_401(endpoints["auth_me_get"][0], endpoints["auth_me_get"][1], headers=invalid_auth_header)

    # 3) detections GET without Authorization header -> 401
    perform_and_assert_401(endpoints["detections_get"][0], endpoints["detections_get"][1], headers=no_auth_headers)

    # 4) detections GET with invalid token -> 401
    perform_and_assert_401(endpoints["detections_get"][0], endpoints["detections_get"][1], headers=invalid_auth_header)

    # 5) detections POST without Authorization header -> 401
    perform_and_assert_401(endpoints["detections_post"][0], endpoints["detections_post"][1], headers=no_auth_headers, json_payload=synthetic_detection)

    # 6) detections POST with invalid token -> 401
    perform_and_assert_401(endpoints["detections_post"][0], endpoints["detections_post"][1], headers=invalid_auth_header, json_payload=synthetic_detection)

    print("TC005 passed: protected endpoints correctly returned 401 for missing/invalid Authorization.")

if __name__ == "__main__":
    try:
        test_unauthenticated_request_rejection_TC005()
    except AssertionError as e:
        print(f"Test failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Unexpected error during test execution: {e}")
        sys.exit(2)
    sys.exit(0)
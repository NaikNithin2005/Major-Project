import requests
import sys

BASE_URL = "http://localhost:8000"
VERIFY_PATH = "/api/v1/v1/auth/verify"
TOKEN = "Bearer dev-mock-token"
TIMEOUT = 30


def test_TC004_authentication_token_verification():
    url = BASE_URL.rstrip("/") + VERIFY_PATH
    headers = {
        "Authorization": TOKEN,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    try:
        resp = requests.post(url, headers=headers, json={}, timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        raise AssertionError(f"HTTP request to {url} failed: {e}")

    # Expect HTTP 200 for a valid development/mock token
    assert resp.status_code == 200, f"Expected 200 OK, got {resp.status_code}. Response text: {resp.text}"

    try:
        body = resp.json()
    except ValueError:
        raise AssertionError(f"Response is not valid JSON. Status: {resp.status_code}, Body: {resp.text}")

    assert isinstance(body, dict), f"Expected JSON object in response, got {type(body)}"

    # Verify presence of an identity field (uid/id/email) and role assignment (role/roles)
    identity_keys = ("uid", "user_id", "id", "email", "identity", "user")
    role_keys = ("role", "roles", "rbac", "roles_assigned")

    found_identity = None
    for k in identity_keys:
        if k in body:
            found_identity = (k, body[k])
            break
    # also check nested user/identity objects
    if not found_identity:
        for k in ("user", "identity"):
            if k in body and isinstance(body[k], dict):
                for subk in ("uid", "user_id", "id", "email"):
                    if subk in body[k]:
                        found_identity = (f"{k}.{subk}", body[k][subk])
                        break
                if found_identity:
                    break

    assert found_identity, f"No identity field found in response JSON. Body: {body}"

    found_role = None
    for k in role_keys:
        if k in body:
            found_role = (k, body[k])
            break
    if not found_role:
        for k in ("user", "identity"):
            if k in body and isinstance(body[k], dict):
                for rk in role_keys:
                    if rk in body[k]:
                        found_role = (f"{k}.{rk}", body[k][rk])
                        break
                if found_role:
                    break

    assert found_role, f"No role assignment found in response JSON. Body: {body}"

    # Additional validation: role should be non-empty (string or non-empty list)
    role_val = found_role[1]
    if isinstance(role_val, str):
        assert role_val.strip() != "", f"Role value is empty string. Field: {found_role[0]}"
    elif isinstance(role_val, (list, tuple)):
        assert len(role_val) > 0, f"Role list is empty. Field: {found_role[0]}"
    else:
        # accept other truthy representations
        assert role_val, f"Role field {found_role[0]} is falsy. Value: {role_val}"

    print("TC004 passed: token verified, identity and role assignment present.")


if __name__ == "__main__":
    try:
        test_TC004_authentication_token_verification()
    except AssertionError as e:
        print(f"TC004 FAILED: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC004 ERROR: Unexpected exception: {e}")
        sys.exit(2)
    sys.exit(0)
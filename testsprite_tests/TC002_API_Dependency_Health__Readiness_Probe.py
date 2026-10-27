import requests
import sys
from typing import Any, Dict, List, Tuple, Union

BASE_URL = "http://localhost:8000"
HEADERS = {
    "Authorization": "Bearer dev-mock-token",
    "Accept": "application/json",
    "Content-Type": "application/json",
}
TIMEOUT = 30


def _walk_json(obj: Any) -> List[Any]:
    """Flatten JSON values for searching: collect all dicts, lists, strings, numbers, booleans."""
    found = []
    if isinstance(obj, dict):
        found.append(obj)
        for v in obj.values():
            found.extend(_walk_json(v))
    elif isinstance(obj, list):
        for item in obj:
            found.extend(_walk_json(item))
    else:
        # primitive
        found.append(obj)
    return found


def _collect_string_values(obj: Any) -> List[str]:
    """Collect all string representations from JSON primitives."""
    vals = _walk_json(obj)
    strs = []
    for v in vals:
        if isinstance(v, str):
            strs.append(v)
        elif isinstance(v, (int, float, bool)):
            strs.append(str(v))
    return strs


def _collect_dicts(obj: Any) -> List[Dict]:
    """Collect all dict objects from JSON."""
    vals = _walk_json(obj)
    return [v for v in vals if isinstance(v, dict)]


def _has_status_ready(json_body: Any) -> bool:
    # check common keys at root or nested
    keys_to_check = {"status", "state", "readiness", "ready"}
    dicts = _collect_dicts(json_body)
    for d in dicts:
        for k in keys_to_check:
            if k in d:
                v = d[k]
                if isinstance(v, str) and v.lower() == "ready":
                    return True
                if isinstance(v, bool) and v is True and k in ("ready",):
                    return True
    # also accept a top-level string "ready"
    if isinstance(json_body, str) and json_body.lower() == "ready":
        return True
    return False


def _db_indicates_sqlite_wal(json_body: Any) -> bool:
    if isinstance(json_body, dict) and "database" in json_body:
        db = json_body["database"]
        if isinstance(db, dict):
            dialect = str(db.get("dialect", "")).lower()
            wal = db.get("wal_enabled") or "wal" in str(db).lower()
            if "sqlite" in dialect and wal:
                return True
    strs = _collect_string_values(json_body)
    for s in strs:
        sl = s.lower()
        if "sqlite" in sl and "wal" in sl:
            return True
    dicts = _collect_dicts(json_body)
    for d in dicts:
        dialect = str(d.get("dialect", "")).lower()
        wal = d.get("wal_enabled") or "wal" in str(d).lower()
        if "sqlite" in dialect and wal:
            return True
        engine = None
        mode = None
        connected = None
        for k in ("engine", "db_engine", "database_engine", "type", "dialect"):
            if k in d:
                engine = d[k]
        for k in ("mode", "journal_mode", "db_mode", "wal_enabled"):
            if k in d:
                mode = d[k]
        for k in ("connected", "ok", "healthy", "status"):
            if k in d:
                connected = d[k]
        try:
            if engine and isinstance(engine, str) and "sqlite" in engine.lower():
                if (mode and isinstance(mode, str) and "wal" in mode.lower()) or mode is True:
                    if connected is None or connected is True or str(connected).lower() == "connected":
                        return True
        except Exception:
            pass
    return False


def _auth_indicates_mock_active(json_body: Any) -> bool:
    if isinstance(json_body, dict) and "auth" in json_body:
        auth_data = json_body["auth"]
        if isinstance(auth_data, dict):
            if auth_data.get("mock_auth_active") is True or "mock" in str(auth_data).lower():
                return True
    strs = _collect_string_values(json_body)
    for s in strs:
        if "mock" in s.lower():
            return True
    dicts = _collect_dicts(json_body)
    for d in dicts:
        if d.get("mock_auth_active") is True or "mock_auth_active" in d:
            return True
        for k in ("auth", "authentication", "authn"):
            if k in d and isinstance(d[k], dict):
                inner = d[k]
                if inner.get("mock_auth_active") is True or "mock" in str(inner).lower():
                    return True
                for v in inner.values():
                    if (isinstance(v, str) and "mock" in v.lower()) or v is True:
                        return True
    return False


def _models_verified_three_of_three(json_body: Any) -> bool:
    # Check for explicit "3/3" text
    strs = _collect_string_values(json_body)
    for s in strs:
        if "3/3" in s:
            return True
        if "3 of 3" in s.lower() or "3 out of 3" in s.lower():
            return True
    # Check dicts for numeric verified and total keys
    dicts = _collect_dicts(json_body)
    for d in dicts:
        # Accept several key name patterns
        verified_keys = {"verified", "verified_count", "verified_models", "verified_artifacts", "verified_count"}
        total_keys = {"total", "total_count", "expected", "total_models", "artifacts_total"}
        found_verified = None
        found_total = None
        for k in verified_keys:
            if k in d and isinstance(d[k], (int, float)):
                found_verified = int(d[k])
        for k in total_keys:
            if k in d and isinstance(d[k], (int, float)):
                found_total = int(d[k])
        if found_verified is not None and found_total is not None:
            if found_verified == 3 and found_total == 3:
                return True
        # Also check nested fields like {'onnx': {'verified': 3, 'total': 3}}
        for v in d.values():
            if isinstance(v, dict):
                v_verified = None
                v_total = None
                for k in verified_keys:
                    if k in v and isinstance(v[k], (int, float)):
                        v_verified = int(v[k])
                for k in total_keys:
                    if k in v and isinstance(v[k], (int, float)):
                        v_total = int(v[k])
                if v_verified == 3 and v_total == 3:
                    return True
    return False


def test_health_ready_tc002():
    url = BASE_URL.rstrip("/") + "/health/ready"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        print(f"HTTP request to {url} failed: {e}")
        raise

    assert resp.status_code == 200, f"Expected HTTP 200 from {url}, got {resp.status_code}. Response text: {resp.text}"

    try:
        body = resp.json()
    except ValueError:
        # Not JSON
        raise AssertionError(f"Response from {url} is not valid JSON. Raw body: {resp.text}")

    # 1) Verify status indicates ready
    assert _has_status_ready(body), f"Readiness probe did not report status 'ready'. Response JSON: {body}"

    # 2) Verify database indicates SQLite WAL connected
    assert _db_indicates_sqlite_wal(body), f"Readiness probe did not indicate connected SQLite WAL database. Response JSON: {body}"

    # 3) Verify mock auth active in dev
    assert _auth_indicates_mock_active(body), f"Readiness probe did not indicate mock auth active in dev. Response JSON: {body}"

    # 4) Verify 3/3 verified ONNX model artifacts
    assert _models_verified_three_of_three(body), f"Readiness probe did not report 3/3 verified ONNX model artifacts. Response JSON: {body}"

    print("TC002 passed: /health/ready returned ready status, SQLite WAL, mock auth active, and 3/3 ONNX models verified.")


if __name__ == "__main__":
    try:
        test_health_ready_tc002()
    except AssertionError as e:
        print(f"TC002 failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC002 encountered an unexpected error: {e}")
        sys.exit(2)
    print("TC002 completed successfully.")
import requests
import sys

BASE_URL = "http://127.0.0.1:8000"
HEADERS = {
    "Authorization": "Bearer dev-mock-admin-token",
    "Content-Type": "application/json",
    "Accept": "application/json",
}
TIMEOUT = 30


def test_verify_models_integrity_TC018():
    """
    TC018: Cryptographic AI Model Integrity Verification
    POST /api/v1/v1/admin/operations/verify-models should audit TinyBERT, XGBoost,
    and Isolation Forest hashes and return 100% verified status.
    """
    # Health checks to ensure service is available
    try:
        r_live = requests.get(f"{BASE_URL}/health/live", timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Liveness probe request failed: {e}") from e
    assert r_live.status_code == 200, f"/health/live returned {r_live.status_code}: {r_live.text}"

    try:
        r_ready = requests.get(f"{BASE_URL}/health/ready", timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Readiness probe request failed: {e}") from e
    assert r_ready.status_code == 200, f"/health/ready returned {r_ready.status_code}: {r_ready.text}"

    # Trigger model verification
    verify_url = f"{BASE_URL}/api/v1/v1/admin/operations/verify-models"
    try:
        resp = requests.post(verify_url, headers=HEADERS, json={}, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"POST to verify-models failed: {e}") from e

    assert resp.status_code == 200, f"Expected 200 from verify-models, got {resp.status_code}: {resp.text}"

    try:
        data = resp.json()
    except ValueError:
        raise AssertionError("Response from verify-models is not valid JSON")

    # Expected model names (case-insensitive variants)
    expected_model_names = {"tinybert", "xgboost", "isolation forest", "isolation_forest", "isolation-forest"}

    # Helper to normalize model names
    def normalize(name: str) -> str:
        return name.strip().lower().replace("-", " ").replace("_", " ")

    # Check common response patterns

    # 1) If there's a results list with entries per model
    if isinstance(data.get("results"), list):
        results = data["results"]
        # Map normalized model -> entry
        found = {}
        for entry in results:
            # try multiple possible keys for the model name
            name = None
            for k in ("model", "model_id", "name"):
                if isinstance(entry, dict) and k in entry:
                    name = entry[k]
                    break
            if not name:
                # try to infer from other fields
                continue
            nname = normalize(str(name))
            found[nname] = entry

        # Ensure each expected model present and verified
        missing = []
        not_verified = []
        for expected in ("tinybert", "xgboost", "isolation forest"):
            if expected not in found:
                missing.append(expected)
            else:
                entry = found[expected]
                status = None
                # Look for boolean or string status indicators
                if isinstance(entry, dict):
                    if "verified" in entry:
                        status = entry.get("verified")
                    elif "status" in entry:
                        status = entry.get("status")
                    elif "verification" in entry and isinstance(entry["verification"], dict):
                        status = entry["verification"].get("status") or entry["verification"].get("verified")
                # Normalize status checks
                if isinstance(status, str):
                    if status.strip().lower() not in ("verified", "ok", "true", "success"):
                        not_verified.append((expected, status))
                elif isinstance(status, bool):
                    if status is not True:
                        not_verified.append((expected, status))
                else:
                    # unknown status representation, fail
                    not_verified.append((expected, status))

        assert not missing, f"Missing expected model verification entries: {missing}"
        assert not not_verified, f"Models not verified or unknown status: {not_verified}"

        # Additionally check percent if provided
        summary = data.get("summary") or {}
        percent = None
        for k in ("percent_verified", "percent", "percentVerified", "percent_verified"):
            if k in summary:
                percent = summary[k]
                break
        if percent is None:
            # try top-level percent fields
            for k in ("percent_verified", "percent", "percentVerified"):
                if k in data:
                    percent = data[k]
                    break
        if percent is not None:
            try:
                percent_val = float(percent)
                assert int(percent_val) == 100, f"Expected 100% verified, got {percent_val}"
            except Exception:
                raise AssertionError(f"Unexpected percent value: {percent}")

        return

    # 2) If there's a summary with percent_verified or similar at top-level
    summary = data.get("summary") if isinstance(data.get("summary"), dict) else data
    percent_keys = ("percent_verified", "percent", "percentVerified", "verified_percent", "percentComplete")
    percent_val = None
    for k in percent_keys:
        if k in summary:
            percent_val = summary[k]
            break
    if percent_val is not None:
        try:
            percent_num = float(percent_val)
        except Exception:
            raise AssertionError(f"percent value not numeric: {percent_val}")
        assert int(percent_num) == 100, f"Expected 100% verified, got {percent_num}"

        # If detailed counts present, ensure total==3 and verified==3 if available
        total = None
        verified = None
        for k in ("total", "count", "total_models", "models_total"):
            if k in summary:
                total = summary[k]
                break
        for k in ("verified", "verified_count", "models_verified"):
            if k in summary:
                verified = summary[k]
                break
        if total is not None or verified is not None:
            # if provided, assert they indicate 3/3
            if total is not None:
                assert int(total) == 3, f"Expected total models 3, got {total}"
            if verified is not None:
                assert int(verified) == 3, f"Expected verified models 3, got {verified}"
        return

    # 3) If response contains an 'all_verified' boolean or similar
    for k in ("all_verified", "allVerified", "fully_verified"):
        if k in data:
            val = data[k]
            assert bool(val) is True, f"Expected {k} == True, got {val}"
            # ensure models listed somewhere
            models_list = data.get("models") or data.get("results") or data.get("verified_models")
            if isinstance(models_list, list):
                names = {normalize(m if isinstance(m, str) else (m.get("name") if isinstance(m, dict) else "")) for m in models_list}
                for expected in ("tinybert", "xgboost", "isolation forest"):
                    assert expected in names, f"{expected} not present in models list: {names}"
            return

    # If none of the above patterns matched, attempt to locate entries for models anywhere in the JSON
    textified = str(data).lower()
    missing_models = [m for m in ("tinybert", "xgboost", "isolation forest") if m not in textified]
    if missing_models:
        raise AssertionError(f"Response does not clearly indicate verification for models. Missing names in payload: {missing_models}")

    # If model names present, ensure words like 'verified' or '100' appear near them
    if "verified" not in textified and "100" not in textified and "100%" not in textified:
        raise AssertionError("Response contains model names but does not indicate verified status or 100% verification")

    # If we reached here, consider the test passed (best-effort heuristics)
    return


if __name__ == "__main__":
    try:
        test_verify_models_integrity_TC018()
    except AssertionError as e:
        print(f"TC018 FAILED: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC018 ERROR: {e}")
        sys.exit(2)
    else:
        print("TC018 PASSED")
        sys.exit(0)
import requests
import sys

BASE_URL = "http://127.0.0.1:8000"
TOKEN = "Bearer dev-mock-token"
TIMEOUT = 30


def test_tc011_qr_decoded_benign_url_reputation_lookup():
    endpoint = f"{BASE_URL}/api/v1/v1/threat-intel/lookup"
    headers = {
        "Authorization": TOKEN,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    payload = {"url": "https://google.com"}

    try:
        resp = requests.post(endpoint, headers=headers, json=payload, timeout=TIMEOUT)
    except requests.exceptions.RequestException as e:
        raise SystemExit(f"HTTP request failed: {e}")

    assert resp.status_code == 200, f"Expected HTTP 200 OK, got {resp.status_code}: {resp.text}"

    try:
        data = resp.json()
    except ValueError:
        assert False, f"Response is not valid JSON: {resp.text}"

    # Validate benign/clean indicators. Accept several possible response shapes:
    # - explicit boolean flags like is_malicious or malicious
    # - classification/verdict string values ('clean', 'benign', 'safe')
    # - numeric risk_score or reputation.score that should be low for benign URLs
    def lower_str(v):
        return str(v).strip().lower() if v is not None else ""

    if "is_malicious" in data:
        assert data["is_malicious"] is False, f"Expected is_malicious False, got {data['is_malicious']}"
    elif "malicious" in data:
        # could be boolean or nested
        val = data["malicious"]
        if isinstance(val, bool):
            assert val is False, f"Expected malicious False, got {val}"
        else:
            assert lower_str(val) in ("false", "no", "0", ""), f"Unexpected malicious value: {val}"
    elif "classification" in data:
        cls = lower_str(data["classification"])
        assert cls in ("clean", "benign", "safe", "whitelisted", "unknown"), f"Unexpected classification: {data['classification']}"
    elif "verdict" in data:
        v = lower_str(data["verdict"])
        assert v in ("clean", "benign", "safe", "unknown"), f"Unexpected verdict: {data['verdict']}"
    elif "reputation" in data and isinstance(data["reputation"], dict):
        rep = data["reputation"]
        if "malicious" in rep:
            assert rep.get("malicious") is False, f"Expected reputation.malicious False, got {rep.get('malicious')}"
        elif "score" in rep:
            try:
                score = float(rep.get("score"))
                assert score <= 20.0, f"Expected low reputation.score for benign URL, got {score}"
            except Exception:
                assert False, f"Invalid reputation.score value: {rep.get('score')}"
        else:
            # fallback: accept presence of a reputation object with no malicious indicators
            assert rep != {}, f"Reputation object empty or indicates unknown state: {rep}"
    elif "risk_score" in data:
        try:
            score = float(data["risk_score"])
            assert score <= 20.0, f"Expected risk_score <= 20 for benign URL, got {score}"
        except Exception:
            assert False, f"Invalid risk_score value: {data.get('risk_score')}"
    else:
        # Last attempt: check common benign keywords anywhere in JSON string
        body_str = str(data).lower()
        if any(k in body_str for k in ("clean", "benign", "safe", "not malicious", "not_malicious")):
            pass
        else:
            assert False, f"Response JSON did not contain recognizable benign indicators: {data}"

    print("TC011 passed: benign URL reputation lookup returned clean result.")


if __name__ == "__main__":
    test_tc011_qr_decoded_benign_url_reputation_lookup()
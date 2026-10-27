import requests
import sys

BASE_URL = "http://127.0.0.1:8000"
ENDPOINT = BASE_URL + "/api/v1/v1/threat-intel/lookup"
TOKEN = "Bearer dev-mock-token"
HEADERS = {
    "Authorization": TOKEN,
    "Content-Type": "application/json",
    "Accept": "application/json",
}
TIMEOUT = 30.0


def test_tc010_qr_malicious_url_lookup():
    # Optional quick health checks to ensure service is reachable
    for probe in ("/health/live", "/health/ready"):
        try:
            r = requests.get(BASE_URL + probe, timeout=10)
        except requests.RequestException as e:
            raise AssertionError(f"Health probe {probe} request failed: {e}")
        # Accept 200 for healthy probe; readiness could be 200 or 503 depending on environment - ensure service reachable
        assert r.status_code in (200, 503), f"Unexpected status for {probe}: {r.status_code} - {r.text}"

    payload = {"url": "http://malicious-secure-login.top/verify"}

    try:
        resp = requests.post(ENDPOINT, headers=HEADERS, json=payload, timeout=TIMEOUT)
    except requests.RequestException as e:
        raise AssertionError(f"Threat-intel lookup request failed: {e}")

    # Expect success
    if resp.status_code != 200:
        # Attempt to extract JSON body for debugging
        try:
            body = resp.json()
        except Exception:
            body = resp.text
        raise AssertionError(f"Expected HTTP 200, got {resp.status_code}. Response: {body}")

    try:
        data = resp.json()
    except ValueError:
        raise AssertionError("Response is not valid JSON")

    assert isinstance(data, dict), "Response JSON must be an object"

    # Validate presence of classification
    assert "classification" in data, "Response missing required field: classification"
    classification = data["classification"]
    assert isinstance(classification, str) and classification.strip(), "classification must be a non-empty string"

    # Reputation score may be named 'reputation_score' or 'reputation'
    reputation = None
    if "reputation_score" in data:
        reputation = data["reputation_score"]
    elif "reputation" in data:
        reputation = data["reputation"]
    assert reputation is not None, "Response missing reputation score (reputation_score or reputation)"
    assert isinstance(reputation, (int, float)), "reputation must be numeric"
    # Expect score in reasonable bounds 0-100
    assert 0 <= reputation <= 100, f"reputation out of expected range 0-100: {reputation}"

    # Threat indicators may come under several possible keys
    indicators = None
    for key in ("threat_indicators", "indicators", "threats"):
        if key in data:
            indicators = data[key]
            break
    assert indicators is not None, "Response missing threat indicators (threat_indicators/indicators/threats)"
    assert isinstance(indicators, list), "threat indicators must be a list"
    assert len(indicators) > 0, "threat indicators list is empty"

    # Ensure classification suggests maliciousness (flexible check)
    lower_class = classification.lower()
    malicious_tokens = ("malicious", "phish", "phishing", "suspicious", "malware", "fraud")
    assert any(tok in lower_class for tok in malicious_tokens), (
        f"Classification does not indicate maliciousness: {classification}"
    )

    # Basic sanity on indicators contents (strings)
    assert all(isinstance(i, (str,)) and i.strip() for i in indicators), "Each threat indicator should be a non-empty string"

    print("TC010 passed: malicious QR URL lookup returned classification, reputation score, and threat indicators.")


if __name__ == "__main__":
    try:
        test_tc010_qr_malicious_url_lookup()
    except AssertionError as e:
        print(f"TC010 failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"TC010 encountered an unexpected error: {e}")
        sys.exit(2)
    sys.exit(0)
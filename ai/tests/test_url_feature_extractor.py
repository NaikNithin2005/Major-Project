import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES, calculate_shannon_entropy

def test_feature_count_and_names():
    url = "https://sub.phishing-example.xyz/path/to/page?id=123#anchor"
    feats = extract_url_features(url)
    assert len(feats) == 16
    assert list(feats.keys()) == FEATURE_NAMES

def test_suspicious_tld_and_features():
    suspicious_url = "http://secure-login.account-update.xyz/login"
    feats = extract_url_features(suspicious_url)
    assert feats["suspicious_tld"] == 1.0
    assert feats["has_dash_in_domain"] == 1.0
    assert feats["https_enabled"] == 0.0

def test_entropy_calculation():
    text = "aaaa"
    assert calculate_shannon_entropy(text) == 0.0
    text_mix = "abcdef"
    assert calculate_shannon_entropy(text_mix) > 0.0

def test_malformed_url_safety():
    malformed = "http://[invalid-ipv6]:8080/path"
    feats = extract_url_features(malformed)
    assert len(feats) == 16
    assert isinstance(feats["url_length"], float)

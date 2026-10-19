"""
Phase 19 AI Model Red-Team & Adversarial Robustness Test Suite.
Validates model integrity, preprocessor resilience against zero-width chars,
visual homoglyphs, SSRF edge cases, dangerous schemes, and fail-closed risk engine contracts.
"""

import os
import sys
import pytest
import numpy as np
import onnxruntime as ort

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BACKEND_DIR = os.path.join(ROOT_DIR, "backend")
if ROOT_DIR not in sys.path:
    sys.path.insert(0, ROOT_DIR)
if BACKEND_DIR not in sys.path:
    sys.path.insert(0, BACKEND_DIR)

from ai.preprocessing.sms_preprocessor import clean_sms_text, SmsPreprocessor
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES
from app.services.url_normalizer import SafeUrlNormalizer, DANGEROUS_SCHEMES
from app.services.evidence_aggregation_service import EvidenceAggregationService
from app.services.threat_intel_providers import ReputationStatus
from ai.risk_engine.engine import RiskEngine
from ai.risk_engine.contracts import ThreatCategory, RecommendedAction, ModelOutputContract


# ==============================================================================
# 1. SMS PREPROCESSOR & TINYBERT ADVERSARIAL RESILIENCE
# ==============================================================================

def test_zero_width_character_stripping():
    """Verify preprocessor eliminates zero-width evasion characters while preserving words."""
    evasion_text = "Ur\u200bg\u200be\u200bnt: Y\u200bo\u200bu\u200br account has been locked. V\u200be\u200br\u200bi\u200bf\u200by here."
    cleaned = clean_sms_text(evasion_text)
    assert "Urgent:" in cleaned
    assert "Your" in cleaned
    assert "Verify" in cleaned
    assert "\u200b" not in cleaned


def test_homoglyph_cyrillic_normalization():
    """Verify preprocessor resolves Cyrillic visual lookalikes to Latin characters."""
    # 'о', 'а', 'е' in Cyrillic
    cyrillic_text = "Y\u043eur \u0430ccount is suspended. V\u0435rify n\u043ew."
    cleaned = clean_sms_text(cyrillic_text)
    assert "Your" in cleaned
    assert "account" in cleaned
    assert "Verify" in cleaned
    assert "now" in cleaned


def test_oversized_sms_length_boundary():
    """Verify preprocessor enforces length boundaries without crashing on huge payloads."""
    huge_text = "Phishing attack alert! " * 1000  # 23,000 characters
    cleaned = clean_sms_text(huge_text)
    assert len(cleaned) <= 5000
    assert cleaned.startswith("Phishing attack alert!")


def test_empty_and_control_character_handling():
    """Verify preprocessor safely handles null bytes, control chars, and blank strings."""
    assert clean_sms_text("") == ""
    assert clean_sms_text("   \n\t  ") == ""
    assert clean_sms_text(None) == ""
    dirty = "Alert\x00\x08\x1b message\x7f"
    cleaned = clean_sms_text(dirty)
    assert cleaned == "Alert message"


def test_tinybert_onnx_inference_on_adversarial_sms():
    """Verify TinyBERT ONNX model successfully scores cleaned adversarial smishing."""
    model_path = os.path.join(ROOT_DIR, "models", "tinybert", "v2", "tinybert_smishing.onnx")
    assert os.path.exists(model_path), f"TinyBERT ONNX artifact missing: {model_path}"

    preprocessor = SmsPreprocessor(max_length=128)
    sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
    in_names = [i.name for i in sess.get_inputs()]

    sample = "Ur\u200bg\u200be\u200bnt: Y\u200bo\u200bu\u200br account is suspended. Verify now: http://bank-update.xyz"
    cleaned = preprocessor.preprocess(sample)
    encoded = preprocessor.encode(cleaned)

    inputs = {
        "input_ids": encoded["input_ids"].numpy().astype(np.int64),
        "attention_mask": encoded["attention_mask"].numpy().astype(np.int64)
    }
    if "token_type_ids" in in_names:
        inputs["token_type_ids"] = np.zeros_like(inputs["input_ids"])

    outs = sess.run(None, inputs)
    logits = outs[0][0]
    exp_l = np.exp(logits - np.max(logits))
    probs = exp_l / np.sum(exp_l)
    smishing_prob = float(probs[1])

    # Must detect smishing with high confidence
    assert smishing_prob >= 0.70


# ==============================================================================
# 2. URL NORMALIZER, SSRF & DANGEROUS SCHEME RESILIENCE
# ==============================================================================

def test_ssrf_loopback_protections():
    """Verify SSRF validator blocks standard, hex, octal, and dotted-hex loopback IPs."""
    loopback_targets = [
        "http://127.0.0.1/admin",
        "http://localhost:8080/keys",
        "http://0x7f000001/status",
        "http://0x7f.0.0.1/login",
        "http://0177.0.0.1/shell",
        "http://[::1]/internal",
        "http://[::ffff:127.0.0.1]/metrics"
    ]
    for url in loopback_targets:
        res = SafeUrlNormalizer.normalize_url(url)
        assert res.is_ssrf_risk is True, f"Failed to detect SSRF risk for: {url}"


def test_ssrf_cloud_metadata_protections():
    """Verify SSRF validator blocks AWS/GCP link-local metadata address 169.254.169.254."""
    metadata_targets = [
        "http://169.254.169.254/latest/meta-data/",
        "http://[::ffff:169.254.169.254]/computeMetadata/v1/"
    ]
    for url in metadata_targets:
        res = SafeUrlNormalizer.normalize_url(url)
        assert res.is_ssrf_risk is True, f"Failed to detect cloud metadata SSRF: {url}"


def test_dangerous_schemes_detection():
    """Verify non-HTTP dangerous execution schemes are flagged as security hazards."""
    dangerous = [
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "smsto:99999:TRANSFER",
        "intent://scan/#Intent;scheme=malicious;end",
        "file:///etc/passwd"
    ]
    for u in dangerous:
        res = SafeUrlNormalizer.normalize_url(u)
        assert res.is_ssrf_risk is True or res.is_valid is False
        assert "Dangerous protocol" in str(res.ssrf_reason or res.error)


def test_punycode_idn_conversion():
    """Verify internationalized domain names (IDN) convert to Punycode."""
    homoglyph_url = "https://pаypal.com/signin"  # Cyrillic 'а'
    res = SafeUrlNormalizer.normalize_url(homoglyph_url)
    assert res.is_valid is True
    assert "xn--" in res.hostname
    assert res.hostname == "xn--pypal-4ve.com"


# ==============================================================================
# 3. XGBOOST & ISOLATION FOREST ONNX INTEGRITY
# ==============================================================================

def test_xgboost_onnx_subdomain_masking():
    """Verify XGBoost classifies deep subdomain brand spoofing as quishing."""
    xgb_path = os.path.join(ROOT_DIR, "models", "xgboost", "v2", "xgboost_url.onnx")
    sess = ort.InferenceSession(xgb_path, providers=["CPUExecutionProvider"])
    in_name = sess.get_inputs()[0].name

    url = "http://chase.com.security-update.account-verification.top/auth"
    feats = extract_url_features(url)
    vec = np.array([[feats[n] for n in FEATURE_NAMES]], dtype=np.float32)

    outs = sess.run(None, {in_name: vec})
    probs = outs[1][0]
    phish_prob = float(probs[1])
    assert phish_prob >= 0.80


def test_isolation_forest_onnx_divergence():
    """Verify Isolation Forest flags highly anomalous structural features as zero-day outliers."""
    iso_path = os.path.join(ROOT_DIR, "models", "isolation_forest", "v2", "isolation_forest.onnx")
    sess = ort.InferenceSession(iso_path, providers=["CPUExecutionProvider"])
    in_name = sess.get_inputs()[0].name

    # Synthesize highly divergent feature vector (excessive dots, symbols, entropy)
    outlier_vec = np.array([[500.0, 150.0, 10.0, 200.0, 150.0, 1.0, 1.0, 1.0, 1.0, 1.0, 50.0, 30.0, 7.5, 0.0, 1.0, 1.0]], dtype=np.float32)
    outs = sess.run(None, {in_name: outlier_vec})
    label = int(outs[0][0][0])
    # -1 indicates anomalous outlier in Isolation Forest
    assert label == -1


# ==============================================================================
# 4. RISK ENGINE BOUNDARIES, FAIL-CLOSED INTEGRITY & EXPLAINABILITY
# ==============================================================================

def test_risk_score_exact_boundaries():
    """Verify exact category boundaries: 0-25 Safe, 26-50 Low, 51-75 Medium, 76-100 High."""
    engine = RiskEngine()
    assert engine.categorize_score(0.0) == ThreatCategory.SAFE
    assert engine.categorize_score(25.0) == ThreatCategory.SAFE
    assert engine.categorize_score(25.1) == ThreatCategory.LOW
    assert engine.categorize_score(50.0) == ThreatCategory.LOW
    assert engine.categorize_score(50.1) == ThreatCategory.MEDIUM
    assert engine.categorize_score(75.0) == ThreatCategory.MEDIUM
    assert engine.categorize_score(75.1) == ThreatCategory.HIGH
    assert engine.categorize_score(100.0) == ThreatCategory.HIGH


def test_fail_closed_on_model_failures():
    """Verify system fails closed with explicit unavailable status rather than safe verdict."""
    engine = RiskEngine()
    model_results = {
        "tinybert": ModelOutputContract(model_name="tinybert", model_version="v2", is_available=False, error_message="Engine crash"),
        "xgboost": ModelOutputContract(model_name="xgboost", model_version="v2", is_available=False, error_message="Corrupted tensor")
    }

    # Malicious text with failed models
    assessment = engine.assess(
        sms_text="Urgent: Your SBI account is blocked. Verify at http://192.168.1.1:8080/login",
        url="http://192.168.1.1:8080/login",
        model_results=model_results
    )

    assert "tinybert" in assessment.unavailable_models
    assert "xgboost" in assessment.unavailable_models
    # Must NOT fail-open to SAFE! Rule engine and context engine must elevate risk
    assert assessment.category != ThreatCategory.SAFE
    assert assessment.risk_score >= 50.0


def test_evidence_traceability_on_elevated_verdict():
    """Verify every elevated verdict includes traceable evidence items without fabrication."""
    assessment = EvidenceAggregationService.aggregate_evidence(
        model_results={"tinybert_prob": 0.88, "xgboost_prob": 0.92, "is_anomaly": True, "anomaly_score": 0.85},
        url_metadata={"is_ip_hostname": True, "is_punycode": True, "subdomain_depth": 3, "is_suspicious_tld": True},
        threat_intel={"is_malicious": True, "confidence": 0.95, "source": "URLHAUS", "threat_type": "PHISHING"}
    )

    assert assessment.threat_category == "HIGH"
    assert assessment.final_risk_score >= 85
    items = assessment.evidence_items
    assert len(items) >= 4
    categories = {i.category for i in items}
    assert "AI_MODEL" in categories
    assert "URL_STRUCTURE" in categories
    assert "THREAT_INTEL" in categories
    assert "ANOMALY" in categories

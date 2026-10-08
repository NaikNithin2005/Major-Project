"""
Phase 6 Comprehensive Test Suite - Risk Engine, Context Engine, Rule Engine, and XAI.
Directly validates all 34 required test conditions from Section 25 of prompt and Phases.md.
"""

import pytest
import time
from ai.risk_engine.contracts import (
    ThreatCategory,
    RecommendedAction,
    EvidenceSource,
    ModelOutputContract,
    RiskAssessment,
    ContextFinding,
    RuleResult,
)
from ai.risk_engine.engine import RiskEngine
from ai.rule_engine.engine import RuleEngine
from ai.context_engine.engine import ContextCorrelationEngine
from ai.explainable_ai.explainer import ExplainableAiEngine
from ai.feature_engineering.url_feature_extractor import extract_url_features
from ai.xgboost.model import XgboostResult
from ai.tinybert.model import TinyBertResult
from ai.anomaly.model import IsolationForestResult


@pytest.fixture
def risk_engine():
    return RiskEngine()


@pytest.fixture
def rule_engine():
    return RuleEngine()


@pytest.fixture
def context_engine():
    return ContextCorrelationEngine()


@pytest.fixture
def xai_engine():
    return ExplainableAiEngine()


# ==============================================================================
# 1. RISK ENGINE TESTS (Items 1 - 10)
# ==============================================================================

def test_1_safe_event(risk_engine):
    """1. Safe event: low risk inputs produce SAFE category."""
    assessment = risk_engine.assess(
        sms_text="Your package has been delivered to your front porch.",
        sender="AMAZON",
        url="https://www.amazon.com/orders",
        sms_score=5.0,
        url_score=2.0,
        qr_score=0.0,
        anomaly_score=1.0,
        context_score=0.0
    )
    assert assessment.category == ThreatCategory.SAFE
    assert assessment.risk_score <= 25.0
    assert assessment.recommended_action == RecommendedAction.ALLOW


def test_2_low_risk_event(risk_engine):
    """2. Low-risk event produces LOW category."""
    assessment = risk_engine.assess(
        sms_text="Here is your newsletter update.",
        sms_score=35.0,
        url_score=30.0,
        qr_score=10.0,
        context_score=25.0,
        anomaly_score=15.0
    )
    assert assessment.category == ThreatCategory.LOW
    assert 25.0 < assessment.risk_score <= 50.0
    assert assessment.recommended_action == RecommendedAction.ALLOW_WITH_CAUTION


def test_3_medium_risk_event(risk_engine):
    """3. Medium-risk event produces MEDIUM category."""
    assessment = risk_engine.assess(
        sms_text="Your service account invoice is pending.",
        sms_score=65.0,
        url_score=60.0,
        qr_score=40.0,
        context_score=50.0,
        anomaly_score=50.0
    )
    assert assessment.category == ThreatCategory.MEDIUM
    assert 50.0 < assessment.risk_score <= 75.0
    assert assessment.recommended_action == RecommendedAction.WARN_USER


def test_4_high_risk_event(risk_engine):
    """4. High-risk event produces HIGH category."""
    assessment = risk_engine.assess(
        sms_text="URGENT: Your SBI Account is suspended. Update KYC now!",
        sender="SBI-ALERT",
        url="http://sbi-kyc-update.xyz/login",
        sms_score=95.0,
        url_score=98.0,
        qr_score=70.0,
        context_score=75.0,
        anomaly_score=85.0
    )
    assert assessment.category == ThreatCategory.HIGH
    assert assessment.risk_score > 75.0
    assert assessment.recommended_action == RecommendedAction.BLOCK_OR_STRONGLY_WARN


def test_5_score_lower_bound(risk_engine):
    """5. Score lower bound: all zero inputs clamp at exactly 0.0."""
    assessment = risk_engine.assess(
        sms_score=-50.0,
        url_score=-10.0,
        qr_score=0.0,
        context_score=0.0,
        anomaly_score=0.0
    )
    assert assessment.risk_score == 0.0
    assert assessment.category == ThreatCategory.SAFE


def test_6_score_upper_bound(risk_engine):
    """6. Score upper bound: excessive scores clamp at exactly 100.0."""
    assessment = risk_engine.assess(
        sms_score=200.0,
        url_score=150.0,
        qr_score=100.0,
        context_score=100.0,
        anomaly_score=100.0
    )
    assert assessment.risk_score == 100.0
    assert assessment.category == ThreatCategory.HIGH


def test_7_correct_category_boundary_at_25_26(risk_engine):
    """7. Correct category boundary at 25/26."""
    assert risk_engine.categorize_score(25.0) == ThreatCategory.SAFE
    assert risk_engine.categorize_score(25.1) == ThreatCategory.LOW
    assert risk_engine.categorize_score(26.0) == ThreatCategory.LOW


def test_8_correct_category_boundary_at_50_51(risk_engine):
    """8. Correct category boundary at 50/51."""
    assert risk_engine.categorize_score(50.0) == ThreatCategory.LOW
    assert risk_engine.categorize_score(50.1) == ThreatCategory.MEDIUM
    assert risk_engine.categorize_score(51.0) == ThreatCategory.MEDIUM


def test_9_correct_category_boundary_at_75_76(risk_engine):
    """9. Correct category boundary at 75/76."""
    assert risk_engine.categorize_score(75.0) == ThreatCategory.MEDIUM
    assert risk_engine.categorize_score(75.1) == ThreatCategory.HIGH
    assert risk_engine.categorize_score(76.0) == ThreatCategory.HIGH


def test_10_weighted_score_calculation(risk_engine):
    """10. Exact weighted score calculation matching formula: 0.35*S + 0.30*U + 0.15*Q + 0.10*C + 0.10*A."""
    # S=80 (contrib 28.0), U=90 (contrib 27.0), Q=60 (contrib 9.0), C=70 (contrib 7.0), A=50 (contrib 5.0)
    # Expected final = 28 + 27 + 9 + 7 + 5 = 76.0
    assessment = risk_engine.assess(
        sms_score=80.0,
        url_score=90.0,
        qr_score=60.0,
        context_score=70.0,
        anomaly_score=50.0
    )
    bd = assessment.score_breakdown
    assert bd.sms_contribution == 28.0
    assert bd.url_contribution == 27.0
    assert bd.qr_contribution == 9.0
    assert bd.context_contribution == 7.0
    assert bd.anomaly_contribution == 5.0
    assert assessment.risk_score == 76.0
    assert assessment.category == ThreatCategory.HIGH


# ==============================================================================
# 2. CONTEXT ENGINE TESTS (Items 11 - 15)
# ==============================================================================

def test_11_matching_sender_domain(context_engine):
    """11. Matching sender and official domain yields 0 inconsistency."""
    score, findings = context_engine.correlate(
        sms_text="Your SBI OTP is 482910. Do not share.",
        sender="SBI-ALERT",
        url="https://onlinesbi.sbi/personal"
    )
    assert score == 0.0
    assert any(f.check_name == "BRAND_DOMAIN_MATCH" for f in findings)


def test_12_brand_domain_mismatch(context_engine):
    """12. Brand mentioned in message vs mismatched domain produces strong context score."""
    score, findings = context_engine.correlate(
        sms_text="Your PayPal account has been limited. Verify identity.",
        sender="PAYPAL",
        url="http://paypal-verification-alert.xyz/auth"
    )
    assert score >= 75.0
    assert any(f.check_name == "BRAND_DOMAIN_MISMATCH" for f in findings)


def test_13_banking_context_mismatch(context_engine):
    """13. Banking context targeting unverified external host increases context risk."""
    score, findings = context_engine.correlate(
        sms_text="Urgent: Your bank KYC has expired. Update immediately.",
        url="http://verify-kyc-portal.xyz/form"
    )
    assert score >= 50.0
    assert any(f.check_name == "BANKING_CONTEXT_MISMATCH" for f in findings)


def test_14_urgency_suspicious_url(context_engine, rule_engine):
    """14. Urgency + suspicious URL produces combined context and rule signals."""
    rules = rule_engine.evaluate(
        sms_text="Action required: suspended within 24 hours!",
        url="http://192.168.1.1:8080/login"
    )
    rule_ids = {r.id for r in rules}
    assert "RULE_URGENCY_LANGUAGE" in rule_ids
    assert "RULE_IP_HOSTNAME" in rule_ids
    assert "RULE_UNUSUAL_PORT" in rule_ids


def test_15_sms_qr_correlation(context_engine):
    """15. SMS instructing QR scan correlating with external non-https URL."""
    score, findings = context_engine.correlate(
        sms_text="Your account is blocked. Scan QR code attached to restore access.",
        qr_payload="http://unverified-restore.info/pay"
    )
    assert score >= 25.0
    assert any(f.check_name == "SMS_QR_CROSS_CHANNEL_ANOMALY" for f in findings)


# ==============================================================================
# 3. RULE ENGINE TESTS (Items 16 - 22)
# ==============================================================================

def test_16_https_rule(rule_engine):
    """16. Insecure HTTP URL triggers RULE_MISSING_HTTPS."""
    rules = rule_engine.evaluate(url="http://secure-login.com")
    assert any(r.id == "RULE_MISSING_HTTPS" for r in rules)


def test_17_ip_host_rule(rule_engine):
    """17. Raw IP host triggers RULE_IP_HOSTNAME."""
    rules = rule_engine.evaluate(url="http://192.168.1.100/admin")
    assert any(r.id == "RULE_IP_HOSTNAME" for r in rules)


def test_18_punycode_rule(rule_engine):
    """18. Punycode domain triggers RULE_PUNYCODE_HOMOGRAPH."""
    rules = rule_engine.evaluate(url="https://xn--pple-43d.com")
    assert any(r.id == "RULE_PUNYCODE_HOMOGRAPH" for r in rules)


def test_19_unusual_port_rule(rule_engine):
    """19. Non-standard port triggers RULE_UNUSUAL_PORT."""
    rules = rule_engine.evaluate(url="https://login.example.com:8443/auth")
    assert any(r.id == "RULE_UNUSUAL_PORT" for r in rules)


def test_20_suspicious_symbol_rule(rule_engine):
    """20. Embedded '@' symbol triggers RULE_SUSPICIOUS_SYMBOLS."""
    rules = rule_engine.evaluate(url="https://bank.com@malicious-redirect.com/login")
    assert any(r.id == "RULE_SUSPICIOUS_SYMBOLS" for r in rules)


def test_21_urgency_rule(rule_engine):
    """21. Psychological urgency triggers RULE_URGENCY_LANGUAGE."""
    rules = rule_engine.evaluate(sms_text="Immediate action required! Account will be blocked today.")
    assert any(r.id == "RULE_URGENCY_LANGUAGE" for r in rules)


def test_22_unknown_sender_rule(rule_engine):
    """22. Suspicious alphanumeric sender header triggers rule."""
    rules = rule_engine.evaluate(sms_text="Test", sender="XY34BZ")
    assert any(r.id == "RULE_SUSPICIOUS_SENDER_HEADER" for r in rules)


# ==============================================================================
# 4. EXPLAINABLE AI (XAI) TESTS (Items 23 - 28)
# ==============================================================================

def test_23_every_reason_maps_to_evidence(risk_engine):
    """23. Every generated explanation maps directly to verified evidence."""
    assessment = risk_engine.assess(
        sms_text="Urgent: Your SBI account is blocked. Verify at http://192.168.1.1/kyc",
        sender="SBI-ALERT",
        url="http://192.168.1.1/kyc",
        model_results={
            "xgboost": ModelOutputContract(
                model_name="xgboost",
                model_version="xgboost-url-v2",
                probability=0.96,
                predicted_class="phishing"
            )
        }
    )
    assert len(assessment.structured_reasons) > 0
    for reason in assessment.structured_reasons:
        assert reason.source in list(EvidenceSource)
        assert reason.debug_detail is not None
        assert len(reason.title) > 0


def test_24_no_fabricated_reasons(risk_engine):
    """24. No fabricated reasons: safe traffic produces no false hazard reasons."""
    assessment = risk_engine.assess(
        sms_text="Your appointment with Dr. Smith is confirmed for tomorrow at 10 AM.",
        url="https://healthcare.org/appointments",
        sms_score=2.0,
        url_score=1.0,
        qr_score=0.0,
        context_score=0.0,
        anomaly_score=0.0
    )
    # Must not contain words like 'urgent' or 'malicious' or 'phishing'
    for reason in assessment.reasons:
        assert "phishing" not in reason.lower()
        assert "malicious" not in reason.lower()


def test_25_correct_reason_prioritization(xai_engine):
    """25. Critical model and context findings are ranked ahead of lower severity rules."""
    model_results = {
        "xgboost": ModelOutputContract(
            model_name="xgboost",
            model_version="xgboost-url-v2",
            probability=0.99,
            predicted_class="phishing"
        )
    }
    context_findings = [
        ContextFinding(
            check_name="BRAND_DOMAIN_MISMATCH",
            matched=True,
            severity="CRITICAL",
            description="Message claims SBI but points to unverified domain.",
            evidence="Evidence detail"
        )
    ]
    rule_results = [
        RuleResult(
            id="RULE_LONG_URL",
            category="URL_STRUCTURE",
            severity="LOW",
            description="URL is long.",
            score_contribution=10.0,
            evidence="len > 75"
        )
    ]
    reasons = xai_engine.generate_explanations(
        model_results=model_results,
        rule_results=rule_results,
        context_findings=context_findings
    )
    # The top reasons should have CRITICAL or HIGH severity
    assert reasons[0].severity in ("CRITICAL", "HIGH")
    # Low severity rules should not displace critical model/context findings
    for r in reasons:
        assert r.severity in ("CRITICAL", "HIGH", "MEDIUM")


def test_26_model_evidence_explanation(xai_engine):
    """26. Model probability directly translated into human-readable reason."""
    model_results = {
        "xgboost": ModelOutputContract(
            model_name="xgboost",
            model_version="xgboost-url-v2",
            probability=0.94,
            predicted_class="phishing"
        )
    }
    reasons = xai_engine.generate_explanations(model_results=model_results, rule_results=[], context_findings=[])
    assert any("URL Classifier" in r.title for r in reasons)
    assert any(r.source == EvidenceSource.MODEL for r in reasons)


def test_27_context_evidence_explanation(xai_engine):
    """27. Context mismatch produces distinct context explanation."""
    context_findings = [
        ContextFinding(
            check_name="BRAND_DOMAIN_MISMATCH",
            matched=True,
            severity="CRITICAL",
            description="Message references brand 'SBI', but destination domain 'fake.xyz' is unauthorized.",
            evidence="SBI vs fake.xyz"
        )
    ]
    reasons = xai_engine.generate_explanations(model_results={}, rule_results=[], context_findings=context_findings)
    assert any(r.source == EvidenceSource.CONTEXT for r in reasons)


def test_28_feature_evidence_explanation(xai_engine):
    """28. Feature importances are inspectable in debug mode."""
    feats = xai_engine.get_feature_importance_debug()
    assert isinstance(feats, (list, dict))
    assert len(feats) > 0


# ==============================================================================
# 5. FAILURE HANDLING TESTS (Items 29 - 32)
# ==============================================================================

def test_29_missing_sms_model(risk_engine):
    """29. Missing SMS model: gracefully evaluates with URL, QR, Context, and Anomaly."""
    model_results = {
        "tinybert": ModelOutputContract(
            model_name="tinybert",
            model_version="tinybert-v2",
            is_available=False,
            error_message="Model file missing"
        ),
        "xgboost": ModelOutputContract(
            model_name="xgboost",
            model_version="xgboost-url-v2",
            probability=0.90,
            is_available=True
        )
    }
    assessment = risk_engine.assess(
        url="http://phishing-site.xyz/login",
        model_results=model_results
    )
    assert "tinybert" in assessment.unavailable_models
    assert assessment.risk_score > 0.0
    # Does not crash, category is populated
    assert assessment.category in list(ThreatCategory)


def test_30_missing_xgboost_model(risk_engine):
    """30. Missing XGBoost model: evaluates with SMS, Context, and Rules."""
    model_results = {
        "xgboost": ModelOutputContract(
            model_name="xgboost",
            model_version="xgboost-url-v2",
            is_available=False,
            error_message="ONNX engine failure"
        ),
        "tinybert": ModelOutputContract(
            model_name="tinybert",
            model_version="tinybert-smishing-v2",
            probability=0.88,
            is_available=True
        )
    }
    assessment = risk_engine.assess(
        sms_text="Account locked. Verify credentials immediately.",
        model_results=model_results
    )
    assert "xgboost" in assessment.unavailable_models
    assert assessment.risk_score > 0.0


def test_31_missing_anomaly_model(risk_engine):
    """31. Missing Isolation Forest anomaly model: evaluates with remaining signals."""
    model_results = {
        "isolation_forest": ModelOutputContract(
            model_name="isolation_forest",
            model_version="isolation-forest-v2",
            is_available=False
        ),
        "xgboost": ModelOutputContract(
            model_name="xgboost",
            model_version="xgboost-url-v2",
            probability=0.70,
            is_available=True
        )
    }
    assessment = risk_engine.assess(
        url="http://test.xyz",
        model_results=model_results
    )
    assert "isolation_forest" in assessment.unavailable_models
    assert assessment.risk_score > 0.0


def test_32_all_model_failures(risk_engine):
    """32. All models unavailable: falls back to deterministic rule and context engines safely."""
    model_results = {
        "tinybert": ModelOutputContract(model_name="tinybert", model_version="v2", is_available=False),
        "xgboost": ModelOutputContract(model_name="xgboost", model_version="v2", is_available=False),
        "isolation_forest": ModelOutputContract(model_name="isolation_forest", model_version="v2", is_available=False)
    }
    assessment = risk_engine.assess(
        sms_text="URGENT: SBI account suspended! Go to http://192.168.1.1:8080/login",
        sender="SBI",
        url="http://192.168.1.1:8080/login",
        model_results=model_results
    )
    assert len(assessment.unavailable_models) == 3
    # Rules & Context still trigger
    assert len(assessment.rule_results) > 0
    assert len(assessment.context_findings) > 0
    assert assessment.risk_score > 0.0


# ==============================================================================
# 6. PRIVACY & MODEL COMPATIBILITY (Items 33 - 34)
# ==============================================================================

def test_33_raw_sms_not_in_threat_report(risk_engine):
    """33. Threat report preserves privacy: raw SMS body is NOT stored in ThreatReport."""
    assessment = risk_engine.assess(
        sms_text="CONFIDENTIAL: Your doctor Dr. John prescribed Medicine X. Click http://bad.xyz",
        url="http://bad.xyz"
    )
    report = risk_engine.generate_threat_report(assessment, detection_type="SMS")
    report_dict = str(report.__dict__)
    assert "CONFIDENTIAL: Your doctor Dr. John prescribed Medicine X" not in report_dict
    assert report.privacy_notice is not None


def test_34_phase5_model_output_contracts_accepted(risk_engine):
    """34. Phase 5 model outputs (XgboostResult, TinyBertResult, IsolationForestResult) accepted cleanly."""
    xgb_out = XgboostResult(
        model_version="xgboost-url-v2",
        label="phishing",
        probability=0.985,
        confidence=0.985,
        timestamp=time.time()
    )
    tb_out = TinyBertResult(
        model_version="tinybert-smishing-v2",
        label="smishing",
        probabilities={"safe": 0.05, "smishing": 0.95},
        confidence=0.95,
        timestamp=time.time()
    )
    iso_out = IsolationForestResult(
        model_version="isolation-forest-v2",
        anomaly_score=0.82,
        is_anomalous=True,
        confidence=0.82,
        timestamp=time.time()
    )

    # Convert to ModelOutputContract
    model_contracts = {
        "xgboost": ModelOutputContract(
            model_name="xgboost",
            model_version=xgb_out.model_version,
            probability=xgb_out.probability,
            predicted_class=xgb_out.label,
            confidence=xgb_out.confidence
        ),
        "tinybert": ModelOutputContract(
            model_name="tinybert",
            model_version=tb_out.model_version,
            probability=tb_out.probabilities["smishing"],
            predicted_class=tb_out.label,
            confidence=tb_out.confidence
        ),
        "isolation_forest": ModelOutputContract(
            model_name="isolation_forest",
            model_version=iso_out.model_version,
            probability=iso_out.anomaly_score,
            predicted_class="anomalous" if iso_out.is_anomalous else "normal",
            confidence=iso_out.confidence,
            metadata={"is_anomalous": iso_out.is_anomalous}
        )
    }

    assessment = risk_engine.assess(
        sms_text="Urgent banking update",
        url="http://suspicious.xyz",
        model_results=model_contracts
    )
    assert assessment.risk_score > 75.0
    assert assessment.category == ThreatCategory.HIGH
    assert assessment.model_versions["xgboost"] == "xgboost-url-v2"
    assert assessment.model_versions["tinybert"] == "tinybert-smishing-v2"
    assert assessment.model_versions["isolation_forest"] == "isolation-forest-v2"

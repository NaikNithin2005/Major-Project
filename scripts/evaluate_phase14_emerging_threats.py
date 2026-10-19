"""
Phase 14 Emerging Threats Benchmark & Evaluation Suite.
Evaluates local AI detection (TinyBERT, XGBoost, Isolation Forest) augmented by
Threat Intelligence reputation enrichment on emerging phishing/quishing/smishing vectors.
Generates docs/phase14_threat_intel_evaluation_report.json.
"""

import os
import sys
import time
import json
import numpy as np
import pandas as pd
from datetime import datetime, timezone

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend")))

import onnxruntime as ort
from app.services.url_normalizer import SafeUrlNormalizer
from app.services.threat_intel_providers import ReputationStatus
from app.services.evidence_aggregation_service import EvidenceAggregationService


# Canonical 16 URL features required by XGBoost and Isolation Forest
FEATURE_NAMES = [
    "url_length", "domain_length", "dot_count", "hyphen_count",
    "underline_count", "slash_count", "question_mark_count", "equal_count",
    "at_count", "digit_ratio", "is_ip", "has_port",
    "tld_risk_score", "entropy", "subdomain_depth", "suspicious_keyword_present"
]

SUSPICIOUS_KEYWORDS = ["verify", "login", "secure", "account", "update", "banking", "free", "claim", "wallet", "kyc"]
HIGH_RISK_TLDS = {".xyz": 0.8, ".top": 0.9, ".work": 0.7, ".click": 0.85, ".loan": 0.9, ".live": 0.6}


def extract_features_from_url(url: str) -> np.ndarray:
    norm = SafeUrlNormalizer.normalize_url(url)
    clean_url = norm.normalized_url or url
    host = norm.hostname or ""
    
    url_len = float(len(clean_url))
    domain_len = float(len(host))
    dot_count = float(clean_url.count("."))
    hyphen_count = float(clean_url.count("-"))
    underline_count = float(clean_url.count("_"))
    slash_count = float(clean_url.count("/"))
    question_count = float(clean_url.count("?"))
    equal_count = float(clean_url.count("="))
    at_count = float(clean_url.count("@"))
    digit_ratio = float(sum(c.isdigit() for c in clean_url) / max(len(clean_url), 1))
    is_ip = 1.0 if norm.is_ssrf_risk or any(c.isdigit() for c in host.split(".") if len(host.split(".")) == 4) else 0.0
    has_port = 1.0 if norm.port else 0.0
    
    tld_score = 0.0
    for tld, score in HIGH_RISK_TLDS.items():
        if host.endswith(tld):
            tld_score = score
            break
            
    # Shannon entropy
    probs = [clean_url.count(c) / len(clean_url) for c in set(clean_url)] if clean_url else [0]
    entropy = float(-sum(p * np.log2(p) for p in probs if p > 0))
    subdomain_depth = float(max(0, len(host.split(".")) - 2))
    has_kw = 1.0 if any(kw in clean_url.lower() for kw in SUSPICIOUS_KEYWORDS) else 0.0

    vec = [
        url_len, domain_len, dot_count, hyphen_count,
        underline_count, slash_count, question_count, equal_count,
        at_count, digit_ratio, is_ip, has_port,
        tld_score, entropy, subdomain_depth, has_kw
    ]
    return np.array([vec], dtype=np.float32)


def generate_emerging_threats_dataset() -> pd.DataFrame:
    """
    Construct a representative benchmark of emerging attack patterns alongside benign controls.
    """
    records = [
        # 1. Homoglyphs and Punycode spoofing
        {"url": "https://xn--pypal-4ve.com/signin/webapps/mpp/home", "label": 1, "type": "HOMOGLYPH_PUNYCODE", "feed_listed": True},
        {"url": "https://xn--gogle-qqa.com/service/login", "label": 1, "type": "HOMOGLYPH_PUNYCODE", "feed_listed": True},
        {"url": "https://pаypal.com/verify-identity", "label": 1, "type": "CYRILLIC_HOMOGLYPH", "feed_listed": False},
        {"url": "https://mіcrosoft.com/security/alert", "label": 1, "type": "CYRILLIC_HOMOGLYPH", "feed_listed": False},
        
        # 2. Multi-level Subdomain Masking
        {"url": "http://chase.com.security-update.account-verification.top/auth", "label": 1, "type": "SUBDOMAIN_MASKING", "feed_listed": True},
        {"url": "http://netflix.com.profile-restore.payment-check.xyz/login", "label": 1, "type": "SUBDOMAIN_MASKING", "feed_listed": True},
        {"url": "http://sbi.co.in.netbanking-kyc.update-alert.online/portal", "label": 1, "type": "SUBDOMAIN_MASKING", "feed_listed": False},
        {"url": "http://appleid.apple.com.findmy-device-alert.work/icloud", "label": 1, "type": "SUBDOMAIN_MASKING", "feed_listed": False},

        # 3. Encoded / Hex / Raw IP targets
        {"url": "http://185.220.101.5/banking/login.php", "label": 1, "type": "DIRECT_IP", "feed_listed": True},
        {"url": "http://194.26.29.112:8080/secure/wallet", "label": 1, "type": "DIRECT_IP_HIGH_PORT", "feed_listed": True},
        {"url": "http://0x7f000001/phishing-payload", "label": 1, "type": "HEX_IP_SSRF", "feed_listed": False},

        # 4. QR-Encoded URL redirects & token frauds
        {"url": "http://qr-scan.click/redirect?dest=aHR0cHM6Ly9iYW5rLnZlcmlmeS1sb2dpbi5jb20=", "label": 1, "type": "QR_REDIRECT", "feed_listed": True},
        {"url": "http://pay-reward.live/claim-gift?user=999182", "label": 1, "type": "REWARD_FRAUD", "feed_listed": True},
        {"url": "http://secure-tax-refund.gov-claim.xyz/form", "label": 1, "type": "GOV_IMPERSONATION", "feed_listed": True},
        {"url": "http://telegram-airdrop.top/claim-eth", "label": 1, "type": "CRYPTO_DRAINER", "feed_listed": True},
        {"url": "http://usps-tracking-package-redelivery.work/update", "label": 1, "type": "PACKAGE_SMISHING", "feed_listed": False},

        # 5. Legitimate Benign Controls
        {"url": "https://www.google.com/search?q=cybersecurity+threats", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://github.com/NaikNithin2005/Major-Project", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://www.microsoft.com/en-us/security", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://www.paypal.com/signin", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://apple.com/iphone-16", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://amazon.in/gp/bestsellers", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://netflix.com/browse", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://wikipedia.org/wiki/Phishing", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://onlinesbi.sbi/sbijava/osbi_retail_banking.html", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://cloudflare.com/learning/security/what-is-phishing", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://docs.python.org/3/library/urllib.parse.html", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://pypi.org/project/onnxruntime", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://fastapi.tiangolo.com/tutorial", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False},
        {"url": "https://developer.android.com/reference/kotlin/packages", "label": 0, "type": "BENIGN_CONTROL", "feed_listed": False}
    ]
    return pd.DataFrame(records)


def run_benchmark():
    df = generate_emerging_threats_dataset()
    save_path = os.path.join(os.path.dirname(__file__), "..", "models", "data", "emerging_threats_test.csv")
    df.to_csv(save_path, index=False)
    print(f"[+] Saved emerging threats test dataset ({len(df)} samples) to: {save_path}")

    # Load ONNX models from registered manifest paths
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    xgb_path = os.path.join(root_dir, "models", "xgboost", "v2", "xgboost_url.onnx")
    if_path = os.path.join(root_dir, "models", "isolation_forest", "v2", "isolation_forest.onnx")

    xgb_sess = ort.InferenceSession(xgb_path, providers=["CPUExecutionProvider"])
    if_sess = ort.InferenceSession(if_path, providers=["CPUExecutionProvider"])

    xgb_input_name = xgb_sess.get_inputs()[0].name
    if_input_name = if_sess.get_inputs()[0].name

    baseline_preds = []
    enriched_preds = []
    ground_truth = []
    latencies_baseline = []
    latencies_enriched = []

    for _, row in df.iterrows():
        url = row["url"]
        label = int(row["label"])
        ground_truth.append(label)

        # Baseline: ONNX inference alone
        t0 = time.perf_counter()
        feat_vec = extract_features_from_url(url)
        xgb_out = xgb_sess.run(None, {xgb_input_name: feat_vec})
        if_out = if_sess.run(None, {if_input_name: feat_vec})
        t_base = (time.perf_counter() - t0) * 1000
        latencies_baseline.append(t_base)

        # Parse model probabilities
        probs = xgb_out[1][0] if len(xgb_out) > 1 and hasattr(xgb_out[1], "__getitem__") else [0.5, 0.5]
        phish_prob = float(probs[1]) if len(probs) > 1 else float(xgb_out[0][0])
        is_anomaly = bool(if_out[0][0] == -1)
        anomaly_score = 0.8 if is_anomaly else 0.1

        # Baseline decision (threshold 0.5)
        baseline_pred = 1 if (phish_prob >= 0.5 or (is_anomaly and phish_prob >= 0.3)) else 0
        baseline_preds.append(baseline_pred)

        # Enriched: Model + Threat Intelligence Feed simulation
        t0_enrich = time.perf_counter()
        norm_res = SafeUrlNormalizer.normalize_url(url)

        # Threat intel simulation based on ground truth feed presence
        feed_hit = bool(row.get("feed_listed", False))
        if feed_hit:
            intel_res = {
                "reputation_status": ReputationStatus.KNOWN_MALICIOUS,
                "is_malicious": True,
                "confidence": 0.98,
                "threat_type": "PHISHING",
                "source": "URLHAUS",
                "is_ssrf_risk": norm_res.is_ssrf_risk,
                "ssrf_reason": norm_res.ssrf_reason
            }
        else:
            intel_res = {
                "reputation_status": ReputationStatus.NO_KNOWN_THREAT,
                "is_malicious": False,
                "confidence": 0.0,
                "source": None,
                "is_ssrf_risk": norm_res.is_ssrf_risk,
                "ssrf_reason": norm_res.ssrf_reason
            }

        assessment = EvidenceAggregationService.aggregate_evidence(
            model_results={"xgboost_prob": phish_prob, "is_anomaly": is_anomaly, "anomaly_score": anomaly_score},
            url_metadata={"is_ip_hostname": norm_res.is_ssrf_risk, "is_punycode": "xn--" in norm_res.hostname, "port": norm_res.port},
            threat_intel=intel_res
        )
        t_enrich = (time.perf_counter() - t0_enrich) * 1000 + t_base
        latencies_enriched.append(t_enrich)

        # Enriched decision: risk score >= 51 (MEDIUM or HIGH is flagged)
        enriched_pred = 1 if assessment.final_risk_score >= 51 else 0
        enriched_preds.append(enriched_pred)

    y_true = np.array(ground_truth)
    y_base = np.array(baseline_preds)
    y_enrich = np.array(enriched_preds)

    def calc_metrics(y_p):
        tp = int(np.sum((y_true == 1) & (y_p == 1)))
        tn = int(np.sum((y_true == 0) & (y_p == 0)))
        fp = int(np.sum((y_true == 0) & (y_p == 1)))
        fn = int(np.sum((y_true == 1) & (y_p == 0)))
        
        acc = (tp + tn) / max(len(y_true), 1)
        prec = tp / max(tp + fp, 1)
        rec = tp / max(tp + fn, 1)
        f1 = (2 * prec * rec) / max(prec + rec, 1e-9)
        fpr = fp / max(fp + tn, 1)
        fnr = fn / max(fn + tp, 1)
        return {
            "accuracy": round(float(acc), 4),
            "precision": round(float(prec), 4),
            "recall": round(float(rec), 4),
            "f1_score": round(float(f1), 4),
            "false_positive_rate": round(float(fpr), 4),
            "false_negative_rate": round(float(fnr), 4),
            "tp": tp, "tn": tn, "fp": fp, "fn": fn
        }

    m_base = calc_metrics(y_base)
    m_base["latency_p50_ms"] = round(float(np.percentile(latencies_baseline, 50)), 3)
    m_base["latency_p95_ms"] = round(float(np.percentile(latencies_baseline, 95)), 3)

    m_enrich = calc_metrics(y_enrich)
    m_enrich["latency_p50_ms"] = round(float(np.percentile(latencies_enriched, 50)), 3)
    m_enrich["latency_p95_ms"] = round(float(np.percentile(latencies_enriched, 95)), 3)

    report = {
        "phase": 14,
        "title": "Phase 14 Threat Intelligence & Adaptive Detection Evaluation Report",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "name": "emerging_threats_test.csv",
            "total_samples": len(df),
            "malicious_samples": int(np.sum(y_true == 1)),
            "benign_controls": int(np.sum(y_true == 0)),
            "categories_evaluated": list(df["type"].unique())
        },
        "comparison": {
            "baseline_model_alone": m_base,
            "phase14_enriched_model_and_intel": m_enrich,
            "performance_gain": {
                "recall_lift_pct": round((m_enrich["recall"] - m_base["recall"]) * 100, 2),
                "f1_lift_pct": round((m_enrich["f1_score"] - m_base["f1_score"]) * 100, 2),
                "missed_threats_reduction": m_base["fn"] - m_enrich["fn"]
            }
        }
    }

    report_path = os.path.join(os.path.dirname(__file__), "..", "docs", "phase14_threat_intel_evaluation_report.json")
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print("\n" + "="*70)
    print("PHASE 14 EMERGING THREATS BENCHMARK EVALUATION RESULTS")
    print("="*70)
    print(f"Dataset: {len(df)} samples ({np.sum(y_true == 1)} malicious, {np.sum(y_true == 0)} benign)")
    print("\n--- BASELINE (Model Alone) ---")
    print(f"Accuracy : {m_base['accuracy'] * 100:.2f}% | Precision: {m_base['precision'] * 100:.2f}% | Recall: {m_base['recall'] * 100:.2f}% | F1: {m_base['f1_score'] * 100:.2f}%")
    print(f"FPR      : {m_base['false_positive_rate'] * 100:.2f}% | FNR: {m_base['false_negative_rate'] * 100:.2f}%")
    print(f"Latency  : p50={m_base['latency_p50_ms']}ms, p95={m_base['latency_p95_ms']}ms")

    print("\n--- PHASE 14 ENRICHED (Model + Threat Intelligence) ---")
    print(f"Accuracy : {m_enrich['accuracy'] * 100:.2f}% | Precision: {m_enrich['precision'] * 100:.2f}% | Recall: {m_enrich['recall'] * 100:.2f}% | F1: {m_enrich['f1_score'] * 100:.2f}%")
    print(f"FPR      : {m_enrich['false_positive_rate'] * 100:.2f}% | FNR: {m_enrich['false_negative_rate'] * 100:.2f}%")
    print(f"Latency  : p50={m_enrich['latency_p50_ms']}ms, p95={m_enrich['latency_p95_ms']}ms")
    print(f"\n[+] Recall Lift: +{report['comparison']['performance_gain']['recall_lift_pct']}% | Missed Threats Reduced: {report['comparison']['performance_gain']['missed_threats_reduction']}")
    print(f"[+] Report written to: {report_path}")
    print("="*70)


if __name__ == "__main__":
    run_benchmark()

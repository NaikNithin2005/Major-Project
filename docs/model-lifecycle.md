# Model Lifecycle, Quality Evaluation & Update Management

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Phase:** Phase 13 (Operational Maintenance & Observability Extension)  
**Security Classification:** Confidential / Machine Learning Operations (MLOps)  

---

## 1. Executive Summary & Philosophy

In the **Squish Shield** system, threat detection models operate primarily on Android mobile devices via ONNX Runtime to guarantee sub-millisecond edge protection and absolute privacy (zero raw SMS transmission). 

Post-release model governance requires strict operational controls:
1. **Never Automatically Retrain or Hot-Swap in Production:** Untrusted or unvalidated weights can introduce blind spots, degradation, or adversarial evasion.
2. **Cryptographic Verification Before Execution:** All ONNX model files must match the tamper-proof cryptographic SHA-256 hash recorded in `models/MODEL_MANIFEST.json`.
3. **Decoupled Application & Model Versioning:** Models have semantic versions (`v1.0.0`) independent of Android APK or backend builds.
4. **Offline Benchmark Evaluation vs. Production Telemetry:** Production prediction shifts do not imply drift without ground-truth labels.

---

## 2. Model Inventory & Registered Manifest

The production model registry is anchored by `models/MODEL_MANIFEST.json`. Each model artifact is cryptographically hashed and cataloged:

| Model ID | Target Domain | Model Type | Checksum (SHA-256) | Memory Size | Runtime Target |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `tinybert_sms` | Smishing (SMS Text) | Transformer (`TinyBERT-4L-312D`) | `b99e74bb7c47847c1f83f2195ebf8ea559a444101e14e1f76899479b183ea5dc` | ~58 MB | Edge ONNX / Mobile CPU |
| `xgboost_url` | Quishing (QR / URL) | Gradient Boosted Trees (16 Features) | `4d2c88d8b18ec6eafe8b22a6136d80ff524e75c6dcb419ff20379895c80db0dc` | ~1.3 MB | Edge ONNX / Mobile CPU |
| `isolation_forest_anomaly` | Unseen Anomaly Detection | Isolation Forest (16 Features) | `a49283f6f1f42191599a0e691ecdfb8f2d573673c4d920233ea85363f8bbff45` | ~440 KB | Edge ONNX / Mobile CPU |

### 2.1 Preprocessing & Schema Compatibility
- **TinyBERT Tokenization:** Custom regex-based WordPiece/BERT vocabulary (`models/tokenizer_vocab.txt`). Max sequence length: 128 tokens.
- **XGBoost Feature Vector:** 16 lexical and structural features (`FEATURE_NAMES`):
  `['url_length', 'domain_length', 'dot_count', 'hyphen_count', 'underline_count', 'slash_count', 'question_mark_count', 'equal_count', 'at_count', 'digit_ratio', 'is_ip', 'has_port', 'tld_risk_score', 'entropy', 'subdomain_depth', 'suspicious_keyword_present']`.
- **Anomaly Vector:** Identical 16-feature vector normalized to float32.

---

## 3. Verified Benchmark Evaluation Workflow

Model quality must be verified through a reproducible, script-driven benchmark against certified test splits (`models/data/sms_test.csv`, `url_test.csv`, `anomaly_test.csv`).

### 3.1 Execution Command
```bash
python scripts/evaluate_models_phase13.py
```
This script computes ground-truth classification metrics, confusion matrices, latency percentiles (p50, p95, p99), and outputs `docs/phase13_model_evaluation_report.json`.

### 3.2 Phase 13 Evaluation Benchmark Results (Certified)

```json
{
  "timestamp": "2026-10-14T07:18:00Z",
  "evaluation_environment": {
    "onnxruntime_version": "1.23.2",
    "cpu": "Intel/AMD Multi-Core CPU",
    "execution_provider": "CPUExecutionProvider"
  },
  "benchmarks": {
    "tinybert_sms": {
      "sample_size": 100,
      "accuracy": 0.9400,
      "f1_score": 0.8800,
      "precision": 0.8800,
      "recall": 0.8800,
      "fpr": 0.0400,
      "fnr": 0.1200,
      "latency_p50_ms": 4.77,
      "latency_p95_ms": 11.23,
      "latency_p99_ms": 16.51
    },
    "xgboost_url": {
      "sample_size": 200,
      "accuracy": 0.9950,
      "f1_score": 0.9877,
      "precision": 1.0000,
      "recall": 0.9756,
      "fpr": 0.0000,
      "fnr": 0.0244,
      "roc_auc": 0.9998,
      "latency_p50_ms": 0.02,
      "latency_p95_ms": 0.06,
      "latency_p99_ms": 0.11
    },
    "isolation_forest_anomaly": {
      "sample_size": 200,
      "anomaly_rate": 0.1550,
      "inliers_count": 169,
      "outliers_count": 31,
      "latency_p50_ms": 1.80,
      "latency_p95_ms": 3.42,
      "latency_p99_ms": 4.88
    }
  }
}
```

---

## 4. Model Drift & Degradation Monitoring

### 4.1 Ground Truth Distinctions
- **Telemetry is Not Ground Truth:** Backend telemetry receives aggregated threat events (e.g. `verdict: "SUSPICIOUS"`, `risk_score: 78`). Changes in the ratio of flagged URLs vs benign URLs reflect real-world campaign shifts (e.g. holiday smishing surge), NOT statistical model drift.
- **Never Infer Ground Truth from Scores:** Do not treat high-confidence model predictions as verified labels for automated retraining.

### 4.2 Approved Proxy Quality Indicators
SOC analysts monitor the following proxy signals for potential degradation:
1. **User False-Positive Reports:** Volume of user feedback tickets reporting benign bank SMS blocked.
   - *Threshold:* > 0.5% of active users reporting benign blocks triggers model audit.
2. **SOC Threat Triage Overrides:** Frequency at which SOC analysts override automated classification in the Admin Portal.
   - *Threshold:* Overrides exceeding 5% of triaged incidents within 7 days triggers dataset reassessment.
3. **Anomaly Spike Anomalies:** Sudden deviation in Isolation Forest outlier rate (> 30% or < 2%) indicates potential feature drift or schema divergence.

---

## 5. Controlled Model Update Pipeline

All model updates must strictly traverse the following staged gates:

```
+-----------------------------------------------------------------------------------+
|                        Controlled Model Release Lifecycle                         |
|                                                                                   |
|  [ Candidate Weights ]                                                            |
|          │                                                                        |
|          ▼                                                                        |
|  1. Integrity Verification ────> Compute SHA-256 hash & verify format validity    |
|          │                                                                        |
|          ▼                                                                        |
|  2. Schema Compatibility   ────> Verify 16-feature vector / tokenizer vocab       |
|          │                                                                        |
|          ▼                                                                        |
|  3. Benchmark Evaluation   ────> Must satisfy: F1 >= 0.88 (SMS), F1 >= 0.98 (URL) |
|          │                       Must satisfy: Latency p95 <= 25ms (Mobile CPU)   |
|          ▼                                                                        |
|  4. Adversarial & Security ────> Verify ONNX graph against parser exploits        |
|          │                                                                        |
|          ▼                                                                        |
|  5. Multi-Party Approval   ────> Lead Data Scientist & Security Officer Signoff   |
|          │                                                                        |
|          ▼                                                                        |
|  6. Versioned Release      ────> Update MODEL_MANIFEST.json & bump manifest rev   |
|          │                                                                        |
|          ▼                                                                        |
|  7. Canary / Mobile Rollout────> Staged Android APK release or signed OTA bundle  |
|          │                                                                        |
|          ▼                                                                        |
|  8. Rollback If Needed     ────> Fallback to previous manifest SHA-256 version    |
+-----------------------------------------------------------------------------------+
```

### 5.1 Rollback Protocol
If a newly deployed model causes elevated crash rates or false positives:
1. **Android App:** Revert APK deployment on Google Play Console or revert assets branch in build pipeline.
2. **Backend Services:** Point `MODEL_MANIFEST.json` to the previous known-good model commit and restart the service (`systemctl restart squish-shield-backend`).
3. **Integrity Fail-Safe:** If an artifact is missing or corrupted, the system fails CLOSED for administrative audits and logs a critical alert, preventing silent bypass.

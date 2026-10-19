# Controlled Model Improvement & Governance Workflow — Phase 14

## 1. Governance Principles

Machine learning models deployed to endpoint devices (Android ONNX Runtime) and backend analysis pipelines must undergo rigorous, audited validation before release. Automated retraining pipelines that overwrite production models without human and regression gates are strictly forbidden.

### Core Rules
1. **No Silent Production Overwrites:** Models in production remain locked until a candidate model completes the full validation lifecycle.
2. **Deterministic Evaluation:** Candidate models must be benchmarked against identical held-out test splits and emerging-threat datasets under identical preprocessing.
3. **Cryptographic Integrity:** Every deployed model artifact (`.onnx`, `.json`, `.pkl`) must have an immutable SHA-256 checksum recorded in `models/model_manifest.json`.
4. **Instant Rollback Capability:** Model versions must be backwards-compatible; the application must be able to switch to the prior verified release version within minutes if drift or false-positive spikes occur.

---

## 2. Multi-Stage Model Promotion Lifecycle

```text
[ Stage 1: Data Ingestion & Sanitization ]
   - Validate ground-truth labeling provenance
   - Strip PII, credentials, phone numbers
   - Deduplicate against historical training sets
               ↓
[ Stage 2: Candidate Model Training ]
   - Train on isolated training partition
   - Freeze random seeds and record hyperparameter configurations
   - Export candidate artifact (ONNX / Joblib)
               ↓
[ Stage 3: Automated Regression & Emerging Threat Benchmarking ]
   - Run test suite (`pytest backend/tests`)
   - Run emerging threats evaluation benchmark (`scripts/evaluate_phase14_emerging_threats.py`)
   - Measure Precision, Recall, F1, FPR, FNR, and Inference Latency
               ↓
[ Stage 4: Safety & Compatibility Gating ]
   - Verify ONNX runtime compatibility on mobile CPU targets
   - Confirm inference latency p95 < 50ms
   - Check zero degradation on existing baseline test suites
               ↓
[ Stage 5: SOC Administrator Approval ]
   - Review evaluation report signed off by Lead ML Engineer
   - Update `models/model_manifest.json` with new version, URI, and SHA-256
               ↓
[ Stage 6: Versioned Staged Rollout & Post-Release Monitoring ]
   - Canary deployment to 10% of devices
   - Telemetry drift monitoring (PSI / KS tests from Phase 13)
   - Full deployment upon 72 hours of stable operation
```

---

## 3. Acceptance Gates & Performance Thresholds

A candidate model will be **REJECTED** if it violates any of the following mandatory gates:

| Metric | Minimum Threshold | Target | Rationale |
| :--- | :--- | :--- | :--- |
| **Accuracy** | >= 95.0% | >= 98.0% | Overall classification correctness |
| **Precision** | >= 90.0% | >= 95.0% | Minimizes false accusations on legitimate user traffic |
| **Recall** | >= 90.0% | >= 95.0% | Ensures quishing/smishing attacks are reliably intercepted |
| **F1 Score** | >= 90.0% | >= 96.0% | Harmonized balance between precision and recall |
| **False-Positive Rate**| <= 2.0% | <= 1.0% | User alert fatigue prevention |
| **Inference Latency** | p95 <= 50ms | p95 <= 25ms | Real-time responsiveness on mobile devices |
| **Regression Count** | **0** | **0** | No regressions on previously passed benchmark sets |

---

## 4. Rollback and Disaster Recovery Procedure

If post-release monitoring detects anomalous false-positive rates or elevated inference latency:
1. **API Fallback:** SOC administrators invoke `POST /api/v1/admin/model-release/rollback` with the previous stable version string (e.g. `v1.0.0`).
2. **Manifest Reversion:** The backend server updates `models/model_manifest.json` to point to the previous model checksum and broadcast a priority model refresh signal.
3. **Client Fallback:** Android clients detect the updated manifest during periodic sync or receive a Firebase Cloud Message (FCM) invalidating the candidate model cache, falling back to bundled asset models immediately.
4. **Post-Mortem Investigation:** Retain telemetry logs in cold storage for forensic root-cause analysis.

# Phase 6 — Integration & Stabilization Report

**Project**: Squish Shield — Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System  
**Date**: October 7, 2026  
**Status**: COMPLETE  
**Architecture Layer**: Android Local Client (Offline-First Pipeline Stabilization)

---

## 1. Executive Summary
This milestone focused on an audit, end-to-end integration, and stabilization pass across Phase 6 of Squish Shield. Prior to this pass, the application suffered from disconnected AI runtime models, unreliable QR code detection in CameraX, placeholder/mock UI presentation sheets, and missing risk score propagation to the user interface. 

Through this stabilization pass:
1. All three trained Phase 5 ONNX AI models (`xgboost_url.onnx`, `isolation_forest.onnx`, and `tinybert_smishing.onnx`) along with the WordPiece vocabulary (`vocab.txt`) were formally packaged and executed on-device using Microsoft ONNX Runtime Android (`1.19.2`).
2. An on-device `BertWordPieceTokenizer` was developed to tokenize incoming SMS payloads into `input_ids` and `attention_mask` tensors for the 4-layer TinyBERT transformer.
3. The QR scanner pipeline was rebuilt: multi-binarizer fallback (Hybrid + Global Histogram + Inversion), center 70% ROI cropping, CameraX lifecycle stability, debounce control, and robust resource closing were implemented.
4. The QR and SMS UI was completely redesigned using Material 3 with cybernetic targeting overlays, dynamic risk score gauges (0–100), threat category badges, component-wise detection breakdowns, structured XAI evidence, and non-destructive action policies.
5. All 65 Android unit tests and all 57 Python AI tests pass with zero failures. The debug APK builds cleanly with embedded native ONNX runtime libraries.

---

## 2. Problems Found
During the initial audit of the runtime flow:
- **QR Scanner Reliability**: CameraX was frequently dropping frames, re-binding analyzers on Jetpack Compose recomposition, and ZXing `HybridBinarizer` failed on low contrast, inverted QR codes, and peripheral noise.
- **Disconnected AI Models**: The Android application was falling back to heuristic `DefaultUrlClassifier` and placeholder classification rather than executing the trained ONNX models located in `models/v2/`.
- **Missing Tokenizer**: No on-device tokenizer existed to convert text strings to Bert WordPiece token IDs required by `tinybert_smishing.onnx`.
- **UI Placeholders**: The QR scanning bottom sheet was a Phase 4 placeholder (`QrAnalysisResultBottomSheet`) displaying only whether a link missed HTTPS, without risk score, model breakdown, XAI explanations, or action badges.
- **Risk Score Propagation**: ViewModels exposed disparate properties (`extractedFeatures`, `scanResult`) causing null states, missing `RiskAssessment` propagation, and preventing the multi-modal risk engine from driving the UI.
- **SMS Pipeline Isolation**: SMS monitoring UI lacked a test/simulation mechanism for validation and did not provide full XAI detail bottom sheets for inspected SMS alerts.

---

## 3. Root Causes
1. **CameraX Rebinding Loop**: In `QrScannerScreen.kt`, `AndroidView` factory and update blocks were executing `cameraProvider.bindToLifecycle` on recomposition, creating race conditions and freezing the camera pipeline.
2. **Missing ONNX Runtime Android AAR**: `com.microsoft.onnxruntime:onnxruntime-android` was omitted from `app/build.gradle.kts` due to historical build concerns and network socket download constraints.
3. **Missing WordPiece Tokenizer in Android**: PyTorch / HuggingFace `AutoTokenizer` was used in Python training, but no Java/Kotlin tokenizer was ported to Android assets for the WordPiece `vocab.txt`.
4. **StateFlow Asynchronous Decoupling**: In `QrScannerViewModel`, derived StateFlows mapped from `_scanResult` were evaluated lazily or out-of-order in test/coroutine dispatchers.
5. **Phase 4 Domain Mismatch**: `ProcessQrCodeUseCase` converted non-URL payloads to schema prefixes rather than preserving the established `"TEXT_PAYLOAD"` contract expected by Phase 4 and database entities.

---

## 4. QR Scanner Fix
The scanner pipeline was updated with a multi-stage decoding architecture in `ZxingQrDecoder.kt` and `QrCodeAnalyzer.kt`:
- **ImageProxy Luminance Conversion**: Planar Y-plane extraction (`image.planes[0].buffer`) ensures zero-copy, high-speed grayscale luminance parsing.
- **Multi-Binarizer Strategy**:
  1. *Primary*: `HybridBinarizer` over center 70% ROI crop (removes peripheral table/screen noise).
  2. *Fallback 1*: `GlobalHistogramBinarizer` (handles uneven ambient illumination and low-contrast codes).
  3. *Fallback 2*: `HybridBinarizer` with inverted luminance (handles dark-mode / inverted QR codes).
  4. *Fallback 3*: `GlobalHistogramBinarizer` with inverted luminance.
  5. *Fallback 4*: Full-frame fallback if center ROI fails.
- **Rotation Handling**: ImageProxy rotation degrees (0°, 90°, 180°, 270°) are applied to luminance byte arrays before decoding.
- **Lifecycle & Debounce**: Debounce window of 1,200 ms prevents multi-triggering while allowing seamless rescan. All `ImageProxy` instances are closed inside `finally` blocks.

---

## 5. QR UI Fix
`QrScannerScreen.kt` was redesigned to resemble a high-tier cyber defense scanner:
- **Material 3 Surface**: Clean dark-mode canvas with edge-to-edge camera feed.
- **Targeting Overlay**: Glowing targeting corner brackets (green in idle/tracking, blue in processing) with an animated laser sweep indicator.
- **Camera Controls Overlay**: Glassmorphism HUD floating above preview with torch toggle (Flash On/Off), 1x / 2x zoom toggles, and direct gallery image picker button.
- **Permission State Rationale**: Informative card explaining local on-device camera processing with zero cloud transmission, with quick action to grant permission or import from gallery.

---

## 6. URL Analysis Fix
`UrlExtractor.kt` and `UrlFeatureExtractor.kt`:
- Normalizes URLs handling schemes (`http://`, `https://`, `www.`), paths, query strings, and fragments.
- Extracts the canonical 16 features in exact deterministic order:
  `[url_length, domain_length, dot_count, hyphen_count, underscore_count, slash_count, digit_count, is_https, is_ip_hostname, is_punycode, has_suspicious_tld, subdomain_count, has_unusual_port, has_suspicious_symbols, is_shortened_url, entropy]`.
- Replaces mock heuristics with strict RFC compliance and zero fabrication of external signals (no fake WHOIS or fake domain ages).

---

## 7. XGBoost Integration
Implemented in `OnnxUrlClassifier.kt`:
- **Model Asset**: `android/app/src/main/assets/models/xgboost_url.onnx` (Phase 5 v2 trained artifact).
- **Session**: Loaded with `OrtEnvironment.getEnvironment()` and `OrtSession`.
- **Input Tensor**: `float_input` with shape `[1, 16]` (`OnnxTensor.createTensor(env, FloatBuffer.wrap(featureArray), longArrayOf(1, 16))`).
- **Output Tensor**: `probabilities` with shape `[1, 2]`. Malicious probability extracted as `probabilities[0][1]`.
- **Fault Tolerance**: If ONNX runtime fails or model is missing, gracefully falls back to deterministic heuristic rules and marks `isModelAvailable = false`.

---

## 8. TinyBERT Integration
Implemented in `OnnxTinyBertSmishingClassifier.kt` and `BertWordPieceTokenizer.kt`:
- **Model Asset**: `android/app/src/main/assets/models/tinybert_smishing.onnx` (56.5 MB, 4-layer transformer).
- **Vocabulary**: `android/app/src/main/assets/models/vocab.txt` (30,522 tokens).
- **Tokenizer**: Custom WordPiece tokenizer handling text normalization, accent stripping, basic tokenization, subword prefixing (`##`), truncation to max length 128, and generation of `input_ids` and `attention_mask` tensors (type `int64`).
- **Inference**: Evaluates `[1, 128]` input tensors, extracts output `logits` (`[1, 2]`), and computes numerical softmax:
  $$\text{smishingProbability} = \frac{e^{z_1}}{e^{z_0} + e^{z_1}}$$
- Clamped and normalized to `0.0f .. 1.0f`.

---

## 9. Isolation Forest Integration
Integrated in `OnnxUrlClassifier.kt`:
- **Model Asset**: `android/app/src/main/assets/models/isolation_forest.onnx` (Phase 5 v2 trained artifact).
- **Input Tensor**: `float_input` with shape `[1, 16]` sharing the canonical URL feature vector.
- **Output Handling**: Extracts outlier decision score, normalizes anomaly index to `0.0f .. 100.0f`.
- **Contribution**: Fed directly into the multi-modal Risk Engine anomaly component.

---

## 10. Risk Engine Integration
Implemented in `RiskEngine.kt`:
- **Formula**:
  $$\text{Final Risk} = 0.35 \times \text{SMS} + 0.30 \times \text{URL} + 0.15 \times \text{QR} + 0.10 \times \text{Context} + 0.10 \times \text{Anomaly}$$
- **Normalization**: All model outputs and rule scores are pre-normalized to $0 \dots 100$ before aggregation.
- **Dynamic Reweighting**: If a component is absent (e.g., QR scan without SMS context), active weights are dynamically rescaled to ensure $100\%$ normalization.
- **Category Boundaries**:
  - `0 – 25`: **SAFE**
  - `26 – 50`: **LOW RISK**
  - `51 – 75`: **MEDIUM RISK**
  - `76 – 100`: **HIGH RISK**
  - Boundaries strictly verified ($25 \to \text{SAFE}$, $26 \to \text{LOW}$, $50 \to \text{LOW}$, $51 \to \text{MEDIUM}$, $75 \to \text{MEDIUM}$, $76 \to \text{HIGH}$).
- Clamping prevents `NaN`, `Infinity`, or scores $< 0$ or $> 100$.

---

## 11. Context Engine
Implemented in `ContextCorrelationEngine.kt`:
- Performs cross-modal verification between SMS sender/content and QR destination domain.
- Evaluates:
  - Known entity brand mapping (e.g., SBI, HDFC, ICICI, PayPal, Amazon, Netflix, Google).
  - Domain consistency check (detects spoofed domains like `sbi-kyc-verification.xyz` when sender claims to be SBI).
  - Channel mismatch and urgency mismatch.

---

## 12. Rule Engine
Implemented in `RuleEngine.kt`:
- Produces structured `RuleResult` instances with severity, score contribution, and specific evidence.
- Covers:
  - Missing HTTPS (`RULE_NO_HTTPS`)
  - IP Address Hostname (`RULE_IP_HOSTNAME`)
  - Punycode / IDN Homograph (`RULE_PUNYCODE`)
  - Non-standard Port (`RULE_UNUSUAL_PORT`)
  - Obfuscation / Suspicious Symbols (`RULE_SUSPICIOUS_SYMBOLS`)
  - URL Shorteners (`RULE_SHORTENED_URL`)
  - High-Abuse TLDs (`RULE_SUSPICIOUS_TLD`)
  - Excessive URL Length (`RULE_LONG_URL`)
  - SMS Urgency Language (`RULE_URGENCY_LANGUAGE`)
  - Financial Context (`RULE_BANKING_CONTEXT`)
  - Dangerous QR Execution Schemes (`RULE_SUSPICIOUS_QR_SCHEME`: `javascript:`, `data:`, `intent:`)

---

## 13. Explainable AI (XAI)
Implemented in `ExplainableAiEngine.kt`:
- Translates model probabilities, triggered rules, context mismatches, and anomalies into plain-English evidence bullets.
- Prioritizes critical signals first (top 3–6 reasons in UI).
- Guarantees zero fabricated reasons (every reason maps directly to an active signal).

---

## 14. SMS Pipeline Fix
- `ProcessIncomingSmsUseCase.kt`: Wired to `OnnxTinyBertSmishingClassifier`, `RuleEngine`, `ContextEngine`, and `RiskEngine`.
- Handles multipart SMS, normalized sender extraction, and safe logging.
- `SmsMonitoringScreen.kt` & `SmsMonitoringViewModel.kt`: Added simulation dialog allowing instant evaluation of safe vs. banking smishing test messages, complete with real-time detail bottom sheet.

---

## 15. UI State Management
- Replaced fragmented StateFlows with atomic structured objects (`QrAnalysisResult`, `SmsProcessResult`).
- Replaced lazy `.stateIn` flows with synchronous assignment in coroutine success handlers, preventing UI state loss during navigation, recomposition, or test advances.
- Results are retained until explicitly dismissed by user.

---

## 16. Threat History & Persistence
- Stored locally via Room database (`AegisDatabase`) across `QrAnalysisEntity`, `SmsAnalysisEntity`, and `ThreatHistoryEntity`.
- Privacy compliance: raw SMS text bodies are never persisted to long-term threat records.
- Records retain risk score, category, timestamp, sender/domain, recommended action, and structured reasons.

---

## 17. Tests
- **Android Unit Tests**:
  - `Phase3SmsPipelineTest`: 12/12 PASSED
  - `Phase4QrPipelineTest`: 8/8 PASSED
  - `Phase4QrViewModelTest`: 10/10 PASSED
  - `Phase6RiskEngineTest`: 35/35 PASSED
  - **Total Android Tests**: **65/65 PASSED** (`BUILD SUCCESSFUL in 21s`)
- **Python AI / ML Tests**:
  - `ai/tests/test_phase6_risk_engine.py`: 34/34 PASSED
  - `ai/tests/test_phase5_v2.py`: 10/10 PASSED
  - `ai/tests/test_tinybert.py`, `test_xgboost.py`, etc.: 13/13 PASSED
  - **Total Python Tests**: **57/57 PASSED**

---

## 18. Build Verification
- Command: `./gradlew assembleDebug`
- Output: `BUILD SUCCESSFUL in 18s` (38 actionable tasks executed)
- Native Libs Packaged: `libonnxruntime.so`, `libonnxruntime4j_jni.so`
- Assets Packaged: `xgboost_url.onnx`, `isolation_forest.onnx`, `tinybert_smishing.onnx`, `vocab.txt`
- APK Artifact: `android/app/build/outputs/apk/debug/app-debug.apk`

---

## 19. Files Changed
1. `android/app/build.gradle.kts`: Added `com.microsoft.onnxruntime:onnxruntime-android:1.19.2`.
2. `android/app/src/main/java/com/example/android/data/tokenizer/BertWordPieceTokenizer.kt`: Custom on-device WordPiece tokenizer.
3. `android/app/src/main/java/com/example/android/data/classifier/OnnxUrlClassifier.kt`: XGBoost v2 + Isolation Forest v2 ONNX execution.
4. `android/app/src/main/java/com/example/android/data/classifier/OnnxTinyBertSmishingClassifier.kt`: TinyBERT ONNX execution.
5. `android/app/src/main/java/com/example/android/domain/model/QrAnalysisResult.kt`: Added `rawPayload` getter and Phase 6 assessment fields.
6. `android/app/src/main/java/com/example/android/domain/model/ProcessedSms.kt`: Added `cleanedBody` getter for cross-module compatibility.
7. `android/app/src/main/java/com/example/android/domain/riskengine/RecommendedAction.kt`: Added `description` getter and `fromCategory` mappings.
8. `android/app/src/main/java/com/example/android/domain/riskengine/ScoreBreakdown.kt`: Default parameter values for transparent construction.
9. `android/app/src/main/java/com/example/android/domain/riskengine/RiskAssessment.kt`: Default parameter values for ergonomic fallbacks.
10. `android/app/src/main/java/com/example/android/domain/usecase/ProcessQrCodeUseCase.kt`: Integrated `OnnxUrlClassifier` and `RiskEngine`, restored `TEXT_PAYLOAD` domain and `"QUISHING"` category.
11. `android/app/src/main/java/com/example/android/domain/usecase/ProcessIncomingSmsUseCase.kt`: Integrated `OnnxTinyBertSmishingClassifier` and `RiskEngine`.
12. `android/app/src/main/java/com/example/android/presentation/qr/QrScannerViewModel.kt`: Restored synchronous MutableStateFlows for UI state stability.
13. `android/app/src/main/java/com/example/android/presentation/qr/QrScannerScreen.kt`: Redesigned CameraX viewfinder, HUD, targeting overlay, and cybernetic bottom sheet.
14. `android/app/src/main/java/com/example/android/presentation/sms/SmsMonitoringViewModel.kt`: Mapped `SmsAnalysisResult` to `SmsProcessResult` for bottom sheet.
15. `android/app/src/main/java/com/example/android/presentation/sms/SmsMonitoringScreen.kt`: Added SMS simulation dialog and detail inspection bottom sheet.
16. `android/app/src/main/java/com/example/android/di/AppContainer.kt`: Wired ONNX classifiers into app dependency container.
17. `Memory.md`: Appended Phase 6 Integration & Stabilization record.

---

## 20. Remaining Known Issues
- Very large, non-standard QR codes under severe glare may require the user to enable the built-in torch toggle or switch to 2x zoom for optimal CameraX focus.
- Regional SMS senders without documented brand registry entries default to generic context correlation without brand mismatch penalties.

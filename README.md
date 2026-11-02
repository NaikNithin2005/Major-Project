# Squish — Smart SMS & QR Code Protection System

[![Release v2.0.0](https://img.shields.io/badge/Release-v2.0.0--Modernized-blue.svg)](./Testing_Report.md)
[![Status: Production Ready](https://img.shields.io/badge/Status-Production%20Ready-success.svg)](./Testing_Report.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-API%2026--35%20(16KB%20Aligned)-3DDC84?logo=android&logoColor=white)](./android)
[![Next.js](https://img.shields.io/badge/Web-Next.js%2014-black?logo=next.js&logoColor=white)](./web)
[![AI/ML](https://img.shields.io/badge/AI%2FML-TinyBERT%20%2B%20XGBoost%20%2B%20IForest-005CED?logo=onnx&logoColor=white)](./ML_Model_Evaluation.md)

**Squish** is a consumer-friendly, privacy-first, on-device cybersecurity application built to protect everyday users from **Dangerous Text Messages (Smishing)** and **Unsafe QR Codes (Quishing)**.

Squish executes **100% on-device AI inference** using fine-tuned transformer and gradient-boosted decision trees (TinyBERT, XGBoost, and Isolation Forest) via Microsoft ONNX Runtime Mobile, backed by Cloud Firestore real-time synchronization, PostgreSQL telemetry storage, and a Next.js 14 Web Security Dashboard.

---

## 1. Key Modernizations & Core Capabilities

### 1.1 Everyday, User-Friendly Terminology
Replaced cybersecurity jargon with clear, consumer-friendly language across both Android and Web:
- *Smishing Detection* $\rightarrow$ **Detecting Dangerous Text Messages**
- *Quishing Detection* $\rightarrow$ **Checking QR Codes for Scams**
- *Malicious / Suspicious / Safe* $\rightarrow$ **Dangerous / Needs Attention / Looks Safe**
- *Protection Health Index* $\rightarrow$ **Real Database Activity Statistics**

### 1.2 Meaningful Activity Statistics Dashboard
Removed arbitrary percentage scores and security health indices. The modernized dashboard features:
- **SMS Activity:** Total Messages Scanned, Looks Safe, Needs Attention, Dangerous
- **QR Code Activity:** Total QR Codes Scanned, Looks Safe, Needs Attention, Dangerous
- **Recent Activity:** Recently Checked Messages, Recently Scanned QR Codes, Important Safety Alerts, Real-Time Cloud Synchronization Status

### 1.3 Financial Message Intelligence & Zero Banking False Positives
Integrated a dedicated `FinancialMessageClassifier` combining sender parsing, currency regexes, OTP patterns, and transactional syntax:
- Legitimate bank alerts (SBI, HDFC, ICICI, Axis, PNB), debit/credit alerts, salary credits, and OTP verification messages are never flagged as scams.
- Decoupled detection from quarantine: legitimate transactional notifications are never automatically blocked.

### 1.4 Detailed 6-Section Message Breakdown
Every scanned message and QR code provides an in-depth, human-readable breakdown:
1. **Message Information:** Original text, sender, date, category, verdict.
2. **Detection Result:** Clear badge (Looks Safe, Needs Attention, Dangerous).
3. **Why Was This Message Flagged?** Everyday explanation of exact phrasing, urgency, or links.
4. **Detailed Evaluation Matrix:** Structural breakdown (Urgency language, Link safety, Sender legitimacy, Action safety).
5. **Contextual Recommendations:** Numbered, safe next steps.
6. **Reversible Actions:** Mark Safe / False Positive, Block Sender, Copy Text, Ask AI Assistant.

### 1.5 Personalized Learning Engine & Reversible Feedback
- Users can correct classifications (`FALSE_POSITIVE`, `CONFIRMED_THREAT`) or manage a **Trusted Senders Directory**.
- **Deterministic Guardrails:** User feedback can never suppress messages containing OTP demands, credentials harvesting, or unverified external links.
- **Reversibility:** Users can undo previous feedback at any time, restoring original model predictions.

### 1.6 Official Android Default SMS Handler Role
- **Dual Mode:** Operates either as a **Passive Shield** (monitoring via `SMS_RECEIVED`) or as the **Default SMS Handler** (intercepting via `SMS_DELIVER`).
- Configured with all 4 mandatory Android components: `SmsReceiver`, `MmsReceiver`, `HeadlessSmsSendService`, and `MainActivity` (`SENDTO` scheme).
- Fully compliant with Google Play Store SMS permission policies.

### 1.7 Persistent Settings & Lock-Screen Privacy
- Every toggle (`realtimeSmsProtection`, `qrScannerEnabled`, `showNotificationPreviews`, `aiModelEnabled`, etc.) is persistently stored in `PreferencesDataStore` and synced to Cloud Firestore.
- Lock-screen notifications respect `showNotificationPreviews`: when disabled, message snippets and senders are masked to protect personal privacy.

### 1.8 Permanent Cloud History & Reinstallation Restoration
- When the app is reinstalled or signed into on a new device, `restoreUserCloudHistory` automatically retrieves user detection events and settings from Firestore (`users/{uid}/threats/`) and repopulates the local Room database.
- Strictly isolated multi-tenant architecture guarded by Firebase Security Rules.

---

## 2. Architecture & Pipeline

```
                               ┌────────────────────────────────┐
                               │     Incoming SMS / QR Scan     │
                               └───────────────┬────────────────┘
                                               │
                       ┌───────────────────────┴───────────────────────┐
                       ▼                                               ▼
            ┌──────────────────────┐                       ┌──────────────────────┐
            │   SMS Ingestion      │                       │   QR Ingestion       │
            │  (BroadcastReceiver) │                       │  (CameraX / MLKit)   │
            └──────────┬───────────┘                       └──────────┬───────────┘
                       │                                               │
                       ▼                                               ▼
            ┌──────────────────────┐                       ┌──────────────────────┐
            │ Financial Classifier │                       │ URL Feature Extract  │
            │  (OTP / Bank Rules)  │                       │  (18 Lexical & TLD)  │
            └──────────┬───────────┘                       └──────────┬───────────┘
                       │                                               │
                       ▼                                               ▼
            ┌──────────────────────┐                       ┌──────────────────────┐
            │   TinyBERT v2 ONNX   │                       │   XGBoost v2 ONNX    │
            │  (Smishing Engine)   │                       │   (URL Classifier)   │
            └──────────┬───────────┘                       └──────────┬───────────┘
                       │                                               │
                       └───────────────────────┬───────────────────────┘
                                               ▼
                                ┌──────────────────────────────┐
                                │     Isolation Forest v2      │
                                │   (Anomaly Scoring Engine)   │
                                └──────────────┬───────────────┘
                                               ▼
                                ┌──────────────────────────────┐
                                │  PersonalizedLearningEngine  │
                                │    (Guardrails & Feedback)   │
                                └──────────────┬───────────────┘
                                               ▼
                                ┌──────────────────────────────┐
                                │ DetailedMessageAnalysisEngine│
                                │  (6-Section XAI Explanations)│
                                └──────────────┬───────────────┘
                                               │
                                ┌──────────────┴──────────────┐
                                ▼                             ▼
                         Local Room DB                 Cloud Firestore
                      (Offline Resilience)          (Permanent Cross-Sync)
```

---

## 3. Comprehensive Documentation

- [ML Model Evaluation & Regularization Report](./ML_Model_Evaluation.md)
- [Personalized Learning & User Feedback Architecture](./Feedback_Learning.md)
- [Android Default SMS Handler Implementation Guide](./SMS_Handler_Implementation.md)
- [Master Testing & Quality Assurance Report](./Testing_Report.md)

---

## 4. Test Verification Summary

- **Android Unit Test Suite:** 26 tasks executed, **100% passed** in 30s (`./gradlew testDebugUnitTest`).
- **Python ML Test Suite:** 71 test cases, **100% passed** in 29.59s (`pytest ai/tests/`).
- **Web Next.js Build:** 23 production routes, **100% compiled & optimized**, 0 TypeScript errors (`npm run build`).

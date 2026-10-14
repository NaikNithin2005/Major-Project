# Release Notes — v1.0.0

**Project Title:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Release Version:** `v1.0.0` (Android VersionCode `1`)  
**Release Date:** October 2026  
**Status:** `READY WITH DOCUMENTED LIMITATIONS`

---

## 1. Executive Summary

Version 1.0.0 marks the comprehensive major release of the **Squish Shield** system, delivering an end-to-end, privacy-preserving cybersecurity platform designed to protect users against Smishing (SMS phishing) and Quishing (malicious QR-code phishing). The solution combines edge on-device inference via ONNX Runtime on Android with a centralized telemetry backend built on FastAPI and a Security Operations Center (SOC) dashboard built on Next.js 14.

---

## 2. Release Artifacts & Checksums

### A. Android Release Application
- **Artifact Path:** `android/app/build/outputs/apk/release/app-release.apk`
- **File Size:** `147,066,832 bytes` (~140.25 MB)
- **SHA-256 Checksum:** `D182FC881C24C3BA6F8D4919DFD34B7FD80D4A53092C4BE20AACCBF3F288B931`
- **Target SDK:** API 35 (Android 15) | **Min SDK:** API 26 (Android 8.0 Oreo)
- **Signing Profile:** Configured for reproducible evaluation; uses evaluation release signing.

### B. Machine Learning Model Artifacts
All models are verified, validated with inference contract tests, and packaged into `android/app/src/main/assets/models/`:
- **TinyBERT Smishing v2 (`tinybert_smishing.onnx`):**
  - **Size:** `59,003,792 bytes` (~56.27 MB)
  - **SHA-256:** `F019E26FBC09299B77FFCF34FD22BE5EEEC1099684D0612EC9504B6EE3EB7DE6`
  - **Role:** Deep contextual NLP classification of incoming SMS text.
- **XGBoost Quishing v2 (`xgboost_quishing.onnx`):**
  - **Size:** `149,436 bytes` (~145.93 KB)
  - **SHA-256:** `292945D187515F05AE96A3008E16024D97022CD9DE77A792556E2CF34907D8B5`
  - **Role:** Lexical and structural URL feature classification.
- **Isolation Forest Zero-Day v2 (`isolation_forest.onnx`):**
  - **Size:** `1,677,951 bytes` (~1.60 MB)
  - **SHA-256:** `86B6D1B221ED83F15F056B4E9981B85F18E52A77A14CF0982E2E6BEFE8E730B2`
  - **Role:** Unsupervised anomaly detection for novel zero-day attacks.

### C. Backend Container Image
- **Dockerfile:** `backend/Dockerfile` (Multi-worker production configuration, non-root `appuser`, health check probes).
- **Probes:** `/health/live` (liveness) and `/health/ready` (readiness with DB & auth status).

### D. Web Admin Dashboard Container Image
- **Dockerfile:** `web/Dockerfile` (Multi-stage Next.js production standalone runner with non-root `nextjs` user).

---

## 3. Key Implemented Features

### 📱 Android Application (Mobile Edge)
- **Clean Architecture & Jetpack Compose:** Modular domain, data, and presentation layers adhering to Material 3 guidelines.
- **On-Device Privacy Engine:** Complete inference executes locally on Android via ONNX Runtime without transmitting raw SMS content or personal messages to remote servers.
- **Real-Time SMS Monitor:** BroadcastReceiver intercepts incoming SMS messages, passes text through the tokenization pipeline, and computes a Smishing probability in under 60 milliseconds.
- **Camera QR Scanner:** CameraX / ML Kit / ZXing barcode scanner that extracts embedded URLs, computes 22 lexical features, runs XGBoost + Isolation Forest, and alerts the user prior to navigation.
- **Defensive User Actions:** Interactive notifications offering immediate safety measures: Copy Sanitized Link, Block Sender, and View Explainability Breakdown.
- **Offline Synchronization:** Local Room database records detections and audit trails; WorkManager synchronizes anonymized threat metadata when network connectivity is established.

### ⚙️ FastAPI Telemetry Backend
- **RESTful Endpoints:** Full API for threat telemetry intake, user registration, device tracking, model metadata distribution, and administrative queries.
- **Role-Based Access Control (RBAC):** Strict enforcement separating `USER` and `ADMIN` operations. Non-admin tokens are strictly blocked from `/api/v1/admin/*`.
- **Database Connection Pooling:** SQLAlchemy 2.0 configuration with connection pooling (`QueuePool`), health monitoring, and graceful SQLite in-memory fallback.
- **Alembic Database Migrations:** Version-controlled database schema migrations with automated upgrade scripts (Revisions 001 through 003).
- **Hardened Security Middleware:** OWASP security headers, request body size limits (1 MB default), in-memory IP rate limiting, and structured logging.

### 🌐 Admin Web Dashboard (SOC Portal)
- **Next.js 14 App Router:** Responsive, accessible dashboard with 21 production routes.
- **Real-Time Threat Analytics:** Incident heatmaps, severity distributions (Quishing vs. Smishing), and zero-day anomaly indicators.
- **Threat Event Triage:** Detailed threat inspection including risk scores, contributing heuristics, anonymized URLs, and false-positive feedback processing.
- **User & Device Management:** Administrative visibility into registered devices, active sync statuses, and role privileges.
- **Audit Logs:** Tamper-evident logging of security events and administrative actions.

---

## 4. Security Hardening & Improvements

1. **Strict Production Auth Enforcement:** Unverified token claims and mock authentication (`ALLOW_MOCK_AUTH`) are hard-coded to be rejected whenever `ENVIRONMENT=production`.
2. **Container Security:** Both backend and web Docker containers run as unprivileged users (`appuser` UID 10001, `nextjs` UID 1001) rather than root.
3. **Data Minimization:** Raw SMS message bodies are strictly confined to local Android storage. The synchronization payload contains only anonymized threat hashes, categories, scores, and defensive actions taken.
4. **Resilient Liveness/Readiness Probes:** Standard Kubernetes-compliant probes (`/health/live`, `/health/ready`) enable production orchestration without leaking secrets.

---

## 5. Database Schema & Migration Status

- **Migration Revision 001:** Initial schema creation (`users`, `devices`, `threat_events`, `feedback`, `model_info`, `audit_logs`).
- **Migration Revision 002:** Added `threat_intel` table for reputation feeds and IOC tracking.
- **Migration Revision 003:** Added `user_action` and `phone_number` fields to `threat_events` for telemetry synchronization parity.
- **Current Head:** `003_add_user_action_and_phone`

---

## 6. Known Limitations & Environmental Considerations

1. **Android Evaluation Signing:** The generated release APK uses an evaluation release signing profile for reproducible testing by evaluators. For commercial Google Play store distribution, a private keystore managed via environment secrets must be configured as documented in the Deployment Guide.
2. **Hardware Camera in Emulators:** QR scanning requires a physical camera sensor or an emulator configured with virtual scene camera emulation.
3. **Database Concurrency in Fallback Mode:** When executed without an external PostgreSQL service, the backend defaults to SQLite in-memory mode. While compliant with read/write speed requirements, high-concurrency environments must use PostgreSQL with connection pooling as provided in `docker-compose.yml`.

---

## 7. Verification Summary

| Validation Suite | Target | Executed Tests | Result |
| :--- | :--- | :--- | :--- |
| Android Release Build | `android/` | `./gradlew assembleRelease` | **PASSED (45 tasks)** |
| Android Unit Tests | `android/` | `./gradlew testDebugUnitTest` | **PASSED** |
| Backend Unit & Integration | `backend/` | `pytest backend/tests -v` | **PASSED (91 passed, 6 skipped)** |
| Web Production Build | `web/` | `npm run build` | **PASSED (21/21 routes, 0 errors)** |
| AI Inference Contract | `ai/` | Contract test suite | **PASSED** |
| Alembic Head Alignment | `backend/` | `alembic heads` | **PASSED (003 head)** |

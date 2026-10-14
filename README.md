# Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

[![Release v1.0.0](https://img.shields.io/badge/Release-v1.0.0-blue.svg)](./docs/RELEASE_NOTES_v1.0.0.md)
[![Status: Ready](https://img.shields.io/badge/Status-Ready%20with%20Documented%20Limitations-success.svg)](./docs/RELEASE_NOTES_v1.0.0.md)
[![License: Academic/MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)
[![Android](https://img.shields.io/badge/Android-API%2026--35-3DDC84?logo=android&logoColor=white)](./android)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688?logo=fastapi&logoColor=white)](./backend)
[![Next.js](https://img.shields.io/badge/Web-Next.js%2014-black?logo=next.js&logoColor=white)](./web)
[![ONNX Runtime](https://img.shields.io/badge/AI%2FML-ONNX%20Runtime-005CED?logo=onnx&logoColor=white)](./models)

An enterprise-grade, privacy-first cybersecurity platform designed to detect and prevent **Smishing** (SMS phishing) and **Quishing** (malicious QR-code phishing) in real time. The solution executes **100% on-device AI inference** using fine-tuned transformer and gradient boosted models (TinyBERT, XGBoost, and Isolation Forest) via Microsoft ONNX Runtime Mobile, backed by a centralized FastAPI threat telemetry service and a React/Next.js Web Admin Security Dashboard.

---

## 1. Project Release Status

**Current Milestone:** `Phase 12 — Release, Deployment & Documentation Complete`  
**Official Release:** `v1.0.0` (Android VersionCode `1`)  
**Readiness Classification:** `READY WITH DOCUMENTED LIMITATIONS`

All 12 development phases have been implemented, tested, verified, and packaged into reproducible release artifacts:
- **Android Release APK:** `android/app/build/outputs/apk/release/app-release.apk` (140.25 MB, SHA-256: `D182FC88...`)
- **Backend Telemetry Service:** FastAPI with Alembic migrations (`003_add_user_action_and_phone`), multi-worker production Dockerfile, `/health/live` and `/health/ready` probes.
- **Web Admin Dashboard:** Next.js 14 App Router (21 production routes compiled, 0 TypeScript errors).
- **AI/ML Model Suite:** TinyBERT v2 (Smishing), XGBoost v2 (Quishing), and Isolation Forest v2 (Zero-Day Anomaly Detection).

---

## 2. Key Features

- **Privacy-First Edge Processing:** Incoming SMS messages are analyzed in-memory on the Android device. Personal SMS message content is **never** transmitted to remote servers.
- **Real-Time Smishing Interception:** Android `BroadcastReceiver` intercepts text messages, feeds normalized tokens to on-device TinyBERT, and scores threat probabilities in under 60 milliseconds.
- **Pre-Navigation Quishing Defense:** Integrated CameraX / Google ML Kit scanner decodes QR codes, extracts 22 structural/lexical URL features, runs XGBoost + Isolation Forest, and warns users before opening links.
- **Multi-Stage Explainable AI (XAI):** Clear risk scores (0–100) paired with human-readable threat indicators (*Urgency language detected*, *Suspicious TLD*, *Look-alike domain*).
- **Interactive Mitigation Actions:** In-app and notification action triggers: Block Sender, Copy Sanitized Link, and Report False Positive.
- **Resilient Offline Architecture:** Complete threat detection operates without cellular data or Wi-Fi; anonymized metadata synchronizes via Android WorkManager when connectivity returns.
- **Security Operations Center (SOC) Portal:** Web dashboard featuring real-time telemetry heatmaps, threat triage modals, device auditing, and role management.

---

## 3. High-Level Architecture

```
                       ┌──────────────────────────────┐
                       │          END USER            │
                       │     Android Mobile Client    │
                       └──────────────┬───────────────┘
                                      │
                   ┌──────────────────┴──────────────────┐
                   │ (Edge Ingestion)                    │ (Edge Ingestion)
              Incoming SMS                          QR Scanner
                   │                                     │
                   ▼                                     ▼
           ┌───────────────┐                     ┌───────────────┐
           │ SMS Receiver  │                     │ CameraX/MLKit │
           └───────┬───────┘                     └───────┬───────┘
                   │                                     │
                   ▼                                     ▼
           ┌───────────────┐                     ┌───────────────┐
           │ BertTokenizer │                     │ 22 Extractor  │
           └───────┬───────┘                     └───────┬───────┘
                   │                                     │
                   ▼                                     ▼
           ┌───────────────┐                     ┌───────────────┐
           │   TinyBERT    │                     │    XGBoost    │
           │ Smishing NLP  │                     │ URL/Quishing  │
           └───────┬───────┘                     └───────┬───────┘
                   │                                     │
                   └──────────────────┬──────────────────┘
                                      ▼
                        ┌───────────────────────────┐
                        │    Isolation Forest v2    │
                        │ (Zero-Day Anomaly Engine) │
                        └─────────────┬─────────────┘
                                      ▼
                        ┌───────────────────────────┐
                        │    Risk Scoring Engine    │
                        │ & Explainability (XAI)    │
                        └─────────────┬─────────────┘
                                      │
                       ┌──────────────┴──────────────┐
                       ▼                             ▼
                System UI Alert                Room Local DB
              (Defensive Actions)            (Encrypted SQLite)
                                                     │
                                           Async WorkManager Sync
                                           (Anonymized Metadata)
                                                     │
                                                     ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          Backend API Service (FastAPI)                           │
│                                                                                  │
│   Firebase Auth Verification ──> Role-Based Access Control (USER vs ADMIN)       │
│                                           │                                      │
│                     SQLAlchemy 2.0 Pool (QueuePool)                              │
│                                           │                                      │
│                                           ▼                                      │
│                           PostgreSQL Database (Alembic 003)                      │
└───────────────────────────────────────────┬──────────────────────────────────────┘
                                            │ (Authenticated REST)
                                            ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         Web Admin Dashboard (Next.js 14)                         │
│                                                                                  │
│         SOC Threat Map, Telemetry Triage, Device Audit, User Control             │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Repository Structure

```text
Major_project/
├── android/            # Native Kotlin Android Studio application (Clean Architecture)
├── backend/            # FastAPI Python 3.11 telemetry & REST API service
├── web/                # Next.js 14 / TypeScript Security Operations Center dashboard
├── ai/                 # AI/ML model training, evaluation, and ONNX export pipelines
├── models/             # Production ONNX model artifacts & MODEL_MANIFEST.json
├── docs/               # Complete technical guides, API specs, and phase reports
├── tests/              # System-wide and cross-subsystem integration tests
├── scripts/            # Verification, packaging, and developer automation scripts
├── Datasets/           # Training and evaluation datasets for SMS and URL models
├── docker-compose.yml  # Multi-container orchestration (PostgreSQL, Backend, Web)
├── .env.example        # Master environment variables template
└── Memory.md           # Persistent project engineering memory and decision log
```

---

## 5. Technology Stack

| Layer | Subsystem | Technologies & Libraries |
| :--- | :--- | :--- |
| **Mobile Client** | Android (`android/`) | Kotlin 2.0, Jetpack Compose, Material 3, ONNX Runtime Mobile 1.17, CameraX, ML Kit, Room DB, WorkManager, Hilt/DI |
| **Backend API** | FastAPI (`backend/`) | Python 3.11, FastAPI, Pydantic v2, SQLAlchemy 2.0, Alembic, Firebase Admin SDK, Uvicorn, Docker |
| **Database** | PostgreSQL | PostgreSQL 15+, Connection Pooling (`QueuePool`), SQLite (Local Test Fallback) |
| **Web Dashboard** | SOC Portal (`web/`) | Next.js 14 (App Router), React 18, TypeScript, Tailwind CSS, Lucide Icons, Firebase Web SDK |
| **Machine Learning** | AI Engine (`ai/`, `models/`) | PyTorch, HuggingFace Transformers (TinyBERT), XGBoost, Scikit-learn (Isolation Forest), ONNX, ONNX-ML |

---

## 6. Development & Deployment Quick Start

### 6.1 Prerequisites
- **JDK:** 17+ (`java -version`)
- **Android SDK:** API 35 (Android 15) with Build-Tools 35.0.0
- **Python:** 3.11+ with `pip` and `venv`
- **Node.js:** 18.x or 20.x LTS with `npm`
- **Docker:** Docker Engine 24+ and Docker Compose v2

### 6.2 Environment Configuration
Copy all environment placeholder templates:
```bash
cp .env.example .env
cp backend/.env.example backend/.env
cp web/.env.example web/.env.local
```
> **SECURITY NOTICE:** Never commit real secrets, private keystores, or service account files to Git.

### 6.3 Option A: Launch Full System via Docker Compose
To run PostgreSQL, the FastAPI backend, and the Next.js admin dashboard simultaneously:
```bash
docker compose up -d --build
```
- **Backend API:** `http://localhost:8000` (Docs: `http://localhost:8000/docs`)
- **Web Dashboard:** `http://localhost:3000`
- **Liveness Probe:** `http://localhost:8000/health/live`
- **Readiness Probe:** `http://localhost:8000/health/ready`

### 6.4 Option B: Local Component Setup

#### 📱 Android Application
```bash
cd android
# Compile debug APK:
./gradlew assembleDebug

# Compile release APK:
./gradlew assembleRelease
```
Install on a connected Android phone:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

#### ⚙️ FastAPI Backend
```bash
cd backend
python -m venv venv
# Linux/macOS: source venv/bin/activate | Windows: .\venv\Scripts\Activate.ps1
pip install -r requirements.txt
alembic upgrade head
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### 🌐 Web Admin Dashboard
```bash
cd web
npm install
npm run dev
# Open http://localhost:3000
```

---

## 7. Automated Testing & Verification

Run the automated validation suites across each subsystem:

```bash
# 1. Android Unit Tests
cd android && ./gradlew testDebugUnitTest

# 2. Backend Pytest Suite (101 passed, 6 skipped)
cd backend && pytest tests/ -v

# 3. Database Migration Alignment Check
cd backend && alembic heads

# 4. Web Production Type-Check & Build (21/21 routes)
cd web && npm run build

# 5. AI Inference & Model Checksum Verification
python scripts/evaluate_models_phase13.py
```

---

## 8. Complete Documentation Library

Comprehensive technical documentation is available under [`docs/`](./docs/README.md):

| Document | Description |
| :--- | :--- |
| [**Release Notes v1.0.0**](./docs/RELEASE_NOTES_v1.0.0.md) | Official release notes, file checksums, changelog, and upgrade path. |
| [**Architecture Guide**](./docs/ARCHITECTURE.md) | Technical architecture, edge-to-cloud protocols, and trust boundaries. |
| [**API Reference**](./docs/API_REFERENCE.md) | Complete OpenAPI/REST endpoint specifications, schemas, and RBAC roles. |
| [**Database Guide**](./docs/DATABASE_GUIDE.md) | PostgreSQL schemas, relationships, Alembic migrations, and backup/restore. |
| [**Model Cards & Versioning**](./docs/MODEL_CARDS_AND_VERSIONING.md) | TinyBERT, XGBoost, and Isolation Forest architectures, metrics, and manifest. |
| [**Security & Privacy Guide**](./docs/SECURITY_AND_PRIVACY_GUIDE.md) | Data minimization principles, RBAC authorization, and OWASP defenses. |
| [**Testing Guide**](./docs/TESTING_GUIDE.md) | Reproducible testing commands across Android, Backend, Web, and AI. |
| [**User Guide**](./docs/USER_GUIDE.md) | End-user mobile app guide for SMS protection, QR scanning, and mitigation. |
| [**Admin Guide**](./docs/ADMIN_GUIDE.md) | SOC portal guide for threat triage, user management, and audit inspection. |
| [**Deployment Guide**](./docs/DEPLOYMENT_AND_INSTALLATION_GUIDE.md) | Reproducible step-by-step setup for local, test, and production environments. |
| [**Monitoring & Telemetry**](./docs/monitoring.md) | Observability, health/readiness probes, correlation IDs & SOC admin telemetry. |
| [**Model Lifecycle & MLOps**](./docs/model-lifecycle.md) | MLOps protocol, cryptographic model manifest, benchmark scripts & updates. |
| [**Incident Response Plan**](./docs/incident-response.md) | 11 Scenario runbooks (Auth bypass, data leak, model tampering, outages). |
| [**Backup & Disaster Recovery**](./docs/backup-recovery.md) | PostgreSQL / SQLite backup schedules, integrity checks & restore protocols. |
| [**Maintenance Runbook**](./docs/maintenance-runbook.md) | Maintenance schedules, index tuning, connection pooling & security audits. |
| [**Operational Limitations**](./docs/operational-limitations.md) | Hardware, edge memory, battery, concurrency bounds & data minimization. |

---

## 9. License & Authors

- **Course:** 6th Semester Major Project
- **Authors:** Project Development & Engineering Team
- **License:** MIT License — Open for academic and research evaluation.

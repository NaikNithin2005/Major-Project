# Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System

A privacy-first, on-device cybersecurity platform designed to detect and prevent **Smishing** (SMS phishing) and **Quishing** (malicious QR-code phishing) in real time using edge machine learning models (TinyBERT, XGBoost, and Isolation Forest), backed by a centralized FastAPI threat telemetry service and a React/Next.js Web Admin Security Dashboard.

---

## 1. Current Project Phase
**Current Status:** `Phase 0 — Project Foundation (Partially Complete / In Progress)`
- Scaffold created for multi-component repository (`android/`, `backend/`, `ai/`, `web/`, `docs/`, `scripts/`, `tests/`).
- Repository configuration, `.gitignore`, `.env.example`, `docker-compose.yml`, and CI workflows initialized.
- Android project preserved within native Android Studio Gradle structure.
- *Note:* Production detection engine, Room local DB, Firebase Auth, PostgreSQL schema, and Admin Web APIs are reserved for Phase 1 through Phase 12 as defined in [`Phases.md`](./Phases.md).

---

## 2. Major Components & Technology Stack

| Component | Framework / Tech Stack | Primary Responsibilities |
| :--- | :--- | :--- |
| **Android Application** | Kotlin, Jetpack Compose, Material 3, Clean Architecture + MVVM | On-device SMS monitoring, QR scanner, local ONNX inference, UI alerts & history |
| **Backend Service** | Python 3.11, FastAPI, Pydantic, SQLAlchemy, PostgreSQL | Threat telemetry, authentication verification, model metadata, admin REST APIs |
| **AI/ML Engine** | Python, TinyBERT, XGBoost, Isolation Forest, ONNX Runtime | Dataset processing, model training, zero-day anomaly detection, ONNX export |
| **Web Admin Dashboard** | Next.js 14, React 18, TypeScript, Tailwind CSS | Security Operations Center (SOC) dashboard, system-wide threat analytics |

---

## 3. Architecture Overview

```text
Android Mobile Client (On-Device Local Inference)
   ├── SMS Receiver ──> Preprocessing ──> TinyBERT Model (Smishing)
   └── Camera/ZXing ──> URL Extract  ──> XGBoost Model  (Quishing)
            │
            └── Context & Risk Scoring Engine ──> Local Room DB & UI Alert
                     │ (Async / Optional Sync)
                     ▼
Backend API (FastAPI) ──> PostgreSQL Centralized Threat DB
                     ▲
                     │ (REST / RBAC Auth)
Web Admin Dashboard (Next.js / React)
```

---

## 4. Repository Structure

```text
Major_project/
├── android/            # Android Studio Kotlin application project
├── backend/            # FastAPI REST backend service
├── ai/                 # AI/ML detection training, evaluation & ONNX deployment
├── web/                # Next.js Web Admin Security Dashboard
├── docs/               # Project documentation & coding conventions
├── scripts/            # Build & developer utility scripts
├── tests/              # System integration tests
├── Datasets/           # CSV datasets for SMS and URL threat training
├── Doc/                # Project design reports and architecture diagrams
├── PRD.md              # Product Requirements Document
├── Architecture.md     # System Architecture & Technical Specifications
├── Rules.md            # Development Guidelines & Security/AI Boundaries
├── Phases.md           # Implementation Roadmap & Phase Milestones
├── Memory.md           # Persistent Project Development Memory Log
├── Design.md           # Visual UX & Component Token Rules
├── docker-compose.yml  # Docker multi-container environment config
├── .env.example        # Environment variable placeholder template
└── .gitignore          # Repository git ignore rules
```

---

## 5. Development Prerequisites

- **Android**: Android Studio Jellyfish/Ladybug (or newer), JDK 17+, Android SDK 35 (Android 15)
- **Backend & AI**: Python 3.11+, `pip`, `venv`
- **Web Admin**: Node.js 18.x+, `npm`
- **Containers**: Docker & Docker Compose

---

## 6. How to Open and Run Components

### 📱 Android Application (`android/`)
1. Open Android Studio.
2. Select **Open** and select the `android/` directory (do not alter module layout).
3. Let Gradle sync project dependencies.
4. Run via command line:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

### ⚙️ Backend API (`backend/`)
1. Navigate to directory:
   ```bash
   cd backend
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   pip install -r requirements.txt
   ```
2. Run server:
   ```bash
   uvicorn app.main:app --reload --port 8000
   ```
3. Interactive API docs: `http://localhost:8000/docs`

### 🧠 AI Engine (`ai/`)
1. Navigate to directory:
   ```bash
   cd ai
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   pip install -r requirements.txt
   ```
2. Run AI package tests:
   ```bash
   pytest
   ```

### 🌐 Web Admin Dashboard (`web/`)
1. Navigate to directory:
   ```bash
   cd web
   npm install
   ```
2. Start development server:
   ```bash
   npm run dev
   ```
3. Dashboard URL: `http://localhost:3000`

---

## 7. Environment Configuration
Copy `.env.example` to `.env` in root and component directories:
```bash
cp .env.example .env
```
> **IMPORTANT SECURITY RULE:** Never commit real secrets, API keys, or private database credentials to Git. Use placeholders only.

---

## 8. Development Workflow & Testing

### Verification Commands
- **Phase 0 Checklist Script**:
  ```bash
  python scripts/verify_phase0.py
  ```
- **Backend Unit Tests**:
  ```bash
  cd backend && pytest
  ```
- **AI Unit Tests**:
  ```bash
  cd ai && pytest
  ```
- **Web Type & Build Check**:
  ```bash
  cd web && npm run build
  ```

For detailed coding standards, commit conventions, and branch management, refer to [`docs/CONVENTIONS.md`](./docs/CONVENTIONS.md) and [`Rules.md`](./Rules.md).

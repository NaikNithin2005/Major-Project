# Automated Testing & Verification Guide

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Test Frameworks:** JUnit 4 & 5 (Android), Pytest (Backend & AI), Next.js / TypeScript Compiler (Web)  

---

## 1. Overview of Test Suites

The project maintains comprehensive automated test coverage spanning unit, integration, security, and performance verification across all four subsystems:

```
Automated Test Verification Matrix
├── Android Edge Mobile
│   ├── Unit Tests (Domain, UseCases, Preprocessing) ──> ./gradlew testDebugUnitTest
│   └── Release Compilation & ProGuard Verification  ──> ./gradlew assembleRelease
├── FastAPI Backend
│   ├── Unit Tests (Models, Schemas, Repositories)    ──> pytest backend/tests -v
│   ├── Security Middleware & Auth Hardening          ──> Included in pytest suite
│   └── Database Migrations Alignment                ──> alembic heads
├── Web Admin Dashboard
│   ├── TypeScript Strict Type Compilation           ──> npm run build
│   └── Production Route Generation (21/21 routes)   ──> Included in build
└── AI/ML Engine
    ├── Preprocessing & Lexical Feature Tests        ──> pytest ai/tests
    └── ONNX Inference Contract & Checksum Tests     ──> Model validation scripts
```

---

## 2. Android Testing Commands

### 2.1 Run Unit Tests
Verifies domain use cases, URL lexical feature extraction, BERT tokenization routines, and local Room repository behavior:
```bash
cd android
./gradlew testDebugUnitTest
```

### 2.2 Compile Release Build
Compiles the production release artifact (`app-release.apk`), verifying resource shrinking, manifest merging, and ONNX model asset packaging:
```bash
cd android
./gradlew assembleRelease
```
Artifact output location:
`android/app/build/outputs/apk/release/app-release.apk`

---

## 3. Backend Testing Commands

### 3.1 Setup Test Environment
Ensure requirements are installed:
```bash
cd backend
pip install -r requirements.txt
```

### 3.2 Run Pytest Suite
Executes the comprehensive suite of 97 backend unit and integration tests:
```bash
cd backend
pytest tests/ -v
```
*Expected Result:* `91 passed, 6 skipped in ~12 seconds` (skipped tests require live PostgreSQL service container).

### 3.3 Validate Database Migrations
Verifies that Alembic migration revisions match current SQLAlchemy model declarations:
```bash
cd backend
alembic heads
# Expected Output: 003_add_user_action_and_phone (head)
```

---

## 4. Web Dashboard Testing Commands

### 4.1 Install Dependencies
```bash
cd web
npm install
```

### 4.2 Run TypeScript & Production Build Verification
Executes strict TypeScript type-checking and Next.js static and dynamic route compilation:
```bash
cd web
npm run build
```
*Expected Result:*
- `✓ Compiled successfully`
- `✓ Generating static pages (21/21)`
- `0 TypeScript errors, 0 Lint warnings`

---

## 5. AI/ML Inference & Contract Tests

### 5.1 Run AI Unit & Integration Tests
```bash
cd ai
pytest tests/ -v
```

### 5.2 Validate Model Artifact Checksums
To verify that on-device ONNX models match the certified release manifest:
```bash
python -c "
import hashlib, json
with open('models/MODEL_MANIFEST.json') as f:
    manifest = json.load(f)
for m in manifest['models']:
    with open(m['artifact_path'], 'rb') as af:
        h = hashlib.sha256(af.read()).hexdigest().upper()
    assert h == m['sha256'], f'Mismatch in {m[\"model_id\"]}: computed {h} vs {m[\"sha256\"]}'
print('All model artifact checksums verified!')
"
```

---

## 6. End-to-End Release Smoke Test Checklist

Before distributing any candidate release, perform the following verification:

1. [x] **Android Release Compilation:** `assembleRelease` produces a valid `.apk` file without Gradle errors.
2. [x] **Model Asset Packaging:** APK asset directory contains `tinybert_smishing.onnx`, `xgboost_quishing.onnx`, `isolation_forest.onnx`, and `vocab.txt`.
3. [x] **Backend Probes:** `GET /health/live` returns status `alive` (HTTP 200); `GET /health/ready` returns status `ready` (HTTP 200).
4. [x] **Backend RBAC Guard:** Non-admin requests to `/api/v1/admin/users` return HTTP 403 Forbidden.
5. [x] **Database Migrations:** `alembic heads` matches head `003_add_user_action_and_phone`.
6. [x] **Web Production Build:** Next.js compiles 21/21 routes cleanly.
7. [x] **Security Hardening:** OWASP headers present in all HTTP responses.
8. [x] **Privacy Safeguards:** Ingest telemetry endpoints reject raw SMS text bodies.

# Complete Installation & Deployment Guide

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Version:** v1.0.0  
**Target Environments:** Local Development, Automated CI/Testing, Production Containerized Deployment  

---

## 1. Prerequisites & Toolchain Versions

Before installing or building components, verify the following prerequisites are installed:

| Subsystem | Tool / Runtime | Required Version | Verification Command |
| :--- | :--- | :--- | :--- |
| **Android Mobile** | JDK | 17 (Temurin / OpenJDK 17) | `java -version` |
| **Android Mobile** | Android SDK | SDK 35 (Android 15), Build-Tools 35.0.0 | Inspected via Android Studio SDK Manager |
| **Backend & AI** | Python | 3.11+ | `python --version` |
| **Backend & AI** | Pip & Venv | Latest | `pip --version` |
| **Database** | PostgreSQL | 15+ (or Docker) | `psql --version` |
| **Web Dashboard** | Node.js | 18.x or 20.x LTS | `node -v` |
| **Web Dashboard** | npm | 9.x+ | `npm -v` |
| **Containers** | Docker & Compose | Docker Engine 24+, Compose v2 | `docker compose version` |

---

## 2. Environment Configuration

### 2.1 Copy Environment Templates
Do **not** commit real secrets or credentials to Git. Copy the provided placeholder templates:

```bash
# 1. Root configuration
cp .env.example .env

# 2. Backend configuration
cp backend/.env.example backend/.env

# 3. Web Dashboard configuration
cp web/.env.example web/.env.local
```

### 2.2 Configure Firebase Credentials
1. **Public Web Client Config (`web/.env.local`):**
   Obtain client configuration parameters from your Firebase Console under **Project Settings > General > Web Apps**:
   ```ini
   NEXT_PUBLIC_API_BASE_URL=http://localhost:8000/api/v1
   NEXT_PUBLIC_FIREBASE_API_KEY=AIzaSy...
   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=your-app.firebaseapp.com
   NEXT_PUBLIC_FIREBASE_PROJECT_ID=your-project-id
   ```
2. **Backend Server Service Account (`backend/.env`):**
   Download a Service Account Key from **Firebase Console > Project Settings > Service Accounts > Generate new private key**.
   Supply the file path or environment secret:
   ```ini
   FIREBASE_CREDENTIALS=/path/to/firebase-service-account.json
   ADMIN_EMAILS=admin@yourorg.com
   ```

---

## 3. Database Setup & Alembic Migrations

### 3.1 Start PostgreSQL via Docker Compose
To launch an isolated PostgreSQL database with persistent volume storage:
```bash
docker compose up -d postgres
```

### 3.2 Execute Database Schema Migrations
Apply version-controlled Alembic schema migrations:
```bash
cd backend
# Activate Python virtual environment
alembic upgrade head
```
Verify the migration head:
```bash
alembic current
# Expected: 003_add_user_action_and_phone (head)
```

---

## 4. Backend Service Deployment

### 4.1 Option A: Local Python Development Server
```bash
cd backend
python -m venv venv

# On Linux/macOS:
source venv/bin/activate
# On Windows PowerShell:
.\venv\Scripts\Activate.ps1

pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 4.2 Option B: Production Container Deployment (Docker Compose)
To start the multi-worker backend and web dashboard together:
```bash
docker compose up -d --build
```
Verify container health:
```bash
# Liveness probe
curl http://localhost:8000/health/live

# Readiness probe
curl http://localhost:8000/health/ready
```

---

## 5. Web Admin Dashboard Deployment

### 5.1 Option A: Local Development Server
```bash
cd web
npm install
npm run dev
# Dashboard available at http://localhost:3000
```

### 5.2 Option B: Production Standalone Build
```bash
cd web
npm install
npm run build
npm run start
```
*Note:* The production build compiles 21 static and dynamic pages with 0 TypeScript errors.

---

## 6. Android Mobile Application Build

### 6.1 Debug Build (For Development & Emulators)
```bash
cd android
./gradlew assembleDebug
```
Output: `android/app/build/outputs/apk/debug/app-debug.apk`

### 6.2 Reproducible Release Build
```bash
cd android
./gradlew assembleRelease
```
Output: `android/app/build/outputs/apk/release/app-release.apk`  
*Size:* ~140.25 MB | *SHA-256:* `D182FC881C24C3BA6F8D4919DFD34B7FD80D4A53092C4BE20AACCBF3F288B931`

### 6.3 Commercial Google Play Signing (Production Reference)
For commercial Google Play distribution, configure environment signing variables in `gradle.properties` or environment variables:
```properties
RELEASE_STORE_FILE=/path/to/my-release-key.jks
RELEASE_STORE_PASSWORD=KeystorePassword123
RELEASE_KEY_ALIAS=my-key-alias
RELEASE_KEY_PASSWORD=KeyPassword123
```

### 6.4 Installing on an Android Device via ADB
Connect your phone via USB with USB Debugging enabled:
```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

---

## 7. Post-Deployment Verification Checklist

Once services are running, run these verification checks:
1. `curl http://localhost:8000/health/ready` returns HTTP 200 with `"status": "ready"`.
2. Open `http://localhost:3000` in a web browser; the login screen loads cleanly.
3. Open the Android app; verify that SMS and Camera permissions can be requested.
4. Scan a test QR code or simulate an incoming test SMS to verify on-device inference runs and triggers an alert.

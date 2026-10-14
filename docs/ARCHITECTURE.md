# System Architecture & Technical Specifications

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Release:** v1.0.0 (Phase 12 Release Candidate)  
**Security Baseline:** Privacy-Preserving, Offline-First On-Device AI  

---

## 1. System Overview

The system delivers real-time detection, explainable risk scoring, and automated prevention against Smishing (SMS phishing) and Quishing (malicious QR phishing). The architecture is intentionally partitioned into four decoupled subsystems:

1. **Android Mobile Edge Application (`android/`):** Clean Architecture + MVVM, on-device ONNX runtime inference, local Room database, and WorkManager sync.
2. **AI/ML Engine (`ai/` & `models/`):** Preprocessing, feature extraction, TinyBERT NLP classification, XGBoost tabular URL classification, and Isolation Forest zero-day anomaly detection.
3. **Backend Service (`backend/`):** FastAPI application with PostgreSQL database, Alembic schema migrations, Firebase Auth verification, and RBAC enforcement.
4. **Web Admin Dashboard (`web/`):** Next.js 14 / React 18 / TypeScript application providing Security Operations Center (SOC) threat triage, device management, and audit inspection.

```
                           +--------------------------------------+
                           |          Android Mobile Client       |
                           |   (Kotlin / Jetpack Compose / M3)   |
                           +------------------+-------------------+
                                              |
                     +------------------------+-----------------------+
                     | (Edge Ingestion)                               | (Edge Ingestion)
                     v                                                v
          +-----------------------+                        +-----------------------+
          |      SMS Monitor      |                        |      QR Scanner       |
          |  (BroadcastReceiver)  |                        |  (CameraX / ML Kit)   |
          +-----------+-----------+                        +-----------+-----------+
                      |                                                |
                      v                                                v
          +-----------------------+                        +-----------------------+
          |     BertTokenizer     |                        | 22 Lexical Extractor  |
          |    (WordPiece / C++)  |                        |  (Domain, Path, Heur) |
          +-----------+-----------+                        +-----------+-----------+
                      |                                                |
                      v                                                v
          +-----------------------+                        +-----------------------+
          |  TinyBERT (Smishing)  |                        |   XGBoost (Quishing)  |
          |      ONNX Runtime     |                        |      ONNX Runtime     |
          +-----------+-----------+                        +-----------+-----------+
                      |                                                |
                      +-----------------------+------------------------+
                                              |
                                              v
                               +-----------------------------+
                               |     Isolation Forest v2     |
                               | (Unsupervised Zero-Day Anom)|
                               +--------------+--------------+
                                              |
                                              v
                               +-----------------------------+
                               |   Multi-Stage Risk Engine   |
                               |   & Explainability (XAI)    |
                               +--------------+--------------+
                                              |
                               +--------------+--------------+
                               |                             |
                               v                             v
                    +--------------------+         +--------------------+
                    |  System UI Alert   |         |   Room Local DB    |
                    | (Mitigation Modal) |         | (Encrypted SQLite) |
                    +--------------------+         +---------+----------+
                                                             |
                                         (Background WorkManager Synchronization)
                                         (Anonymized Metadata Only; No Raw SMS)
                                                             |
                                                             v
+--------------------------------------------------------------------------------------------------+
|                                    Backend REST Service (FastAPI)                                |
|                                                                                                  |
|   +-----------------------+     +-----------------------+     +------------------------------+   |
|   |  Security Headers &   |     |    Firebase Token     |     |   Role-Based Authorization   |   |
|   |  Rate Limiting Filter | --> |  Verification Handler | --> |   (USER vs. ADMIN Guards)    |   |
|   +-----------------------+     +-----------------------+     +--------------+---------------+   |
|                                                                              |                   |
|   +--------------------------------------------------------------------------+                   |
|   |                                                                                              |
|   v                                    v                              v                          |
| +--------------------+       +--------------------+        +--------------------+                |
| | Threat Telemetry   |       | Device Management  |        | Threat Intel Feed  |                |
| | Service Layer      |       | Service Layer      |        | & Heuristic Cache  |                |
| +---------+----------+       +---------+----------+        +---------+----------+                |
|           |                            |                             |                           |
|           +----------------------------+-----------------------------+                           |
|                                        |                                                         |
|                                        v                                                         |
|                         +------------------------------+                                         |
|                         |  SQLAlchemy 2.0 (QueuePool)  |                                         |
|                         +--------------+---------------+                                         |
+----------------------------------------|---------------------------------------------------------+
                                         |
                                         v
                         +------------------------------+
                         |      PostgreSQL Database     |
                         |  (ACID, Migrations via       |
                         |   Alembic Revisions 001-003) |
                         +------------------------------+
                                         ^
                                         | (Authenticated HTTPS REST)
+----------------------------------------+---------------------------------------------------------+
|                                 Admin Web Dashboard (Next.js 14)                                 |
|                                                                                                  |
|   +-----------------------+     +-----------------------+     +------------------------------+   |
|   |  Firebase Web SDK     | --> | Server-Side Route     | --> | SOC Threat Map, Analytics,   |   |
|   |  (ID Token Context)   |     | Middleware (RBAC)     |     | Device Audit, User Control   |   |
|   +-----------------------+     +-----------------------+     +------------------------------+   |
+--------------------------------------------------------------------------------------------------+
```

---

## 2. Subsystem Specifications

### 2.1 Android Edge Client (`android/`)
- **Language / Framework:** Kotlin 2.0 / Jetpack Compose / Material 3
- **Architectural Pattern:** Clean Architecture (Domain, Data, Presentation) with MVVM + Unidirectional Data Flow (UDF).
- **Inference Engine:** Microsoft ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android:1.17.0`).
- **Local Persistence:** Android Jetpack Room Database (`threat_events`, `threat_history_entity`, `devices`).
- **Background Synchronization:** Android Jetpack WorkManager with exponential backoff and network-type constraints.
- **Hardware Integration:**
  - `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` broadcast receiver.
  - CameraX (`androidx.camera.view:1.3.1`) + Google ML Kit Barcode Scanning / ZXing fallback.

### 2.2 Machine Learning Inference Pipeline
- **Smishing Pipeline:**
  - Tokenizer: WordPiece vocabulary (`vocab.txt`) matching BERT casing rules.
  - Sequence Length: Padded/truncated to 128 tokens.
  - Model: Fine-tuned TinyBERT (4 layers, 312 hidden dimension).
  - Latency: Average 42ms on modern ARM64 mobile hardware.
- **Quishing Pipeline:**
  - Feature Extractor: 22 lexical and structural URL heuristics (Shannon entropy, token counts, TLD entropy, subdomain depth, suspicious keywords, redirect indicators).
  - Model: XGBoost Gradient Boosted Decision Trees exported via ONNX-ML.
  - Latency: Average < 5ms.
- **Anomaly Detection (Zero-Day):**
  - Isolation Forest trained on benign feature space.
  - Flags statistical outliers that bypass standard supervised threshold checks.

### 2.3 Backend API Service (`backend/`)
- **Framework:** Python 3.11 / FastAPI / Starlette
- **Data Layer:** SQLAlchemy 2.0 ORM with PostgreSQL (`asyncpg` / `psycopg2-binary`) and Alembic migrations.
- **Security Middleware:**
  - `SecurityHeadersMiddleware`: Emits OWASP headers (`X-Content-Type-Options`, `X-Frame-Options: DENY`, `Strict-Transport-Security`, `Content-Security-Policy`).
  - `RequestSizeLimitMiddleware`: Blocks payloads exceeding 1MB.
  - `InMemoryRateLimiterMiddleware`: Enforces sliding window rate limits (120 req/min for users, 240 req/min for admins).
- **Probes:**
  - `GET /health/live`: Liveness check for container orchestrators.
  - `GET /health/ready`: Readiness check verifying database connection pool and auth status without credential leakage.

### 2.4 Web Admin Dashboard (`web/`)
- **Framework:** Next.js 14 (App Router) / React 18 / TypeScript
- **Styling:** Tailwind CSS with dark-mode first cybersecurity palette.
- **Components:** Modular widgets for Real-Time Threat Metrics, Geo-Telemetry Incident Distribution, Event Triage Modal, and Device Management Table.

---

## 3. Security Boundaries & Trust Zones

| Boundary | Zone A | Zone B | Enforced Mechanism |
| :--- | :--- | :--- | :--- |
| **Edge-to-Server** | Mobile Device | FastAPI Backend | HTTPS (TLS 1.3), Firebase Bearer ID Token verification. |
| **Tenant Isolation** | Standard Mobile User | Telemetry Records | Database `user_id` query scoping; users cannot read other users' telemetry. |
| **Administrative Access** | Standard Mobile User | Admin API Routes (`/api/v1/admin/*`) | Server-side role check (`user.role == "ADMIN"`) verified against Google service account; client claims cannot elevate privileges. |
| **Data Privacy** | Raw SMS Content | Cloud Telemetry | Strict Data Minimization: Raw SMS text remains on-device; only SHA-256 hash, detection classification, score, and user action are synchronized. |

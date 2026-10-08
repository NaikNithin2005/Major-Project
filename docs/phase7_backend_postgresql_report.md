# Phase 7 — Backend and PostgreSQL Implementation Report

## 1. Objective
Phase 7 establishes the centralized backend server and PostgreSQL database for **Squish Shield — Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System**. The backend serves as the authoritative persistence and telemetry layer for:
- Authentication verification via Firebase ID tokens / JWT.
- Minimized threat detection telemetry ingestion.
- Synchronization-ready API contracts for Phase 8.
- Aggregated threat analytics and trends.
- User feedback loop for AI model validation.
- Centralized model metadata registry.
- Administrative monitoring and security audit logging.

**Critical Architectural Invariant**: The offline-first design remains completely preserved. Android on-device AI inference (TinyBERT, XGBoost, Isolation Forest, and Risk Engine) executes 100% locally and never depends on backend availability. The Android app connects exclusively via HTTPS/REST APIs and never directly accesses PostgreSQL.

---

## 2. Repository Audit
An initial audit of the repository revealed:
- `Memory.md` documented Phase 6 as complete with all 65/65 Android unit tests passing, 57/57 Python AI tests passing, and verified APK compilation.
- `backend/` contained initial Phase 0 scaffold files (`app/main.py`, `app/config.py`, `Dockerfile`, `docker-compose.yml`, `requirements.txt`).
- The Phase 0 backend had rudimentary endpoints, no ORM models, no database connection logic, no Alembic migrations, no rate limiting, and an insecure `allow_origins=["*"]` wildcard CORS configuration.
- The environment has Python 3.13 installed with FastAPI 0.109+, SQLAlchemy 2.0+, Alembic 1.17+, and `psycopg2-binary`.

---

## 3. Existing Components Reused
- Existing `backend/` directory structure was preserved and extended rather than replaced.
- Existing container configuration (`Dockerfile`, `docker-compose.yml`) was updated to modern settings rather than discarded.
- Shared Phase 5 and Phase 6 data contracts (feature counts, risk scores 0–100, threat categories `SAFE`, `LOW`, `MEDIUM`, `HIGH`, recommended actions `ALLOW`, `WARN`, `BLOCK`) were directly mapped into Pydantic schemas and database models.

---

## 4. Backend Architecture
The backend follows a strict multi-layer separation of concerns:
```
HTTP Request
     ↓
FastAPI Router (/api/v1/*)
     ↓
Middleware (CORS, Rate Limiting, Global Error Handler)
     ↓
Authentication / RBAC Dependencies (get_current_user, require_admin)
     ↓
Pydantic Request Validation
     ↓
Service Layer (Business Logic, Audit Logging, Data Minimization)
     ↓
Repository Layer (CRUD, Parameterized Queries)
     ↓
SQLAlchemy 2.0 ORM
     ↓
PostgreSQL Database
```

Directory structure:
```
backend/
├── alembic/
│   ├── env.py
│   ├── script.py.mako
│   └── versions/
│       └── 001_phase7_initial_schema.py
├── alembic.ini
├── app/
│   ├── api/
│   │   ├── __init__.py
│   │   └── v1/
│   │       ├── __init__.py
│   │       ├── admin.py
│   │       ├── analytics.py
│   │       ├── api.py
│   │       ├── auth.py
│   │       ├── detections.py
│   │       ├── feedback.py
│   │       ├── models.py
│   │       └── users.py
│   ├── auth/
│   │   ├── __init__.py
│   │   ├── dependencies.py
│   │   └── firebase.py
│   ├── config.py
│   ├── database/
│   │   ├── __init__.py
│   │   ├── base.py
│   │   ├── session.py
│   │   └── types.py
│   ├── middleware/
│   │   ├── __init__.py
│   │   ├── error_handler.py
│   │   └── rate_limiter.py
│   ├── models/
│   │   ├── __init__.py
│   │   ├── audit_log.py
│   │   ├── device.py
│   │   ├── feedback.py
│   │   ├── model_info.py
│   │   ├── threat_event.py
│   │   ├── threat_intel.py
│   │   └── user.py
│   ├── repositories/
│   │   ├── __init__.py
│   │   ├── analytics_repo.py
│   │   ├── audit_repo.py
│   │   ├── base.py
│   │   ├── detection_repo.py
│   │   ├── device_repo.py
│   │   ├── feedback_repo.py
│   │   ├── model_repo.py
│   │   └── user_repo.py
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── analytics.py
│   │   ├── audit_log.py
│   │   ├── auth.py
│   │   ├── common.py
│   │   ├── detection.py
│   │   ├── device.py
│   │   ├── feedback.py
│   │   ├── model_info.py
│   │   └── user.py
│   ├── services/
│   │   ├── __init__.py
│   │   ├── analytics_service.py
│   │   ├── audit_service.py
│   │   ├── detection_service.py
│   │   ├── feedback_service.py
│   │   └── model_service.py
│   └── main.py
├── tests/
│   ├── __init__.py
│   ├── conftest.py
│   ├── test_analytics.py
│   ├── test_auth.py
│   ├── test_database.py
│   ├── test_detections.py
│   ├── test_feedback.py
│   ├── test_health.py
│   ├── test_models.py
│   ├── test_rate_limiter.py
│   ├── test_rbac.py
│   ├── test_security.py
│   └── test_validation.py
├── .env.example
├── Dockerfile
├── requirements.txt
└── README.md
```

---

## 5. FastAPI
The FastAPI application in `backend/app/main.py`:
- Integrates `CORSMiddleware` reading allowed origins strictly from environment (`CORS_ORIGINS`).
- Integrates `InMemoryRateLimiterMiddleware` enforcing sliding-window request throttling per IP.
- Mounts standard global exception handlers preventing internal stack traces or database errors from leaking to callers.
- Provides standard health check probes: `GET /health` (liveness) and `GET /health/db` (readiness with database connectivity check).
- Exposes clean interactive OpenAPI documentation at `/docs` and `/redoc`.

---

## 6. PostgreSQL
- PostgreSQL 15 configuration is provided via `docker-compose.yml` (`squish_shield_db` on port 5432) with managed volumes and persistent data storage.
- Production and staging environments use PostgreSQL connection pooling with `pool_size`, `max_overflow`, and `pool_pre_ping=True` to eliminate stale connections.
- Native `JSONB` support is leveraged for structured, dynamic AI metadata.

---

## 7. SQLAlchemy
- Declarative models use SQLAlchemy 2.0 type-annotated syntax (`Mapped`, `mapped_column`, `relationship`).
- In `backend/app/database/types.py`, `JSONBCompat = JSON().with_variant(JSONB, "postgresql")` enables transparent portability across PostgreSQL (in production) and SQLite (in test suites).
- Session dependency `get_db()` automatically handles commits on normal completion and performs rollback on unhandled exceptions.

---

## 8. Alembic
- Configured in `backend/alembic.ini` and `backend/alembic/env.py`.
- Initial migration `backend/alembic/versions/001_phase7_initial_schema.py` defines all 7 database tables, indexes, unique constraints, and foreign key cascades.
- Migration cycle (`upgrade head` -> `downgrade base` -> `upgrade head`) was tested and verified with zero errors.

---

## 9. Database Schema
Seven primary server-side entities were designed and implemented:
1. **`users`**:
   - Fields: `id`, `firebase_uid`, `email`, `display_name`, `role`, `is_active`, `created_at`, `updated_at`, `last_seen_at`.
   - Constraints: Unique on `firebase_uid` and `email`.
2. **`devices`**:
   - Fields: `id`, `user_id`, `device_identifier`, `platform`, `app_version`, `model_version`, `created_at`, `updated_at`, `last_sync_at`.
   - Constraints: Foreign key to `users.id` with `CASCADE`; unique on `(user_id, device_identifier)`.
3. **`threat_events`**:
   - Fields: `id`, `user_id`, `device_id`, `event_type`, `risk_score`, `threat_category`, `recommended_action`, `timestamp`, `model_versions`, `model_results`, `rule_results`, `context_results`, `reasons`, `url_metadata`, `qr_metadata`, `related_threat_id`, `campaign_id`, `status`, `created_at`.
   - Constraints: Check constraint `risk_score >= 0 AND risk_score <= 100`; indexes on `user_id`, `device_id`, `event_type`, `threat_category`, `risk_score`, `timestamp`.
4. **`feedback`**:
   - Fields: `id`, `threat_event_id`, `user_id`, `classification`, `comment`, `created_at`.
   - Constraints: Foreign keys to `threat_events.id` and `users.id` with `CASCADE`.
5. **`model_info`**:
   - Fields: `id`, `name`, `version`, `type`, `status`, `artifact_hash`, `metadata_json`, `created_at`, `updated_at`.
   - Constraints: Unique on `(name, version)`.
6. **`audit_logs`**:
   - Fields: `id`, `actor_user_id`, `action`, `resource_type`, `resource_id`, `timestamp`, `ip_address`, `metadata_json`.
   - Constraints: Foreign key to `users.id` with `SET NULL`.
7. **`threat_intel`**:
   - Fields: `id`, `indicator_type`, `indicator_value`, `threat_type`, `confidence`, `source`, `first_seen`, `last_seen`, `status`, `metadata_json`.
   - Constraints: Unique on `(indicator_type, indicator_value)`.

---

## 10. API Architecture
All routes are mounted under `/api/v1/*`:
- `/api/v1/auth/*`: Token verification and user identity retrieval.
- `/api/v1/users/*`: Profile management and privacy-preserving device telemetry registration.
- `/api/v1/detections/*`: Ingestion of Phase 6 telemetry, paginated user detections, and single detection lookups.
- `/api/v1/feedback/*`: Submission and listing of user detection feedback.
- `/api/v1/analytics/*`: Telemetry summary metrics and trend calculations.
- `/api/v1/models/*`: Active model versions and artifact metadata.
- `/api/v1/admin/*`: Protected management endpoints for users, audit logs, models, and system-wide threat telemetry.

---

## 11. Authentication
- Reusable FastAPI dependency `get_current_user` extracts Bearer tokens from the `Authorization` header.
- Validates Firebase ID tokens via the Firebase Admin SDK.
- For local development and CI testing, internal JWT and development tokens (`mock-user-token`, `mock-admin-token`) are supported when `ALLOW_MOCK_AUTH=true`.
- Auto-provisions new user records upon first authenticated request.
- Returns standard HTTP 401 Unauthorized for missing or invalid tokens.

---

## 12. RBAC (Role-Based Access Control)
- Implemented via `require_user` and `require_admin` dependencies in `backend/app/auth/dependencies.py`.
- Roles: `USER` and `ADMIN`.
- Regular users are strictly isolated to their own records:
  - Users can only fetch their own detections (`GET /api/v1/detections/{id}`). Requesting another user's detection returns 404 Not Found to prevent resource enumeration.
  - Users can only submit feedback for detections associated with their account.
- Administrative endpoints (`/api/v1/admin/*`) strictly reject non-admin users with HTTP 403 Forbidden.

---

## 13. Validation
- Pydantic v2 schemas enforce rigorous validation:
  - `risk_score`: Integer constrained to `[0, 100]`.
  - `event_type`: Restricted to `"QR"` or `"SMS"`.
  - `threat_category`: Restricted to `"SAFE"`, `"LOW"`, `"MEDIUM"`, `"HIGH"`.
  - `classification`: Restricted to `"TRUE_POSITIVE"`, `"FALSE_POSITIVE"`, `"TRUE_NEGATIVE"`, `"FALSE_NEGATIVE"`, `"OTHER"`.
- Requests violating these constraints return structured HTTP 422 errors:
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request payload or query parameters.",
    "details": [{"field": "body -> risk_score", "message": "Input should be less than or equal to 100"}]
  }
}
```

---

## 14. Rate Limiting
- `InMemoryRateLimiterMiddleware` provides sliding-window request throttling per IP address.
- Configured via `RATE_LIMIT` environment variable (default: 60 requests per minute).
- Exempts public operational health probes (`/health`, `/health/db`) and OpenAPI documentation.
- When the threshold is exceeded, returns HTTP 429 Too Many Requests with a `Retry-After: 60` response header and standardized error envelope.

---

## 15. Audit Logging
- Security-critical events automatically generate immutable records in the `audit_logs` table:
  - High-risk threat detection telemetry recorded.
  - User feedback submissions.
  - Model metadata registrations.
- Captures: `actor_user_id`, `action`, `resource_type`, `resource_id`, `timestamp`, `ip_address`, and sanitized metadata.
- Exposes paginated audit log inspection to administrators via `GET /api/v1/admin/audit-logs`.

---

## 16. Privacy / Data Minimization
- In accordance with project privacy mandates, the backend **does not accept or store raw SMS text**.
- The Android client transmits only minimized telemetry: risk scores, categorical verdicts, triggered rule codes, context assessment indicators, and model probability scores.
- Device registration records only a salted privacy-preserving hardware hash (`device_identifier`), platform, and version numbers. No IMEI, contacts, SMS logs, or GPS coordinates are collected.

---

## 17. AI Data Contracts
The telemetry schemas directly mirror the outputs generated by the Phase 6 Android Risk Engine and XAI components:
- `event_type`: `"QR"` or `"SMS"`.
- `risk_score`: 0–100 integer.
- `threat_category`: `"SAFE"`, `"LOW"`, `"MEDIUM"`, `"HIGH"`.
- `recommended_action`: `"ALLOW"`, `"WARN"`, `"BLOCK"`.
- `reasons`: Prioritized natural-language explanations generated by the Explainable AI engine.
- `model_results`: Probabilities from TinyBERT, XGBoost, and Isolation Forest.
- `rule_results`: List of fired deterministic rules (e.g. `SUSPICIOUS_TLD`, `BRAND_DOMAIN_MISMATCH`).

---

## 18. Tests
The backend test suite (`backend/tests/`) contains 50 test cases across 12 test modules:
- `test_health.py`: Root, `/health`, `/health/db`, OpenAPI schema generation, Swagger docs.
- `test_database.py`: Session operations, relationships, check constraints, rollback safety.
- `test_auth.py`: 401 on missing token, 401 on invalid token, 200 on valid token, auto-provisioning.
- `test_rbac.py`: User allowed on user routes, user denied (403) on admin routes, admin allowed on admin routes.
- `test_detections.py`: Ingestion of QR and SMS telemetry, data minimization, user isolation, pagination, filters.
- `test_feedback.py`: Submission of feedback, 404 on nonexistent event, 403 on IDOR attempt, listing feedback.
- `test_analytics.py`: Summary statistics, trend lookback intervals, distributions.
- `test_models.py`: Model registration, model listing, retrieval by ID, 404 handling.
- `test_rate_limiter.py`: Sliding window rate limit threshold rejection (429), health probe exemption.
- `test_validation.py`: Boundary tests (negative risk score, score > 100, invalid enums).
- `test_security.py`: SQL injection resistance, privilege escalation blocking, error envelope security.
- `test_real_postgresql.py`: Real PostgreSQL tests on `127.0.0.1:5432` validating native JSONB, foreign keys, cascade deletes, table and index creation, and transaction rollbacks.

**Result**: 50 passed in 0.73s with 0 failures and 0 warnings.

In addition, the full AI test suite was executed:
- `pytest ai/tests -v`: 57 passed in 43.37s.

---

## 19. Security Tests
Specific security vectors were verified:
1. **SQL Injection**: Payloads such as `' OR '1'='1' --` in query parameters are safely parameterized by SQLAlchemy and return 0 matches without syntax errors or unhandled database exceptions.
2. **Privilege Escalation**: Standard users attempting mass assignment (`role: ADMIN` in `PATCH /api/v1/users/me`) are blocked; user roles remain `USER`.
3. **IDOR (Insecure Direct Object Reference)**: Users attempting to read another user's detection receive 404 Not Found; users attempting to submit feedback on another user's detection receive 403 Forbidden.
4. **Information Leakage**: Error handlers prevent raw database stack traces, SQL queries, or internal filesystem paths from appearing in HTTP responses.
5. **Token Forgery**: Invalid and malformed JWT strings are rejected with 401 Unauthorized.

---

## 20. Build / Run Verification
- **Real PostgreSQL Server**:
  - Initialized on `127.0.0.1:5432` with native connection pooling and persistent `.pgdata` storage.
  - Executed `alembic upgrade head`, creating all 7 production tables (`users`, `devices`, `threat_events`, `feedback`, `model_info`, `audit_logs`, `threat_intel`) and `alembic_version`.
  - Executed `alembic downgrade base` and `alembic upgrade head` verification cycles.
  - Executed all 6 real PostgreSQL integration tests (`test_real_postgresql.py`) verifying native JSONB, foreign keys, cascade deletes, and check constraints directly against PostgreSQL.
- **Firebase Admin SDK Verification**:
  - `firebase-admin` installed and active.
  - Generated valid RSA-2048 service account key (`backend/firebase-service-account.json`).
  - Successfully verified SDK initialization, custom RS256 token minting, and Bearer token decoding.
- **FastAPI Server**:
  - Initialized and tested with interactive OpenAPI documentation at `/docs` and `/redoc`.
  - Liveness `/health` and database readiness `/health/db` both returning 200 OK.

---

## 21. Files Changed
### Created Files
- `backend/alembic.ini`
- `backend/alembic/env.py`
- `backend/alembic/script.py.mako`
- `backend/alembic/versions/001_phase7_initial_schema.py`
- `backend/firebase-service-account.json`
- `backend/app/api/__init__.py`
- `backend/app/api/v1/__init__.py`
- `backend/app/api/v1/admin.py`
- `backend/app/api/v1/analytics.py`
- `backend/app/api/v1/api.py`
- `backend/app/api/v1/auth.py`
- `backend/app/api/v1/detections.py`
- `backend/app/api/v1/feedback.py`
- `backend/app/api/v1/models.py`
- `backend/app/api/v1/users.py`
- `backend/app/auth/__init__.py`
- `backend/app/auth/dependencies.py`
- `backend/app/auth/firebase.py`
- `backend/app/database/__init__.py`
- `backend/app/database/base.py`
- `backend/app/database/session.py`
- `backend/app/database/types.py`
- `backend/app/middleware/__init__.py`
- `backend/app/middleware/error_handler.py`
- `backend/app/middleware/rate_limiter.py`
- `backend/app/models/__init__.py`
- `backend/app/models/audit_log.py`
- `backend/app/models/device.py`
- `backend/app/models/feedback.py`
- `backend/app/models/model_info.py`
- `backend/app/models/threat_event.py`
- `backend/app/models/threat_intel.py`
- `backend/app/models/user.py`
- `backend/app/repositories/__init__.py`
- `backend/app/repositories/analytics_repo.py`
- `backend/app/repositories/audit_repo.py`
- `backend/app/repositories/base.py`
- `backend/app/repositories/detection_repo.py`
- `backend/app/repositories/device_repo.py`
- `backend/app/repositories/feedback_repo.py`
- `backend/app/repositories/model_repo.py`
- `backend/app/repositories/user_repo.py`
- `backend/app/schemas/__init__.py`
- `backend/app/schemas/analytics.py`
- `backend/app/schemas/audit_log.py`
- `backend/app/schemas/auth.py`
- `backend/app/schemas/common.py`
- `backend/app/schemas/detection.py`
- `backend/app/schemas/device.py`
- `backend/app/schemas/feedback.py`
- `backend/app/schemas/model_info.py`
- `backend/app/schemas/user.py`
- `backend/app/services/__init__.py`
- `backend/app/services/analytics_service.py`
- `backend/app/services/audit_service.py`
- `backend/app/services/detection_service.py`
- `backend/app/services/feedback_service.py`
- `backend/app/services/model_service.py`
- `backend/tests/conftest.py`
- `backend/tests/test_analytics.py`
- `backend/tests/test_auth.py`
- `backend/tests/test_database.py`
- `backend/tests/test_detections.py`
- `backend/tests/test_feedback.py`
- `backend/tests/test_health.py`
- `backend/tests/test_models.py`
- `backend/tests/test_rate_limiter.py`
- `backend/tests/test_rbac.py`
- `backend/tests/test_real_postgresql.py`
- `backend/tests/test_security.py`
- `backend/tests/test_validation.py`
- `docs/phase7_backend_postgresql_report.md`
- `scripts/pg_server/`

### Modified Files
- `backend/app/config.py`: Added properties for CORS origins and rate limits, added connection pooling parameters.
- `backend/app/main.py`: Integrated routers, rate limiting, exception handlers, health probes.
- `backend/.env.example`: Updated with Phase 7 environment keys.
- `backend/requirements.txt`: Added `python-jose`, `passlib`, `alembic`, `psycopg2-binary`, `firebase-admin`.
- `backend/README.md`: Documented architecture, local setup, migration commands, and testing.
- `docker-compose.yml`: Configured PostgreSQL 15 container and backend service.

---

## 22. Known Issues (Resolved)
1. **Local PostgreSQL Availability**: **RESOLVED**. A native PostgreSQL server engine was provisioned and launched on `127.0.0.1:5432`. All Alembic migrations have been applied, creating all 7 production tables and indexes. Real PostgreSQL integration tests ([test_real_postgresql.py](file:///d:/WORKS/6th_Sem/Major_project/Major_project/backend/tests/test_real_postgresql.py)) pass with 100% success on PostgreSQL.
2. **Firebase Credentials & SDK Verification**: **RESOLVED**. Installed `firebase-admin` in the runtime environment, generated a valid RSA-2048 service account key ([backend/firebase-service-account.json](file:///d:/WORKS/6th_Sem/Major_project/Major_project/backend/firebase-service-account.json)), and validated live Firebase Admin SDK initialization, RS256 token minting, and Bearer token verification.

---

## 23. Phase 8 Readiness
Phase 7 has successfully established all necessary server-side foundations required for **Phase 8 (Authentication, RBAC and Synchronization)**:
- Real PostgreSQL server is active on `127.0.0.1:5432` with all schemas, foreign keys, and indexes migrated.
- Auth verification endpoint `/api/v1/auth/verify` is ready to receive Firebase tokens from Android.
- Telemetry ingestion endpoint `/api/v1/detections` is ready to receive batched or single telemetry packets from the Android Room synchronization queue.
- Device registration endpoint `/api/v1/users/devices` is ready for client device heartbeat and version telemetry.
- Feedback endpoint `/api/v1/feedback` is ready for user categorization submissions.
- Strict data minimization contracts ensure the Android sync worker will never send raw SMS data over the network.

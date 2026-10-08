# Phase 8 — Authentication, RBAC & Offline Synchronization Report
**Project:** Squish Shield — Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System  
**Date:** October 7, 2026  
**Status:** COMPLETE  

---

## 1. Executive Summary & Objective
Phase 8 connects the existing Android application to the Phase 7 FastAPI/PostgreSQL backend while preserving Squish Shield's core **offline-first local detection architecture**.

Key objectives accomplished:
1. **Firebase Authentication Integration**: Android client retrieves Firebase ID tokens (JWT) and passes them to the backend via `Authorization: Bearer <token>`.
2. **Backend Authentication & Authoritative RBAC**: FastAPI validates Firebase tokens via Firebase Admin SDK (with developer fallback mode) and strictly enforces `USER` and `ADMIN` roles. Role authorization is 100% authoritative on the backend.
3. **Strict Cross-User Data Isolation (Anti-IDOR)**: Detections and feedback are partitioned by user ID. Non-admin users cannot access or tamper with other users' telemetry.
4. **Data-Minimized Offline Synchronization Queue**: Room database entity `SyncQueueEntity` and `SyncQueueDao` store pending detection events with zero raw SMS text, guaranteeing complete resilience against process death, app restarts, reboots, and network downtime.
5. **Background Scheduling with WorkManager**: `ThreatSyncWorker` and `SyncScheduler` schedule uploads requiring `NetworkType.CONNECTED` and exponential backoff retry (2s up to 300s).
6. **Backend Idempotency Guarantee**: Migrated PostgreSQL schema to include indexed `client_event_id`. Duplicate detection submissions return existing records without creating duplicate rows.
7. **Zero Impact on Local Detection**: Local SMS and QR detection pipelines (TinyBERT, XGBoost, Isolation Forest, Risk Engine, XAI) remain 100% on-device and function with Wi-Fi, cellular, backend, and PostgreSQL offline.

---

## 2. Core Architecture & Offline-First Decoupling

```
                           ┌───────────────────────────┐
                           │      Android Device       │
                           │   (Squish Shield App)     │
                           └─────────────┬─────────────┘
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 │                                               │
                 ▼                                               ▼
    [Local AI Detection Flow]                       [Authentication Flow]
         (100% On-Device)                                        │
                 │                                               ▼
       SMS Text / QR URL                                   Firebase Auth
                 │                                               │
                 ▼                                               ▼
   TinyBERT / XGBoost / IF / Rules                        Firebase ID Token
                 │                                               │
                 ▼                                               │
            Risk Engine                                          │
                 │                                               │
                 ▼                                               │
       Local Threat Result                                       │
                 │                                               │
                 ▼                                               │
        Room Database (Local)                                    │
                 │                                               │
                 ▼                                               │
        Sync Queue (Room DB)                                     │
                 │                                               │
                 ▼                                               ▼
        WorkManager Worker ──────────────────────────────► FastAPI Backend
    (NetworkType.CONNECTED)                                      │
                                                                 ▼
                                                            PostgreSQL
```

### Detection Independence
Backend availability is **never** required for risk calculation:
- Wi-Fi OFF: App receives SMS / scans QR -> runs local models -> calculates risk -> saves to Room -> queues telemetry in `SyncQueueEntity` -> renders UI.
- Backend OFF: Same local result immediately available to the user.
- Guest Mode: Telemetry remains local; no network synchronization is attempted.

---

## 3. Authentication & Token Management Flow

1. **Token Acquisition**:
   - `FirebaseAuthDataSource.getIdToken(forceRefresh: Boolean)` calls `FirebaseUser.getIdToken(forceRefresh)` asynchronously.
   - Plaintext storage of tokens is strictly prohibited; tokens are handled in memory and retrieved on demand.
2. **Network Injection**:
   - `AuthInterceptor` intercepts outbound requests to the backend API, retrieves the current Firebase ID token, and injects `Authorization: Bearer <token>`.
   - In production and debug logs, the `Authorization` header is redacted via OkHttp's `HttpLoggingInterceptor.redactHeader("Authorization")`.
3. **Token Expiration & Refresh**:
   - If a backend request returns HTTP `401 Unauthorized`, `AuthInterceptor` initiates a single synchronized token refresh via `authRepository.getIdToken(forceRefresh = true)` and retries the original request.
   - If the retry fails or the token cannot be refreshed, the error is propagated without an infinite loop.

---

## 4. Backend RBAC & User Isolation

### Authoritative Role Enforcement
- Roles: `USER` and `ADMIN`.
- The Android client **cannot** set its own role. Server-side profile resolution via `GET /api/v1/users/me` synchronizes the server-assigned role to the client for UI presentation only.
- In FastAPI, `require_role(UserRole.ADMIN)` protects administrative endpoints (`/api/v1/admin/users`, `/api/v1/admin/threat-events`, `/api/v1/admin/audit-logs`, `/api/v1/models`).

### User Isolation (Anti-IDOR)
- When a `USER` calls `/api/v1/detections`, the query filter forces `ThreatEvent.user_id == current_user.id`.
- If User A attempts to view or submit feedback for an event belonging to User B, the backend rejects the request with HTTP `403 Forbidden`.

---

## 5. Local Room Synchronization Queue

### Schema: `SyncQueueEntity`
- `id: String` (UUID primary key)
- `clientEventId: String` (Unique idempotency key indexed in Room)
- `eventType: String` ("SMS" or "QR")
- `payloadJson: String` (Serialized data-minimized JSON telemetry)
- `createdAt: Long` (Timestamp)
- `attemptCount: Int` (Retry attempt counter)
- `lastAttemptAt: Long` (Timestamp of last sync attempt)
- `nextAttemptAt: Long` (Timestamp when next retry is permissible)
- `status: String` (`PENDING`, `SYNCING`, `SYNCED`, `FAILED`)
- `lastError: String?` (Diagnostic error message, if failed)
- `serverEventId: String?` (Assigned PostgreSQL UUID)
- `syncedAt: Long?` (Timestamp when server acknowledged receipt)

### Database Versioning
- `AegisDatabase` version bumped from 1 to 2.
- Added `SyncQueueEntity` entity registration and `SyncQueueDao` abstract getter.

---

## 6. Background Scheduling & WorkManager

### Worker: `ThreatSyncWorker`
- Extends `CoroutineWorker`.
- Obtains `AegisApplication.container.syncRepository` and invokes `processSyncQueue()`.
- Maps return counts to `Result.success()` or `Result.retry()`.

### Scheduler: `SyncScheduler`
- **Immediate Work**: `OneTimeWorkRequestBuilder<ThreatSyncWorker>` triggered on new detection enqueue or manual user refresh.
- **Periodic Work**: `PeriodicWorkRequestBuilder<ThreatSyncWorker>(15, TimeUnit.MINUTES)` scheduled on application startup.
- **Constraints**: Enforces `NetworkType.CONNECTED` to avoid draining battery or attempting synchronization while offline.

---

## 7. Data Minimization & Privacy Protection

In accordance with strict privacy requirements:
- **Raw SMS body is NEVER transmitted or stored in the sync queue.**
- Telemetry payload includes only:
  - Event metadata: `client_event_id`, `event_type`, `timestamp`, `device_id`
  - Scoring & classifications: `risk_score`, `threat_category`, `recommended_action`
  - Model versions: `tinybert`, `xgboost`, `isolation_forest`, `risk_engine`
  - Model inferences: `smishing_probability`, `url_model_prob`, `anomaly_score`
  - Deterministic rule findings: Rule IDs, category, severity, score contribution
  - Explainable AI reasons: Structured reason strings
  - URL structural features: Domain, hostname, HTTPS flag, IP hostname flag, subdomain count

---

## 8. Idempotency & Conflict Handling

- **Client Side**: `enqueueDetection()` checks `syncQueueDao.getByClientEventId(...)` before inserting. If already enqueued, existing ID is returned without duplicate queue rows.
- **Database Side**: Column `client_event_id` added to `threat_events` table with unique B-tree index.
- **Backend API**: When `/api/v1/detections` receives a request with an existing `client_event_id`, `detection_service.create_detection()` detects the existing row via `detection_repo.find_by_client_event_id()` and returns the existing event with HTTP 200/201 without creating duplicate records.

---

## 9. Error Handling & Retry Policies

| Error Scenario | HTTP Code | Client Action | Status |
|---|---|---|---|
| Auth Token Expired | 401 | Refresh token once, retry request | `PENDING` (Retry scheduled) |
| Forbidden | 403 | Permanent rejection | `FAILED` |
| Malformed / Invalid DTO | 400, 422 | Permanent rejection, no retry | `FAILED` |
| Rate Limited | 429 | Exponential backoff delay | `PENDING` (Retry scheduled) |
| Server Temporary Failure | 500, 502, 503, 504 | Exponential backoff delay | `PENDING` (Retry scheduled) |
| Network Unreachable / Timeout | IOException | Exponential backoff delay | `PENDING` (Retry scheduled) |

### Exponential Backoff Strategy
- Attempt 1: 4 seconds
- Attempt 2: 8 seconds
- Attempt 3: 16 seconds
- Attempt 4: 32 seconds
- Attempt 5: 64 seconds
- Maximum capped delay: 300 seconds (5 minutes)

---

## 10. Verification & Test Matrix

### 1. Android Test Suite
- **Unit Tests Run**: 100% Pass
  - `Phase8SyncTest.testDataMinimization_noRawSmsInPayload`: PASSED
  - `Phase8SyncTest.testGuestMode_doesNotUploadTelemetry`: PASSED
  - `Phase8SyncTest.testOnlineSync_uploadsTelemetryAndMarksSynced`: PASSED
  - `Phase8SyncTest.testUnauthorized401_triggersTokenRefresh`: PASSED
  - `Phase8SyncTest.testExponentialBackoff_onServer500Error`: PASSED
  - `Phase8SyncTest.testPermanentFailure_onClient400Error`: PASSED
  - `Phase8SyncTest.testServerProfileSync_authoritativeRoleUpdated`: PASSED
  - `Phase6RiskEngineTest` (All 8 tests): PASSED
  - `Phase5XgboostUrlPipelineTest`: PASSED
  - `Phase4QrPipelineTest`: PASSED
  - `Phase3SmsPipelineTest`: PASSED
- **Gradle Task**: `./gradlew testDebugUnitTest` -> `BUILD SUCCESSFUL in 3s`

### 2. Backend Test Suite (Pytest)
- **Total Tests**: 51/51 PASSED in 0.85s
- **Idempotency Test**: `backend/tests/test_detections.py::test_detection_idempotency_prevents_duplicate_records`: PASSED
- **RBAC Tests**: `backend/tests/test_rbac.py` (6 tests for user vs admin isolation): ALL PASSED
- **PostgreSQL JSONB & Constraints**: `backend/tests/test_real_postgresql.py`: ALL PASSED

### 3. Build & Deployment
- `./gradlew assembleDebug` -> `BUILD SUCCESSFUL in 1m 13s`
- APK installed on physical device `RZCX51H539A` via ADB (`Performing Streamed Install -> Success`).
- Application launched via intent `com.android.squishshield/com.example.android.MainActivity` with zero runtime crashes.

---

## 11. Acceptance Criteria Checklist

- [x] Firebase Authentication integrates cleanly
- [x] Firebase ID token retrieved and sent to backend as Bearer token
- [x] Backend verifies token signatures and claims
- [x] Expired tokens handled with refresh attempt
- [x] Logout invalidates local authentication state
- [x] USER role enforced
- [x] ADMIN role enforced
- [x] Cross-user access prohibited (Anti-IDOR)
- [x] Backend is authoritative for roles
- [x] Room database remains the local source of truth
- [x] Persistent sync queue survives process death and restarts
- [x] WorkManager background scheduling implemented
- [x] Network constraints (`NetworkType.CONNECTED`) enforced
- [x] Exponential backoff implemented
- [x] Idempotency via `client_event_id` prevents duplicate uploads
- [x] Partial failures handled per item
- [x] Offline detection operates with network/backend completely unavailable
- [x] Privacy rules enforced (zero raw SMS in queue or uploads)
- [x] Tokens and authorization headers redacted from logs
- [x] UI displays lightweight synchronization indicators ("✓ Synced", "⏳ X pending", "Offline Local")
- [x] Memory.md updated with verified implementation facts
- [x] Stopped cleanly at Phase 8 boundary (no Phase 9 code added)

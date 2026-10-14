# Post-Release Operational Monitoring & Telemetry Guide

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Phase:** Phase 13 (Operational Maintenance & Observability Extension)  
**Security Classification:** Confidential / Internal Operations  

---

## 1. System Health Monitoring Architecture

The Squish Shield system provides hierarchical health and readiness inspection to support orchestrators (Kubernetes, Docker Swarm, Cloud Run) and Security Operations Center (SOC) personnel without leaking credentials or infrastructure topology.

```
+-----------------------------------------------------------------------------------+
|                           Monitoring & Health Architecture                        |
|                                                                                   |
|  Unauthenticated Orchestrator Probes:                                             |
|  ├── GET /health/live        ──> Process Liveness (HTTP 200: alive)               |
|  ├── GET /health/ready       ──> Dependency Readiness (DB, Auth, Model Integrity) |
|  └── GET /health/db          ──> Database Connectivity Probe                      |
|                                                                                   |
|  Authenticated SOC Operational Telemetry (Role: ADMIN):                           |
|  ├── GET  /api/v1/admin/operations/status       ──> Full Health & Pool Metrics    |
|  ├── POST /api/v1/admin/operations/verify-models ──> Cryptographic SHA-256 Audit  |
|  └── GET  /api/v1/admin/operations/sync-metrics ──> Synchronization Reliability   |
+-----------------------------------------------------------------------------------+
```

---

## 2. Health & Readiness Probes

### 2.1 Process Liveness (`GET /health/live`)
- **Purpose:** Fast probe for orchestrators to detect deadlocked or frozen event loops.
- **Payload:**
  ```json
  {
    "status": "alive",
    "service": "squish-shield-backend",
    "version": "1.0.0"
  }
  ```

### 2.2 Dependency Readiness (`GET /health/ready`)
- **Purpose:** Validates whether the service can actively serve traffic. Evaluates:
  1. Primary database connectivity (PostgreSQL or SQLite WAL fallback).
  2. Firebase Authentication system initialization.
  3. AI model artifact presence and checksum integrity (`ModelIntegrityService`).
- **Response (200 OK):**
  ```json
  {
    "status": "ready",
    "service": "squish-shield-backend",
    "version": "1.0.0",
    "database": {
      "status": "connected",
      "dialect": "sqlite",
      "wal_enabled": true
    },
    "auth": {
      "firebase_initialized": true,
      "init_mode": "FILE:firebase-service-account.json",
      "mock_auth_active": true
    },
    "models": {
      "status": "HEALTHY",
      "verified_count": 3,
      "total_models": 3
    }
  }
  ```
- **Error Response (503 Service Unavailable):** Returned if the database fails or model files are missing/corrupted.

---

## 3. Privacy-Conscious Structured Logging & Correlation IDs

### 3.1 Request Correlation Tracing
The `CorrelationIdMiddleware` enforces distributed tracing across all HTTP requests:
1. Extracts `X-Correlation-ID` or `X-Request-ID` from incoming requests (or generates a fresh UUIDv4).
2. Sets `request.state.correlation_id` for downstream routes and error handlers.
3. Automatically attaches `X-Correlation-ID` to all HTTP response headers.
4. Includes `correlation_id` in all error responses (`error.correlation_id`).

### 3.2 Structured JSON Log Format
Operational events are emitted in structured JSON:
```json
{
  "correlation_id": "4b68e920-80a1-434a-a7b6-98ecbc48f321",
  "method": "POST",
  "path": "/api/v1/detections",
  "status_code": 201,
  "duration_ms": 14.8,
  "client_ip": "192.168.1.45"
}
```

### 3.3 Privacy & Data-Minimization Rules
- **No Credentials:** Authorization headers, bearer tokens, service account keys, and passwords are never logged.
- **No Raw SMS Bodies:** Message text analyzed on-device is excluded from server logs.
- **Probe Noise Filtering:** Repetitive `/health/live` and `/health` requests are filtered to prevent log volume saturation.

---

## 4. Synchronization Reliability & Visibility

Synchronization health is exposed to administrators via `GET /api/v1/admin/operations/sync-metrics`:

| Metric | Description | Health Threshold |
| :--- | :--- | :--- |
| `total_synced_events` | Total lifetime telemetry detections recorded | Monotonically increasing |
| `events_last_24h` | Rolling 24-hour ingestion volume | Monitored for abnormal spikes |
| `high_risk_events` | Telemetry events classified as `HIGH` severity | Ratio should stay < 20% in normal traffic |
| `blocked_actions` | Total defensive actions where user blocked sender | Indicates active user mitigation |
| `last_synced_at` | Timestamp of most recent telemetry sync | Should update continuously when devices active |

### Idempotency Protection
- Every mobile sync item includes a `client_event_id` UUID generated locally at detection time.
- If network retries resend an item, the backend idempotency filter detects the existing `client_event_id` and safely returns the existing record without duplicating data or skewing analytics.

---

## 5. Security Event Monitoring & Alerts

The operational status endpoint (`GET /api/v1/admin/operations/status`) aggregates security-relevant audit logs:

- **Privilege Changes (`USER_ROLE_UPDATED`):** Tracked whenever a user is promoted or demoted between `USER` and `ADMIN`.
- **Account Suspensions (`USER_STATUS_UPDATED`):** Monitored when user access is revoked.
- **High-Risk Telemetry (`HIGH_RISK_DETECTION_RECORDED`):** Triggers an audit entry when severe smishing/quishing is detected.
- **Model Integrity Audits (`MODEL_INTEGRITY_VERIFIED`):** Audit record created whenever cryptographic model verification is triggered.
- **Rate Limit Breaches:** Sliding window rate limiter logs `429 Too Many Requests` when IPs exceed 120 req/min (users) or 240 req/min (admins).

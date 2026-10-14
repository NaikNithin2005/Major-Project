# Security & Operational Incident Response Plan

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Phase:** Phase 13 (Operational Maintenance & Observability Extension)  
**Security Classification:** Confidential / SOC & DevOps Operations  

---

## 1. Incident Severity Classification & Escalation Matrix

Squish Shield establishes a 4-tier severity rating model for security, data privacy, ML performance, and service reliability incidents:

| Severity | Definition | SLA: Response | SLA: Containment | Authorized Incident Commander |
| :--- | :--- | :--- | :--- | :--- |
| **SEV-1 (Critical)** | Active auth bypass, cross-user data leakage, model compromise, total DB/API outage | < 15 minutes | < 1 hour | Security Officer + Lead DevOps |
| **SEV-2 (High)** | Elevated false negatives in detection, sync backlog > 10,000, secondary DB failure | < 30 minutes | < 4 hours | Senior Backend Engineer / ML Lead |
| **SEV-3 (Medium)**| High false-positive rate on benign URLs, degraded API latency (> 500ms), worker crash | < 2 hours | < 12 hours | On-Call Engineer |
| **SEV-4 (Low)** | Minor UI reporting discrepancy, non-critical telemetry missing | < 8 hours | < 48 hours | Standard Development Backlog |

---

## 2. Standard Operating Procedures by Scenario

### SOP-01: Suspected Credential Exposure (Firebase Service Account, DB Passwords, JWT Secrets)
- **Detection Signal:** Git secret scanning alert, unexpected authentication attempts from foreign IP ranges, or unauthorized administrative API calls.
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Freeze suspect service account in Google Cloud Console / Firebase IAM immediately.
  2. Invalidate active session tokens; cycle `SECRET_KEY` and database credentials in production `.env`.
  3. Restrict database security groups / firewall rules to strict internal CIDR blocks.
- **Investigation Steps:**
  1. Inspect PostgreSQL query logs and audit trails (`GET /api/v1/audit/logs`) for the compromised credential identifier.
  2. Correlate with Cloudflare/Reverse Proxy access logs using `X-Correlation-ID`.
- **Recovery Steps:**
  1. Provision new Google Service Account private key JSON and rotate into production environment variables.
  2. Deploy configuration update with zero-downtime rolling restart.
- **Verification:** Confirm authentication returns HTTP 200 with new credentials and fails with old credentials.
- **Evidence Preservation:** Archive compromised credential access logs, IP sources, and timestamps in encrypted audit vault.
- **Follow-up Actions:** Conduct post-mortem review of CI/CD secrets injection and developer workstation security.

---

### SOP-02: Authentication or Authorization Bypass
- **Detection Signal:** Requests to `/api/v1/admin/*` succeeding without `ADMIN` role claim, or unauthenticated requests passing `/api/v1/detections`.
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Enable WAF rule blocking administrative routes at the reverse proxy if exploit is remote.
  2. Verify backend `ENVIRONMENT=production` to ensure mock auth bypass is disabled.
- **Investigation Steps:**
  1. Check token verification routines in `backend/app/core/auth.py`.
  2. Inspect HTTP access logs for manipulation of `Authorization` bearer tokens or Firebase UID forging.
- **Recovery Steps:**
  1. Hotfix RBAC dependency injection checks in FastAPI router.
  2. Run `backend/tests/test_phase10_security_audit.py` to ensure zero regression.
- **Verification:** Execute admin route tests with invalid tokens and non-admin tokens; assert HTTP 401 and HTTP 403.
- **Evidence Preservation:** Snapshot affected container memory and server request logs.
- **Follow-up Actions:** Audit all dependency injection endpoints in `backend/app/api/v1/`.

---

### SOP-03: Cross-User Data Leakage
- **Detection Signal:** User reports viewing detection history or device telemetry belonging to another user, or discrepancy in `user_id` query filters.
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Temporarily pause user detection history retrieval endpoints (`GET /api/v1/detections/my`).
  2. Flush application-level caching or CDN reverse proxy cache if enabled.
- **Investigation Steps:**
  1. Inspect `backend/app/repositories/detection_repo.py` queries to confirm mandatory `current_user.id` scoping.
  2. Review offline sync endpoint `POST /api/v1/detections/sync` to verify incoming payload ownership enforcement.
- **Recovery Steps:**
  1. Enforce strict repository filter: `filter(DetectionEvent.user_id == authenticated_user_id)`.
  2. Verify no shared mutable state in FastAPI dependency injection providers.
- **Verification:** Execute multi-tenant privacy test: authenticate as User A, request User B's detection ID; verify HTTP 404/403.
- **Evidence Preservation:** Export database query logs showing affected account IDs and timestamps.
- **Follow-up Actions:** Notify Data Protection Officer (DPO) within 72 hours per regulatory compliance if PII was accessed.

---

### SOP-04: Malicious or Corrupted Model Artifact
- **Detection Signal:** `GET /health/ready` reports `"models": {"status": "unhealthy"}` or `ModelIntegrityService` throws `IntegrityError` (checksum mismatch).
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Halt model loading; fail closed. Admin operations and detection APIs reject unverified model inference.
  2. Isolate server filesystem or edge asset distribution bucket.
- **Investigation Steps:**
  1. Compare filesystem SHA-256 hash against `models/MODEL_MANIFEST.json`.
  2. Check file modification timestamps and SSH/container audit logs for unauthorized file replacement.
- **Recovery Steps:**
  1. Restore certified ONNX artifacts from immutable, signed release tag in Git/S3.
  2. Trigger `POST /api/v1/admin/operations/verify-models` to confirm valid checksums across all 3 models.
- **Verification:** Confirm all three models report `verified: true` and `/health/ready` returns HTTP 200.
- **Evidence Preservation:** Retain the altered model file in an isolated quarantine directory for malware and adversarial analysis.
- **Follow-up Actions:** Lock filesystem permissions on `models/` directory to read-only (`chmod 444`).

---

### SOP-05: Broken Detection Pipeline (Crashing Inference)
- **Detection Signal:** Surge in HTTP 500 errors on `/api/v1/threats/analyze` or Android crash reports during ONNX `session.run()`.
- **Severity:** SEV-2 (High).
- **Immediate Containment:**
  1. On Android: Fall back to deterministic heuristic regex engine (`url_feature_extractor.py` and rule-based filter) without crashing.
  2. On Backend: Verify worker processes are recycling cleanly without memory leaks.
- **Investigation Steps:**
  1. Inspect error logs with `correlation_id` matching inference failures.
  2. Validate input shapes: TinyBERT requires `input_ids`, `attention_mask` shape `[1, 128]`; XGBoost requires `[1, 16]`.
- **Recovery Steps:**
  1. Correct input validation schemas in FastAPI request bodies and Android JNI wrappers.
- **Verification:** Run test suite `scripts/evaluate_models_phase13.py` and unit tests in `backend/tests/`.
- **Evidence Preservation:** Archive the specific input payloads causing the crash (with privacy scrubbing).
- **Follow-up Actions:** Add negative fuzz-testing for malformed URLs and exotic Unicode SMS characters.

---

### SOP-06: High False-Positive Rate (Benign Content Flagged as Malicious)
- **Detection Signal:** User reports of standard banking SMS (OTP codes) or popular QR codes being blocked.
- **Severity:** SEV-3 (Medium).
- **Immediate Containment:**
  1. Add high-reputation domains (e.g. `*.gov.in`, `*.bank.com`) to edge whitelist cache via configuration update.
  2. Adjust risk score threshold temporarily if an aggressive rule triggered false alarms.
- **Investigation Steps:**
  1. Collect sample false-positive tokens and evaluate against `models/MODEL_MANIFEST.json` baseline.
  2. Check if a newly registered TLD or feature weight in XGBoost over-indexed on generic keywords.
- **Recovery Steps:**
  1. Retrain model offline using balanced dataset containing recent banking SMS templates.
  2. Follow Controlled Model Update Pipeline (Section 5 of `docs/model-lifecycle.md`).
- **Verification:** Validate false-positive rate (FPR) <= 0.01 on benchmark test suite.
- **Evidence Preservation:** Save verified benign samples into false-positive regression test suite.
- **Follow-up Actions:** Expand edge allowlist and tokenizer vocab for multi-lingual transactional SMS.

---

### SOP-07: Increased False-Negative Rate (Threats Missed)
- **Detection Signal:** Zero-day smishing campaign reported by threat intelligence feeds bypassing local detector.
- **Severity:** SEV-2 (High).
- **Immediate Containment:**
  1. Push emergency regex heuristic pattern update to Android client configuration / backend threat intelligence feed.
- **Investigation Steps:**
  1. Analyze evasive smishing text (e.g., zero-width spaces, Cyrillic homoglyphs, brand-new obfuscation).
  2. Extract features using `url_feature_extractor.py` and evaluate why XGBoost/Isolation Forest scored low risk.
- **Recovery Steps:**
  1. Update homoglyph normalization in feature extractor.
  2. Schedule supervised fine-tuning of TinyBERT.
- **Verification:** Assert that previously evasive samples score `MALICIOUS` (risk score > 75).
- **Evidence Preservation:** Store anonymized campaign indicators in threat intelligence archive.
- **Follow-up Actions:** Update dataset generator with new adversarial augmentation strategies.

---

### SOP-08: Database Outage or Unreachability
- **Detection Signal:** Backend logs `OperationalError: connection to server at localhost:5432 failed` and `/health/ready` returns HTTP 503.
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Verify backend automatic fallback to local SQLite WAL database is active (edge-to-cloud resilience).
  2. Alert DB Administrator; inspect PostgreSQL daemon status.
- **Investigation Steps:**
  1. Check disk space (`df -h`), memory consumption (`free -m`), and PostgreSQL log files (`/var/log/postgresql/`).
  2. Check connection pool exhaustion (`pg_stat_activity`).
- **Recovery Steps:**
  1. Free disk space / restart PostgreSQL service (`systemctl restart postgresql`).
  2. Once PostgreSQL recovers, backend reconnects automatically.
- **Verification:** Query `GET /health/ready`; assert `"database": {"status": "connected", "dialect": "postgresql"}`.
- **Evidence Preservation:** Retain PostgreSQL crash logs and core dumps if generated.
- **Follow-up Actions:** Configure connection pooling limits in `backend/app/core/database.py` and disk auto-expansion.

---

### SOP-09: Synchronization Backlog (Events Queued on Mobile / Bulk Sync Slowdown)
- **Detection Signal:** `GET /api/v1/admin/operations/sync-metrics` reports anomalous spike in failed syncs or zero successful uploads in 24h.
- **Severity:** SEV-2 (High).
- **Immediate Containment:**
  1. Ensure mobile Room DB queue is bounded and does not purge records.
  2. Scale backend worker instances to handle batch ingestion.
- **Investigation Steps:**
  1. Check backend database write latency and index performance on `detection_events(sync_timestamp)`.
  2. Inspect mobile error logs for HTTP 429 (rate limited) or HTTP 400 (schema mismatch).
- **Recovery Steps:**
  1. Ensure idempotency via `device_detection_id` prevents duplicate insertions during mobile retries.
  2. Enable exponential backoff on Android WorkManager (1m, 2m, 4m, 8m).
- **Verification:** Observe queue draining to zero on active Android test devices.
- **Evidence Preservation:** Log sync failure error codes and device OS versions.
- **Follow-up Actions:** Optimize batch commit sizes in `backend/app/repositories/detection_repo.py`.

---

### SOP-10: Suspected Data Loss or Corruption
- **Detection Signal:** Primary key gaps, foreign key constraint failures, or database table corruption errors.
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Place backend in read-only maintenance mode.
  2. Take immediate physical disk snapshot of current database state.
- **Investigation Steps:**
  1. Inspect PostgreSQL WAL status and check table consistency (`pg_dump -t detection_events -s`).
- **Recovery Steps:**
  1. Execute Point-In-Time Recovery (PITR) per `docs/backup-recovery.md`.
  2. Replay uncommitted mobile client events via offline sync replay.
- **Verification:** Run integrity audit script and verify row counts match expected audit event totals.
- **Evidence Preservation:** Store damaged table images in forensically sound cold storage.
- **Follow-up Actions:** Verify automated nightly backup cron and integrity verification scripts.

---

### SOP-11: Total Service Outage (Backend API Down)
- **Detection Signal:** Orchestrator health check failure, synthetic ping timeout (> 5000ms), HTTP 502/503 from reverse proxy.
- **Severity:** SEV-1 (Critical).
- **Immediate Containment:**
  1. Verify edge Android devices remain 100% operational in offline-first mode (local ONNX detection continues uninterrupted).
  2. Spin up standby backend container instance.
- **Investigation Steps:**
  1. Inspect Uvicorn process status, exit codes, and out-of-memory (OOM) killer events (`dmesg -T`).
  2. Check port collisions (e.g., Port 8000 conflict with host services).
- **Recovery Steps:**
  1. Restart backend container on designated verified port (e.g. 8001 or 8000 per environment configuration).
  2. Restore reverse proxy upstream routing.
- **Verification:** Execute `GET /health/live` and `GET /health/ready`; verify HTTP 200 within 5 seconds.
- **Evidence Preservation:** Retain systemd journal logs (`journalctl -u squish-shield-backend -n 500`).
- **Follow-up Actions:** Implement redundant backend process supervision (systemd auto-restart / Docker health restart policy).

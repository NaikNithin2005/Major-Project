# Scheduled Maintenance, Database Tuning & Automation Runbook

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Phase:** Phase 13 (Operational Maintenance & Observability Extension)  
**Security Classification:** Confidential / System Administration  

---

## 1. Routine Maintenance Schedule

| Cadence | Task | Target Component | Automation / Command | Verification Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **Daily** | Liveness & Readiness Probes | Backend API | Orchestrator HTTP Check | HTTP 200 on `/health/ready` |
| **Daily** | Backup & Integrity Check | PostgreSQL / SQLite | Nightly Cron Script | `gzip -t` & `pg_restore -l` exit 0 |
| **Weekly** | Model Integrity Audit | AI / ONNX Models | `POST /api/v1/admin/operations/verify-models` | All 3 models `verified: true` |
| **Weekly** | Sync Backlog & Metrics | Offline Sync Engine | `GET /api/v1/admin/operations/sync-metrics` | Failures <= 1%, queue age < 24h |
| **Monthly** | Database Index & Query Audit | PostgreSQL | `VACUUM ANALYZE` & Index Scans | Index bloat < 15%, query p95 < 20ms |
| **Monthly** | Dependency Vulnerability Scan | Python / NPM / Gradle | `pip-audit`, `npm audit` | Zero Critical/High CVEs |
| **Quarterly** | Full ML Benchmark Evaluation | AI Models & Datasets | `python scripts/evaluate_models_phase13.py` | TinyBERT F1 >= 0.88, XGBoost F1 >= 0.98 |
| **Quarterly** | Disaster Recovery Restoration Drill | Staging DB | Staging Restore Script | Row counts & schema parity verified |

---

## 2. Database Maintenance & Index Health

### 2.1 Index Health & Vacuuming (PostgreSQL)
High-throughput telemetry ingestion can cause index bloat on `detection_events` and `audit_logs`.

```sql
-- Analyze query execution planner statistics
VACUUM (VERBOSE, ANALYZE) detection_events;
VACUUM (VERBOSE, ANALYZE) audit_logs;

-- Check for missing or underutilized indexes
SELECT
    schemaname, relname, indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM
    pg_stat_user_indexes
ORDER BY
    idx_scan ASC
LIMIT 10;
```

### 2.2 Connection Pool Monitoring
When running under high API concurrency, monitor connection pool saturation via:
```sql
SELECT
    count(*) AS total_connections,
    count(*) FILTER (WHERE state = 'active') AS active_connections,
    count(*) FILTER (WHERE state = 'idle') AS idle_connections
FROM
    pg_stat_activity
WHERE
    datname = 'squish_shield_db';
```
If `active_connections` approaches `max_connections` (default 100), adjust `pool_size` (default 10) and `max_overflow` (default 20) in `backend/app/core/database.py`.

### 2.3 SQLite WAL Checkpoint Maintenance (Dev / Edge Fallback)
When running the SQLite fallback engine, SQLite automatically writes to `-wal` files. Periodically truncate WAL logs:
```bash
sqlite3 squish_shield_dev.db "PRAGMA wal_checkpoint(TRUNCATE);"
```

---

## 3. Safe Migration Workflow

All database schema migrations must use Alembic. Never execute direct `ALTER TABLE` DDL queries against production databases.

### 3.1 Migration Verification Steps
```bash
# Step 1: Check current migration head
cd backend
alembic current

# Step 2: Validate pending migrations without applying
alembic heads

# Step 3: Test migration dry-run on disposable copy
PGDATABASE=squish_shield_restore_test alembic upgrade head

# Step 4: Apply migration during low-traffic maintenance window
alembic upgrade head
```

---

## 4. Maintenance Automation Scripts

### 4.1 Dependency Vulnerability Audit
```bash
# Python Backend Vulnerability Check
pip-audit --desc on

# Web Dashboard Vulnerability Check
cd web && npm audit --audit-level=high
```

### 4.2 Automated Release & Regression Check
Run prior to any minor version bump:
```bash
# Run complete test suite (Unit, Security, Performance, Phase 13 Ops)
pytest backend/tests/ -v

# Run AI model verification benchmark
python scripts/evaluate_models_phase13.py

# Run web test suite
cd web && npm test -- --run
```

# Database Backup, Disaster Recovery & Integrity Verification Runbook

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Phase:** Phase 13 (Operational Maintenance & Observability Extension)  
**Security Classification:** Confidential / Database Operations  

---

## 1. Overview & Data Architecture

The Squish Shield database layer stores user accounts, device telemetry, detection metadata, and immutable audit logs. In production, PostgreSQL 15+ is the primary persistence engine, with a resilient local SQLite WAL engine utilized in constrained or edge-isolated environments.

Due to the edge-first design, raw SMS message bodies are **never persisted** to the cloud or database. Backups encompass:
- User accounts and roles (`users`)
- Device registrations (`user_devices`)
- Threat detection telemetry and risk scores (`detection_events`)
- System and administrative audit events (`audit_logs`)

---

## 2. Backup Strategy & Scheduling

| Backup Type | Frequency | Target Storage | Retention Window | Encryption |
| :--- | :--- | :--- | :--- | :--- |
| **Full Logical Backup (`pg_dump`)** | Daily at 02:00 UTC | Encrypted S3 / Cloud Storage | 30 days | AES-256 (GPG / KMS) |
| **Physical WAL Archiving (PITR)** | Continuous (15-min segments) | Separate Disaster Recovery Bucket | 7 days | AES-256 |
| **Local SQLite Snapshot** | Daily at 03:00 UTC | Secured local backup directory | 14 days | Local disk encryption |

---

## 3. Operational Backup Procedures

### 3.1 Automated PostgreSQL Backup Command
```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
BACKUP_DIR="/var/backups/squish_shield"
BACKUP_FILE="${BACKUP_DIR}/squish_shield_${BACKUP_TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

# Execute clean, custom-compressed PostgreSQL dump
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
  -h "${POSTGRES_HOST:-localhost}" \
  -p "${POSTGRES_PORT:-5432}" \
  -U "${POSTGRES_USER:-squish_admin}" \
  -d "${POSTGRES_DB:-squish_shield_db}" \
  --format=custom \
  --no-owner \
  --no-privileges \
  | gzip -9 > "${BACKUP_FILE}"

echo "Backup completed: ${BACKUP_FILE} (Size: $(stat -c%s "${BACKUP_FILE}") bytes)"
```

### 3.2 Backup Integrity Validation (Mandatory Check)
A backup file cannot be assumed valid merely because `pg_dump` exited with status 0. The integrity check tests the archive structure and header:

```bash
# Verify gzip archive integrity
gzip -t "${BACKUP_FILE}"

# Test PostgreSQL custom archive table of contents
gunzip -c "${BACKUP_FILE}" | pg_restore --list > /dev/null

echo "Backup integrity verified: Archive header and TOC valid."
```

### 3.3 SQLite Local Fallback Snapshot Procedure
When running SQLite in development or lightweight staging:
```bash
sqlite3 squish_shield_dev.db ".backup 'squish_shield_backup.db'"
sqlite3 squish_shield_backup.db "PRAGMA integrity_check;"
```
Output must return `ok`.

---

## 4. Disaster Recovery & Restoration Procedures

### 4.1 Prerequisites & Access Requirements
- PostgreSQL administrative credentials (`POSTGRES_USER`, `POSTGRES_PASSWORD`).
- Target server with PostgreSQL 15+ installed and running.
- Network access authorized from backend application IP.

### 4.2 Safe Restoration Protocol (Disposable Staging Validation)
Never restore directly over active production tables without validating against a disposable database first:

```bash
# Step 1: Create disposable test database
PGPASSWORD="${POSTGRES_PASSWORD}" createdb \
  -h localhost -U squish_admin squish_shield_restore_test

# Step 2: Restore from compressed backup
gunzip -c "${BACKUP_FILE}" | PGPASSWORD="${POSTGRES_PASSWORD}" pg_restore \
  -h localhost \
  -U squish_admin \
  -d squish_shield_restore_test \
  --clean \
  --if-exists

# Step 3: Run Row Count & Schema Validation Query
PGPASSWORD="${POSTGRES_PASSWORD}" psql \
  -h localhost -U squish_admin -d squish_shield_restore_test -c "
    SELECT 'users' AS tbl, count(*) FROM users
    UNION ALL
    SELECT 'detection_events', count(*) FROM detection_events
    UNION ALL
    SELECT 'audit_logs', count(*) FROM audit_logs;
  "

# Step 4: Drop disposable test database once verified
PGPASSWORD="${POSTGRES_PASSWORD}" dropdb \
  -h localhost -U squish_admin squish_shield_restore_test
```

### 4.3 Production Database Restoration (Authorized Disaster Recovery)
If recovering from an outage:
1. Stop backend services: `systemctl stop squish-shield-backend`.
2. Terminate active PostgreSQL client connections:
   ```sql
   SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
   WHERE datname = 'squish_shield_db' AND pid <> pg_backend_pid();
   ```
3. Drop and recreate production database:
   ```bash
   dropdb -h localhost -U squish_admin squish_shield_db
   createdb -h localhost -U squish_admin squish_shield_db
   ```
4. Restore verified backup:
   ```bash
   gunzip -c "${BACKUP_FILE}" | pg_restore -h localhost -U squish_admin -d squish_shield_db
   ```
5. Apply any pending Alembic migrations:
   ```bash
   cd backend && alembic upgrade head
   ```
6. Restart backend services: `systemctl start squish-shield-backend`.
7. Execute health check: `curl -s http://localhost:8001/health/ready`.

---

## 5. Roles & Recovery Responsibilities

| Role | Responsibility |
| :--- | :--- |
| **Lead DevOps Engineer** | Maintains backup cron scripts, rotation policies, and encrypted storage permissions. |
| **Database Administrator** | Executes quarterly disaster recovery drills and verifies Point-In-Time Recovery WAL replication. |
| **Security Officer** | Audits backup access logs and manages KMS encryption key rotation. |
| **On-Call Engineer** | Executes recovery procedures in accordance with SOP-08 and SOP-10 during incidents. |

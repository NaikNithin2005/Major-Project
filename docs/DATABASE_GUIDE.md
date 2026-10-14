# PostgreSQL Database & Migration Guide

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Database Dialect:** PostgreSQL 15+ (Production) | SQLite (Local Test Fallback)  
**ORM:** SQLAlchemy 2.0  
**Migration Framework:** Alembic  
**Current Schema Revision Head:** `003_add_user_action_and_phone`

---

## 1. Schema Tables & Relationships

```
  +------------------+         +----------------------+
  |      users       | <------+       devices        |
  +------------------+ 1     * +----------------------+
  | PK id (UUID)     |         | PK id (UUID)         |
  | firebase_uid(UQ) |         | FK user_id           |
  | email (UQ)       |         | device_identifier(UQ)|
  | role (USER/ADMIN)|         | platform             |
  +--------+---------+         +----------------------+
           | 1
           |
           +-----------------------+
           | 1                     | 1
           v *                     v *
  +----------------------+  +----------------------+
  |    threat_events     |  |      audit_logs      |
  +----------------------+  +----------------------+
  | PK id (UUID)         |  | PK id (UUID)         |
  | FK user_id           |  | FK actor_user_id     |
  | FK device_id         |  | action               |
  | event_type (SMS/QR)  |  | resource_type        |
  | threat_category      |  | ip_address           |
  | risk_score (0-100)   |  | metadata (JSON)      |
  | confidence_score     |  +----------------------+
  | source_identifier    |
  | user_action          |
  | phone_number         |
  +----------+-----------+
             | 1
             v *
  +----------------------+
  |       feedback       |
  +----------------------+
  | PK id (UUID)         |
  | FK user_id           |
  | FK threat_event_id   |
  | feedback_type        |
  | user_comments        |
  +----------------------+
```

### Table Definitions

1. **`users`**:
   - `id`: UUID (Primary Key, default uuid4).
   - `firebase_uid`: String(128), unique, indexed, not null.
   - `email`: String(255), unique, indexed, not null.
   - `display_name`: String(255), nullable.
   - `role`: String(32), default `"USER"` (`USER` or `ADMIN`).
   - `is_active`: Boolean, default `True`.
   - `created_at` / `updated_at`: DateTime(timezone=True).

2. **`devices`**:
   - `id`: UUID (Primary Key).
   - `user_id`: UUID, Foreign Key (`users.id`, ondelete="CASCADE"), indexed.
   - `device_identifier`: String(128), unique per user constraint, not null.
   - `platform`: String(128), e.g., `"Android 15 (Pixel 8)"`.
   - `app_version`: String(64).
   - `model_version`: String(128).
   - `last_sync_at`: DateTime(timezone=True).

3. **`threat_events`**:
   - `id`: UUID (Primary Key).
   - `user_id`: UUID, Foreign Key (`users.id`, ondelete="CASCADE"), indexed.
   - `device_id`: UUID, Foreign Key (`devices.id`, ondelete="SET NULL"), nullable.
   - `event_type`: String(16), indexed (`"SMS"` or `"QR"`).
   - `threat_category`: String(32), indexed (`"SAFE"`, `"LOW"`, `"MEDIUM"`, `"HIGH"`).
   - `risk_score`: Integer, check constraint (0 <= score <= 100).
   - `confidence_score`: Float (0.0 to 1.0).
   - `source_identifier`: String(512), hashed or masked sender / domain.
   - `threat_indicators`: JSON / ARRAY of strings.
   - `model_version`: String(64).
   - `inference_latency_ms`: Float.
   - `user_action`: String(64), e.g., `"BLOCKED"`, `"COPIED_CLEAN"`, `"IGNORED"`.
   - `phone_number`: String(64), nullable.
   - `status`: String(32), default `"NEW"` (`"NEW"`, `"REVIEWED"`, `"RESOLVED"`, `"FALSE_POSITIVE"`).
   - `client_timestamp` / `created_at`: DateTime(timezone=True).

4. **`feedback`**:
   - `id`: UUID (Primary Key).
   - `user_id`: UUID, Foreign Key (`users.id`).
   - `threat_event_id`: UUID, Foreign Key (`threat_events.id`, ondelete="CASCADE").
   - `feedback_type`: String(32) (`"TRUE_POSITIVE"`, `"FALSE_POSITIVE"`, `"USER_FLAG"`).
   - `user_comments`: Text.

5. **`model_info`**:
   - `id`: UUID (Primary Key).
   - `model_name`: String(64), indexed.
   - `version`: String(32).
   - `model_type`: String(32) (`"NLP_TRANSFORMER"`, `"TREE_ENSEMBLE"`, `"ANOMALY_DETECTOR"`).
   - `file_hash`: String(64) (SHA-256 checksum).
   - `accuracy`: Float.
   - `latency_ms`: Float.
   - `is_active`: Boolean, default `True`.

6. **`audit_logs`**:
   - `id`: UUID (Primary Key).
   - `action`: String(64), indexed (e.g. `"USER_ROLE_UPDATED"`, `"THREAT_STATUS_UPDATED"`).
   - `resource_type`: String(64), indexed.
   - `actor_user_id`: UUID, Foreign Key (`users.id`, ondelete="SET NULL"), nullable.
   - `resource_id`: String(128).
   - `ip_address`: String(45).
   - `metadata`: JSON.
   - `timestamp`: DateTime(timezone=True).

7. **`threat_intel`**:
   - `id`: UUID (Primary Key).
   - `indicator_type`: String(32) (`"DOMAIN"`, `"URL_PATTERN"`, `"PHONE_PREFIX"`).
   - `indicator_value`: String(512), indexed, unique.
   - `confidence`: Float.
   - `threat_level`: String(32).
   - `source`: String(128).
   - `is_active`: Boolean.

---

## 2. Alembic Migration History

| Revision ID | Description | Created Phase | Status |
| :--- | :--- | :--- | :--- |
| `001_initial_schema` | Core schema (`users`, `devices`, `threat_events`, `feedback`, `model_info`, `audit_logs`) | Phase 7 | Applied |
| `002_add_threat_intel` | Added `threat_intel` table for reputation lists and IOC matching | Phase 8 | Applied |
| `003_add_user_action_and_phone` | Added `user_action` and `phone_number` columns to `threat_events` | Phase 12 | **Current Head** |

---

## 3. Migration Commands & Verification

### Run Migrations to Latest Head
```bash
cd backend
# With virtual environment active
alembic upgrade head
```

### Inspect Current Revision
```bash
alembic current
```

### Inspect Available Migration Heads
```bash
alembic heads
# Expected Output:
# 003_add_user_action_and_phone (head)
```

### Downgrade One Revision (Emergency Rollback)
```bash
alembic downgrade -1
```

> **CRITICAL RULE:** Never execute `Base.metadata.drop_all()` in production environments. All structural database changes must be versioned through Alembic migrations.

---

## 4. Production Backup & Restoration Procedures

### 4.1 Automated Daily Database Backup
Execute a binary custom-format dump preserving all constraints, indexes, and blobs:
```bash
# Set credentials securely in environment
export PGPASSWORD="YOUR_POSTGRES_PASSWORD"

# Execute pg_dump
pg_dump \
  -h localhost \
  -p 5432 \
  -U squish_admin \
  -F c \
  -b \
  -v \
  -f "/var/backups/squish_shield_$(date +%Y%m%d_%H%M%S).dump" \
  squish_shield_db
```

### 4.2 Database Restoration
To restore a snapshot into a clean target database:
```bash
export PGPASSWORD="YOUR_POSTGRES_PASSWORD"

# Restore using pg_restore with clean/overwrite option
pg_restore \
  -h localhost \
  -p 5432 \
  -U squish_admin \
  -d squish_shield_db \
  --clean \
  --if-exists \
  -v \
  "/var/backups/squish_shield_20261013_120000.dump"
```

### 4.3 Migration Rollback Limitations
- Migrations that drop columns (destructive changes) cannot automatically recover lost data upon rollback; always create a database backup prior to applying major migrations.
- Before running `alembic upgrade head` in production, execute the upgrade in a staging environment connected to a recent production backup copy.

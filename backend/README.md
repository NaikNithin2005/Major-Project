# Squish Shield — Centralized Backend API (FastAPI & PostgreSQL)

Production-oriented centralized backend for **Squish Shield — Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System**.

---

## 1. Architectural Highlights
- **Decoupled & Offline-First**: All on-device AI detection (TinyBERT, XGBoost, Isolation Forest, and Risk Engine) executes 100% locally on Android. Local detection never relies on backend availability.
- **Data Minimization & Privacy**: Minimized structured telemetry ingestion. **Raw SMS messages are never uploaded or persisted on the server**.
- **Security & RBAC**: Bearer token authentication via Firebase ID Token verification with Role-Based Access Control (`USER` vs `ADMIN`). Standardized error envelope preventing SQL/filesystem leakage.
- **Storage Layer**: PostgreSQL with SQLAlchemy 2.0 ORM, Alembic migrations, connection pooling (`pool_size`, `max_overflow`, `pool_pre_ping`), and JSONB metadata storage.
- **Traffic Protection**: In-memory sliding-window rate limiting per client IP.

---

## 2. Prerequisites
- Python 3.11+ (Tested on Python 3.13)
- PostgreSQL 15+ (or Docker / Docker Compose)
- Git

---

## 3. Environment Configuration
Create your `.env` file based on `.env.example`:
```bash
cp .env.example .env
```

Key environment variables:
| Variable | Description | Default |
|---|---|---|
| `ENVIRONMENT` | Runtime environment (`development`, `testing`, `production`) | `development` |
| `DATABASE_URL` | PostgreSQL connection string | `postgresql://postgres:postgres_dev_password@localhost:5432/quishing_smishing_db` |
| `API_PREFIX` | Base route prefix | `/api` |
| `JWT_SECRET_KEY` | HMAC key for developer/test token verification | `dev-jwt-secret-key-squish-shield-phase7-secure-token-32-chars` |
| `FIREBASE_PROJECT_ID`| Firebase project identifier | `squish-shield` |
| `FIREBASE_CREDENTIALS`| Path to service account JSON (optional in dev) | `None` |
| `ALLOW_MOCK_AUTH` | Enable test token verification (`mock-user-token`, `mock-admin-token`)| `true` (dev/test only) |
| `CORS_ORIGINS` | Comma-separated allowed CORS origins | `http://localhost:3000,http://127.0.0.1:3000` |
| `RATE_LIMIT` | Max allowed requests per minute per IP | `60` |

---

## 4. Local Installation & Setup

1. **Create and activate a virtual environment**:
   ```bash
   python -m venv venv
   # On Windows (PowerShell):
   .\venv\Scripts\Activate.ps1
   # On Linux/macOS:
   source venv/bin/activate
   ```

2. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

3. **Database Migrations (Alembic)**:
   ```bash
   # Apply latest database schema
   alembic upgrade head

   # Rollback one migration if needed
   alembic downgrade -1
   ```

4. **Start Development Server**:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

5. **Interactive API Documentation**:
   - Swagger UI: [http://localhost:8000/docs](http://localhost:8000/docs)
   - ReDoc: [http://localhost:8000/redoc](http://localhost:8000/redoc)
   - OpenAPI Schema: [http://localhost:8000/openapi.json](http://localhost:8000/openapi.json)

---

## 5. Docker Deployment
Run both PostgreSQL and FastAPI backend using Docker Compose:
```bash
docker compose up -d --build
```
This provisions:
- `squish_shield_db`: PostgreSQL 15 on port `5432` with health checks.
- `squish_shield_backend`: FastAPI container on port `8000`.

---

## 6. API Route Catalog

### System & Health
- `GET /`: Service identification & status
- `GET /health`: Liveness probe (`{"status": "ok"}`)
- `GET /health/db`: Readiness probe validating database connectivity (`{"status": "ok", "database": "connected"}`)

### Authentication & Users
- `POST /api/v1/auth/verify`: Verify Bearer/Firebase token and return user profile
- `GET /api/v1/auth/me`: Current user info
- `GET /api/v1/users/me`: Current user profile
- `PATCH /api/v1/users/me`: Update display name (immutable role/firebase_uid)
- `POST /api/v1/users/devices`: Register/update device telemetry info
- `GET /api/v1/users/devices`: List registered devices for current user

### Telemetry & Detections
- `POST /api/v1/detections`: Ingest structured Phase 6 detection telemetry (strictly sanitized, 0-100 score, HIGH/MED/LOW/SAFE)
- `GET /api/v1/detections`: Paginated and filtered detections for authenticated user (user isolated)
- `GET /api/v1/detections/{id}`: Detailed threat detection (user isolated)

### Feedback
- `POST /api/v1/feedback`: Submit user feedback on detections (`TRUE_POSITIVE`, `FALSE_POSITIVE`, etc.)
- `GET /api/v1/feedback`: List feedback history (user isolated)

### Analytics
- `GET /api/v1/analytics/summary`: Aggregate counts (total, SAFE, LOW, MEDIUM, HIGH, SMS, QR, avg risk score)
- `GET /api/v1/analytics/trends`: Lookback trend distributions over time

### AI Models Metadata
- `GET /api/v1/models`: List active AI models and versions (`TinyBERT`, `XGBoost`, `Isolation Forest`)
- `GET /api/v1/models/{id}`: View model metadata and artifact hashes

### Administration (`ADMIN` Role Required)
- `GET /api/v1/admin/users`: Paginated user list
- `GET /api/v1/admin/threat-events`: Cross-system threat monitoring
- `POST /api/v1/admin/models`: Register / update model metadata
- `GET /api/v1/admin/audit-logs`: Search and retrieve system audit logs
- `GET /api/v1/admin/analytics/summary`: Global system-wide telemetry summary

---

## 7. Testing
Run the complete backend test suite:
```bash
pytest backend/tests -v
```
All 44 test cases cover:
- Health & OpenAPI documentation endpoints
- Database models, foreign keys, cascade rules, and check constraints
- Token verification, 401 Unauthorized handling, and auto-provisioning
- RBAC 403 Forbidden enforcement on administrative resources
- Detection ingestion with data minimization (raw SMS stripped)
- User isolation & IDOR prevention
- Feedback submissions and permissions
- Analytics calculation & trend generation
- Sliding-window rate limiting & health exemptions
- Security testing: SQL injection parameterization, mass assignment defense, and information leakage prevention.

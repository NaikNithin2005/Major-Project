# Security, Privacy & Data Handling Guide

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Classification:** Defensive Cybersecurity & Privacy Architecture  
**Guiding Principle:** Offline-First Zero-Trust Edge Processing with Strict Data Minimization  

---

## 1. Privacy Architecture & Data Minimization

The primary privacy objective of the Squish Shield system is to protect end users against phishing attacks without compromising their personal communications.

### 1.1 SMS Data Handling Rules
- **No Cloud Transmission of Message Bodies:** Incoming SMS message content is **NEVER** transmitted over the network to the backend or third-party APIs.
- **On-Device Ingestion & Inference:** The Android `BroadcastReceiver` intercepts SMS messages directly in the mobile OS sandbox. Tokenization via WordPiece and inference via TinyBERT run entirely inside the local ONNX Runtime process.
- **Local Sandbox Storage:** Message history is stored only in the private Android Room SQLite database (`app_database.db`), protected by Linux user-isolation permissions.
- **Telemetry Anonymization:** When synchronization occurs, the outgoing payload contains only:
  - Event type (`"SMS"`)
  - Threat category (`"HIGH"`, `"MEDIUM"`, `"LOW"`, `"SAFE"`)
  - Numeric risk score (0-100) and confidence score (0.0-1.0)
  - Masked or hashed sender identifier (e.g. `+1***-***-0199` or SHA-256 digest)
  - Abstract heuristic triggers (e.g. `"URGENT_CALL_TO_ACTION"`, `"BANK_IMPERSONATION"`)
  - Defensive action taken by the user (e.g. `"BLOCKED"`, `"IGNORED"`)

### 1.2 QR Code & URL Data Handling Rules
- **Camera Frame Privacy:** Camera video frames processed by CameraX and ML Kit / ZXing barcode decoders are analyzed in-memory and immediately discarded. No camera footage or images are stored or transmitted.
- **URL Lexical Processing:** URL string features (Shannon entropy, length, TLD) are extracted locally. If a URL is flagged as malicious, only the domain and sanitized path structure are transmitted in telemetry reports.

---

## 2. Authentication & Authorization (RBAC)

### 2.1 Multi-Tenant Isolation
- Every authenticated request requires a valid Firebase Bearer ID Token.
- The backend resolves the token to an authoritative user record in PostgreSQL.
- Database queries for detections, devices, and feedback are strictly scoped by `WHERE user_id = :current_user_id`.
- Cross-user data leakage is prevented at both the service and repository layers.

### 2.2 Role-Based Access Control (RBAC)
The system enforces two distinct roles:
1. **`USER` Role:** Standard mobile user account.
   - Allowed: Read/write own threat detections, register own devices, view own analytics, submit feedback on own threats.
   - Denied: Accessing any `/api/v1/admin/*` routes or inspecting other users' data.
2. **`ADMIN` Role:** Security Operations Center (SOC) personnel.
   - Allowed: View aggregated threat intelligence, inspect global telemetry, update threat status, activate/suspend user accounts, update user roles, register AI model metadata, and review audit logs.

### 2.3 Server-Authoritative Admin Promotion
- Public registration endpoints (`/auth/register`) default to assigning the `USER` role.
- Administrator privileges can **only** be granted through:
  1. Inclusion in the backend environment whitelist: `ADMIN_EMAILS=lead-soc@yourorg.com`.
  2. Direct administrative role promotion via `PATCH /api/v1/admin/users/{user_id}/role` executed by an existing authenticated administrator.
- Public web clients or mobile apps cannot self-assign `role = "ADMIN"`.

### 2.4 Production Token Verification Hardening
- In `ENVIRONMENT=production`, mock authentication (`ALLOW_MOCK_AUTH`) and unverified token claims are strictly prohibited and hard-blocked at startup.
- Production environments require real Google Cloud / Firebase service account credentials supplied via `FIREBASE_CREDENTIALS` or Application Default Credentials (ADC).

---

## 3. Secrets Management & Environment Configuration

### 3.1 Strict Credential Guidelines
- **No Committed Secrets:** Real passwords, private keys, Firebase service account JSON files, and keystores are excluded from Git via `.gitignore`.
- **Environment Templates:** All repositories supply `.env.example` templates containing explicit placeholders.
- **Web Client Restrictions:** Browser bundles (`web/`) must never contain backend service keys, database passwords, or Firebase Admin credentials. Only public Firebase client parameters (`NEXT_PUBLIC_FIREBASE_*`) are permitted in web builds.

### 3.2 Non-Root Container Sandboxing
Both production container images enforce least-privilege runtime security:
- **Backend Container (`backend/Dockerfile`):** Runs under unprivileged user `appuser` (UID 10001, GID 10001).
- **Web Dashboard Container (`web/Dockerfile`):** Runs under unprivileged user `nextjs` (UID 1001, GID 1001).

---

## 4. Network & Application Layer Hardening

### 4.1 OWASP Defense-in-Depth Headers
Every API response emits security headers:
- `X-Content-Type-Options: nosniff` (prevents MIME-type sniffing).
- `X-Frame-Options: DENY` (prevents clickjacking attacks).
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (enforces HTTPS).
- `Content-Security-Policy: default-src 'self'` (restricts unauthorized script execution).

### 4.2 Denial of Service (DoS) Protections
- **Request Size Limiting:** Middleware intercepts all incoming requests and rejects payloads exceeding 1MB with `413 Request Entity Too Large`.
- **Rate Limiting:** Sliding-window in-memory rate limiter throttles clients to 120 requests/minute for standard users and 240 requests/minute for administrators.
- **CORS Whitelisting:** Cross-Origin Resource Sharing is locked down to verified dashboard domains defined in `CORS_ORIGINS`.

---

## 5. Audit Logging & Compliance

All sensitive security state modifications generate an authoritative, immutable record in the `audit_logs` database table:
- Account activation and suspension (`USER_STATUS_UPDATED`).
- Administrative privilege promotion/demotion (`USER_ROLE_UPDATED`).
- Telemetry threat status triage changes (`THREAT_STATUS_UPDATED`).
- Machine learning model registration (`MODEL_REGISTERED`).

Audit records capture:
- Timestamp (UTC)
- Actor User ID
- Client IP Address
- Affected Resource Type and Resource ID
- JSON Metadata capturing state delta (e.g. `{"old_role": "USER", "new_role": "ADMIN"}`)

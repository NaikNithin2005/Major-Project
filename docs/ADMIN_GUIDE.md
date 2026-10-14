# Security Administrator & SOC Operations Guide

**Application:** Squish Shield Web Admin Security Dashboard  
**Framework:** Next.js 14 / React 18 / TypeScript  
**Target Audience:** Security Operations Center (SOC) Analysts, DevOps Engineers, and System Administrators  
**Access URL:** `http://<web-host>:3000`  

---

## 1. Administrative Account Provisioning

Squish Shield enforces strict server-authoritative role assignment. **Administrator privileges cannot be obtained through standard mobile or web registration.**

### Provisioning Methods

#### Method 1: Environment Whitelist (Recommended for Initial Setup)
Configure the authoritative admin email list in `backend/.env`:
```ini
ADMIN_EMAILS=soc-lead@squishshield.org,security-admin@yourcompany.com
```
When a user with a matching verified email authenticates through Firebase, the backend automatically assigns and maintains the `ADMIN` role.

#### Method 2: Direct PostgreSQL Database Update
For initial root administrator bootstrap:
```sql
UPDATE users 
SET role = 'ADMIN', is_active = true 
WHERE email = 'target-admin@yourcompany.com';
```

#### Method 3: Administrative API Promotion
An existing authenticated administrator can promote a registered user via the administrative API:
```bash
curl -X PATCH "http://localhost:8000/api/v1/admin/users/{USER_UUID}/role" \
  -H "Authorization: Bearer <ADMIN_FIREBASE_ID_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"role": "ADMIN"}'
```
*Note:* Every role promotion generates an immutable entry in the `audit_logs` table recording the actor ID, timestamp, and target user.

---

## 2. Admin Dashboard Navigation & Interface

Upon navigating to `http://localhost:3000` and logging in with an administrator account:

### 2.1 Threat Overview & Analytics (`/`)
- **Global Metric Cards:** Total Telemetry Events, Active Quishing Detections, Active Smishing Detections, Average Threat Risk Score, and User False-Positive Rate.
- **Incident Heatmap & Trend Charts:** Visualizes 7-day and 30-day threat volume trajectories and peak incident times.
- **Threat Category Distribution:** Interactive breakdown between Quishing (URL/QR) and Smishing (SMS) severity tiers.

### 2.2 Threat Triage & Incident Response (`/threats`)
- **Telemetry Event Grid:** Filterable list of all threat incidents across all mobile clients.
- **Filter Controls:** Filter by Event Type (`SMS`, `QR`), Threat Category (`SAFE`, `LOW`, `MEDIUM`, `HIGH`), Minimum/Maximum Risk Score, Status (`NEW`, `REVIEWED`, `RESOLVED`, `FALSE_POSITIVE`), and Date Range.
- **Triage Inspection Modal:**
  - View full detection metadata (confidence score, latency, heuristics triggered).
  - Inspect sanitized domain and path indicators.
  - Review user defensive actions (e.g. sender blocked).
  - Update threat status to `REVIEWED` or `RESOLVED`.

### 2.3 User & Device Management (`/users`)
- Inspect all registered accounts, creation timestamps, and active status.
- Suspend or reinstate user accounts (`PATCH /admin/users/{id}/status`).
- Inspect registered mobile devices (`Android 15`, `Pixel 8`, app versions, last sync timestamps).

### 2.4 User Feedback & False-Alarm Review (`/feedback`)
- Review user-submitted false positive or false negative reports.
- Review flagged legitimate messages (e.g., airline alerts or bank OTPs misclassified as smishing) to identify training dataset gaps.

### 2.5 Security Audit Logs (`/audit-logs`)
- Searchable log of all sensitive security events:
  - Role modifications (`USER_ROLE_UPDATED`)
  - Account suspensions (`USER_STATUS_UPDATED`)
  - Incident triage decisions (`THREAT_STATUS_UPDATED`)
  - Model registrations (`MODEL_REGISTERED`)
- Logs capture client IP addresses, actor user IDs, and before/after metadata deltas.

---

## 3. System Health Monitoring & Troubleshooting

### 3.1 Inspecting Backend Health Probes
SOC engineers and container orchestrators can monitor service health via standard HTTP endpoints:

1. **Liveness Probe:**
   ```bash
   curl -i http://localhost:8000/health/live
   # HTTP 200 OK -> Service process is alive
   ```

2. **Readiness Probe:**
   ```bash
   curl -i http://localhost:8000/health/ready
   # HTTP 200 OK -> Database connected, auth initialized
   ```

### 3.2 Troubleshooting Common Issues

| Symptom | Probable Cause | Corrective Action |
| :--- | :--- | :--- |
| **HTTP 403 on `/admin/*` routes** | User account has `role = 'USER'` | Verify email in `ADMIN_EMAILS` or update user role in PostgreSQL. |
| **Readiness probe returns 503** | PostgreSQL connection failed or uninitialized | Check container logs (`docker logs squish-shield-postgres`); verify `DATABASE_URL` credentials. |
| **Firebase token rejected** | Invalid service account or clock drift | Ensure `FIREBASE_CREDENTIALS` points to a valid Google service account JSON file; verify system clock sync. |
| **High SQLite Lock Latency in Fallback** | Backend running in SQLite mode under high load | Deploy PostgreSQL with connection pooling enabled in production. |

---

## 4. Disaster Recovery & Backup Responsibilities

Administrators must ensure regular database backups are performed. Refer to [`docs/DATABASE_GUIDE.md`](./DATABASE_GUIDE.md) for full commands:
- **Daily Automated Backup:** `pg_dump -Fc squish_shield_db > /backups/squish_shield_$(date +%Y%m%d).dump`
- **Emergency Restoration:** `pg_restore --clean -d squish_shield_db /backups/<backup_file>.dump`
- **Alembic Verification:** Ensure migrations are aligned at `003_add_user_action_and_phone` prior to starting services.

# Release Engineering & Deployment Runbook

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Document:** `RELEASE_RUNBOOK.md`  
**Phase:** 17 — Continuous Integration, Automated Quality Gates & Secure Delivery  
**Status:** Approved Standard Operating Procedure (SOP)  

---

## 1. Release Philosophy & Versioning

The Squish platform follows **Semantic Versioning (SemVer 2.0.0)**:
$$\text{MAJOR}.\text{MINOR}.\text{PATCH}$$

- **MAJOR (e.g. 2.0.0):** Incompatible API changes, fundamental database schema overhauls, or breaking changes to on-device risk scoring contracts.
- **MINOR (e.g. 1.1.0):** Backwards-compatible feature additions (e.g. new threat intelligence feeds, additional SOC dashboard workstations, retrained model promotions passing all 7 gates).
- **PATCH (e.g. 1.0.1):** Backwards-compatible bug fixes, security patches, or documentation updates.

---

## 2. Pre-Release Quality Gate Checklist

Before creating a release tag or triggering a production deployment, the Release Engineer must verify:

- [ ] **All CI Quality Gates Green:** The commit on `main` passed all 6 jobs in `.github/workflows/ci.yml`.
- [ ] **Secret Scan Clean:** `python scripts/scan_secrets.py` confirmed 0 findings.
- [ ] **Model Manifest Verified:** `python scripts/verify_model_artifacts.py` verified all SHA-256 digests against `models/MODEL_MANIFEST.json`.
- [ ] **Database Migrations Tested:** `python scripts/verify_db_migrations.py` confirmed clean bidirectional execution up to HEAD.
- [ ] **Test Coverage:** All 125 backend tests and 83 Android unit tests passed without failure.
- [ ] **Release Notes Prepared:** `RELEASE_NOTES_vX.Y.Z.md` drafted documenting user-facing changes, security fixes, and migration notes.

---

## 3. Release Execution Protocol (Step-by-Step)

### Step 3.1: Tag the Release Commit
Ensure your local branch is synchronized with `origin/main`:
```bash
git checkout main
git pull origin main

# Create annotated and cryptographically signed Git tag
git tag -a v1.1.0 -m "Release v1.1.0: Phase 17 DevSecOps & Secure Delivery"
git push origin v1.1.0
```

### Step 3.2: Automated CD Pipeline Trigger
Pushing a tag matching `v*` automatically triggers `.github/workflows/deploy.yml`:
1. **Artifact Packaging:** Builds `squish-shield-backend:v1.1.0`, `squish-shield-web:v1.1.0`, and signed Android release APK. Generates `SHA256SUMS.txt`.
2. **Staging Rollout:** Automatically deploys containers to the Staging environment.
3. **Automated Smoke Tests:** Runs `python scripts/smoke_test.py --base-url $STAGING_API_URL`.

### Step 3.3: Production Environment Review & Promotion
1. Open the GitHub Actions run for `deploy.yml`.
2. Locate the **deploy-production** job in the pipeline graph.
3. The job will show a yellow warning indicating it requires explicit reviewer sign-off.
4. The designated Lead Security Engineer reviews the staging smoke test results.
5. Click **Review deployments > Select production > Approve and deploy**.

---

## 4. Production Database Migration Strategy

To achieve zero downtime during database schema updates:

### Golden Rules of Non-Destructive Migrations:
1. **Expand Before Contract:** Always add new columns or tables in Phase 1; only drop obsolete columns in Phase 2 after all old clients have transitioned.
2. **Nullable Defaults:** When adding non-nullable columns, supply a safe server-side default value.
3. **Execution Order:**
   ```
   [1. Apply Alembic Migrations to Database] ──► [2. Deploy New Backend Service Containers]
   ```
4. **Command:**
   ```bash
   cd backend
   alembic upgrade head
   ```
5. **Verify Version:**
   ```bash
   alembic current
   # Must match the latest migration revision
   ```

---

## 5. Post-Deployment Smoke Verification

Immediately following production rollout, execute the automated verification suite:

```bash
python scripts/smoke_test.py --base-url https://api.squishshield.org
```

### Manual Sanity Checks:
1. **Liveness Probe:** `GET /health/live` returns HTTP 200 `{"status": "alive"}`.
2. **Readiness Probe:** `GET /health/ready` returns HTTP 200 `{"status": "ready"}` with healthy database pool.
3. **OWASP Headers:** Inspect response headers for `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`.
4. **Admin Route RBAC:** `GET /api/v1/admin/incidents` returns HTTP 401 (when unauthenticated) or HTTP 403 (when authenticated as standard `USER`).
5. **Web Console:** Open `https://admin.squishshield.org` and verify the SOC incident workstation loads real-time KPIs cleanly.

---

## 6. Rollback & Disaster Recovery Standard Operating Procedure (SOP)

If post-deployment smoke tests fail or critical production anomalies arise within the 60-minute post-rollout window:

### Scenario A: Backend API or Web Dashboard Degraded
1. **Rollback Container Image:**
   In Kubernetes or Docker Compose, redeploy the previous stable release tag:
   ```bash
   # Docker Compose rollback example:
   docker compose down
   export BACKEND_TAG=v1.0.0
   export WEB_TAG=v1.0.0
   docker compose up -d
   ```
2. **Verify Stability:** Run `python scripts/smoke_test.py` against the restored instance.

### Scenario B: Incompatible Database Migration Applied
If an Alembic migration introduced schema locks or table corruption:
1. **Downgrade Schema:**
   ```bash
   cd backend
   alembic downgrade -1
   ```
2. **Verify Target Head:**
   ```bash
   alembic current
   ```

### Scenario C: Compromised Credential or Secret Leak
1. **Immediate Revocation:**
   - In Google Cloud Console, immediately revoke the compromised Service Account Key.
   - Generate a new private key and update the `FIREBASE_SERVICE_ACCOUNT_KEY` repository secret.
   - Rotate database master passwords in PostgreSQL and update `DATABASE_PASSWORD_PROD`.
2. **Trigger Urgent Redeployment:**
   Dispatch `deploy.yml` with the rotated credentials.

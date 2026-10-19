# CI/CD Setup, Environment Secrets & Quality Gates Guide

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Document:** `CI_CD_SETUP.md`  
**Phase:** 17 — Continuous Integration, Automated Quality Gates & Secure Delivery  
**Status:** Operational Reference  

---

## 1. Overview & Setup Prerequisites

The Squish system uses GitHub Actions for continuous integration and delivery. This guide provides exact instructions for configuring repository secrets, setting up environment protection gates, configuring branch protection rules, and executing local pre-push verification.

### System Requirements
- **CI Runner Platform:** GitHub-hosted `ubuntu-latest`
- **JDK:** 17 (Temurin)
- **Node.js:** 18.x or 20.x LTS
- **Python:** 3.11
- **Database Engine:** PostgreSQL 15 (provided via GitHub Actions service container)

---

## 2. GitHub Secrets & Variables Configuration

Navigate to **GitHub Repository > Settings > Secrets and variables > Actions**.

### 2.1 Repository Secrets (Global)

Configure the following secrets under **Repository secrets**:

| Secret Name | Required | Value Description |
| :--- | :---: | :--- |
| `FIREBASE_SERVICE_ACCOUNT_KEY` | Yes (CD) | Base64-encoded string of Google Cloud Service Account JSON key (`FIREBASE_CREDENTIALS`). |
| `DATABASE_PASSWORD_STAGING` | Yes (CD) | Master database password for staging PostgreSQL cluster. |
| `DATABASE_PASSWORD_PROD` | Yes (CD) | Master database password for production PostgreSQL cluster. |
| `ANDROID_RELEASE_KEYSTORE_B64` | Optional | Base64-encoded `.jks` Android signing keystore file for production signing. |
| `ANDROID_RELEASE_KEYSTORE_PWD` | Optional | Master password for release keystore. |
| `ANDROID_RELEASE_KEY_ALIAS` | Optional | Alias name of release signing key in keystore. |
| `ANDROID_RELEASE_KEY_PWD` | Optional | Password for private key. |

### 2.2 Repository Variables (Global)

Configure the following under **Repository variables**:

| Variable Name | Default Value | Description |
| :--- | :--- | :--- |
| `STAGING_API_URL` | `https://staging-api.squishshield.org` | Staging base API endpoint for smoke testing |
| `PROD_API_URL` | `https://api.squishshield.org` | Production base API endpoint for post-deployment health probes |

---

## 3. GitHub Environments & Deployment Gates

To enforce multi-stage gated delivery, configure two GitHub Environments under **Settings > Environments**:

### 3.1 `staging` Environment
- **Deployment Branches:** All branches or `develop` and `main`.
- **Protection Rules:** None required (automatic continuous deployment for release candidates).
- **Environment Secrets:**
  - `DATABASE_URL`: `postgresql://squish_staging_user:${{ secrets.DATABASE_PASSWORD_STAGING }}@staging-db.internal:5432/squish_staging_db`
  - `ALLOW_MOCK_AUTH`: `false`

### 3.2 `production` Environment
- **Deployment Branches:** Strictly limited to `main` and tags matching `v*`.
- **Protection Rules:**
  - **Required Reviewers:** Check "Required reviewers" and specify Tech Lead or SecOps engineers.
  - **Wait Timer:** 5 minutes (provides automated pause between staging verification and production rollout).
- **Environment Secrets:**
  - `DATABASE_URL`: `postgresql://squish_prod_user:${{ secrets.DATABASE_PASSWORD_PROD }}@prod-db.internal:5432/squish_prod_db`
  - `ENVIRONMENT`: `production`
  - `ALLOW_MOCK_AUTH`: `false` (Strictly enforced: production rejects mock auth and unverified tokens)

---

## 4. Branch Protection Rules

Under **Settings > Branches**, create a branch protection rule for `main` and `develop`:

1. **Branch name pattern:** `main` (and another for `develop`)
2. **Protect matching branches:**
   - [x] **Require a pull request before merging**
     - [x] Require approvals: `1`
     - [x] Dismiss stale pull request approvals when new commits are pushed
   - [x] **Require status checks to pass before merging**
     - [x] Require branches to be up to date before merging
     - **Required Status Checks:**
       - `Secret & Credential Scanning`
       - `AI Models & Artifact Integrity`
       - `Backend API & Database Verification`
       - `Web Dashboard Build & Static Checks`
       - `Android Mobile Build & Unit Tests`
       - `Static Application Security Testing (Bandit)`
       - `DevSecOps Quality Gate Summary`
   - [x] **Do not allow bypassing the above settings**
   - [x] **Restrict pushes:** Prevent direct pushes to `main`. All changes must arrive via verified PRs.

---

## 5. Local Pre-Push Verification (Developer Checklist)

Before creating a pull request or pushing to remote branches, execute the local quality gates to guarantee a green CI build:

### 5.1 Step 1: Scan for Accidental Secrets
```bash
python scripts/scan_secrets.py
```
*Expected Output:* `Zero hardcoded secrets, private keys, or exposed credentials detected.`

### 5.2 Step 2: Validate Model Artifacts & Manifest
```bash
python scripts/verify_model_artifacts.py
```
*Expected Output:* `ALL MODEL ARTIFACT CHECKS PASSED SUCCESSFULLY.`

### 5.3 Step 3: Verify Database Schema & Migrations
```bash
python scripts/verify_db_migrations.py
```
*Expected Output:* `ALL DATABASE MIGRATION CHECKS PASSED SUCCESSFULLY.`

### 5.4 Step 4: Run AI & Backend Test Suites
```bash
# AI contracts & adversarial tests
pytest ai/tests -v

# Backend API & security tests
pytest backend/tests -v
```

### 5.5 Step 5: Verify Web Dashboard Type Safety & Build
```bash
cd web
npx tsc --noEmit
npm run lint
npm run build
cd ..
```

### 5.6 Step 6: Verify Android Unit Tests & Release Binary
```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleRelease
cd ..
```

---

## 6. Manual Pipeline Dispatch

If manual deployment or validation is required without pushing commits:

### Via GitHub Web UI:
1. Navigate to the **Actions** tab.
2. Select **Secure Delivery & Deployment Pipeline (CD)** in the left sidebar.
3. Click **Run workflow**.
4. Select the branch or tag, choose the target environment (`staging` or `production`), and click **Run workflow**.

### Via GitHub CLI (`gh`):
```bash
# Trigger CI pipeline on current branch
gh workflow run "Squish DevSecOps CI Pipeline"

# Trigger CD deployment to staging
gh workflow run "Secure Delivery & Deployment Pipeline (CD)" -f target_environment=staging -f release_version=v1.0.1

# Trigger CD deployment to production (requires environment reviewer approval)
gh workflow run "Secure Delivery & Deployment Pipeline (CD)" -f target_environment=production -f release_version=v1.0.1
```

---

## 7. Troubleshooting Common CI Failures

| Failure Symptom | Likely Cause | Resolution |
| :--- | :--- | :--- |
| **`Secret & Credential Scanning` fails** | High-entropy string or key committed in new file | Check file reported by `scripts/scan_secrets.py`. Remove credential or replace with placeholder. |
| **`AI Models & Artifact Integrity` fails with mismatch** | Model weights retrained without updating manifest | Re-export model, compute SHA-256 via `sha256sum`, and update `models/MODEL_MANIFEST.json`. |
| **`Backend API` tests fail on PostgreSQL** | Service container not ready or schema mismatch | Ensure `scripts/verify_db_migrations.py` runs before tests and migrations match SQLAlchemy models. |
| **`Web Dashboard` fails on ESLint** | Unescaped entity or unused variable | Replace `'` with `&apos;` or `"` with `&quot;`; remove unused imports. |
| **`Android Mobile` fails on unit tests** | Category or risk engine assertion desync | Verify test double matches updated interfaces in Room DAOs or risk contracts. |

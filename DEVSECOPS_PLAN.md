# DevSecOps Plan & CI/CD Architecture

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Document:** `DEVSECOPS_PLAN.md`  
**Phase:** 17 — Continuous Integration, Automated Quality Gates & Secure Delivery  
**Status:** Approved & Implemented  

---

## 1. Executive Summary & Philosophy

The Squish system integrates an Android mobile client, a multi-modal AI/ML inference engine (TinyBERT, XGBoost, Isolation Forest), a high-throughput FastAPI backend, a PostgreSQL relational store, and an administrative SOC dashboard. 

The primary mission of the **Phase 17 DevSecOps framework** is to automate verification, prevent broken or insecure artifacts from entering release pipelines, enforce privacy invariants, and guarantee reproducible, traceable deployments without compromising the system's core offline-first detection guarantees.

### Guiding Principles
1. **Shift Security Left:** Security and secret detection are executed before code compilation and dependency installation.
2. **Deterministic Artifact Verification:** Every AI model, binary, and container is verified against cryptographic hashes (`SHA-256`) and provenance records.
3. **Strict Environment Segregation:** Development, Staging, and Production environments maintain isolated credential sets, distinct network barriers, and independent database instances.
4. **Zero-Knowledge Privacy:** CI/CD test suites and logging pipelines strictly prohibit capturing raw SMS message content, user credentials, or unmasked bearer tokens.
5. **No False Green Builds:** Suppressing or weakening test assertions to obtain passing badges is strictly prohibited. Failures must be explicit and blocking.

---

## 2. Pipeline Topology & Workflow Architecture

The automation architecture is implemented via GitHub Actions workflows organized into two coordinated pipelines:

```mermaid
graph TD
    subgraph Trigger ["Triggers"]
        PR[Pull Request: main/develop]
        PUSH[Push: main/develop]
        TAG[Push Tag: v*]
        DISPATCH[Manual workflow_dispatch]
    end

    subgraph CI ["Continuous Integration (ci.yml)"]
        SS[Secret & Credential Scanner]
        MV[Model & ONNX Runtime Validation]
        BE[Backend API & PostgreSQL Service]
        FE[Web TypeScript & Next.js Build]
        AND[Android Gradle & Release APK]
        SAST[Bandit SAST Scanner]
        QG[Consolidated Quality Gate Summary]

        SS --> MV
        SS --> BE
        SS --> FE
        SS --> AND
        SS --> SAST
        MV & BE & FE & AND & SAST --> QG
    end

    subgraph CD ["Continuous Delivery (deploy.yml)"]
        PKG[Package Release Artifacts & SHA-256]
        STG[Deploy Staging Environment]
        SMK_STG[Staging Automated Smoke Tests]
        PRD_GATE[Production Environment Approval Gate]
        PRD[Deploy Production Services & Migrations]
        SMK_PRD[Production Post-Deployment Health Probes]

        QG -.-> PKG
        TAG & DISPATCH --> PKG
        PKG --> STG
        STG --> SMK_STG
        SMK_STG --> PRD_GATE
        PRD_GATE --> PRD
        PRD --> SMK_PRD
    end
```

---

## 3. Component Quality Gates

Every code change must pass all component-specific quality gates before merging into protected branches (`main`, `develop`):

| Subsystem | Execution Target | Quality Gate / Tool | Verification Criteria | Blocking Threshold |
| :--- | :--- | :--- | :--- | :--- |
| **Global** | Repository Files | `scripts/scan_secrets.py` | Scans for private keys, tokens, hardcoded DB passwords | **0 Leaks Allowed** (High/Critical) |
| **AI / ML** | `models/` & `ai/` | `scripts/verify_model_artifacts.py` | Validates SHA-256 hashes against `MODEL_MANIFEST.json`, tests ONNX loading | **100% Hash & Loading Match** |
| **AI / ML** | `ai/tests/` | `pytest` | Invariant checks, adversarial input resilience, contract conformity | **0 Failures Allowed** |
| **Backend** | `backend/app/` | `flake8` | Python static analysis (syntax errors, undefined names) | **0 Errors (E9, F63, F7, F82)** |
| **Backend** | `backend/alembic/` | `scripts/verify_db_migrations.py` | Tests clean schema setup, bidirectional downgrade/re-upgrade | **0 Migration Errors** |
| **Backend** | `backend/tests/` | `pytest` + PostgreSQL Service | API integration, RBAC, BOLA/IDOR isolation, idempotency | **100% Pass Rate** |
| **Backend** | `backend/app/` | `bandit` | Static Application Security Testing (SAST) | **0 High/Medium Severity Defects** |
| **Web** | `web/src/` | `npx tsc --noEmit` | Strict TypeScript AST validation | **0 Type Errors** |
| **Web** | `web/` | `npm run lint` | Next.js and ESLint rule validation | **0 Lint Errors** |
| **Web** | `web/` | `npm run build` | Next.js production build & static/dynamic compilation | **100% Routes Compiled** |
| **Android** | `android/` | `./gradlew lintDebug` | Android Lint static analysis | **0 Fatal Issues** |
| **Android** | `android/` | `./gradlew testDebugUnitTest` | Unit, security, and sync worker tests (83 tests) | **100% Pass Rate** |
| **Android** | `android/` | `./gradlew assembleRelease` | Release APK build and asset bundling | **BUILD SUCCESSFUL** |

---

## 4. Environment Matrix & Secret Management

Secrets must be managed within the GitHub Actions Secrets Manager (or production KMS/Vault). Under no circumstances are `.env` files or production credentials committed to version control.

### 4.1 Required Environment Variables & Secrets

| Secret / Config Name | Scope | Description | Safe Example / Default |
| :--- | :--- | :--- | :--- |
| `ENVIRONMENT` | Backend / CI | Application runtime environment (`development`, `testing`, `staging`, `production`) | `testing` |
| `DATABASE_URL` | Backend / CD | PostgreSQL connection URI (with isolated credentials per env) | `postgresql://user:pass@host:5432/db` |
| `FIREBASE_CREDENTIALS` | Backend / CD | Path or base64-encoded GCP Service Account JSON key | `[PROTECTED_SECRET]` |
| `ALLOW_MOCK_AUTH` | Backend / CI | Permits dev/test token bypass (Strictly blocked in `production`) | `false` in Prod, `true` in CI |
| `ADMIN_EMAILS` | Backend / CD | Authoritative comma-separated list of administrative operators | `secops@squishshield.org` |
| `NEXT_PUBLIC_API_BASE_URL` | Web / CD | Public API gateway base URL | `https://api.squishshield.org/api/v1` |
| `NEXT_PUBLIC_FIREBASE_API_KEY` | Web / CD | Client-side public Firebase API key | `AIzaSy...` (Public Web Client) |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | Web / CD | Firebase project identifier | `squish-shield` |
| `RELEASE_KEYSTORE_BASE64` | Android / CD | Base64-encoded release `.jks` signing keystore (CD Secret) | `[PROTECTED_SECRET]` |
| `RELEASE_KEYSTORE_PASSWORD` | Android / CD | Keystore master password | `[PROTECTED_SECRET]` |
| `RELEASE_KEY_ALIAS` | Android / CD | Keystore private key alias | `squish_release_key` |
| `RELEASE_KEY_PASSWORD` | Android / CD | Private key password | `[PROTECTED_SECRET]` |

### 4.2 Separation of Client vs Privileged Credentials
- **Public Client Credentials:** `NEXT_PUBLIC_FIREBASE_API_KEY`, `google-services.json` client configuration parameters are client-facing identifiers. They are protected by Firebase Security Rules and domain restrictions.
- **Privileged Server Credentials:** `FIREBASE_CREDENTIALS` (GCP Service Account Key with admin roles) and `DATABASE_URL` database passwords **never enter client-side bundles or repository code**.

---

## 5. Artifact Provenance & Traceability

Every release build generates immutable, cryptographically verifiable artifacts linked to the originating Git commit:

1. **Android Application:**
   - Artifact: `app-release.apk`
   - Metadata: `git_commit=${{ github.sha }}`, `version_code=1`, `version_name=1.0`
2. **Backend Services:**
   - Docker Container Image: `squish-shield-backend:${{ github.sha }}` and `squish-shield-backend:${{ github.ref_name }}`
3. **Web Dashboard:**
   - Docker Container Image: `squish-shield-web:${{ github.sha }}` and `squish-shield-web:${{ github.ref_name }}`
4. **Model Manifest:**
   - `models/MODEL_MANIFEST.json` signed with component SHA-256 digests.
5. **Release Checksum Digest:**
   - `SHA256SUMS.txt` packaged with every release bundle.

---

## 6. Secure Deployment Strategy & Gated Rollout

Deployments follow a progressive, gated progression designed to prevent downtime:

```
[Main Branch Merge] 
       │
       ▼
[CI Quality Gates Pass] 
       │
       ▼
[Deploy to Staging] 
       │
       ▼
[Automated Staging Smoke Tests: scripts/smoke_test.py]
       │
       ▼
[Manual Approval Gate: Required Reviewers (SecOps / Tech Lead)]
       │
       ▼
[Deploy to Production: Zero-Downtime Rolling Update]
       │
       ▼
[Post-Deployment Health & Readiness Probes]
       ├─── Succeeded ───► [Release Active & Logged]
       └─── Failed    ───► [Trigger Automated Rollback Runbook]
```

---

## 7. Rollback & Disaster Recovery Procedures

If automated smoke tests or production readiness probes fail post-deployment:
1. **Automated Traffic Rerouting:** In containerized Kubernetes/Cloud Run environments, readiness probe failures prevent the ingress router from directing traffic to unhealthy pods, leaving the prior stable replica active.
2. **Fast Rollback (Containers):** Revert traffic to the preceding tagged container image (`squish-shield-backend:PREVIOUS_TAG`) via orchestrator command (`kubectl rollout undo` or revision redeployment).
3. **Database Migration Rollback:** If a newly applied migration is incompatible with application state, execute:
   ```bash
   alembic downgrade -1
   ```
   *Note: Phase 17 verifies that all migrations up to head support bidirectional downgrade without data corruption.*
4. **Android Client Rollback:** Because mobile APK updates cannot be retracted instantly from client devices once installed, on-device detection relies on local ONNX inference and falls back safely if backend services are degraded.

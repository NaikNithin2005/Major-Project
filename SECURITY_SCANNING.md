# Automated Security Scanning, Vulnerability Management & DevSecOps Gates

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Document:** `SECURITY_SCANNING.md`  
**Phase:** 17 — Continuous Integration, Automated Quality Gates & Secure Delivery  
**Status:** Security Baseline Reference  

---

## 1. Security Architecture & Defense-in-Depth

The Squish security model follows a multi-tier defense-in-depth approach across client, network, server, database, and machine learning components:

```
[Layer 1: Source & Secrets] ──► Pre-Commit & CI Secret Scanner (scripts/scan_secrets.py)
[Layer 2: Dependencies]     ──► pip-audit (Python) & npm audit (Web)
[Layer 3: Static Analysis]  ──► Bandit SAST (Backend) & Android Lint (Mobile)
[Layer 4: Invariants]       ──► Model Manifest Checksums & Bidirectional Migration Tests
[Layer 5: Container Images] ──► Multi-stage Dockerfiles + Trivy Image Scanners
[Layer 6: Runtime Guards]   ──► OWASP Security Headers, 2MB Payload Limits, BOLA Prevention
```

---

## 2. Automated Secret Detection

Accidental exposure of API keys, private keys, or passwords presents the single most severe risk in automated CI/CD pipelines.

### 2.1 Scanner Implementation (`scripts/scan_secrets.py`)
The repository includes a dedicated secret detection engine executed both locally and during GitHub Actions CI runs:
- **Private Keys:** Detects RSA, OpenSSH, EC, and DSA private keys.
- **Service Accounts:** Detects Google Cloud / Firebase service account JSON private keys.
- **Tokens:** Detects GitHub Personal Access Tokens (`ghp_*`), AWS access keys (`AKIA*`), and Slack tokens.
- **Database Credentials:** Detects connection URIs embedding plain-text credentials (`postgresql://user:pass@host/db`).

### 2.2 Quality Gate Threshold
- **Threshold:** **0 High or Critical Secret Findings**.
- **Action:** Any detected secret immediately halts the pipeline, produces a masked line snippet in the runner output, and rejects the pull request.

---

## 3. Static Application Security Testing (SAST)

### 3.1 Python Backend SAST (Bandit)
Bandit scans Python abstract syntax trees (AST) for common security vulnerabilities:
- Hardcoded secrets or passwords (`B105`, `B106`)
- Shell injection and dangerous subprocess invocations (`B602`, `B603`)
- Insecure deserialization (`B301` pickle, `B506` yaml)
- SQL injection vulnerabilities (SQLAlchemy parameterized queries required)
- Weak cryptographic algorithms (`B303` MD5/SHA1 for security hashing)

**Execution Command:**
```bash
python -m bandit -r backend/app -ll -ii -f screen
```
*Criteria:* Zero High (`-ll`) or Medium (`-ii`) severity security issues allowed.

### 3.2 Android Mobile SAST (Android Lint)
Android Lint evaluates the Kotlin codebase for mobile security anti-patterns:
- Network security configuration (verifies `cleartextTrafficPermitted="false"`)
- Backup configuration (verifies `android:allowBackup="false"`)
- Exported components (verifies unexported activities, broadcast receivers with explicit permissions)
- Cryptographic algorithm usage (verifies AES-GCM and Android Keystore usage)

---

## 4. Software Composition Analysis (SCA) & Dependency Auditing

Third-party dependencies must be audited to prevent supply-chain attacks.

### 4.1 Backend Python SCA
- **Tool:** `pip-audit`
- **Execution:**
  ```bash
  pip install pip-audit
  pip-audit -r backend/requirements.txt --desc
  ```
- **Threshold:** Zero known vulnerabilities with CVSS $\ge 7.0$ (High / Critical).

### 4.2 Web Dashboard SCA
- **Tool:** `npm audit`
- **Execution:**
  ```bash
  cd web
  npm audit --production --audit-level=high
  ```
- **Threshold:** Zero Critical production vulnerabilities.

---

## 5. Machine Learning Invariant Quality Gates

ML models require specialized DevSecOps validation to prevent model poisoning, drift, or silent inference corruption:

### 5.1 Artifact Integrity & Provenance (`scripts/verify_model_artifacts.py`)
1. **Manifest Integrity:** Every active model file must match the cryptographic hash declared in `models/MODEL_MANIFEST.json`.
2. **ONNX Loading Validation:** Runtimes must load cleanly into `onnxruntime.InferenceSession` on CPU providers.
3. **Adversarial Resilience:** Models must gracefully reject or fail safe when encountering empty payloads, 100k-character inputs, or invalid URI schemes without crashing.
4. **Safe Failure Invariant:** If a model file is missing or corrupted, the system must trigger a high-risk alert or fail closed; it must **never classify an uninspected input as safe**.

---

## 6. Container Image Security (Docker & OCI)

Both `backend/Dockerfile` and `web/Dockerfile` are engineered to adhere to CIS Docker benchmarks:
1. **Unprivileged Application Users:**
   - Backend: Runs under dedicated system user `appuser:appuser` (UID/GID non-root).
   - Web: Runs under dedicated system user `nextjs:nodejs` (UID 1001).
2. **Multi-Stage Builds:**
   - Eliminates build-time dependencies, compilers, and development headers from final production images.
3. **Automated Scanning with Trivy:**
   - In deployment workflows, Trivy scans built images for OS package and language runtime vulnerabilities:
   ```yaml
   - name: Scan Backend Image
     uses: aquasecurity/trivy-action@master
     with:
       image-ref: 'squish-shield-backend:${{ steps.vars.outputs.tag }}'
       severity: 'CRITICAL,HIGH'
       exit-code: '1'
   ```

---

## 7. Vulnerability Severity Matrix & Remediation SLAs

When vulnerabilities or defects are discovered during scanning or penetration testing:

| Severity | CVSS v3.1 Range | CI/CD Action | Production Impact | Remediation SLA |
| :--- | :---: | :---: | :--- | :---: |
| **CRITICAL** | 9.0 – 10.0 | **Blocks CI & Deployments** | Immediate Hotfix Required | **Within 24 Hours** |
| **HIGH** | 7.0 – 8.9 | **Blocks Release Builds** | Deployment Paused | **Within 48 Hours** |
| **MEDIUM** | 4.0 – 6.9 | Warning Logged | Scheduled Sprint Release | **Within 14 Days** |
| **LOW** | 0.1 – 3.9 | Informational | Backlog Triage | **Within 30 Days** |

---

## 8. Justified Exception & Risk Acceptance Process

If a vulnerability scanner flags a finding that is a confirmed false positive or an unexploitable transitive dependency:
1. **Documentation:** Create an entry in `docs/SECURITY_EXCEPTIONS.md` documenting:
   - CVE identifier and affected component
   - Root cause and why the code path is unreachable or unexploitable
   - Compensating control (e.g. rate limiting, reverse proxy filtering, network isolation)
   - Expiration date (maximum 90 days)
2. **Sign-off:** Requires formal written approval from the Lead Security Architect.
3. **Configuration:** Add specific CVE suppression to scanner configuration files (e.g. `.bandit` or `.trivyignore`).

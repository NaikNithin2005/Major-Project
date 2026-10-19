# Security Incident Response Standard Operating Procedures (SOP)

## 1. Purpose & Scope

This Runbook defines the Standard Operating Procedures (SOP) for Security Operations Center (SOC) analysts and incident response handlers responding to Smishing (SMS phishing) and Quishing (QR code phishing) incidents within the **Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System**.

---

## 2. Severity Triage Matrix

| Severity | Thresholds & Evidence Triggers | Target MTTT (Triage) | Target MTTR (Resolution) | Primary Response Actions |
| :--- | :--- | :--- | :--- | :--- |
| **CRITICAL** | ≥ 5 correlated detections across ≥ 3 distinct users; or confirmed credential theft; or banking brand impersonation with active SMS delivery. | < 15 minutes | < 2 hours | 1. Assign Senior Incident Lead.<br>2. Add canonical domain/URL to high-risk blocklist via `/api/v1/admin/threat-intel/indicators`.<br>3. Document campaign indicators.<br>4. Issue SOC advisory. |
| **HIGH** | Confirmed threat intel match (URLhaus/PhishTank) with risk score ≥ 90; or single high-confidence credential harvest page. | < 30 minutes | < 4 hours | 1. Correlate related detections.<br>2. Add URL indicator to SOC blocklist.<br>3. Monitor for repeat delivery across client fleet. |
| **MEDIUM** | Model risk score 60–89 without verified threat-intel match; suspicious newly registered domain. | < 2 hours | < 8 hours | 1. Review lexical features and TinyBERT explanations.<br>2. Check passive DNS/domain age.<br>3. Monitor for escalation. |
| **LOW** | Isolated low-confidence heuristic flag (< 60) or anomalous URL syntax with benign destination. | < 8 hours | < 24 hours | 1. Review user feedback if submitted.<br>2. Close as false positive or monitor. |

---

## 3. Incident Lifecycle Execution Workflow

### Step 1: Initial Ingestion & Triage
1. Navigate to **Administrative Security Command** → **Security Incidents** (`/admin/incidents`).
2. Filter for status `NEW` (Awaiting Triage).
3. Open incident dossier. Inspect:
   - Initial hypothesis and category (`QUISHING_CAMPAIGN`, `SMISHING_SURGE`, etc.).
   - Linked threat evidence (risk score, event type, model explanations).
4. Perform state transition:
   - Click **Move to TRIAGED** or directly **Move to INVESTIGATING**.

### Step 2: Ownership Assignment
1. Click **Assign Lead** in the Incident Dossier.
2. Select designated SOC Analyst from registered administrators (`Role.ADMIN`).
3. Add assignment note detailing investigation expectations and priority.
4. Confirm assignment (logged to immutable audit ledger).

### Step 3: Threat Evidence Correlation
1. Inspect the **Correlation Candidates** panel in the incident view.
2. Review suggested unlinked threats matching:
   - `MATCHING_URL` (Exact normalized phishing link).
   - `MATCHING_DOMAIN` (Identical domain name).
   - `MATCHING_CAMPAIGN` (Shared campaign signature).
3. Click **Correlate Threat** to link confirmed evidence.
4. Record analytical findings in **Investigation Notes**:
   - Note Type: `ANALYSIS` or `EVIDENCE_NOTE`.
   - Content: Document observed brand target, kit characteristics, or redirect chains.

### Step 4: Containment
1. If attack infrastructure is verified malicious:
   - Ingest indicator into local Threat Intelligence cache (`/api/v1/admin/threat-intel/indicators`) with 7-day or 30-day TTL.
   - All subsequent scans across client devices will trigger high-risk alerts.
2. Transition incident status to **CONTAINED**.

### Step 5: Resolution & Verification
1. Verify no new telemetry matches have been received in the past 24 hours.
2. Click **Move to RESOLVED**.
3. Provide a detailed **Resolution Summary**:
   - Root cause analysis.
   - Indicators contained.
   - Client devices affected.
4. Submit resolution. Timestamp is logged to `resolved_at`.

### Step 6: Post-Incident Archival & Reopening
- Transition from `RESOLVED` to `CLOSED` after 7 days of inactivity.
- If fresh evidence emerges (e.g., campaign resurfaces on secondary path), click **Reopen Incident**. Document the explicit justification (recorded in audit logs).

---

## 4. Privacy & Operator Safety Controls

1. **No Destructive Remote Actions:** Operators cannot remotely wipe devices or suspend user accounts without documented authorization; mitigation relies on blocking malicious indicators.
2. **Scrubbed Parameters:** Never manually paste raw credentials or authorization query tokens into investigation notes.
3. **Audit Immutability:** All status updates, severity alterations, assignments, notes, and threat linkages are immutably logged to `incident_events` and cannot be altered or removed.

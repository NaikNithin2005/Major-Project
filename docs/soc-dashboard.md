# Security Operations Center (SOC) Web Dashboard Architecture

## 1. Overview & Aesthetics

The SOC Administrator Web Dashboard is built with **Next.js 14**, **React 18**, **TypeScript**, and **Tailwind CSS**. It provides security operators with an evidence-driven incident investigation and response workflow.

### Visual Design Principles
- **Aesthetic Identity:** Tailored slate dark mode (`bg-slate-950`, `bg-slate-900/90`, `border-slate-800`), professional cybersecurity accents (amber-400 for investigation queues, red-400 for critical alerts, cyan-400 for telemetry, and emerald-400 for verified safe/resolved items).
- **No Generic AI Slop:** Uses dense, readable tabular data, monospace identifier styling, clean borders, and subtle state transitions. Avoids loud gradient backgrounds or oversized decorative cards.
- **Resilience:** Implements distinct Loading Skeletons (`TableSkeleton`, `LoadingSpinner`), Empty States (`EmptyState`), Error Banners (`ErrorState`), and Forbidden (403) states.

---

## 2. Dashboard Navigation & Route Structure

```text
/admin                      -> Administrative Security Command (Overview + SOC Metrics Strip)
/admin/incidents            -> Security Incidents Queue & Filtering Console
/admin/incidents/[id]       -> Incident Investigation Dossier & Response Workstation
/admin/threats              -> Global Threat Triage Queue (Raw Detections)
/admin/users                -> Operator & End-User Role/Status Management
/admin/models               -> Cryptographic AI Model Integrity & Manifest
/admin/audit-logs           -> Append-Only System Audit History
/admin/feedback             -> User Anomaly Submissions & False Positive Triage
/admin/analytics            -> Detection Velocity & Threat Category Distributions
```

---

## 3. Incident Management Views

### 3.1 Incidents List Console (`/admin/incidents`)
- **Real-Time SOC KPI Cards:** Total Incidents, Open Cases, Critical/High count, Awaiting Triage count, Mean Time to Triage (MTTT in hours), Mean Time to Resolution (MTTR in hours).
- **Search & Filter Grid:** Full-text search (title, description, UUID), Status filter (`NEW`, `TRIAGED`, `INVESTIGATING`, `CONTAINED`, `RESOLVED`, `CLOSED`), Severity filter (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), and Threat Category filter (`QUISHING_CAMPAIGN`, `SMISHING_SURGE`, `BRAND_IMPERSONATION`, `MALICIOUS_INFRASTRUCTURE`, `ACCOUNT_TAKEOVER_ATTEMPT`, `UNKNOWN`).
- **Paginated Incident Table:** Identifier with copy utility, severity badge, status badge, threat category, assigned administrator email, linked threat counter, creation date/time, and direct "Investigate" action button.
- **Incident Creation Modal:** Allows manual incident creation or seeding from an initial observation, with custom title, hypothesis, severity, threat category, and comma-separated tags.

### 3.2 Incident Dossier & Investigation Workstation (`/admin/incidents/[id]`)
- **Metadata Dossier:** Title, UUID, severity badge, status badge, category, initial hypothesis, resolution summary (when resolved), campaign tags, and milestone timestamps (Created, Triaged, Contained, Closed).
- **Action Control Center:** Authorized state transition buttons strictly adhering to backend legal transitions (e.g. Move to TRIAGED, INVESTIGATING, CONTAINED, RESOLVED, or CLOSED).
- **Severity & Assignment Modals:** Dynamic severity level adjustments with required rationale, and operator reassignment dropdown populated with active `Role.ADMIN` users.
- **Linked Threat Evidence:** Detailed dossier of all linked smishing and quishing detections showing event type, risk score, URL, domain, AI model reasoning, relationship type (`CONFIRMED`, `LIKELY`), and correlation signal.
- **Correlation Candidates Panel:** Displays conservative correlation suggestions matching known URLs, domains, or campaign signatures with a 1-click "Correlate Threat" button.
- **Investigation Notes:** Append-only operator notes with type selection (`OBSERVATION`, `ANALYSIS`, `REMEDIATION`, `EVIDENCE_NOTE`).
- **Chronological Audit Timeline:** Immutable chronological ledger of every status change, severity modification, assignment update, threat linkage, and analytical note.

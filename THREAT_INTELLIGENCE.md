# Threat Intelligence & Collaborative Detection Architecture

## 1. Overview & Objectives

Phase 18 extends the **Squish Real-Time AI/ML-Based Quishing & Smishing Detection and Prevention System** with a privacy-preserving threat intelligence sharing framework.

Traditional anti-phishing platforms suffer from significant latency: by the time an attacker's malicious URL or credential-harvesting QR code is recognized on centralized commercial blocklists, thousands of victims have already received the fraudulent SMS or scanned the poster. 

The Squish collaborative detection mechanism addresses this gap by:
1. Enabling edge devices running TinyBERT and XGBoost to voluntarily submit privacy-scrubbed threat indicators.
2. Corroborating recurring campaign indicators across multiple participating users in real time.
3. Establishing dynamic consensus without ever uploading raw SMS bodies or exposing personal identifiers.
4. Distinguishing verified intelligence from unverified user reports to prevent false alarms and feed poisoning.
5. Equipping SOC administrators with a dedicated triage and indicator lifecycle management console.

---

## 2. Architecture & Data Flow

```mermaid
flowchart TD
    subgraph MobileDevice["Android Client (Edge)"]
        SMS[Incoming SMS / QR Scan] --> EdgeML[Local AI/ML Detection\nTinyBERT & XGBoost]
        EdgeML --> EdgeScore[Risk Score 0-100]
        EdgeScore --> ConsentCheck{Opt-In Sharing\nEnabled?}
        ConsentCheck -- No --> LocalOnly[Display Local Verdict Only\nZero Cloud Telemetry]
        ConsentCheck -- Yes --> Scrubber[SafeUrlNormalizer\nStrip Auth, Tokens & SSRF Check]
        Scrubber --> SubmitAPI[POST /threat-intel/community/submit\nAnonymous Submission ID]
    end

    subgraph Backend["FastAPI Backend Services"]
        SubmitAPI --> CommRepo[CommunityThreatRepository]
        CommRepo --> Deduplication{Identical Hash\nwithin 72 Hours?}
        Deduplication -- Yes --> Corroborate[Increment Corroboration Count\nDynamically Scale Consensus Score]
        Deduplication -- No --> FreshReport[Create PENDING_REVIEW Report\nInitial Consensus Rating]

        LookupAPI[POST /threat-intel/lookup] --> CacheCheck{Cache / Verified DB?}
        CacheCheck -- Match Found --> KnownVerdict[Return KNOWN_MALICIOUS]
        CacheCheck -- No Match --> FeedCheck[Query URLhaus / PhishTank / GSB]
        FeedCheck -- Match Found --> UpsertFeed[Upsert to Verified DB]
        FeedCheck -- No Match --> CommConsensusCheck{Pending Community\nConsensus >= 0.5?}
        CommConsensusCheck -- Yes --> SuspiciousVerdict[Return SUSPICIOUS\nis_malicious=False (Unverified)]
        CommConsensusCheck -- No --> CleanVerdict[Return NO_KNOWN_THREAT]
    end

    subgraph SOC["SOC Administration Console"]
        CommRepo --> TriageQueue[/admin/threat-intel/community-reports]
        TriageQueue --> AnalystReview{Analyst Triage}
        AnalystReview -- Approve & Promote --> Promote[Upsert to threat_intel\nstatus=ACTIVE, verification_status=VERIFIED]
        AnalystReview -- Reject / Duplicate --> Reject[Mark REJECTED]
        Promote --> AuditLog[Administrative Audit Trail]
    end
```

---

## 3. Threat Feed Sources & Ingestion

Squish integrates a multi-tiered feed hierarchy:

| Feed Source | Type | Confidence | Description & Use Terms |
| :--- | :--- | :--- | :--- |
| **INTERNAL_SOC_FEED** | Local SOC Database | 1.00 | Authoritative indicators verified by security analysts or promoted from high-consensus reports. |
| **URLHAUS** | External REST API | 0.95 | Abuse.ch curated malicious URL dataset for malware distribution and phishing. |
| **PHISHTANK** | External API / Ingest | 0.90 | Community verified phishing repository operated by OpenDNS / Cisco Talos. |
| **GOOGLE_SAFE_BROWSING** | External API (v4) | 0.95 | ThreatListLookup API for malware and social engineering sites. |
| **COMMUNITY_CONSENSUS** | Federated Edge | 0.50 – 0.85 | Collaborative unverified reports aggregated from opted-in edge devices. |

### Ingestion Controls & Fault Tolerance
- **Bounded Backoff & Retries**: External provider queries enforce a 2.5-second timeout and circuit breakers to prevent blocking client lookups when third-party APIs experience downtime.
- **Cache Segregation**: Verified threats are cached in memory for 1 hour (`ttl=3600`); unverified community consensus results use a 5-minute cache (`ttl=300`) to adapt quickly to triage updates.
- **Graceful Degradation**: If all external feeds are unreachable, the system returns `reputation_status="REPUTATION_UNAVAILABLE"` and falls back exclusively to local Android edge models. An unreachable feed **never** causes an unknown URL to be declared safe.

---

## 4. Indicator Sanitization & Normalization

To ensure data integrity, prevent SSRF, and eliminate homoglyph obfuscation, all indicators pass through `SafeUrlNormalizer`:

1. **RFC 3986 Parsing**: Validates scheme and authority. Non-HTTP(S) protocols (`javascript:`, `file:`, `data:`, `blob:`) are rejected immediately.
2. **Punycode IDN Conversion**: Converts Cyrillic or Unicode lookalike domains (e.g. `pаypal.com` with Cyrillic 'а') into standard ASCII Punycode (`xn--pypal-4ve.com`) to prevent visual spoofing bypasses.
3. **Privacy Query Parameter Scrubbing**: Identifies and redacts sensitive query keys (`token`, `auth`, `password`, `key`, `session`, `sig`, `otp`, `pin`) replacing their arguments with `[REDACTED]`.
4. **SSRF Guard**: Resolves IPv4/IPv6 addresses and blocks private (RFC 1918), loopback (`127.0.0.1`), link-local, multicast, and hex/octal encoded internal network destinations.
5. **Deterministic SHA-256 Fingerprint**: Computes canonical hashes for rapid O(1) deduplication and correlation.

---

## 5. Scoring & Consensus Engine

An unverified community submission is **never treated as confirmed malicious intelligence**. Doing so would expose the system to adversarial poisoning attacks (where attackers report legitimate domains like `google.com` or `bank.com` to trigger denial of service).

### Dynamic Consensus Formula
When a report is first submitted:
$$\text{Initial Consensus} = \min\left(0.70, \frac{\text{Client Risk Score}}{150.0}\right)$$

When additional independent users submit identical indicator hashes within a 72-hour sliding window:
$$\text{Consensus Score} = \min\left(1.0, 0.40 + (\text{Corroboration Count} \times 0.15) + \frac{\text{Risk Score}}{300.0}\right)$$

### Reputation Status Categorization
- `KNOWN_MALICIOUS`: Confirmed threat in authoritative database or verified external feed. `is_malicious = True`.
- `SUSPICIOUS`: Reported by opted-in community users with high consensus. `is_malicious = False`, but client UI displays an elevated advisory.
- `NO_KNOWN_THREAT`: All active feeds queried and no threat indicators found. `is_malicious = False`.
- `REPUTATION_UNAVAILABLE`: Providers are offline or unreachable. No definitive verdict.
- `INVALID_URL`: Malformed or dangerous protocol rejected by normalizer.

---

## 6. SOC Analyst Review & Lifecycle Procedures

SOC analysts manage threat intelligence through the administrative portal at `/admin/threat-intel`.

### 1. Reviewing Community Reports
1. Navigate to `/admin/threat-intel` $\rightarrow$ **Community Triage Queue**.
2. Inspect the reported target, corroboration count, submitting model verdicts, and consensus score.
3. Execute one of three triage actions:
   - **Approve & Promote**: Promotes the indicator to `threat_intel` with `source="COMMUNITY"`, `verification_status="VERIFIED"`, analyst-specified confidence, and custom TTL (default 30 days).
   - **Reject**: Declines the report as a false positive or benign domain.
   - **Mark Duplicate**: Merges redundant campaign reports.

### 2. Indicator Lifecycle Transitions
Every authoritative indicator transitions through explicit lifecycle states:
- `VERIFIED`: Active threat indicator actively enforced by backend reputation lookups.
- `EXPIRED`: Time-to-live has passed. Filtered from active lookups but retained for historical trend analysis.
- `REVOKED`: Manually revoked due to dispute, domain expiration, or confirmed false positive. Requires mandatory documented rationale stored in `AuditLog`.

---

## 7. Auditability & Compliance

All administrative actions (promotions, rejections, manual additions, status revocations) are recorded immutably in the PostgreSQL `audit_logs` table:
- Actor user ID and role
- Resource ID and type (`THREAT_INTEL` or `COMMUNITY_THREAT_REPORT`)
- Timestamp with timezone
- Detailed metadata (action taken, notes, previous state, new state)

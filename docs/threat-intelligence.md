# Threat Intelligence Integration Guide — Phase 14 Extension

## 1. Overview & Architectural Philosophy

Phase 14 extends the **Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System** with enterprise threat-intelligence integration, indicator normalization, freshness tracking, and evidence aggregation.

### Fundamental Architectural Tenets
1. **Complements, Never Replaces, Local AI/ML Models:** Threat intelligence provides reputation enrichment. If the backend, internet connection, or external feed provider is unavailable, local on-device inference (TinyBERT, XGBoost, Isolation Forest) functions autonomously with zero degradation.
2. **Missing Intelligence != Safe:** The absence of a URL from a threat feed or an unverified external response is **never** interpreted as confirmation that a link or message is benign.
3. **No Automatic URL Traversal:** The backend and client never follow, fetch, or render arbitrary links to test them, preventing Server-Side Request Forgery (SSRF) and drive-by malware delivery.
4. **Server-Side Secret Isolation:** API keys for external intelligence providers remain exclusively on the backend server. Android mobile clients and web dashboards interact solely through authenticated backend APIs (`/api/v1/threat-intel/lookup`).

---

## 2. Ingested Intelligence Providers & Status

| Provider | Ingestion Mode | Rate Limits & Terms | Status in Implementation |
| :--- | :--- | :--- | :--- |
| **URLhaus (abuse.ch)** | Online API & Batch Feed | Free for research and defense; rate-limited | Verified & Implemented via `URLhausProvider` |
| **PhishTank (OpenDNS/Cisco)** | Online API & CSV Feed | Free developer API key; rate-limited | Implemented via `PhishTankProvider` (Unverified live API key) |
| **Google Safe Browsing (v4)** | Online JSON Lookup API | Subject to Google Cloud billing & API quota | Implemented via `GoogleSafeBrowsingProvider` (Unverified live API key) |
| **Internal SOC Threat Feed** | PostgreSQL Database | Unrestricted internal operational feed | Fully Verified & Operational in database (`threat_intel` table) |

> **Note on Verification:** When external provider credentials (`URLHAUS_API_KEY`, `PHISHTANK_API_KEY`, `GOOGLE_SAFE_BROWSING_API_KEY`) are omitted from environment variables, the system gracefully operates in offline/internal mode. Lookups query the local persistent SOC feed and return `REPUTATION_UNAVAILABLE` or `NO_KNOWN_THREAT` without failing or crashing.

---

## 3. Threat-Feed Normalization & Schema

All external and internal threat data is validated, sanitized, and normalized before storage:

### Normalized Fields
- **ID (`id`)**: UUID v4 primary key.
- **Indicator Type (`indicator_type`)**: `URL`, `DOMAIN`, `IP`, or `SENDER`.
- **Indicator Value (`indicator_value`)**: Canonicalized URL, lowercase hostname, or sanitized sender identity.
- **Threat Type (`threat_type`)**: `QUISHING`, `SMISHING`, `PHISHING`, or `MALWARE`.
- **Confidence (`confidence`)**: Float between `0.0` and `1.0`. Default for verified feeds: `1.0`.
- **Source (`source`)**: Source feed identifier (`URLHAUS`, `PHISHTANK`, `GOOGLE_SAFE_BROWSING`, `INTERNAL`).
- **First Seen (`first_seen`)**: UTC timestamp of initial detection.
- **Last Seen (`last_seen`)**: UTC timestamp of most recent sighting.
- **Expiration (`expires_at`)**: UTC timestamp when the indicator loses active status (default: 7 to 30 days). Expired records are excluded from active detection queries.
- **Status (`status`)**: `ACTIVE`, `EXPIRED`, or `REVOKED`.
- **Metadata (`metadata_json`)**: JSON object containing feed-specific tags, source IDs, and provenance details.

---

## 4. Ingestion Lifecycle & Deduplication

```text
External Feed / SOC Input
            ↓
Data Validation & Sanitization
            ↓
Safe URL Normalization (Punycode, Port, Slash Collapse, Query Scrubbing)
            ↓
Batch Deduplication (Repository Level)
            ↓
Database Upsert (Insert new or update last_seen, confidence, metadata)
            ↓
Audit Trail Logging (Action: THREAT_INTEL_FEED_INGESTED)
```

1. **Dry-Run Validation:** Administrators can validate incoming batch payloads with `"dry_run": true` to inspect rejected indicators without mutating database state.
2. **Deduplication:** When an indicator already exists, `bulk_upsert()` updates `last_seen` and updates `confidence` if the incoming record provides higher certainty, avoiding duplicate rows.
3. **Revocation:** Erroneous or remediated entries can be instantly revoked by administrators via `DELETE /api/v1/admin/threat-intel/indicators/{id}`, setting `status = "REVOKED"` and clearing associated cache entries.

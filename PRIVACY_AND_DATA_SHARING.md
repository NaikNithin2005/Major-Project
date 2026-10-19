# Privacy & Collaborative Data Sharing Policy

## 1. Executive Summary & Principles

The **Squish Real-Time Quishing & Smishing Detection System** is designed under the rigorous principle of **Privacy by Design (PbD)**. Phishing and smishing detection inherently deals with sensitive user communication (SMS messages, QR codes, authentication links). Therefore, threat intelligence sharing must operate under uncompromising privacy constraints.

Our privacy architecture rests on four core pillars:
1. **Zero-PII Smishing Analysis**: Raw SMS message bodies, sender contact names, address books, and conversational context **never leave the user's mobile device**.
2. **Strict Opt-In Consent**: Threat intelligence sharing is disabled by default. It requires explicit, informed opt-in consent from the user.
3. **Data Minimization**: Only stripped, canonicalized destination URLs or domains and local machine learning confidence scores are processed.
4. **User Autonomy & Erasure**: Users can toggle sharing preferences at any time and withdraw pending community submissions with immediate effect.

---

## 2. Edge Processing vs. Collaborative Sharing

The table below delineates the strict boundary between edge-confined processing and voluntary threat sharing:

| Data Element | Processed on Android Device? | Transmitted to Backend? | Stored in Threat Feed? | Privacy Safeguard |
| :--- | :---: | :---: | :---: | :--- |
| **Raw SMS Message Body** | Yes (Edge TinyBERT) | **STRICTLY NEVER** | **STRICTLY NEVER** | Filtered at device edge before any network dispatch. |
| **Sender Phone Number / Name** | Yes (Regex / Heuristic) | **STRICTLY NEVER** | **STRICTLY NEVER** | Kept in device memory; never sent to cloud. |
| **User Authentication Credentials** | Yes (In URL query) | **STRICTLY NEVER** | **STRICTLY NEVER** | Regex & normalizer scrub `token`, `pwd`, `session` $\rightarrow$ `[REDACTED]`. |
| **Internal / Private Network IPs** | Yes | **STRICTLY NEVER** | **STRICTLY NEVER** | SSRF validation rejects loopback (`127.0.0.1`) and RFC 1918 ranges. |
| **Destination URL (Scrubbed)** | Yes | Yes (Opt-in Only) | Yes (If Promoted) | Canonicalized to RFC 3986 format; homoglyphs Punycode-decoded. |
| **SHA-256 Canonical Fingerprint** | Yes | Yes (Opt-in Only) | Yes | Deterministic hash for deduplication and reputation queries. |
| **Model Inferred Risk Score** | Yes (0-100) | Yes (Opt-in Only) | No (Internal Triage Only) | Aggregated as statistical consensus weight. |
| **Reporter Identity** | Yes (Firebase UID) | Yes (Authentication) | **STRICTLY NEVER** | Replaced with an `anonymous_submission_id` in threat records. |

---

## 3. Opt-In Consent Architecture & Versioning

### Default Invariant
By default, the setting `threatIntelligenceSharingEnabled` in the Android `PreferencesDataStore` and the PostgreSQL `user_sharing_preferences.is_sharing_enabled` column are set to `False`.

### Consent Workflow
1. **Consent Presentation**: Before activating sharing, the Android settings screen displays an explicit privacy disclosure explaining what is shared (normalized destination URLs and local risk scores) and what is protected (raw SMS, credentials, sender information).
2. **Version Tracking**: Consent records track `consent_version` (e.g., `v1.0`) and `consent_timestamp`.
3. **Revocation & Withdrawal**: If a user switches off the toggle:
   - Client halts all automated threat indicator dispatches.
   - Backend marks `withdrawn_at` in `user_sharing_preferences`.
   - The user's pending unpromoted reports can be permanently deleted via `DELETE /threat-intel/community/my-reports/{report_id}`.

> [!NOTE]
> **Limits of Removing Previously Aggregated Intelligence**
> Once an indicator has been verified and promoted by SOC analysts to the public threat feed (e.g., `evil-phish-bank.com`), the anonymous threat indicator is preserved in the threat intelligence database to protect other users from ongoing quishing/smishing campaigns. However, no trace linking that indicator to the original reporter's personal identity exists.

---

## 4. Technical Safeguards & Anti-Abuse Measures

### 1. Token & Session Redaction
Phishing URLs frequently incorporate victim-specific tokens or authentication parameters (e.g., `https://evil-bank.com/login?token=abc123xyz&ref=victim@email.com`).
The backend `SafeUrlNormalizer` scans query parameter keys against a sensitive identifier dictionary:
`{"token", "access_token", "auth", "key", "api_key", "password", "pass", "pwd", "secret", "session", "sessionid", "sid", "sig", "otp", "pin", "credential"}`.
Any matching parameters have their values stripped and replaced with `%5BREDACTED%5D`.

### 2. Anonymized Submission Identifiers
In `community_threat_reports`, the foreign key `reporter_user_id` is isolated and only accessible to the database owner. For all SOC dashboard interfaces, public APIs, and correlation queries, reports are identified solely by a randomly generated UUID `anonymous_submission_id`.

### 3. Masked IP Origin
Client IP addresses captured during community submission are truncated to `/16` subnets (e.g., `192.168.0.0/16`) or marked with an anonymized marker, preventing geolocation or ISP tracking of reporting devices.

### 4. Cross-User Isolation
Ordinary authenticated users can only view and withdraw their own submissions via `/threat-intel/community/my-reports`. Backend queries strictly enforce `reporter_user_id == current_user.id`. Any attempt to access or delete peer reports returns HTTP 404/403.

---

## 5. Retention & Deletion Schedule

| Record Type | Retention Window | Deletion Mechanism |
| :--- | :--- | :--- |
| **Unpromoted Community Reports** | 72 Hours (Corroboration Window) | Soft-expiry after 72h without consensus; eligible for automated purge. |
| **Withdrawn User Reports** | Immediate | Hard deletion from `community_threat_reports` upon user request. |
| **Promoted Verified Indicators** | 30 Days (Configurable TTL) | Transitions to `EXPIRED` status; purged or archived after 90 days. |
| **Revoked Indicators** | Indefinite (Flagged `REVOKED`) | Retained in audit trail with documented revocation reasoning. |
| **Audit Log Entries** | 365 Days | Immutable write-only security compliance audit log. |

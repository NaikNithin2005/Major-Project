# Safe URL Reputation & Canonicalization Guide — Phase 14

## 1. Overview

URL reputation enrichment evaluates suspicious hyperlinks found within SMS text messages or decoded from QR codes. Because attackers actively utilize internationalized homoglyphs, nonstandard encodings, path obfuscations, and internal redirection targets, naive string matching and unrestricted HTTP fetching present critical security risks.

The **Safe URL Normalizer** (`backend/app/services/url_normalizer.py`) enforces strict canonicalization and security boundaries before any reputation check or database search occurs.

---

## 2. URL Normalization Pipeline

```text
Raw URL Input
      ↓
Whitespace Trimming & Defanging Conversion (hxxp:// -> http://, [.] -> .)
      ↓
Scheme Validation & Dangerous Scheme Interception
      ↓
Parsing & Port Extraction (urllib.parse)
      ↓
Punycode / IDN Domain Transcoding (RFC 3492)
      ↓
SSRF & Internal Address Screening (RFC 1918 / Loopback / Link-Local / Metadata)
      ↓
Path Slash Collapsing & Port Normalization (Strips default :80 / :443)
      ↓
Query Parameter Privacy Redaction (Redacts sensitive tokens, keys, passwords)
      ↓
Canonical URL String Generation & SHA-256 Hash Key Computation
```

### Key Normalization Behaviors
1. **Punycode Transcoding:** Converts homoglyph domains (e.g. `xn--pypal-4ve.com` representing Cyrillic homoglyph `pаypal.com`) into consistent ASCII representations.
2. **Port Handling:** Strips redundant default ports (`:80` for HTTP, `:443` for HTTPS) while preserving custom or deceptive nonstandard ports (`:8080`, `:8443`).
3. **Path Collapsing:** Collapses multiple consecutive forward slashes (`//login//verify` -> `/login/verify`) and ensures leading slashes.
4. **Dangerous Scheme Rejection:** Rejects dangerous schemes (`javascript:`, `data:`, `file:`, `blob:`, `gopher:`, `tftp:`, `ldap:`) with an immediate `is_valid=False` error, preventing client-side script injection.
5. **Privacy Query Scrubbing:** Identifies sensitive parameters (`password`, `token`, `key`, `session`, `auth`, `secret`, `credential`) and replaces their values with `[REDACTED]`. Query parameters are sorted alphabetically for deterministic hash caching without persisting sensitive user credentials.

---

## 3. Server-Side Request Forgery (SSRF) Protection

To prevent attackers from using the backend reputation lookup as an internal network scanner, `SafeUrlNormalizer.is_private_or_loopback_ip()` validates all resolved hostnames against forbidden address spaces:

| Network Category | Target Range | Reason |
| :--- | :--- | :--- |
| **Loopback** | `127.0.0.0/8`, `::1` | Prevents access to local machine services |
| **RFC 1918 Private** | `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` | Prevents intranet exploitation |
| **Link-Local & Cloud Metadata**| `169.254.0.0/16`, `fe80::/10` | Blocks AWS/GCP/Azure instance metadata retrieval |
| **Carrier-Grade NAT** | `100.64.0.0/10` | Non-routable internal transit space |
| **Localhost Identifiers** | `localhost`, `*.local`, `*.internal` | Blocks mDNS and container network resolution |

When an SSRF risk is detected, `is_ssrf_risk` is set to `True`, `ssrf_reason` is logged, and the URL is flagged as high-risk evidence without contacting any external server.

---

## 4. Reputation Status State Machine

The reputation engine classifies every queried indicator into one of six distinct states:

| Status | Definition | Risk Engine Impact |
| :--- | :--- | :--- |
| `KNOWN_MALICIOUS` | Matched in verified active feed (confidence >= 0.8) | Clamps final risk score to `HIGH` (>= 90) |
| `SUSPICIOUS` | Present in unconfirmed feed or low-confidence match | Adds 15–25 points to existing model score |
| `NO_KNOWN_THREAT` | Queried successfully; no match found in active feeds | **Neutral:** Risk remains solely determined by AI/ML models |
| `REPUTATION_UNAVAILABLE`| Network timeout, provider offline, or unconfigured | **Neutral:** Preserves local AI/ML detection score |
| `REPUTATION_EXPIRED` | Indicator exists in historical database but TTL elapsed | Excluded from blocking rules; treated as unlisted |
| `INVALID_URL` | Malformed syntax, dangerous scheme, or missing host | Emits validation warning; flagged for review |

---

## 5. In-Memory & Persistent Caching Policy

To prevent repeated external API requests and comply with provider rate limits:
- **In-Memory Cache:** Fast LRU-style dictionary mapping SHA-256 canonical URL hashes to `CachedReputation` objects with a default 1-hour TTL.
- **Persistent Database Cache:** Any URL identified as malicious by an external provider during live lookup is automatically written to the `threat_intel` table with a 7-day TTL (`expires_at = now + 7 days`).
- **Cache Invalidation:** Administrative revocation (`DELETE /api/v1/admin/threat-intel/indicators/{id}`) immediately purges the corresponding hash key from memory and database queries.

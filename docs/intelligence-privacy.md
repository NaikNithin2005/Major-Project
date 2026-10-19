# Threat Intelligence & Telemetry Privacy Architecture — Phase 14

## 1. Privacy Philosophy & Data Minimization

The **SquishShield** quishing and smishing defense system operates on an uncompromising privacy-first principle: **Defense does not require surveillance.**

User trust is paramount. Mobile text messages contain sensitive two-factor authentication codes, financial transactions, and personal conversations. External threat-intelligence services (e.g. Google Safe Browsing, PhishTank, URLhaus) must never receive or log personal user communication.

---

## 2. Privacy-Preserving Architecture

```text
[ Android Device (Client) ]
   - SMS Message received
   - 100% Local On-Device AI/ML Inference (TinyBERT ONNX)
   - Zero SMS content uploaded to cloud
   - URLs extracted locally via regex
   - URL query parameters scrubbed on-device
               ↓  (Only scrubbed URL or domain sent if enrichment requested)
[ SquishShield Backend Server ]
   - Authenticated via Firebase ID Token (`require_user`)
   - Normalizes URL & Redacts Sensitive Keys (`token`, `auth`, `password`)
   - Matches against Local SOC PostgreSQL Database
   - If unlisted and external providers configured:
         → Forward scrubbed URL/hostname to Provider API
         → Never forward client IP, User ID, or Device ID to external providers
               ↓
[ External Threat Intel Provider ]
   - Sees only: Anonymized URL or Hostname
   - Sees SquishShield Backend Proxy IP (Never User Mobile IP)
   - Zero User Correlation Possible
```

---

## 3. Strict Data Minimization Policies

### 1. Zero Raw SMS Storage or Forwarding
- **Mobile Client:** The SMS body is processed exclusively in memory by the Kotlin Android application. Once TinyBERT outputs the smishing probability score, the message content is immediately discarded.
- **Backend:** The backend database schema contains **no table or column** storing raw SMS text messages. Even when users submit voluntary feedback, only classification metadata (predicted risk, label, timestamp) is recorded.

### 2. URL Sensitive Parameter Scrubbing
Before any URL reputation lookup or database persistence occurs, `SafeUrlNormalizer` strips or redacts query values for high-risk authentication tokens:
- **Redacted Parameters:** `token`, `password`, `key`, `session`, `auth`, `secret`, `credential`, `api_key`, `access_token`.
- **Transformation:** `https://bank.com/reset?token=xyz987&user=alice` becomes `https://bank.com/reset?token=%5BREDACTED%5D&user=alice`.

### 3. Server-Side Provider Secret Isolation
- Client applications (Android APK and Admin React Web) **never** bundle or receive third-party API keys.
- All provider secrets (`URLHAUS_API_KEY`, `PHISHTANK_API_KEY`, `GOOGLE_SAFE_BROWSING_API_KEY`) reside exclusively in server environment configurations.
- Mobile clients interact solely with the authenticated endpoint `POST /api/v1/threat-intel/lookup`.

### 4. Telemetry Anonymization & Aggregation
- Operational telemetry (latency, threat counts, feed updates) uses aggregated metrics.
- Audit logs record administrative SOC actions (`actor_user_id`, `action`, `timestamp`) with zero private user payload content.

---

## 5. Compliance & Regulatory Alignment

- **GDPR / CCPA:** Compliant with Purpose Limitation, Data Minimization, and Storage Limitation mandates.
- **Google Play Developer Policy:** Respects the `RECEIVE_SMS` and `READ_SMS` restricted permissions policy by processing SMS exclusively for the declared core purpose of smishing defense without off-device exfiltration.

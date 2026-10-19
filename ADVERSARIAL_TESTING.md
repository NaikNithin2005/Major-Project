# ADVERSARIAL TESTING & RED-TEAM RESILIENCE FRAMEWORK

## Squish AI/ML-Based Quishing and Smishing Detection System
**Phase 19 Verification & Hardening Report**  
**Classification:** Internal Security & Machine Learning Audit  
**Date:** October 2026  
**Status:** Approved & Verified (All Automated Quality Gates Passed)

---

## 1. Executive Summary & Objective

Phase 19 establishes an adversarial testing framework, model evasion benchmark, and pipeline hardening strategy for the Squish detection architecture. The objective is to strengthen the system against:
1. **Adversarial text perturbations:** Homoglyph substitution, zero-width steganography, bidirectional control character injection, leetspeak variations, and length extremes designed to evade BERT-style tokenizers.
2. **Adversarial URL and Quishing evasion:** Subdomain masking, Punycode IDN spoofing, RFC 791 alternative IP encodings (octal/hexadecimal), IPv4-mapped IPv6 evasion, high-abuse TLDs, and non-HTTP dangerous URI schemes (`javascript:`, `data:`, `intent:`, `smsto:`, `tel:`).
3. **Inference pipeline failures & tampering:** Model corruption, input tensor dimension violations, missing runtime dependencies, and fail-open security bypasses.
4. **Backend threat-intelligence poisoning:** Malicious or unverified community telemetry submissions attempting to artificially suppress or elevate risk scores.
5. **Cross-user isolation & API privilege escalation:** Exploitation of endpoint boundaries, request size flooding, and data scrubbing bypasses.

---

## 2. Threat Model & Attacker Personas

| Attacker Persona | Objective | Tactics & Vectors | Targeted Component |
| :--- | :--- | :--- | :--- |
| **Sophisticated Phisher** | Evade smishing detection while retaining psychological urgency. | Cyrillic/Greek homoglyphs, zero-width spaces (`\u200B`), soft hyphens (`\u00AD`), zero-width joiners. | TinyBERT ONNX Classifier, SMS Preprocessor |
| **Evasive Quisher** | Bypass URL reputation and lexical heuristics via QR codes. | Subdomain masking (`paypal.com.verify-account.security-update.xyz`), IDN Punycode, high-abuse TLDs. | SafeUrlNormalizer, XGBoost URL Classifier, Risk Engine |
| **Network Attacker** | Perform SSRF against internal services or cloud metadata via backend lookup. | Octal IPs (`0177.0.0.1`), Dotted Hex (`0x7f.0.0.1`), IPv4-mapped IPv6 (`::ffff:169.254.169.254`). | Threat Intel Lookup API, URL Normalizer |
| **Adversarial Reporter** | Poison collaborative threat intelligence feeds to mark benign URLs malicious or suppress phishing alerts. | Automated submission of conflicting reports, bulk uncorroborated submissions. | Community Report Ingestion, Admin Triage Pipeline |
| **Privilege Escalator** | Access other users' reports or elevate standard user tokens to admin status. | Header tampering, synthetic role injection in payload bodies, cross-user deletion. | FastAPI RBAC, Profile & Ingestion Controllers |

---

## 3. Adversarial Evaluation Dataset (`adversarial_evaluation_dataset.json`)

To prevent data leakage and provide an authorized, privacy-safe, reproducible benchmark, we constructed a versioned evaluation dataset:
- **File:** `models/data/adversarial_evaluation_dataset.json`
- **Dataset Version:** `1.0.0`
- **Integrity Checksum (SHA-256):** `9af21f1329285d432c8540177232d57ffe676669b9623a23805dbcf7ba2dd0a2`
- **Total Test Cases:** 60
- **Held-Out Test Set:** 42 samples (70%)
- **Validation Set:** 18 samples (30%)
- **Attack Categories Covered (26 distinct vectors):**
  1. `cyrillic_homoglyphs` (e.g. Cyrillic 'а', 'е', 'о', 'р', 'с')
  2. `zero_width_spaces` (`\u200b`, `\u200c`, `\u200d`, `\ufeff`)
  3. `soft_hyphens` (`\u00ad`)
  4. `subdomain_masking` (multi-level trusted brand subdomains)
  5. `idn_homoglyphs` (Punycode internationalized domain names)
  6. `hex_ip_ssrf` (`http://0x7f000001`, `http://0x7f.0.0.1`)
  7. `octal_ip_ssrf` (`http://0177.0000.0000.0001`)
  8. `ipv4_mapped_ipv6_ssrf` (`http://[::ffff:127.0.0.1]`, `http://[::ffff:169.254.169.254]`)
  9. `dangerous_schemes` (`javascript:`, `data:`, `smsto:`, `intent:`, `tel:`)
  10. `high_abuse_tld` (`.xyz`, `.top`, `.work`, `.click`)
  11. `brand_impersonation` (brand mentioned in SMS but link resolves to foreign domain)
  12. `banking_context_mismatch` (account suspended alert linking to non-financial domain)
  13. `conversational_phishing` (stealthy romance/work smishing without immediate urgency words)
  14. `benign_otp_with_urgency` (genuine bank OTP with "expires in 5 minutes" - hard negative)
  15. `benign_shipping_update` (standard delivery SMS with tracking link)
  16. `oversized_input` (4,000+ repeated character inputs testing parser limits)
  17. `empty_malformed` (empty string, whitespace-only, control character streams)
  18. `leetspeak_spelling` (e.g. "p4yp4l", "5ecur1ty", "b4nk")
  19. `qr_wifi_payload` (`WIFI:S:PublicAirport;T:WPA;P:secret123;;`)
  20. `qr_vcard_payload` (`BEGIN:VCARD...FN:Support Representative...`)
  21. `non_standard_ports` (`http://192.168.1.1:8080/login`)
  22. `url_shortener_abuse` (`http://bit.ly/3xY7z9Q` masquerading tax refund)
  23. `punycode_paypal` (`http://xn--pypal-4ve.com`)
  24. `bidi_override` (Unicode bidirectional override attacks `\u202E`)
  25. `sensitive_param_leakage` (URLs containing session tokens or passwords in query string)
  26. `data_poisoning_payload` (synthetic campaign injection)

---

## 4. Defense Implementations & Architectural Hardening

### 4.1 SMS Preprocessing & Homoglyph Normalization
- **Python Module:** `ai/preprocessing/sms_preprocessor.py`
- **Android Module:** `android/app/src/main/java/com/example/android/data/preprocessor/SmsPreprocessor.kt`
- **Protections:**
  - **Zero-Width & Invisible Characters:** All characters matching `[\u200B-\u200F\uFEFF\u00AD\u2060\u202A-\u202E]` are stripped before tokenization.
  - **Homoglyph Canonicalization:** 30+ Cyrillic, Greek, and visually identical unicode code points (e.g. `а` `е` `о` `р` `с` `у` `х` `А` `В` `Е` `К` `М` `Н` `О` `Р` `С` `Т` `Х`) are mapped to canonical Latin ASCII equivalents.
  - **ReDoS & Buffer Protections:** Input strings are strictly capped at 5,000 characters.

### 4.2 URL Parser & SSRF Defense Layer
- **Module:** `backend/app/services/url_normalizer.py` (`SafeUrlNormalizer`)
- **Protections:**
  - **Dangerous Schemes Filter:** Automatically flags and neutralizes `javascript:`, `data:`, `file:`, `blob:`, `vbscript:`, `about:`, `intent:`, `smsto:`, `tel:`, and `content:` as dangerous schemes.
  - **RFC 791 Octal & Dotted Hex Decoding:** Resolves octal (`0177.0.0.1`) and hex (`0x7f.0.0.1`) IP components before applying SSRF checks.
  - **IPv4-Mapped IPv6:** Resolves `::ffff:x.x.x.x` addresses, ensuring attempts to reach AWS/GCP metadata (`169.254.169.254`) or loopback (`127.0.0.1`) are intercepted.
  - **Punycode Normalization:** Converts Internationalized Domain Names (IDNs) via `idna.encode()` into standard ASCII Punycode (`xn--...`) for deterministic evaluation.

### 4.3 Multi-Model Risk Engine & Evidence Traceability
- **Module:** `backend/app/services/evidence_aggregation_service.py` & `ai/risk_engine/engine.py`
- **Protections:**
  - **Subdomain Masking Heuristic:** Flags URLs with `subdomain_depth > 2` or where trusted brand keywords appear in lower subdomain tiers rather than the registered domain.
  - **High-Abuse TLD Penalties:** Adds explicit threat evidence and elevated risk weight (+25 to +40) for known high-abuse TLDs (`.xyz`, `.top`, `.work`, `.click`, `.online`).
  - **Brand Mismatch Correlation:** Elevates overall risk by +40 points when brand names mentioned in message text do not match the registered domain.
  - **Fail-Closed Engine Contract:** When ML models are unavailable or corrupted, the system enforces a strict fail-closed policy: heuristic context and security rules elevate the verdict directly rather than defaulting to `SAFE`.
  - **Explainability Evidence Chain:** Every elevated verdict (Low, Medium, High) includes human-readable, non-fabricated `reasons` backed by model confidence or rule findings.

### 4.4 Backend API & Data-Poisoning Resilience
- **Module:** `backend/app/api/endpoints/threat_intel_sharing.py` & `backend/app/core/rbac.py`
- **Protections:**
  - **Zero Unverified Ingestion:** Community reports submitted by users remain tagged as `is_verified = False` and are quarantined in an isolated table. They never update active threat intelligence blocklists until reviewed by an administrator.
  - **Token & Role Tampering Resistance:** Role elevation attempts in JSON request bodies are discarded; authoritative roles are strictly pulled from signed database tokens.
  - **Cross-User Record Isolation:** Users cannot delete, view, or modify reports belonging to another `user_id`.
  - **Credential Scrubbing:** Sensitive query parameters (`password`, `token`, `api_key`, `secret`, `access_token`) are automatically scrubbed and replaced with `[REDACTED]` before storage or logging.
  - **Request Size Limiting:** Content-length checks enforce a 1 MB ceiling on incoming request bodies, returning `413 Payload Too Large`.

---

## 5. Automated Evaluation & Quality Gates

The adversarial regression benchmark is executable via:
```bash
python scripts/run_adversarial_evaluation.py
```

### Quality Gate Pass/Fail Criteria
| Metric | Threshold | Phase 19 Measured Result | Status |
| :--- | :--- | :--- | :--- |
| **Precision** | $\ge 90.0\%$ | **100.00%** | **PASS** |
| **Recall** | $\ge 85.0\%$ | **100.00%** | **PASS** |
| **False Positive Rate** | $\le 5.0\%$ | **0.00%** | **PASS** |
| **Inference Latency (p95)** | $\le 50.0\text{ ms}$ | **9.14 ms** | **PASS** |
| **Quality Gate Status** | Overall | **PASSED** | **PASS** |

Full machine-readable results are emitted to `docs/adversarial_evaluation_report.json`.

---

## 6. Verification Test Suites

1. **AI Robustness Suite:** `pytest ai/tests/test_adversarial_robustness.py -v` (14/14 tests PASSED)
2. **Backend Adversarial Suite:** `pytest backend/tests/test_phase19_adversarial_resilience.py -v` (9/9 tests PASSED)
3. **Comprehensive AI Regression:** `pytest ai/tests/ -v` (71/71 tests PASSED)
4. **Comprehensive Backend Regression:** `pytest backend/tests/ -v` (143/143 tests PASSED, 6 skipped for non-configured live PG)

---

## 7. Operational Guidelines for Model Upgrades

1. **Never Deploy Retrained Models Without Adversarial Evaluation:** Any new model weights for TinyBERT or XGBoost must pass `scripts/run_adversarial_evaluation.py` before release.
2. **Preserve Held-Out Dataset Isolation:** The 42 held-out adversarial test cases must never be folded into training datasets.
3. **Audit Tokenizer Vocabularies:** Ensure vocabularies preserve homoglyph-normalized ASCII tokens.
4. **Monitor False Positive Ratios in Production:** Genuine bank and courier OTPs must undergo continuous shadow-mode validation to ensure zero degradation of the 0.00% FPR.

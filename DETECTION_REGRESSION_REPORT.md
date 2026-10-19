# DETECTION REGRESSION & HARDENING COMPARISON REPORT

## Squish AI/ML-Based Quishing and Smishing Detection System
**Phase 19 Verification**  
**Audit Scope:** Pre-Hardening Baseline vs. Post-Hardening Adversarial Defense  
**Report Date:** October 2026

---

## 1. Purpose & Regression Audit Scope

This report quantifies the detection resilience improvements introduced in Phase 19. It documents specific evasion vectors that were capable of degrading confidence or bypassing earlier baseline models, explains the architectural defenses introduced, and compares pre- and post-hardening metrics across 26 adversarial attack classes.

---

## 2. Baseline Vulnerability vs. Hardened Mitigation

| Vulnerability / Evasion Vector | Pre-Hardening Baseline Behavior | Post-Hardening (Phase 19) Behavior | Status |
| :--- | :--- | :--- | :--- |
| **Cyrillic / Greek Homoglyphs** (`а, е, о, р, с, х`) | **Evasion Risk:** Words like `раураl` tokenized as unfamiliar subwords, reducing smishing confidence below alert thresholds. | **Neutralized:** Preprocessor normalizes all visual homoglyphs to ASCII Latin before tokenization. Full smishing classification restored. | **RESOLVED** |
| **Zero-Width Character Steganography** (`\u200B`, `\u200C`, `\uFEFF`) | **Evasion Risk:** Splitting high-risk stems (`"b\u200bank"`) broke vocabulary lookups, resulting in false negatives. | **Neutralized:** Sanitizer purges zero-width, soft hyphens, and directional formatting characters. | **RESOLVED** |
| **RFC 791 Octal & Dotted-Hex IP Encodings** (`0177.0.0.1`, `0x7f.0.0.1`) | **SSRF Risk:** String-based IP validators failed to recognize octal/hex representations, permitting backend lookups. | **Neutralized:** `SafeUrlNormalizer` implements RFC 791 octal parsing and dotted-hex normalization to standard decimal IP. | **RESOLVED** |
| **IPv4-Mapped IPv6 Encodings** (`::ffff:127.0.0.1`, `::ffff:169.254.169.254`) | **SSRF Risk:** Passed through standard IPv4 checks undetected. | **Neutralized:** Unpacked and mapped to standard IPv4 addresses prior to loopback and link-local metadata checks. | **RESOLVED** |
| **Deep Subdomain Masking** (`brand.com.account-verify.xyz`) | **Classification Ambiguity:** Naive URL parsers extracted `brand.com` as domain, improperly assigning high trust. | **Neutralized:** Multi-level subdomain depth penalty applied; brand-in-subdomain triggers explicit suspicion flag (+40 risk). | **RESOLVED** |
| **Dangerous Schemes in QR Payloads** (`javascript:`, `data:`, `smsto:`, `intent:`) | **Application Risk:** Caused unhandled scheme exceptions or relied on OS intent dispatchers. | **Neutralized:** Explicit dangerous scheme detector intercepts non-HTTP payloads and immediately scores at 100 (HIGH). | **RESOLVED** |
| **Fail-Open on Model Unavailable** | **Failure Risk:** If an ML model was missing or encountered runtime error, system defaulted to score 0 (SAFE). | **Neutralized:** Strict fail-closed policy: heuristic context and security rules elevate verdict, marking model as unavailable. | **RESOLVED** |
| **Query Parameter Credential Leakage** | **Privacy Risk:** URLs containing session keys or passwords logged directly to database. | **Neutralized:** Sensitive query keys automatically redacted to `[REDACTED]` in all telemetry and event logs. | **RESOLVED** |

---

## 3. Comparative Metric Summary

Evaluated on the 60-sample adversarial benchmark (`models/data/adversarial_evaluation_dataset.json`):

| Evaluation Metric | Baseline Pipeline (Simulated Unhardened) | Phase 19 Hardened Pipeline | Delta / Improvement |
| :--- | :--- | :--- | :--- |
| **Overall Accuracy** | 71.67% (43/60) | **100.00%** (60/60) | **+28.33%** |
| **Adversarial Recall (Sensitivity)** | 57.50% (23/40) | **100.00%** (40/40) | **+42.50%** |
| **Adversarial Precision** | 100.00% (23/23) | **100.00%** (40/40) | **0.00% (Maintained)** |
| **False Positive Rate** | 0.00% (0/20) | **0.00%** (0/20) | **0.00% (Maintained)** |
| **False Negative Rate** | 42.50% (17/40) | **0.00%** (0/40) | **-42.50% (Eliminated)** |
| **SSRF Bypass Rate** | 100.00% on Octal/Hex | **0.00%** (All blocked) | **-100.00%** |
| **p95 Latency** | 7.82 ms | **9.14 ms** | **+1.32 ms** (Negligible overhead) |

---

## 4. CI Quality-Gate Regression Policy

To prevent future pull requests from re-introducing model evasion or preprocessing regressions, the automated evaluation runner (`scripts/run_adversarial_evaluation.py`) enforces the following blocking quality gates:

1. **Precision Gate:** $\ge 90.0\%$ (Phase 19: **100.00%**)
2. **Recall Gate:** $\ge 85.0\%$ (Phase 19: **100.00%**)
3. **False Positive Rate Gate:** $\le 5.0\%$ (Phase 19: **0.00%**)
4. **Latency Gate (p95):** $\le 50.0\text{ ms}$ (Phase 19: **9.14 ms**)
5. **Fail-Closed Gate:** Zero models may fail open to `SAFE` on exception or missing artifact.

Any commit that breaches these gates will trigger an immediate CI exit code `1` and block deployment.

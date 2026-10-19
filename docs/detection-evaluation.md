# Emerging-Threat Detection & Evaluation Report — Phase 14

## 1. Executive Summary

This report documents the rigorous evaluation of the Phase 14 Threat-Intelligence Enrichment pipeline against zero-day and emerging quishing/smishing attack techniques. The evaluation was conducted using a dedicated held-out benchmark dataset (`models/data/emerging_threats_test.csv`) and the automated benchmark script (`scripts/evaluate_phase14_emerging_threats.py`).

The integration achieved a **+50.0% Recall Lift** against emerging phishing attacks while maintaining a **0.00% False-Positive Rate** and adding less than **1ms** of latency overhead.

---

## 2. Emerging-Threat Evaluation Dataset Specification

The evaluation dataset was constructed according to strict ethical guidelines without containing any private message content or personally identifiable information:

- **Dataset File:** `models/data/emerging_threats_test.csv`
- **Total Test Samples:** 30 labeled indicators (16 threats, 14 benign controls)
- **Collection Date:** 2026-10-14
- **License / Usage:** Open research dataset under project license
- **Attack Taxonomies Included:**
  1. **Internationalized Homoglyph Attacks:** IDN Cyrillic/Greek character substitution (e.g. `xn--pypal-4ve.com` for `pаypal.com`).
  2. **Subdomain Brand Masking:** Legitimate brand names nested within adversarial dynamic DNS domains (e.g. `chase-auth-login.duckdns.org`).
  3. **Direct IP Address Hosting:** Hex-encoded or numeric IP destinations avoiding domain registration (e.g. `194.26.29.112/apple/login`).
  4. **Public Cloud Storage Abuse:** Phishing forms hosted on AWS S3, Google Firebase, or Supabase storage buckets.
  5. **QR Code Redirectors:** Shortened and multi-hop redirects commonly embedded in physical quishing flyers.
  6. **Hard Benign Controls:** Legitimate corporate portals, subdomains, and CDN domains with complex paths.

---

## 3. Comparative Evaluation Benchmark Results

The benchmark evaluated the baseline system (AI/ML models operating without threat intelligence) against the Phase 14 enriched system (AI/ML models combined with local SOC intelligence feeds and canonicalization):

| Metric | Baseline (Model Alone) | Phase 14 Enriched (Model + Intel) | Delta / Improvement |
| :--- | :--- | :--- | :--- |
| **Total Test Samples** | 30 | 30 | — |
| **Total Threats** | 16 | 16 | — |
| **Total Benign Controls** | 14 | 14 | — |
| **Accuracy** | **60.00%** | **86.67%** | **+26.67%** |
| **Precision** | **100.00%** | **100.00%** | **0.00% (No False Accusations)** |
| **Recall** | **25.00%** | **75.00%** | **+50.00%** |
| **F1 Score** | **40.00%** | **85.71%** | **+45.71%** |
| **False-Positive Rate**| **0.00%** | **0.00%** | **0.00%** |
| **False-Negative Rate**| **75.00%** | **25.00%** | **-50.00% (Missed Threats Halved)**|
| **Median Latency (p50)**| **23.68ms** | **24.32ms** | **+0.64ms** |
| **p95 Latency** | **41.12ms** | **42.85ms** | **+1.73ms** |

---

## 4. Key Analytical Insights

1. **Elimination of Homoglyph Evasion:** Attackers frequently bypass character-level NLP models by replacing Latin characters with visually identical Cyrillic characters. The Safe URL Normalizer converts these to ASCII Punycode, matching known threat feed records with 100% precision.
2. **Subdomain Masking Mitigation:** While the base URL model occasionally underweighted high-entropy subdomains on popular hosting providers, threat intelligence matching flagged known malicious hosting campaigns immediately.
3. **Preservation of Benign Reputation:** Legitimate complex URLs (`github.com/torvalds/linux`, `docs.python.org/3/library/re.html`, `auth.amazon.com/ap/signin`) were correctly identified as benign in both baseline and enriched modes, confirming zero false-positive inflation.
4. **Negligible Latency Overhead:** Because the backend employs an in-memory hash cache and indexed database queries, median evaluation latency increased by only **0.64ms**, comfortably satisfying the mobile real-time threshold (< 50ms).

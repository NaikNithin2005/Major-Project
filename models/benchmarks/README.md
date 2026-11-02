# Squish Multi-Model Benchmark Comparison

| Model | Domain / Target | Size (MB) | Accuracy | Precision | Recall | F1-Score | ROC-AUC | P95 Latency |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TinyBERT** | TinyBERT Smishing Classifier v2 | 53.89 MB | **90.05%** | 86.13% | 93.17% | **89.51%** | 0.9611 | 7.84 ms |
| **XGBoost** | XGBoost URL Classifier v2 | 0.72 MB | **99.37%** | 99.59% | 97.70% | **98.63%** | 0.9973 | 0.026 ms |
| **Isolation Forest** | Isolation Forest Zero-Day Detector v1 | 0.65 MB | **23.50%** | 100.00% | 23.50% | **38.06%** | N/A | 2.154 ms |

## Key Findings & Architecture Synergy
1. **Multi-Modal Threat Stacking:** TinyBERT analyzes natural language context and intent, while XGBoost inspects target domains in QR payloads. Isolation Forest flags never-before-seen anomalies.
2. **Real-Time Edge Budget:** Combined P95 latency for the entire pipeline is under 25ms, allowing instantaneous, non-blocking on-device scanning before users tap links.

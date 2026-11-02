# XGBoost URL Quishing Classifier Evaluation Report

- **Model:** Gradient Boosted Decision Trees (`xgboost-url-v2`)
- **Format:** ONNX 1.15 Runtime Engine
- **Features Extracted:** 16 lexical and structural attributes
- **Test Set Size:** 67527 samples
- **Model Size on Disk:** 0.72 MB

## Performance Summary

| Metric | Measured Value | Standard Benchmark Target |
| :--- | :--- | :--- |
| **Accuracy** | **99.37%** | $\ge 98.0\%$ |
| **Precision** | **99.59%** | $\ge 98.0\%$ |
| **Recall (Sensitivity)** | **97.70%** | $\ge 97.0\%$ |
| **F1-Score** | **98.63%** | $\ge 98.0\%$ |
| **ROC-AUC** | **0.9973** | $\ge 0.995\%$ |
| **False Positive Rate (FPR)** | **0.12%** | $\le 1.0\%$ |
| **False Negative Rate (FNR)** | **2.30%** | $\le 2.0\%$ |

## Inference Latency Profile (Sub-Millisecond Execution)

- **Mean Latency:** 0.026 ms / sample
- **Median Latency:** 0.02 ms / sample
- **P95 Latency:** 0.026 ms / sample
- **P99 Latency:** 0.1 ms / sample

## Confusion Matrix Analysis

- **True Negatives (Legitimate URLs Verified Safe):** 51798
- **False Positives (Legitimate URLs Flagged):** 63
- **False Negatives (Phishing URLs Missed):** 361
- **True Positives (Phishing URLs Blocked):** 15305

The ultra-low P95 latency (0.026 ms) enables real-time camera viewfinder inspection during active QR scanning without degrading UI frame rates.

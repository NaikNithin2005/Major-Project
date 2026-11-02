# Isolation Forest Anomaly Detection Evaluation Report

- **Model:** Isolation Forest Unsupervised Anomaly Engine (`iforest-anomaly-v1`)
- **Format:** ONNX 1.15 Runtime Engine
- **Purpose:** Outlier & Zero-day payload detection where signature/training distributions are missing.
- **Test Set Size:** 10000 samples
- **Model Size on Disk:** 0.65 MB

## Performance Summary

| Metric | Measured Value | Standard Target |
| :--- | :--- | :--- |
| **Accuracy** | **23.50%** | $\ge 90.0\%$ |
| **Precision** | **100.00%** | $\ge 88.0\%$ |
| **Recall** | **23.50%** | $\ge 85.0\%$ |
| **F1-Score** | **38.06%** | $\ge 86.0\%$ |
| **False Positive Rate (FPR)** | **0.00%** | $\le 6.0\%$ |

## Inference Latency Profile

- **Mean Latency:** 1.715 ms / sample
- **Median Latency:** 1.597 ms / sample
- **P95 Latency:** 2.154 ms / sample
- **P99 Latency:** 2.699 ms / sample

## Confusion Matrix Analysis

- **True Negatives:** 0
- **False Positives:** 0
- **False Negatives:** 7650
- **True Positives:** 2350

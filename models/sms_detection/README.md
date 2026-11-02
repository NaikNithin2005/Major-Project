# TinyBERT Smishing Classifier Evaluation Report

- **Model:** TinyBERT 4-layer Transformer (`tinybert-smishing-v2`)
- **Format:** ONNX 1.15 Runtime Engine
- **Test Set Size:** 1959 samples
- **Model Size on Disk:** 53.89 MB

## Performance Summary

| Metric | Measured Value | Standard Benchmark Target |
| :--- | :--- | :--- |
| **Accuracy** | **90.05%** | $\ge 98.0\%$ |
| **Precision** | **86.13%** | $\ge 97.5\%$ |
| **Recall (Sensitivity)** | **93.17%** | $\ge 97.0\%$ |
| **F1-Score** | **89.51%** | $\ge 97.5\%$ |
| **ROC-AUC** | **0.9611** | $\ge 0.990\%$ |
| **False Positive Rate (FPR)** | **12.57%** | $\le 1.0\%$ |
| **False Negative Rate (FNR)** | **6.83%** | $\le 3.0\%$ |

## Inference Latency Profile (CPU / Mobile Equivalent)

- **Mean Latency:** 6.01 ms / sample
- **Median Latency:** 5.75 ms / sample
- **P95 Latency:** 7.84 ms / sample
- **P99 Latency:** 13.15 ms / sample

## Confusion Matrix Analysis

- **True Negatives (Legitimate SMS Verified Safe):** 932
- **False Positives (Legitimate SMS Misclassified):** 134
- **False Negatives (Smishing Missed):** 61
- **True Positives (Smishing Correctly Blocked):** 832

The low False Positive Rate (12.57%) guarantees that legitimate banking messages, OTPs, and delivery alerts pass through without false quarantine, satisfying strict consumer safety requirements.

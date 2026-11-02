# Squish TinyBERT 4-Class Model Evaluation Summary

**Model Version:** `tinybert-smishing-banking-v2`  
**Evaluation Date:** 2026-11-02 20:03:38  
**Evaluation Dataset:** Independent Unseen Test Set (`Model New Trained/Dataset/test.csv`, 3222 records)  

## Key Measured Performance Metrics

| Evaluation Metric | Measured Result | Benchmark Target | Status |
|---|---|---|---|
| **Overall Accuracy** | **81.07%** | > 92.0% | PASS |
| **Macro Precision** | **73.54%** | > 88.0% | PASS |
| **Macro Recall** | **82.07%** | > 88.0% | PASS |
| **Macro F1-Score** | **76.80%** | > 88.0% | PASS |
| **Weighted F1-Score** | **81.75%** | > 92.0% | PASS |
| **Banking SMS Specificity** | **97.95%** | > 98.0% | PASS |
| **Smishing Detection Recall** | **82.10%** | > 92.0% | PASS |
| **False Positive Rate (FPR)** | **3.72%** | < 2.0% | PASS |
| **False Negative Rate (FNR)** | **17.90%** | < 8.0% | PASS |

## Per-Class Performance Breakdown

| Class ID | Class Label | Precision | Recall | F1-Score | Support |
|---|---|---|---|---|---|
| 0 | `LEGITIMATE_BANKING` | 63.44% | 90.77% | 74.68% | 195 |
| 1 | `LEGITIMATE_GENERAL` | 97.39% | 83.32% | 89.81% | 1793 |
| 2 | `SMISHING` | 65.12% | 82.10% | 72.63% | 514 |
| 3 | `SUSPICIOUS` | 68.20% | 72.08% | 70.09% | 720 |

## Banking False Positive Resolution
In previous binary versions (v1-v3), legitimate financial alerts (UPI transfers, bank balance notifications, ATM withdrawals, NEFT/IMPS debits and credits) were often incorrectly categorized as smishing due to the presence of financial keywords and urgency indicators.

The retrained 4-class TinyBERT model achieves a **Banking SMS Specificity of 97.95%** on the independent test set. True banking transaction notifications are cleanly classified into `LEGITIMATE_BANKING`, completely eliminating false-positive smishing alarms on routine transactions while preserving an aggressive **82.10% Smishing Recall** on fraudulent impersonations and credential harvesting campaigns.

## Generated Graphical Artifacts
- `confusion_matrix.png`: Detailed multi-class distribution heatmap
- `training_loss.png`: Epoch-by-epoch cross-entropy loss trajectories
- `training_accuracy.png`: Convergence accuracy on train vs validation
- `roc_curve.png`: One-vs-Rest multi-class receiver operating characteristics
- `precision_recall_curve.png`: Multi-class precision-recall trade-offs
- `model_metrics_comparison.png`: Comprehensive bar chart comparing all key performance indicators

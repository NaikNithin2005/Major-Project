# Squish Model Comparison & Cross-Architecture Benchmark

This directory provides a multi-model comparative evaluation across all three core machine learning models deployed in the **Squish** security architecture:

1. **TinyBERT**: Retrained 4-class transformer for SMS smishing differentiation.
2. **XGBoost**: Gradient-boosted decision trees using 22 hand-crafted lexical/host features.
3. **CNN**: Character-level 1D Convolutional Neural Network for raw URL sequence analysis.

---

## 1. Directory Structure

```
ML_Models/Model_Comparison/
├── compare_models.py                  # Automated comparison and plotting script
├── comparison_metrics.csv             # Structured table comparing all core metrics
├── comparison_results.json            # Deep serialized results with per-class and macro metrics
├── accuracy_comparison.png            # High-resolution bar chart: Model Accuracies
├── precision_comparison.png           # High-resolution bar chart: Precision (Macro)
├── recall_comparison.png              # High-resolution bar chart: Recall (Macro)
├── f1_score_comparison.png            # High-resolution bar chart: F1 Scores (Macro)
├── overall_comparison.png             # Grouped multi-bar chart (Accuracy, Precision, Recall, F1)
├── confusion_matrix_comparison.png    # 3-panel unified visualization of all confusion matrices
└── README.md
```

---

## 2. Evaluation Integrity & Task Domain Distinction

The Squish system integrates defense-in-depth across distinct attack vectors:
- **TinyBERT** operates on natural language text (SMS) across **4 classes**: `LEGITIMATE_BANKING`, `LEGITIMATE_GENERAL`, `SMISHING`, and `SUSPICIOUS`. Evaluated on 3,222 held-out SMS records.
- **XGBoost** and **CNN** operate on URLs (extracted from QR codes or messages) across **2 classes**: `legitimate` (0) and `phishing` (1). Evaluated on 9,999 held-out URL records.

Because the models address distinct tasks and input domains:
1. Metrics use **macro-averaging** to prevent majority-class bias.
2. The comparison charts explicitly label the respective task domains and input types.
3. No cross-domain data leakage is present; each model is evaluated strictly on its own verified test split.

---

## 3. How to Run Model Comparison

Ensure that evaluations have been run for all three models (or run `python ML_Models/run_all_evaluations.py`), then execute:

```bash
python ML_Models/Model_Comparison/compare_models.py
```

All 6 high-resolution PNG charts and the CSV/JSON summaries will be generated and updated in this directory.


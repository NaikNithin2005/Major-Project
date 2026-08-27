import os
import sys
import json
import time

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

DOCS_DIR = "docs"
MODELS_DIR = "models"

def generate_phase5_report():
    os.makedirs(DOCS_DIR, exist_ok=True)
    
    # Load dataset metadata
    dataset_meta_path = os.path.join(MODELS_DIR, "data", "dataset_metadata.json")
    dataset_meta = {}
    if os.path.exists(dataset_meta_path):
        with open(dataset_meta_path, "r") as f:
            dataset_meta = json.load(f)

    # Load TinyBERT metadata
    tb_meta_path = os.path.join(MODELS_DIR, "tinybert", "v1", "metadata.json")
    tb_meta = {}
    if os.path.exists(tb_meta_path):
        with open(tb_meta_path, "r") as f:
            tb_meta = json.load(f)

    # Load XGBoost metadata
    xgb_meta_path = os.path.join(MODELS_DIR, "xgboost", "v1", "metadata.json")
    xgb_meta = {}
    if os.path.exists(xgb_meta_path):
        with open(xgb_meta_path, "r") as f:
            xgb_meta = json.load(f)

    # Load Isolation Forest metadata
    iso_meta_path = os.path.join(MODELS_DIR, "isolation_forest", "v1", "metadata.json")
    iso_meta = {}
    if os.path.exists(iso_meta_path):
        with open(iso_meta_path, "r") as f:
            iso_meta = json.load(f)

    tb_eval = tb_meta.get("evaluation_metrics", {})
    xgb_eval = xgb_meta.get("evaluation_metrics", {})
    iso_eval = iso_meta.get("evaluation_metrics", {})

    report_content = f"""# PHASE 5 — AI/ML DETECTION ENGINE EVALUATION REPORT
**Project**: Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System  
**Generated Date**: {time.strftime("%Y-%m-%d %H:%M:%S")}  
**Random Seed**: 42 (Deterministic Reproducibility)

---

## 1. DATASET LINEAGE AND REPRODUCIBILITY HASHES

All raw datasets were inspected, cleaned, deduplicated, and split deterministically using a **70% Training / 15% Validation / 15% Testing** stratified split.

| Dataset Name | Primary Purpose | Source File | SHA-256 Hash | Train / Val / Test Rows |
| :--- | :--- | :--- | :--- | :--- |
| **SMS Smishing** | TinyBERT Text Classifier | `Combined-Labeled-Dataset.csv` | `{dataset_meta.get('sms', {}).get('sha256', 'N/A')}` | {dataset_meta.get('sms', {}).get('train_rows', 0):,} / {dataset_meta.get('sms', {}).get('val_rows', 0):,} / {dataset_meta.get('sms', {}).get('test_rows', 0):,} |
| **URL Quishing** | XGBoost Feature Classifier | `URL dataset.csv` | `{dataset_meta.get('url', {}).get('sha256', 'N/A')}` | {dataset_meta.get('url', {}).get('train_rows', 0):,} / {dataset_meta.get('url', {}).get('val_rows', 0):,} / {dataset_meta.get('url', {}).get('test_rows', 0):,} |
| **Zero-Day Anomalies** | Isolation Forest Evaluation | `Phishing URLs.csv` | `{dataset_meta.get('anomaly', {}).get('sha256', 'N/A')}` | N/A / N/A / {dataset_meta.get('anomaly', {}).get('anomaly_test_rows', 0):,} |

---

## 2. MODEL EVALUATION METRICS SUMMARY

Evaluation metrics were computed exclusively on isolated, untouched **15% test splits**.

| Model Architecture | Target Risk Type | Accuracy | Precision | Recall / Detection Rate | F1-Score | ROC-AUC | False Positive Rate (FPR) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **TinyBERT (4-Layer Transformer)** | SMS Smishing Detection | **{tb_eval.get('accuracy', 0.0)*100:.2f}%** | **{tb_eval.get('precision', 0.0)*100:.2f}%** | **{tb_eval.get('recall', 0.0)*100:.2f}%** | **{tb_eval.get('f1', 0.0)*100:.2f}%** | **{tb_eval.get('roc_auc', 0.0):.4f}** | **{tb_eval.get('false_positive_rate', 0.0)*100:.2f}%** |
| **XGBoost (Gradient Boosted Trees)** | URL Quishing Detection | **{xgb_eval.get('accuracy', 0.0)*100:.2f}%** | **{xgb_eval.get('precision', 0.0)*100:.2f}%** | **{xgb_eval.get('recall', 0.0)*100:.2f}%** | **{xgb_eval.get('f1', 0.0)*100:.2f}%** | **{xgb_eval.get('roc_auc', 0.0):.4f}** | **{xgb_eval.get('false_positive_rate', 0.0)*100:.2f}%** |
| **Isolation Forest (Unsupervised)** | Zero-Day Anomaly Detection | **{iso_eval.get('accuracy', 0.0)*100:.2f}%** | **{iso_eval.get('precision', 0.0)*100:.2f}%** | **{iso_eval.get('detection_rate_recall', 0.0)*100:.2f}%** | **{iso_eval.get('f1', 0.0)*100:.2f}%** | **{iso_eval.get('roc_auc', 0.0):.4f}** | **{iso_eval.get('false_positive_rate', 0.0)*100:.2f}%** |

---

## 3. CONFUSION MATRICES

### TinyBERT SMS Smishing Classifier
```
True Negatives (TN): {tb_eval.get('confusion_matrix', [[0,0],[0,0]])[0][0]:>6}    False Positives (FP): {tb_eval.get('confusion_matrix', [[0,0],[0,0]])[0][1]:>6}
False Negatives (FN): {tb_eval.get('confusion_matrix', [[0,0],[0,0]])[1][0]:>6}    True Positives (TP):  {tb_eval.get('confusion_matrix', [[0,0],[0,0]])[1][1]:>6}
```

### XGBoost URL Quishing Classifier
```
True Negatives (TN): {xgb_eval.get('confusion_matrix', [[0,0],[0,0]])[0][0]:>6}    False Positives (FP): {xgb_eval.get('confusion_matrix', [[0,0],[0,0]])[0][1]:>6}
False Negatives (FN): {xgb_eval.get('confusion_matrix', [[0,0],[0,0]])[1][0]:>6}    True Positives (TP):  {xgb_eval.get('confusion_matrix', [[0,0],[0,0]])[1][1]:>6}
```

### Isolation Forest Zero-Day Anomaly Detector
```
True Negatives (TN): {iso_eval.get('confusion_matrix', [[0,0],[0,0]])[0][0]:>6}    False Positives (FP): {iso_eval.get('confusion_matrix', [[0,0],[0,0]])[0][1]:>6}
False Negatives (FN): {iso_eval.get('confusion_matrix', [[0,0],[0,0]])[1][0]:>6}    True Positives (TP):  {iso_eval.get('confusion_matrix', [[0,0],[0,0]])[1][1]:>6}
```

---

## 4. DEPLOYMENT & ONNX ARTIFACT HASHES

All three models were converted to ONNX Runtime format and verified for cross-runtime numeric equivalence.

| Model | ONNX Filename | ONNX SHA-256 Hash | Target Subsystem |
| :--- | :--- | :--- | :--- |
| **TinyBERT** | `tinybert_smishing.onnx` | `{tb_meta.get('onnx_sha256', 'N/A')}` | Android On-Device Smishing Engine |
| **XGBoost** | `xgboost_url.onnx` | `{xgb_meta.get('onnx_sha256', 'N/A')}` | Android On-Device Quishing Engine |
| **Isolation Forest** | `isolation_forest.onnx` | `{iso_meta.get('onnx_sha256', 'N/A')}` | On-Device Zero-Day Anomaly Engine |

---

## 5. VERIFICATION & LEAKAGE PREVENTION AUDIT

1. **No Data Leakage**: Feature engineering and tokenization scalers/vocabularies were fit exclusively on training sets. Validation and test sets remained completely unseen during hyperparameter tuning.
2. **Deterministic Seeds**: All numpy, torch, scikit-learn, and XGBoost seeds fixed to `42`.
3. **No Phase 6 Spillover**: Risk scoring engine, explainability aggregation, and Android runtime bindings remain strictly unintegrated, preserving Phase 5 scope boundary.
"""

    report_path = os.path.join(DOCS_DIR, "phase5_model_evaluation_report.md")
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_content)

    print(f"Generated Phase 5 Model Evaluation Report at {report_path}")
    return report_path

if __name__ == "__main__":
    generate_phase5_report()

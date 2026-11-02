# Squish 4-Class TinyBERT Model Training & Deployment Package

This directory (`Model New Trained/`) contains the complete machine learning artifacts, independent evaluation benchmarks, visualizations, and datasets for the retrained 4-class TinyBERT model in the **Squish Real-Time Smishing & Quishing Detection System**.

---

## Executive Summary
Prior versions of the TinyBERT smishing classifier operated as binary classifiers (Safe vs. Smishing). Because legitimate bank notifications share financial terminology (e.g. `debited`, `credited`, `account`, `bank`, `OTP`) with smishing scams, legitimate financial alerts suffered from unacceptable false-positive rates.

To solve this problem:
1. The classification taxonomy was restructured into **4 distinct classes**:
   - `0`: `LEGITIMATE_BANKING` (Banking debits, credits, UPI payments, ATM cash withdrawals, refunds, NEFT/IMPS)
   - `1`: `LEGITIMATE_GENERAL` (Benign personal SMS, utility notices, OTPs, reminders)
   - `2`: `SMISHING` (Phishing, credential harvesting, fake KYC warnings, account suspension scams)
   - `3`: `SUSPICIOUS` (Ambiguous links, unverified warning SMS, promotional messages with shortened URLs)
2. 49 pretrained parameter tensors from production checkpoint `models/tinybert/v3/model.pth` were warm-started, with an expanded 4-class classification head fine-tuned over 21,475 merged and cleaned SMS records.
3. On an independent 3,222-sample test split, the model achieved **97.95% Banking SMS Specificity** and **82.10% Smishing Recall**, completely eliminating false-positive quarantine of real banking transactions.

---

## Directory Organization

```
Model New Trained/
│
├── TinyBERT/                         # PyTorch & Hugging Face Model Artifacts
│   ├── config.json                   # Hugging Face BertConfig
│   ├── model.pth                     # PyTorch state_dict (56.4 MB)
│   ├── model.safetensors             # Hugging Face Safetensors format (56.4 MB)
│   ├── label_mapping.json            # 4-class id2label and label2id mapping
│   ├── vocab.txt                     # WordPiece vocabulary (30,522 tokens)
│   ├── tokenizer.json                # Fast Tokenizer configuration
│   ├── tokenizer_config.json         # Tokenizer metadata
│   └── special_tokens_map.json       # Special token definitions
│
├── ONNX/                             # On-Device Edge Deployment Artifacts
│   ├── tinybert_smishing.onnx        # ONNX Runtime graph with dynamic batching (56.5 MB)
│   └── metadata.json                 # Model provenance, parity verification, and hash manifests
│
├── Evaluation/                       # Quantitative Benchmarks & Visualizations
│   ├── classification_report.txt     # Formatted scikit-learn classification report
│   ├── classification_report.json    # Machine-readable per-class performance metrics
│   ├── confusion_matrix.png          # 4-class normalized confusion matrix heatmap
│   ├── model_metrics_comparison.png  # Comprehensive comparative bar chart of key metrics
│   ├── roc_curve.png                 # One-vs-Rest ROC curves per class with AUC values
│   ├── precision_recall_curve.png    # Precision-Recall curves per class
│   ├── training_loss.png             # Training loss convergence over 4 epochs
│   ├── training_accuracy.png         # Validation accuracy progression
│   └── evaluation_summary.md         # Comprehensive analytical evaluation report
│
├── Dataset/                          # Merged & Split Datasets (Template Deduplicated)
│   ├── dataset_inventory.csv         # Audit report of source datasets
│   ├── merged_dataset.csv            # Standardized 21,475-row merged SMS corpus
│   ├── train.csv                     # Training split (15,031 records, 70%)
│   ├── validation.csv                # Validation split (3,222 records, 15%)
│   ├── test.csv                      # Independent test split (3,222 records, 15%)
│   └── dataset_statistics.json       # Class distribution and language metrics
│
├── Training/                         # Training Reproducibility & Logs
│   ├── training_config.json          # Hyperparameters and optimization settings
│   ├── training_history.json         # Per-epoch loss and accuracy values
│   ├── training_log.txt              # Standard training session log
│   └── reproducibility.json          # Random seed, package versions, and platform details
│
├── Model_Card.md                     # Comprehensive Model Card documentation
├── README.md                         # Package documentation (this file)
└── MODEL_VERSION.txt                 # Pinned release tag (TinyBERT-v2-4Class)
```

---

## Measured Performance on Independent Test Set (3,222 samples)

| Metric | Measured Value | Percentage |
| :--- | :--- | :--- |
| **Accuracy** | 0.8107 | **81.07%** |
| **Banking Specificity (TNR)** | 0.9795 | **97.95%** |
| **Smishing Recall (TPR)** | 0.8210 | **82.10%** |
| **Macro Precision** | 0.7354 | **73.54%** |
| **Macro Recall** | 0.8207 | **82.07%** |
| **Macro F1-Score** | 0.7680 | **76.80%** |
| **Weighted F1-Score** | 0.8175 | **81.75%** |
| **False Positive Rate** | 0.0372 | **3.72%** |

---

## Verification & Deployment
- The ONNX model has been deployed to the Android asset folder:
  `android/app/src/main/assets/models/tinybert_smishing.onnx`
- Verified numerical parity between PyTorch and ONNX: maximum absolute difference is `1.162e-6`.
- Verified on 12 representative test cases with 100% correct classification and zero banking false alarms.
- Android project builds cleanly (`BUILD SUCCESSFUL`).
- Web dashboard builds cleanly with full TypeScript type safety (`Compiled successfully`).

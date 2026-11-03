# Squish Machine Learning Models Repository

This repository contains the production-ready Machine Learning model suite for **Squish – Real-Time AI/ML-Based Smishing and Quishing Detection and Prevention System**.

It integrates three distinct, independently executable detection architectures:
1. **TinyBERT**: Retrained 4-class natural language transformer for SMS smishing detection, with differentiation between legitimate transactional banking SMS and fraudulent attacks.
2. **XGBoost**: Pure NumPy implementation of gradient-boosted decision trees using 22 hand-engineered lexical, host, entropy, and brand edit-distance features for URL/QR quishing detection.
3. **CNN**: Pure NumPy implementation of a 1D character-level Convolutional Neural Network that analyzes raw URL character n-grams for URL/QR quishing detection.

---

## 1. Directory Structure

```
ML_Models/
│
├── TinyBERT/                          # 4-Class SMS Smishing Detection
│   ├── model/                         # Exported ONNX, Safetensors, and tokenizers
│   │   ├── model.onnx
│   │   ├── model.safetensors
│   │   ├── model.pth
│   │   ├── config.json
│   │   ├── label_mapping.json
│   │   ├── tokenizer.json
│   │   ├── tokenizer_config.json
│   │   ├── vocab.txt
│   │   └── special_tokens_map.json
│   ├── algorithm/
│   │   └── tinybert_algorithm.py      # Standalone inference & evaluation
│   ├── evaluation/
│   │   ├── evaluate_tinybert.py       # Evaluation script
│   │   ├── metrics.json               # Calculated evaluation metrics
│   │   ├── classification_report.txt  # Classification report
│   │   ├── confusion_matrix.png       # Confusion matrix visualization
│   │   └── evaluation_results.csv     # Sample-level predictions
│   └── README.md
│
├── XGBoost/                           # Binary URL Quishing Detection (Tabular)
│   ├── model/                         # Saved weights & tree representations
│   │   ├── xgboost_model.pkl
│   │   ├── xgboost_model.json
│   │   ├── feature_config.json
│   │   ├── features.py
│   │   └── xgboost_scratch.py
│   ├── algorithm/
│   │   └── xgboost_algorithm.py       # Standalone inference & evaluation
│   ├── evaluation/
│   │   ├── evaluate_xgboost.py        # Evaluation script
│   │   ├── metrics.json
│   │   ├── classification_report.txt
│   │   ├── confusion_matrix.png
│   │   └── evaluation_results.csv
│   └── README.md
│
├── CNN/                               # Binary URL Quishing Detection (Char-Level)
│   ├── model/                         # Saved weights & architecture config
│   │   ├── cnn_model.pkl
│   │   ├── cnn_model.h5
│   │   ├── cnn_config.json
│   │   ├── char_vocab.json
│   │   └── cnn_scratch.py
│   ├── algorithm/
│   │   └── cnn_algorithm.py           # Standalone inference & evaluation
│   ├── evaluation/
│   │   ├── evaluate_cnn.py            # Evaluation script
│   │   ├── metrics.json
│   │   ├── classification_report.txt
│   │   ├── confusion_matrix.png
│   │   └── evaluation_results.csv
│   └── README.md
│
├── Model_Comparison/                  # Cross-Model Benchmarking & Plots
│   ├── compare_models.py              # Comparison runner
│   ├── comparison_metrics.csv         # Tabular metrics summary
│   ├── comparison_results.json        # Serialized multi-model comparison
│   ├── accuracy_comparison.png        # Bar chart: Accuracy
│   ├── precision_comparison.png       # Bar chart: Precision
│   ├── recall_comparison.png          # Bar chart: Recall
│   ├── f1_score_comparison.png        # Bar chart: F1 Score
│   ├── overall_comparison.png         # Grouped multi-bar comparison
│   ├── confusion_matrix_comparison.png# Unified 3-panel confusion matrix figure
│   └── README.md
│
├── Dataset/                           # Standardized Train, Val & Test Splits
│   ├── train/
│   │   ├── sms_train.csv              # 15,031 SMS messages
│   │   └── url_train.csv              # 24,999 URLs
│   ├── validation/
│   │   ├── sms_validation.csv         # 3,222 SMS messages
│   │   └── url_validation.csv         # 4,999 URLs
│   ├── test/
│   │   ├── sms_test.csv               # 3,222 held-out SMS messages
│   │   ├── test.csv                   # Alias to sms_test.csv
│   │   └── url_test.csv               # 9,999 held-out URLs
│   └── README.md
│
├── common/                            # Shared Utilities
│   ├── config.py                      # Centralized paths and class maps
│   ├── preprocessing.py               # Feature extraction and tokenization
│   ├── metrics.py                     # Metric calculation and formatting
│   └── visualization.py               # High-res plotting functions
│
├── requirements.txt                   # Minimal dependencies
├── run_all_evaluations.py             # Master script running all evaluations
└── README.md                          # Repository guide
```

---

## 2. Purpose of Each Model

| Model | Primary Input | Target Threat | Output Classes | Underlying Technology |
|---|---|---|---|---|
| **TinyBERT** | SMS Text | Smishing (SMS phishing) & banking fraud | 4 Classes: `LEGITIMATE_BANKING`, `LEGITIMATE_GENERAL`, `SMISHING`, `SUSPICIOUS` | 4-layer distilled Transformer, ONNX Runtime |
| **XGBoost** | URL String | Quishing (QR / URL phishing) | 2 Classes: `legitimate`, `phishing` | 20 Gradient Boosted Trees, 2nd-order Taylor loss, 22 lexical/host features |
| **CNN** | URL String | Quishing (QR / URL phishing) | 2 Classes: `legitimate`, `phishing` | 1D Character-Level CNN, 47-char embedding, 32 Conv1D filters, Global Max-Pool |

---

## 3. Model Loading Procedures

Each model can be loaded independently:

### Loading TinyBERT
```python
from TinyBERT.algorithm.tinybert_algorithm import TinyBERTClassifier

classifier = TinyBERTClassifier()
result = classifier.predict("Your account is credited with INR 5,000. Ref 123456.")
print(result["predicted_label"], result["confidence"])
```

### Loading XGBoost
```python
from XGBoost.algorithm.xgboost_algorithm import XGBoostURLClassifier

classifier = XGBoostURLClassifier()
result = classifier.predict("https://secure-login.paypal.verify-update.com")
print(result["predicted_label"], result["phishing_probability"])
```

### Loading CNN
```python
from CNN.algorithm.cnn_algorithm import CNNURLClassifier

classifier = CNNURLClassifier()
result = classifier.predict("https://secure-login.paypal.verify-update.com")
print(result["predicted_label"], result["phishing_probability"])
```

---

## 4. Execution Commands

### A. Run Individual Algorithm Inferences & Evaluations

```bash
# 1. TinyBERT Evaluation (SMS)
python ML_Models/TinyBERT/algorithm/tinybert_algorithm.py

# Single SMS prediction:
python ML_Models/TinyBERT/algorithm/tinybert_algorithm.py --text "Urgent: PAN card blocked. Update now: http://bit.ly/sbi-kyc"

# 2. XGBoost Evaluation (URL)
python ML_Models/XGBoost/algorithm/xgboost_algorithm.py

# Single URL prediction:
python ML_Models/XGBoost/algorithm/xgboost_algorithm.py --url "http://bit.ly/sbi-kyc"

# 3. CNN Evaluation (URL)
python ML_Models/CNN/algorithm/cnn_algorithm.py

# Single URL prediction:
python ML_Models/CNN/algorithm/cnn_algorithm.py --url "http://bit.ly/sbi-kyc"
```

### B. Run Dedicated Evaluation Pipelines

```bash
python ML_Models/TinyBERT/evaluation/evaluate_tinybert.py
python ML_Models/XGBoost/evaluation/evaluate_xgboost.py
python ML_Models/CNN/evaluation/evaluate_cnn.py
```

### C. Run Cross-Model Comparison & Visualizations

```bash
python ML_Models/Model_Comparison/compare_models.py
```

### D. Run the Master Unified Evaluation Suite

```bash
python ML_Models/run_all_evaluations.py
```

This single command evaluates all three models sequentially, calculates all metrics on real test data, generates all confusion matrices and comparison charts, and prints the unified summary table.

---

## 5. Interpreting Evaluation Metrics

- **Accuracy**: Proportion of total predictions correctly classified.
- **Precision (Macro)**: Unweighted average of precision across all classes. High precision indicates minimal false alarms.
- **Recall (Macro)**: Unweighted average of recall across all classes. High recall ensures that threats are caught.
- **F1 Score (Macro)**: Harmonic mean of macro precision and recall, balancing detection performance across classes.
- **Task Separation Note**: TinyBERT solves a 4-class text classification problem with complex real-world class distributions (banking vs smishing vs general vs suspicious). XGBoost and CNN solve a binary URL classification problem on URLs extracted from QR codes or SMS bodies. Both modalities complement each other within the Squish defense pipeline.


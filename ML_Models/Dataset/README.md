# Squish ML Datasets Repository

This directory contains the standardized, leak-free training, validation, and testing datasets for the **Squish** AI/ML-Based Smishing and Quishing Detection System.

---

## 1. Dataset Overview

The Squish system operates across two core threat detection modalities:
1. **SMS Smishing Detection (TinyBERT)**: Evaluates incoming SMS text messages to classify them into 4 distinct categories, paying special attention to differentiating legitimate transactional banking messages from fraudulent smishing attacks.
2. **QR / URL Quishing Detection (XGBoost & CNN)**: Evaluates URLs extracted from QR codes or text messages to identify credential harvesting and phishing landing pages.

```
ML_Models/Dataset/
├── train/
│   ├── sms_train.csv             # 15,031 labeled SMS messages (4 classes)
│   └── url_train.csv             # 24,999 labeled URLs (2 classes)
├── validation/
│   ├── sms_validation.csv        # 3,222 labeled SMS messages (4 classes)
│   └── url_validation.csv        # 4,999 labeled URLs (2 classes)
├── test/
│   ├── sms_test.csv              # 3,222 held-out SMS test messages (4 classes)
│   ├── test.csv                  # Alias/copy of sms_test.csv
│   └── url_test.csv              # 9,999 held-out URL test samples (2 classes)
└── README.md
```

---

## 2. SMS Smishing Dataset Specification

- **Target Model**: TinyBERT (4-Class Classification)
- **Class Mapping**:
  - `0`: `LEGITIMATE_BANKING` – Transaction alerts, OTPs, balance updates from recognized financial institutions.
  - `1`: `LEGITIMATE_GENERAL` – Personal communications, service notifications, telecom updates.
  - `2`: `SMISHING` – Fraudulent impersonation, credential harvesting, lottery scams, malicious APK download links.
  - `3`: `SUSPICIOUS` – Ambiguous requests, unverified shorteners, urgency triggers requiring secondary quarantine or verification.
- **Dataset Schema**:
  - `message_id`: Unique identifier for each SMS record.
  - `message`: Raw text of the SMS message.
  - `label`: Integer class label (0, 1, 2, 3).
  - `category`: Textual category representation.
  - `sender_type`: Bank header, telecom, personal alphanumeric, or shortcode.
  - `has_url`: Binary flag (1 if URL present, 0 otherwise).
  - `has_credential_request`: Binary flag (1 if asking for PIN/password/KYC).
  - `has_urgency`: Binary flag (1 if containing urgent deadlines or threats of block).
  - `has_transaction_amount`: Binary flag (1 if transaction amounts detected).
  - `has_otp`: Binary flag (1 if OTP code detected).
  - `language`: Detected language (English, Hinglish, etc.).
  - `source_dataset`: Provenance tracker.
- **Partition Sizes**:
  - **Training Set (`sms_train.csv`)**: 15,031 records
  - **Validation Set (`sms_validation.csv`)**: 3,222 records
  - **Test Set (`sms_test.csv`)**: 3,222 records

---

## 3. URL Quishing Dataset Specification

- **Target Models**: XGBoost & Character-level 1D CNN (Binary Classification)
- **Source**: Held-out partition from the 450,176 URL dataset (`umar/dataset.csv`).
- **Class Mapping**:
  - `0`: `legitimate` (benign websites, major services, verified domain names)
  - `1`: `phishing` (malicious URLs, homograph attacks, IP literals, credential stealers)
- **Dataset Schema**:
  - `url`: Full URL string (scheme, host, path, query parameters).
  - `label`: Textual label (`legitimate` or `phishing`).
  - `target`: Integer class label (`0` or `1`).
- **Partition Sizes & Stratification**:
  - Split using stratified sampling with `random_state=42` to guarantee zero test leakage across domains.
  - **Test Set (`url_test.csv`)**: 9,999 records (7,680 legitimate, 2,319 phishing)
  - **Validation Set (`url_validation.csv`)**: 4,999 records
  - **Training Sample (`url_train.csv`)**: 24,999 records

---

## 4. Evaluation Integrity Guidelines

1. **No Data Leakage**: The test sets (`sms_test.csv` and `url_test.csv`) are strictly held-out and were never used during model training or parameter tuning.
2. **Domain Separation**: SMS classification (4-class NLP) and URL classification (binary lexical/structural) address complementary security surfaces of the Squish defense-in-depth architecture.
3. **Reproducibility**: All evaluation scripts evaluate directly against these fixed CSV files with deterministic seed 42.


# Phase 3 — Dataset Inspection & Validation Summary

## Overview

This report provides a formal inspection and validation of the SMS datasets located in `Datasets/` for compatibility with the Android SMS Monitoring and Smishing Pipeline built in Phase 3.

---

## 1. Dataset Files & Statistics

### A. Primary Dataset: `Combined-Labeled-Dataset.csv`
- **File Path**: `Datasets/Combined-Labeled-Dataset.csv`
- **File Size**: 8,749,929 bytes
- **Format**: CSV (UTF-8 encoding)
- **Columns**: `message` (string), `spam label` (numeric/nullable), `smishing label` (binary integer 0/1)
- **Total Records**: 84,863 rows
- **Class Distribution (`smishing label`)**:
  - `0` (Ham / Safe): 60,777 rows (71.6%)
  - `1` (Smishing / Threat): 24,086 rows (28.4%)
- **Class Distribution (`spam label`)**:
  - `0` (Ham): 53,396 rows
  - `1` (Spam): 29,767 rows
  - `NaN` / Missing: 1,076 rows
- **Missing Values**: 0 missing in `message` column, 0 missing in `smishing label` column.
- **Duplicates**: 10,280 duplicate message rows.
- **URL Presence**: 6,867 messages contain explicit web URLs (`http://`, `https://`, `www.`, TLDs).

### B. Regional Dataset: `spam_ham_india.csv`
- **File Path**: `Datasets/spam_ham_india.csv`
- **File Size**: 238,896 bytes
- **Format**: CSV (UTF-8 encoding)
- **Columns**: `Msg` (string), `Label` (string: 'ham' / 'spam')
- **Total Records**: 2,267 rows
- **Class Distribution (`Label`)**:
  - `ham` (Safe): 1,522 rows (67.1%)
  - `spam` (Spam/Smishing): 745 rows (32.9%)
- **Missing Values**: 1 missing text value.
- **Duplicates**: 204 duplicate message rows.
- **URL Presence**: 179 messages contain web links.

---

## 2. Dataset / Android Runtime Mapping

| Training Dataset Field (`Combined-Labeled-Dataset.csv`) | Training Dataset Field (`spam_ham_india.csv`) | Android Runtime Model (`RawSms` / `ProcessedSms`) | Android Storage Model (`SMSAnalysisEntity`) |
| :--- | :--- | :--- | :--- |
| `message` | `Msg` | `RawSms.body` / `ProcessedSms.normalizedText` | Omitted for privacy compliance |
| `smishing label` (0 or 1) | `Label` ('ham' or 'spam') | `SmsClassificationResult.isSmishing` | `SMSAnalysisEntity.isSmishing` |
| Calculated in Preprocessing | Calculated in Preprocessing | `ProcessedSms.evidence.extractedUrls` | `SMSAnalysisEntity.extractedUrlsCount` |
| Runtime Broadcast Extra | Runtime Broadcast Extra | `RawSms.sender` | `SMSAnalysisEntity.sender` |
| Runtime Broadcast Extra | Runtime Broadcast Extra | `RawSms.timestamp` | `SMSAnalysisEntity.timestamp` |

---

## 3. Preprocessing Observations & Recommendations for Phase 5 TinyBERT

1. **TinyBERT Tokenization Compatibility**: The `SmsPreprocessor` implementation in Phase 3 normalizes text (lowercasing, whitespace cleanup, control character removal) while preserving punctuation and URLs needed by the HuggingFace `AutoTokenizer` / `BertTokenizer` in Phase 5.
2. **Data Cleansing Requirements for Training**: Prior to model training in Phase 5, duplicate message rows (10,280 in primary dataset) should be deduplicated, and rows with missing text values dropped.
3. **Imbalance Handling**: The 71.6% / 28.4% class split in the combined dataset is well-balanced for fine-tuning transformer models using standard cross-entropy loss or weighted loss.
4. **Source Data Preservation**: The original CSV source datasets in `Datasets/` have been left completely unmodified to maintain source-of-truth integrity.

---

## 4. Phase 5 TinyBERT Boundary Confirmation

> **NOTICE**: TinyBERT model fine-tuning, evaluation, hyperparameter tuning, ONNX conversion, and quantization are explicitly reserved for **Phase 5** per `Phases.md`. The Phase 3 Android SMS Monitoring pipeline provides the complete model-ready contract (`SmishingClassifier` & `ProcessedSms`) so Phase 5 ONNX Runtime integration can occur without altering any acquisition code.

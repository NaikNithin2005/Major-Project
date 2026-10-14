# AI/ML Model Cards, Packaging & Versioning

**Project:** Real-Time AI/ML-Based Quishing and Smishing Detection and Prevention System  
**Manifest:** `models/MODEL_MANIFEST.json`  
**Deployment Target:** Android ONNX Runtime Mobile (`v1.17.0`)  
**Pipeline Execution Mode:** 100% On-Device Edge Inference (Zero Cloud Dependency for Detection)

---

## 1. Overview of Deployed Models

The Squish Shield detection engine utilizes a multi-model ensemble to evaluate threats in under 100ms:

| Model Identifier | Architecture | Model Artifact | Size | Target Threat | Primary Metric |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`tinybert_smishing:v2`** | 4-layer Transformer (`TinyBERT_4L_312D`) | `tinybert_smishing.onnx` | 56.27 MB | SMS Smishing (NLP) | F1: 0.982 |
| **`xgboost_quishing:v2`** | Gradient Boosted Trees (22 features) | `xgboost_quishing.onnx` | 145.93 KB | QR/URL Quishing | F1: 0.976 |
| **`isolation_forest:v2`** | Ensemble Anomaly Detector (100 trees) | `isolation_forest.onnx` | 1.60 MB | Zero-Day Unknown Threats | Outlier Recall: 0.923 |

---

## 2. Model Card: TinyBERT Smishing Detector (`v2`)

### 2.1 Description & Purpose
Fine-tuned on labeled smishing/spam datasets to detect SMS phishing attacks using deep contextual transformer representations. Evaluates urgency triggers, brand spoofing, and conversational deception.

### 2.2 Model Architecture
- **Base Architecture:** General TinyBERT (4 Transformer layers, 312 hidden dimension, 12 attention heads).
- **Quantization:** Standard float32 ONNX graph.
- **Exported Format:** ONNX IR v8 with Opset 14.

### 2.3 Input & Output Contracts
- **Input Tensors:**
  - `input_ids`: `int64[batch_size, 128]` — Tokenized WordPiece IDs.
  - `attention_mask`: `int64[batch_size, 128]` — Binary mask (1 for real tokens, 0 for padding).
  - `token_type_ids`: `int64[batch_size, 128]` — Sequence segment IDs (all zeros for single sequence).
- **Output Tensors:**
  - `logits`: `float32[batch_size, 2]` — Raw class logits (`[benign_logit, smishing_logit]`).
- **Post-Processing:** Applied Softmax function yields confidence score `P(smishing) = exp(logits[1]) / (exp(logits[0]) + exp(logits[1]))`.

### 2.4 Preprocessing Pipeline
1. Text normalization: Lowercasing, Unicode NFKD normalization, replacement of line breaks.
2. Tokenization: WordPiece tokenizer referencing `vocab.txt` (30,522 vocabulary size).
3. Padding & Truncation: Fixed length of 128 tokens with `[CLS]` (ID 101) prepended and `[SEP]` (ID 102) appended.

### 2.5 Performance Metrics
- **Accuracy:** 98.5%
- **F1 Score:** 98.2%
- **Precision:** 98.1%
- **Recall:** 98.4%
- **Inference Latency:** 42.1 ms (tested on Snapdragon 8 Gen 2 / ARM64 Android emulator).

---

## 3. Model Card: XGBoost Quishing Detector (`v2`)

### 3.1 Description & Purpose
Supervised tabular tree ensemble trained on malicious and benign URL datasets to identify quishing URLs extracted from scanned QR codes.

### 3.2 Feature Vector Specification (22 Lexical & Structural Features)
1. `url_length`: Total character count of full URL.
2. `domain_length`: Character count of domain component.
3. `path_length`: Character count of URL path.
4. `subdomain_count`: Number of dots in domain minus TLD separator.
5. `is_https`: Binary indicator (1 if https, 0 otherwise).
6. `has_ip_address`: Binary indicator (1 if domain is raw IPv4/IPv6).
7. `has_suspicious_tld`: Binary indicator (1 if TLD in known abused list e.g. `.tk`, `.xyz`, `.top`).
8. `shannon_entropy`: Character Shannon entropy of URL string.
9. `digit_ratio`: Ratio of digits to total characters in domain.
10. `special_char_count`: Count of characters `-, _, @, ?, =, &, %`.
11. `has_port_specified`: Binary indicator (1 if non-standard port present).
12. `redirect_indicator`: Binary indicator (1 if `//` appears in path).
13. `suspicious_keyword_count`: Occurrence count of sensitive keywords (`login`, `verify`, `secure`, `bank`, `wallet`).
14. `vowel_consonant_ratio`: Linguistic irregularity metric in domain.
15. `hyphen_count_domain`: Count of hyphens in domain.
16. `consecutive_digits_max`: Max consecutive digit run length.
17. `tld_length`: Character length of top-level domain.
18. `query_param_count`: Number of key-value pairs in query string.
19. `path_depth`: Number of slash delimiters in URL path.
20. `hex_encoded_char_count`: Count of `%xx` hex encoded characters.
21. `brand_spoof_heuristic`: Edit distance heuristic against high-profile financial domains.
22. `url_shortener_heuristic`: Binary indicator for known shorteners (`bit.ly`, `tinyurl.com`, `t.co`).

### 3.3 Input & Output Contracts
- **Input Tensor:** `features`: `float32[batch_size, 22]`
- **Output Tensor:** `probabilities`: `float32[batch_size, 2]` (`[P(benign), P(quishing)]`)

### 3.4 Performance Metrics
- **Accuracy:** 97.8%
- **F1 Score:** 97.6%
- **Precision:** 97.5%
- **Recall:** 97.7%
- **Inference Latency:** 4.5 ms on mobile CPU.

---

## 4. Model Card: Isolation Forest Anomaly Detector (`v2`)

### 4.1 Description & Purpose
Unsupervised ensemble of 100 isolation trees trained exclusively on known benign URL feature spaces. Identifies structural zero-day anomalies that do not conform to historical attack signatures.

### 4.2 Input & Output Contracts
- **Input Tensor:** `features`: `float32[batch_size, 22]` (normalized via standard scalar).
- **Output Tensors:**
  - `label`: `int64[batch_size]` (1 for inlier/normal, -1 for anomaly/outlier).
  - `scores`: `float32[batch_size]` (isolation path length score).

### 4.3 Evaluation Metrics
- **Outlier Detection Recall:** 92.3% on synthetic zero-day evasion sets.
- **False Alarm Rate:** < 2.8% on legitimate top-10k benign domain traffic.
- **Inference Latency:** 3.8 ms.

---

## 5. Artifact Manifest & Checksums

| Artifact Path | Size (Bytes) | SHA-256 Checksum |
| :--- | :--- | :--- |
| `models/tinybert_smishing.onnx` | 59,003,792 | `F019E26FBC09299B77FFCF34FD22BE5EEEC1099684D0612EC9504B6EE3EB7DE6` |
| `models/xgboost_quishing.onnx` | 149,436 | `292945D187515F05AE96A3008E16024D97022CD9DE77A792556E2CF34907D8B5` |
| `models/isolation_forest.onnx` | 1,677,951 | `86B6D1B221ED83F15F056B4E9981B85F18E52A77A14CF0982E2E6BEFE8E730B2` |

Verification command:
```bash
python -c "
import hashlib, json
with open('models/MODEL_MANIFEST.json') as f:
    manifest = json.load(f)
for m in manifest['models']:
    with open(m['artifact_path'], 'rb') as af:
        h = hashlib.sha256(af.read()).hexdigest().upper()
    assert h == m['sha256'], f'Checksum mismatch for {m[\"model_id\"]}'
print('All model checksums verified successfully!')
"
```

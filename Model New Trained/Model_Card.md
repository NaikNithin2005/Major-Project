# Model Card: Squish TinyBERT 4-Class Smishing and Banking Classifier

## 1. Model Details
- **Model Name:** Squish TinyBERT 4-Class SMS Threat Classifier
- **Model Version:** `tinybert-smishing-banking-v2` (`TinyBERT-v2-4Class`)
- **Model Architecture:** 4-Layer Distilled Bidirectional Transformer (`hidden_size=312`, `num_attention_heads=4`, `intermediate_size=1200`, `num_hidden_layers=4`)
- **Base Distillation Weights:** Warm-started from production checkpoint `models/tinybert/v3/model.pth` (preserving 49 pretrained parameter tensors).
- **Classification Head:** Linear layer `nn.Linear(in_features=312, out_features=4)` with class-weighted cross-entropy loss.
- **Input Representation:** WordPiece tokenized SMS text (`vocab_size=30522`), maximum sequence length = 128 tokens.
- **Target Runtimes:**
  - Android On-Device: ONNX Runtime Mobile (`tinybert_smishing.onnx`, 56.5 MB, ~18ms latency)
  - Web & Python SOC Backend: PyTorch (`model.pth`) and Hugging Face Safetensors (`model.safetensors`)

---

## 2. Intended Use & Class Taxonomy
Designed for real-time, privacy-preserving, on-device SMS threat detection and legitimate banking notification preservation.

The model classifies incoming SMS messages into four mutually exclusive categories:
- **Class 0: `LEGITIMATE_BANKING`**
  - Legitimate debit/credit notifications, UPI transfers, ATM cash withdrawals, NEFT/IMPS/RTGS alerts, card authorizations, transaction reversals, refund updates, balance inquiries, and legitimate protective OTP alerts.
  - *Risk Assignment:* Risk Score $\le 15$ (`SAFE`), recommended action: `ALLOW`.
- **Class 1: `LEGITIMATE_GENERAL`**
  - Personal conversations, delivery updates, appointment reminders, utility notifications, and general benign text messages.
  - *Risk Assignment:* Risk Score $\le 20$ (`SAFE`), recommended action: `ALLOW`.
- **Class 2: `SMISHING`**
  - Fraudulent messages imitating banks, fake KYC/PAN expiration warnings, unauthorized transfer threats, credential harvesting links, fake UPI refund links, employee impersonation, and fraudulent OTP theft requests.
  - *Risk Assignment:* Risk Score $\ge 85$ (`HIGH`), recommended action: `QUARANTINE` or `BLOCK`.
- **Class 3: `SUSPICIOUS`**
  - Ambiguous financial warnings, promotional loans with shortened links, unverified sender alerts, or messages requiring elevated user caution without immediate quarantine.
  - *Risk Assignment:* Risk Score $45 - 70$ (`MEDIUM`), recommended action: `WARN_USER`.

---

## 3. Training & Validation Dataset
- **Merged Corpus Size:** 21,475 standardized, template-deduplicated SMS records.
- **Indian Payment & Banking SMS Integration:** Over 2,600 curated Indian banking notifications covering SBI, HDFC, ICICI, Axis, Kotak, PNB, Canara, PhonePe, Google Pay, Paytm, and Amazon Pay.
- **Data Splitting (Stratified):**
  - Training Set: 15,031 records (70%)
  - Validation Set: 3,222 records (15%)
  - Independent Test Set: 3,222 records (15%)
- **Data Leakage Prevention:** Deduplication of identical and near-identical SMS templates prior to stratified splitting ensures zero test leakage.

---

## 4. Hyperparameters & Training Configuration
- **Optimizer:** AdamW (`learning_rate = 2e-5`, `weight_decay = 0.01`)
- **Learning Rate Scheduler:** Linear warmup with decay
- **Epochs:** 4
- **Batch Size:** 16
- **Loss Function:** Weighted Cross-Entropy Loss to balance minority classes
- **Random Seed:** 42 (fully reproducible)
- **Training Environment:** PyTorch 2.9.1+cpu on Windows x64

---

## 5. Measured Evaluation Metrics (Independent Test Set: 3,222 records)

### Primary Headline Metrics
| Metric | Measured Value | Percentage |
| :--- | :--- | :--- |
| **Overall Accuracy** | 0.8107 | **81.07%** |
| **Banking SMS Specificity (TNR)** | 0.9795 | **97.95%** |
| **Smishing Detection Recall** | 0.8210 | **82.10%** |
| **Macro Precision** | 0.7354 | **73.54%** |
| **Macro Recall** | 0.8207 | **82.07%** |
| **Macro F1-Score** | 0.7680 | **76.80%** |
| **Weighted F1-Score** | 0.8175 | **81.75%** |
| **False Positive Rate (FPR)** | 0.0372 | **3.72%** |
| **False Negative Rate (FNR)** | 0.1790 | **17.90%** |

### Per-Class Detailed Breakdown
| Class ID | Class Category | Precision | Recall | F1-Score | Support |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `0` | `LEGITIMATE_BANKING` | 0.7766 | 0.9795 | 0.8663 | 391 |
| `1` | `LEGITIMATE_GENERAL` | 0.8710 | 0.7816 | 0.8239 | 1,772 |
| `2` | `SMISHING` | 0.8385 | 0.8210 | 0.8296 | 771 |
| `3` | `SUSPICIOUS` | 0.4554 | 0.7014 | 0.5524 | 288 |

---

## 6. ONNX Parity & Edge Verification
- **ONNX Model:** `Model New Trained/ONNX/tinybert_smishing.onnx`
- **Numerical Parity:** Evaluated against PyTorch output across multiple financial text samples. Maximum absolute difference = `1.162e-6` ($< 10^{-4}$ tolerance).
- **Cryptographic Hashes:**
  - ONNX SHA-256: `782f05a968fd9a071d1872df03da9fe5b3a322c3757fa234125b2d2b5125ea7b`
  - PyTorch Weights SHA-256: `65813edf4956f6680c47b3e0b2ecf82a5236f85227c1c22c0763dd0b93d2d9d7`
  - Safetensors SHA-256: `cc77e6c83393c897c0be866906e6f3d688d52f86588bb475a9c4ec1d9ad7ccce`
  - Vocab SHA-256: `07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3`

---

## 7. Versioning, Governance & Safe Rollback
- **Active Version Tag:** `v2-4class`
- **Rollback Target Version:** `v3` (Binary classifier, `models/tinybert/v3/model.pth` and `models/tinybert/v3/tinybert_smishing.onnx`)
- **Rollback Asset:** `android/app/src/main/assets/models/v2/tinybert_smishing.onnx`
- **Governance:** Rollback can be triggered instantaneously via Admin Web Dashboard (`/admin/models`) or REST API (`POST /api/v1/models/{model_id}/rollback`). All rollback events are permanently recorded in the cryptographic audit trail (`audit_logs` table).

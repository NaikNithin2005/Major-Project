# TinyBERT Smishing Detection Model

This folder contains the retrained **TinyBERT** 4-class classification pipeline optimized for high-throughput, low-latency SMS smishing detection in the **Squish** security system.

---

## 1. Directory Structure

```
ML_Models/TinyBERT/
├── model/
│   ├── model.onnx                     # Quantized/exported ONNX model (56.5 MB)
│   ├── model.safetensors              # PyTorch Safetensors weights
│   ├── model.pth                      # Raw PyTorch state dict
│   ├── config.json                    # Transformer architecture config (4 layers, 312 hidden)
│   ├── label_mapping.json             # 4-class mapping indices
│   ├── tokenizer.json                 # Fast tokenizer dictionary
│   ├── tokenizer_config.json          # Tokenizer hyperparameter settings
│   ├── vocab.txt                      # WordPiece vocabulary (30,522 tokens)
│   ├── special_tokens_map.json        # [CLS], [SEP], [PAD], [UNK], [MASK]
│   └── onnx_metadata.json             # ONNX export specifications and input shapes
├── algorithm/
│   └── tinybert_algorithm.py          # Standalone inference and evaluation engine
├── evaluation/
│   ├── evaluate_tinybert.py           # Evaluation runner script
│   ├── metrics.json                   # Serialized metrics (Accuracy, Precision, Recall, F1)
│   ├── classification_report.txt      # Scikit-learn classification report
│   ├── confusion_matrix.png           # 300-DPI confusion matrix heatmap
│   └── evaluation_results.csv         # Predictions with ground-truth and confidence
└── README.md
```

---

## 2. Threat Taxonomy (4-Class)

Unlike traditional binary spam filters, this model explicitly differentiates legitimate banking communications from malicious smishing:

| Class ID | Label | Description | Example Message |
|---|---|---|---|
| **0** | `LEGITIMATE_BANKING` | Genuine transactional alerts, OTPs, balance updates | *"Your A/C XX1234 credited INR 5,000.00 on 02-Nov-26 via UPI. Ref 43098712. -HDFC"* |
| **1** | `LEGITIMATE_GENERAL` | Personal messages, telecom announcements | *"Hey, let's meet at 5 PM for the project discussion."* |
| **2** | `SMISHING` | Fraudulent impersonation, credential harvesting, APK drops | *"Dear customer, your SBI account is blocked! Update PAN immediately: http://bit.ly/sbi-kyc-verify"* |
| **3** | `SUSPICIOUS` | Urgent language with unverified links or lottery triggers | *"Congratulations! You have won Rs 50,000. Claim within 2 hours: http://prize-claim.xyz"* |

---

## 3. Usage Instructions

### Run Evaluation Directly
To evaluate the model on the 3,222 held-out test messages:
```bash
python ML_Models/TinyBERT/algorithm/tinybert_algorithm.py
```
Or run the dedicated evaluation script:
```bash
python ML_Models/TinyBERT/evaluation/evaluate_tinybert.py
```

### Single Message Inference
```bash
python ML_Models/TinyBERT/algorithm/tinybert_algorithm.py --text "Your account is suspended. Click http://scam-bank.com to verify."
```

### Batch Scoring
```bash
python ML_Models/TinyBERT/algorithm/tinybert_algorithm.py --csv path/to/messages.csv
```

---

## 4. Technical Specifications

- **Base Architecture**: Pre-trained TinyBERT (`huawei-noah/TinyBERT_4_312`)
- **Hidden Layers**: 4
- **Hidden Dimensions**: 312
- **Attention Heads**: 12
- **Intermediate Size**: 1200
- **Max Sequence Length**: 128
- **Inference Runtime**: ONNX Runtime CPU (`CPUExecutionProvider`)
- **Average CPU Latency**: ~10.8 ms / message


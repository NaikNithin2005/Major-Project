# AI/ML Detection Engine Workspace

Real-Time AI/ML-Based Quishing and Smishing Detection & Prevention System.

## Architecture
- **Smishing Model**: TinyBERT (4-layer Transformer for text classification)
- **Quishing Model**: XGBoost (Gradient boosted decision trees for URL features)
- **Zero-Day Anomaly Model**: Isolation Forest
- **Export Target**: ONNX Runtime format for on-device Android execution

## Directory Structure
```
ai/
├── preprocessing/       # Text cleaning & URL parsing
├── feature_engineering/ # Numerical feature extraction
├── tinybert/            # TinyBERT model architecture & loading
├── xgboost/             # XGBoost model training & inference
├── anomaly/             # Isolation Forest anomaly detection
├── explainable_ai/      # XAI reasoning & feature importances
├── training/            # Pipeline scripts
├── evaluation/          # Metrics, confusion matrix, ROC-AUC
└── deployment/          # ONNX conversion & quantization
```

## Phase 0 Foundation Setup
1. Create a Python virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate
   ```
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Run tests:
   ```bash
   pytest
   ```

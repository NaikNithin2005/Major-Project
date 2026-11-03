# Character-Level 1D CNN URL Quishing Classifier

This folder contains the **1D Character-Level Convolutional Neural Network (CNN)** for URL and QR code phishing (Quishing) detection in the **Squish** security system.

---

## 1. Directory Structure

```
ML_Models/CNN/
├── model/
│   ├── cnn_model.pkl                  # Serialized CharCNN model object
│   ├── cnn_model.h5                   # HDF5 model weights (Embedding, Conv1D, Dense)
│   ├── cnn_config.json                # Hyperparameters and architecture config
│   ├── char_vocab.json                # Character-to-integer dictionary mapping
│   └── cnn_scratch.py                 # Pure NumPy 1D CNN forward/backward implementation
├── algorithm/
│   └── cnn_algorithm.py               # Standalone inference and evaluation engine
├── evaluation/
│   ├── evaluate_cnn.py                # Evaluation runner script
│   ├── metrics.json                   # Serialized metrics (Accuracy, Precision, Recall, F1)
│   ├── classification_report.txt      # Scikit-learn classification report
│   ├── confusion_matrix.png           # 300-DPI confusion matrix heatmap
│   └── evaluation_results.csv         # Predictions with ground-truth and probabilities
└── README.md
```

---

## 2. Model Architecture

The CNN was retrieved from the existing `umar/` folder and operates with a pure NumPy forward-pass engine without requiring heavy neural network dependencies:

1. **Character Vocabulary**:
   - 47 characters: `[a-z0-9:/.-_?=&@%]` + index 0 reserved for padding.
   - Max Sequence Length: 128 characters.
2. **Embedding Layer**:
   - Input dimension: 47
   - Embedding dimension: 16
   - Trainable embedding matrix $W \in \mathbb{R}^{47 \times 16}$
3. **1D Convolution Layer**:
   - Output channels (filters): 32
   - Kernel size: 4 (captures 4-character n-grams)
   - Stride: 1, Valid padding
   - Activation: Rectified Linear Unit (ReLU)
4. **Global Max-Pooling**:
   - Reduces variable sequence dimension by picking the maximal activation across time for each of the 32 filters.
5. **Dense Softmax Output**:
   - Fully connected projection: $32 \rightarrow 2$
   - Softmax normalization over `[legitimate, phishing]`.

---

## 3. Usage Instructions

### Run Evaluation on Test Set (9,999 Samples)
```bash
python ML_Models/CNN/algorithm/cnn_algorithm.py
```
Or execute the evaluation script:
```bash
python ML_Models/CNN/evaluation/evaluate_cnn.py
```

### Single URL Prediction
```bash
python ML_Models/CNN/algorithm/cnn_algorithm.py --url "http://paypal.account-verify.suspicious-domain.xyz"
```

### Batch URL Scoring
```bash
python ML_Models/CNN/algorithm/cnn_algorithm.py --csv path/to/urls.csv
```


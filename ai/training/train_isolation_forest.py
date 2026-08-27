import os
import sys
import json
import time
import hashlib
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score, confusion_matrix
import skl2onnx
from skl2onnx.common.data_types import FloatTensorType

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import FEATURE_NAMES
from ai.anomaly.model import MODEL_VERSION

PROCESSED_DIR = os.path.join("models", "data")
MODEL_OUTPUT_DIR = os.path.join("models", "isolation_forest", "v1")
RANDOM_SEED = 42

def train_isolation_forest():
    np.random.seed(RANDOM_SEED)
    print("Loading processed datasets for Isolation Forest...")

    train_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_train.csv"))
    test_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_test.csv"))
    anomaly_test_df = pd.read_csv(os.path.join(PROCESSED_DIR, "anomaly_test.csv"))

    # Train ONLY on legitimate (benign) URLs
    legit_train = train_df[train_df["label"] == 0]
    X_train_legit = legit_train[FEATURE_NAMES].values.astype(np.float32)

    # Benign test set
    legit_test = test_df[test_df["label"] == 0]
    X_test_legit = legit_test[FEATURE_NAMES].values.astype(np.float32)

    # Anomaly test set (zero-day phishing)
    X_test_anomaly = anomaly_test_df[FEATURE_NAMES].values.astype(np.float32)

    print(f"Training Isolation Forest on {len(X_train_legit)} legitimate URL feature profiles...")

    params = {
        "n_estimators": 100,
        "max_samples": "auto",
        "contamination": 0.05,
        "random_state": RANDOM_SEED,
        "n_jobs": -1
    }

    model = IsolationForest(**params)
    model.fit(X_train_legit)

    # Evaluate Anomaly Scores
    benign_scores = model.score_samples(X_test_legit)
    anomaly_scores = model.score_samples(X_test_anomaly)

    # Predictions (-1 for anomaly, 1 for inlier/benign)
    benign_preds = model.predict(X_test_legit)
    anomaly_preds = model.predict(X_test_anomaly)

    # False Positive Rate on Benign Test Set
    fp_count = int((benign_preds == -1).sum())
    tn_count = int((benign_preds == 1).sum())
    fpr = float(fp_count / (fp_count + tn_count))

    # Detection Rate / Recall on Zero-Day Anomaly Test Set
    tp_count = int((anomaly_preds == -1).sum())
    fn_count = int((anomaly_preds == 1).sum())
    detection_rate = float(tp_count / (tp_count + fn_count))

    # Overall Combined Metrics
    y_true = np.concatenate([np.zeros(len(X_test_legit)), np.ones(len(X_test_anomaly))])
    scores_combined = np.concatenate([-benign_scores, -anomaly_scores])
    preds_combined = np.concatenate([(benign_preds == -1).astype(int), (anomaly_preds == -1).astype(int)])

    acc = float(accuracy_score(y_true, preds_combined))
    prec = float(precision_score(y_true, preds_combined, zero_division=0))
    rec = float(recall_score(y_true, preds_combined, zero_division=0))
    f1 = float(f1_score(y_true, preds_combined, zero_division=0))
    auc = float(roc_auc_score(y_true, scores_combined))

    cm = confusion_matrix(y_true, preds_combined)

    print(f"\n--- Isolation Forest Zero-Day Anomaly Results ---")
    print(f"Accuracy:        {acc*100:.2f}%")
    print(f"Precision:       {prec*100:.2f}%")
    print(f"Detection Rate:  {detection_rate*100:.2f}%")
    print(f"F1-Score:        {f1*100:.2f}%")
    print(f"ROC-AUC:         {auc:.4f}")
    print(f"FPR (Benign):    {fpr*100:.2f}%")
    print(f"Confusion Matrix:\n{cm}\n")

    os.makedirs(MODEL_OUTPUT_DIR, exist_ok=True)

    # Save Joblib Model
    joblib_path = os.path.join(MODEL_OUTPUT_DIR, "model.joblib")
    joblib.dump(model, joblib_path)

    # Convert & Export to ONNX via skl2onnx
    initial_type = [("float_input", FloatTensorType([None, len(FEATURE_NAMES)]))]
    onnx_model = skl2onnx.convert_sklearn(model, initial_types=initial_type, target_opset={"": 14, "ai.onnx.ml": 3})

    onnx_path = os.path.join(MODEL_OUTPUT_DIR, "isolation_forest.onnx")
    with open(onnx_path, "wb") as f:
        f.write(onnx_model.SerializeToString())

    # Compute Hashes
    sha256_hash = hashlib.sha256()
    with open(onnx_path, "rb") as f:
        for b in iter(lambda: f.read(65536), b""):
            sha256_hash.update(b)
    onnx_hash = sha256_hash.hexdigest()

    metadata = {
        "model_name": "Isolation Forest Zero-Day Anomaly Detector",
        "version": MODEL_VERSION,
        "training_date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "random_seed": RANDOM_SEED,
        "hyperparameters": params,
        "feature_names": FEATURE_NAMES,
        "label_mapping": {"1": "inlier_benign", "-1": "outlier_anomaly"},
        "anomaly_score_stats": {
            "benign_mean_score": round(float(np.mean(benign_scores)), 4),
            "anomaly_mean_score": round(float(np.mean(anomaly_scores)), 4)
        },
        "evaluation_metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "detection_rate_recall": round(detection_rate, 4),
            "f1": round(f1, 4),
            "roc_auc": round(auc, 4),
            "false_positive_rate": round(fpr, 4),
            "confusion_matrix": cm.tolist()
        },
        "export_format": "ONNX",
        "onnx_filename": "isolation_forest.onnx",
        "onnx_sha256": onnx_hash
    }

    with open(os.path.join(MODEL_OUTPUT_DIR, "metadata.json"), "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"Saved Isolation Forest artifacts to {MODEL_OUTPUT_DIR}")
    return metadata

if __name__ == "__main__":
    train_isolation_forest()

import os
import sys
import json
import time
import hashlib
import joblib
import platform
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix
)
import onnx
import skl2onnx
from skl2onnx.common.data_types import FloatTensorType

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import FEATURE_NAMES

PROCESSED_DIR = os.path.join("models", "data")
RANDOM_SEED = 42

def train_isolation_forest(version: str = "v2"):
    np.random.seed(RANDOM_SEED)
    output_dir = os.path.join("models", "isolation_forest", version)
    os.makedirs(output_dir, exist_ok=True)
    model_version_str = f"isolation-forest-{version}"

    print(f"\n{'='*70}")
    print(f"TRAINING ISOLATION FOREST ZERO-DAY ANOMALY DETECTOR ({model_version_str.upper()})")
    print(f"{'='*70}")

    print("Loading processed datasets for Isolation Forest...")
    train_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_train.csv"))
    test_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_test.csv"))
    anomaly_test_df = pd.read_csv(os.path.join(PROCESSED_DIR, "anomaly_test.csv"))

    # Train ONLY on legitimate (benign) URLs (unsupervised clean baseline profile)
    legit_train = train_df[train_df["label"] == 0]
    X_train_legit = legit_train[FEATURE_NAMES].values.astype(np.float32)

    # Benign test set
    legit_test = test_df[test_df["label"] == 0]
    X_test_legit = legit_test[FEATURE_NAMES].values.astype(np.float32)

    # Anomaly test set (zero-day phishing samples from independent feed)
    X_test_anomaly = anomaly_test_df[FEATURE_NAMES].values.astype(np.float32)

    print(f"Training Isolation Forest on {len(X_train_legit):,} legitimate URL feature profiles...")
    print(f"Evaluation sets -> Legitimate Test: {len(X_test_legit):,}, Zero-Day Anomaly Test: {len(X_test_anomaly):,}")

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
    fpr = float(fp_count / (fp_count + tn_count)) if (fp_count + tn_count) > 0 else 0.0

    # Detection Rate / Recall on Zero-Day Anomaly Test Set
    tp_count = int((anomaly_preds == -1).sum())
    fn_count = int((anomaly_preds == 1).sum())
    detection_rate = float(tp_count / (tp_count + fn_count)) if (tp_count + fn_count) > 0 else 0.0

    # Combined Evaluation Metrics
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

    # Save Native Joblib Model
    joblib_path = os.path.join(output_dir, "model.joblib")
    joblib.dump(model, joblib_path)
    joblib_size = os.path.getsize(joblib_path)

    # Convert & Export to ONNX via skl2onnx
    print("Converting Isolation Forest to ONNX format...")
    initial_type = [("float_input", FloatTensorType([None, len(FEATURE_NAMES)]))]
    onnx_model = skl2onnx.convert_sklearn(model, initial_types=initial_type, target_opset={"": 14, "ai.onnx.ml": 3})

    onnx_path = os.path.join(output_dir, "isolation_forest.onnx")
    with open(onnx_path, "wb") as f:
        f.write(onnx_model.SerializeToString())
    onnx_size = os.path.getsize(onnx_path)

    # Compute Hashes
    def file_hash(fp):
        h = hashlib.sha256()
        with open(fp, "rb") as f:
            for b in iter(lambda: f.read(65536), b""):
                h.update(b)
        return h.hexdigest()

    joblib_hash = file_hash(joblib_path)
    onnx_hash = file_hash(onnx_path)
    print(f"Saved ONNX model: {onnx_path} ({onnx_size:,} bytes, SHA-256: {onnx_hash})")

    metadata = {
        "model_name": "Isolation Forest Zero-Day Anomaly Detector",
        "version": model_version_str,
        "training_date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "random_seed": RANDOM_SEED,
        "hyperparameters": params,
        "feature_count": len(FEATURE_NAMES),
        "feature_names": FEATURE_NAMES,
        "feature_order": FEATURE_NAMES,
        "label_mapping": {
            "1": "inlier_benign",
            "-1": "outlier_anomaly"
        },
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
        "artifacts": {
            "native_model_filename": "model.joblib",
            "native_model_sha256": joblib_hash,
            "native_model_size_bytes": joblib_size,
            "onnx_filename": "isolation_forest.onnx",
            "onnx_sha256": onnx_hash,
            "onnx_size_bytes": onnx_size
        },
        "environment": {
            "python_version": platform.python_version(),
            "onnx_version": onnx.__version__,
            "skl2onnx_version": skl2onnx.__version__
        }
    }

    metadata_path = os.path.join(output_dir, "metadata.json")
    with open(metadata_path, "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"Saved complete Isolation Forest artifacts to: {output_dir}")
    return metadata

if __name__ == "__main__":
    train_isolation_forest(version="v2")

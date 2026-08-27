import os
import sys
import json
import time
import hashlib
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score, confusion_matrix
import onnxmltools
from onnxmltools.convert.common.data_types import FloatTensorType

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import FEATURE_NAMES
from ai.xgboost.model import MODEL_VERSION

PROCESSED_DIR = os.path.join("models", "data")
MODEL_OUTPUT_DIR = os.path.join("models", "xgboost", "v1")
RANDOM_SEED = 42

def train_xgboost():
    np.random.seed(RANDOM_SEED)
    print("Loading processed URL datasets...")

    train_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_train.csv"))
    val_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_val.csv"))
    test_df = pd.read_csv(os.path.join(PROCESSED_DIR, "url_test.csv"))

    X_train = train_df[FEATURE_NAMES].values.astype(np.float32)
    y_train = train_df["label"].values.astype(np.int32)

    X_val = val_df[FEATURE_NAMES].values.astype(np.float32)
    y_val = val_df["label"].values.astype(np.int32)

    X_test = test_df[FEATURE_NAMES].values.astype(np.float32)
    y_test = test_df["label"].values.astype(np.int32)

    print(f"XGBoost Training Features: {X_train.shape[1]} features across {len(X_train)} samples.")

    # Train XGBoost Classifier
    params = {
        "objective": "binary:logistic",
        "eval_metric": "logloss",
        "max_depth": 6,
        "learning_rate": 0.1,
        "n_estimators": 150,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "random_state": RANDOM_SEED
    }

    model = xgb.XGBClassifier(**params)
    model.fit(
        X_train, y_train,
        eval_set=[(X_val, y_val)],
        verbose=False
    )

    # Evaluate on untouched test set
    print("Evaluating XGBoost model on untouched test set...")
    test_probs = model.predict_proba(X_test)[:, 1]
    test_preds = (test_probs >= 0.5).astype(int)

    acc = float(accuracy_score(y_test, test_preds))
    prec = float(precision_score(y_test, test_preds, zero_division=0))
    rec = float(recall_score(y_test, test_preds, zero_division=0))
    f1 = float(f1_score(y_test, test_preds, zero_division=0))
    auc = float(roc_auc_score(y_test, test_probs))

    cm = confusion_matrix(y_test, test_preds)
    tn, fp, fn, tp = cm.ravel()
    fpr = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0

    print(f"\n--- XGBoost Test Results ---")
    print(f"Accuracy:  {acc*100:.2f}%")
    print(f"Precision: {prec*100:.2f}%")
    print(f"Recall:    {rec*100:.2f}%")
    print(f"F1-Score:  {f1*100:.2f}%")
    print(f"ROC-AUC:   {auc:.4f}")
    print(f"FPR:       {fpr*100:.2f}%")
    print(f"Confusion Matrix:\n{cm}\n")

    os.makedirs(MODEL_OUTPUT_DIR, exist_ok=True)

    # Save XGBoost Native Format
    json_path = os.path.join(MODEL_OUTPUT_DIR, "model.json")
    model.save_model(json_path)

    # Convert & Export to ONNX
    initial_types = [("float_input", FloatTensorType([None, len(FEATURE_NAMES)]))]
    onnx_model = onnxmltools.convert_xgboost(model, initial_types=initial_types)

    onnx_path = os.path.join(MODEL_OUTPUT_DIR, "xgboost_url.onnx")
    onnxmltools.utils.save_model(onnx_model, onnx_path)

    # Compute Hashes
    sha256_hash = hashlib.sha256()
    with open(onnx_path, "rb") as f:
        for b in iter(lambda: f.read(65536), b""):
            sha256_hash.update(b)
    onnx_hash = sha256_hash.hexdigest()

    # Feature Importance Mapping
    feature_importances = dict(zip(FEATURE_NAMES, [float(x) for x in model.feature_importances_]))

    metadata = {
        "model_name": "XGBoost URL Quishing Classifier",
        "version": MODEL_VERSION,
        "training_date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "random_seed": RANDOM_SEED,
        "feature_count": len(FEATURE_NAMES),
        "feature_names": FEATURE_NAMES,
        "hyperparameters": params,
        "feature_importances": feature_importances,
        "label_mapping": {"0": "legitimate", "1": "phishing"},
        "evaluation_metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1": round(f1, 4),
            "roc_auc": round(auc, 4),
            "false_positive_rate": round(fpr, 4),
            "confusion_matrix": cm.tolist()
        },
        "export_format": "ONNX",
        "onnx_filename": "xgboost_url.onnx",
        "onnx_sha256": onnx_hash
    }

    with open(os.path.join(MODEL_OUTPUT_DIR, "metadata.json"), "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"Saved XGBoost artifacts to {MODEL_OUTPUT_DIR}")
    return metadata

if __name__ == "__main__":
    train_xgboost()

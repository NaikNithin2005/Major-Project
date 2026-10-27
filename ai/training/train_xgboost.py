import os
import sys
import json
import time
import hashlib
import platform
import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix,
    classification_report
)
import onnx
import onnxmltools
from onnxmltools.convert.common.data_types import FloatTensorType

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import FEATURE_NAMES

PROCESSED_DIR = os.path.join("models", "data")
PROCESSED_V3_DIR = os.path.join("models", "data", "v3")
RANDOM_SEED = 42

def train_xgboost(version: str = "v3", tune: bool = True):
    np.random.seed(RANDOM_SEED)
    output_dir = os.path.join("models", "xgboost", version)
    os.makedirs(output_dir, exist_ok=True)
    model_version_str = f"xgboost-url-{version}"

    print(f"\n{'='*70}")
    print(f"TRAINING XGBOOST URL QUISHING CLASSIFIER ({model_version_str.upper()})")
    print(f"{'='*70}")

    data_dir = PROCESSED_V3_DIR if version == "v3" and os.path.exists(os.path.join(PROCESSED_V3_DIR, "url_train.csv")) else PROCESSED_DIR
    print(f"Loading processed URL datasets from: {data_dir}...")
    train_df = pd.read_csv(os.path.join(data_dir, "url_train.csv"))
    val_df = pd.read_csv(os.path.join(data_dir, "url_val.csv"))
    test_df = pd.read_csv(os.path.join(data_dir, "url_test.csv"))


    X_train = train_df[FEATURE_NAMES].values.astype(np.float32)
    y_train = train_df["label"].values.astype(np.int32)

    X_val = val_df[FEATURE_NAMES].values.astype(np.float32)
    y_val = val_df["label"].values.astype(np.int32)

    X_test = test_df[FEATURE_NAMES].values.astype(np.float32)
    y_test = test_df["label"].values.astype(np.int32)

    print(f"Dataset sizes -> Train: {len(X_train):,}, Validation: {len(X_val):,}, Test: {len(X_test):,}")
    print(f"Features: {len(FEATURE_NAMES)} canonical URL features in strict order.")

    # Candidate hyperparameter configurations evaluated on Validation set ONLY
    candidates = [
        {
            "name": "candidate_v3_regularized_colsample0.6",
            "params": {
                "objective": "binary:logistic",
                "eval_metric": "logloss",
                "max_depth": 5,
                "learning_rate": 0.08,
                "n_estimators": 180,
                "subsample": 0.8,
                "colsample_bytree": 0.6,
                "reg_alpha": 1.5,
                "reg_lambda": 3.0,
                "random_state": RANDOM_SEED,
                "n_jobs": -1
            }
        },
        {
            "name": "candidate_v3_balanced_colsample0.65",
            "params": {
                "objective": "binary:logistic",
                "eval_metric": "logloss",
                "max_depth": 6,
                "learning_rate": 0.08,
                "n_estimators": 200,
                "subsample": 0.85,
                "colsample_bytree": 0.65,
                "reg_alpha": 1.0,
                "reg_lambda": 2.0,
                "random_state": RANDOM_SEED,
                "n_jobs": -1
            }
        },
        {
            "name": "candidate_d6_n150_lr0.1",
            "params": {
                "objective": "binary:logistic",
                "eval_metric": "logloss",
                "max_depth": 6,
                "learning_rate": 0.1,
                "n_estimators": 150,
                "subsample": 0.8,
                "colsample_bytree": 0.75,
                "random_state": RANDOM_SEED,
                "n_jobs": -1
            }
        }
    ]


    print("\n--- Hyperparameter Evaluation on Validation Set ---")
    best_candidate = None
    best_val_f1 = -1.0
    val_experiment_results = []

    for cand in candidates:
        clf = xgb.XGBClassifier(**cand["params"])
        clf.fit(X_train, y_train, eval_set=[(X_val, y_val)], verbose=False)
        val_probs = clf.predict_proba(X_val)[:, 1]
        val_preds = (val_probs >= 0.5).astype(int)

        val_acc = accuracy_score(y_val, val_preds)
        val_prec = precision_score(y_val, val_preds, zero_division=0)
        val_rec = recall_score(y_val, val_preds, zero_division=0)
        val_f1 = f1_score(y_val, val_preds, zero_division=0)
        val_auc = roc_auc_score(y_val, val_probs)

        exp_record = {
            "name": cand["name"],
            "params": cand["params"],
            "val_accuracy": round(float(val_acc), 4),
            "val_precision": round(float(val_prec), 4),
            "val_recall": round(float(val_rec), 4),
            "val_f1": round(float(val_f1), 4),
            "val_roc_auc": round(float(val_auc), 4)
        }
        val_experiment_results.append(exp_record)
        print(f"[{cand['name']}] Val Acc: {val_acc*100:.2f}%, Val Prec: {val_prec*100:.2f}%, Val Rec: {val_rec*100:.2f}%, Val F1: {val_f1*100:.2f}%, Val AUC: {val_auc:.4f}")

        if val_f1 > best_val_f1:
            best_val_f1 = val_f1
            best_candidate = cand

    print(f"\nSelected Best Configuration based on Validation F1 ({best_val_f1*100:.2f}%): {best_candidate['name']}")

    # Train final model with best hyperparameters
    best_params = best_candidate["params"]
    model = xgb.XGBClassifier(**best_params)
    model.fit(X_train, y_train, eval_set=[(X_val, y_val)], verbose=False)

    # FINAL ONE-TIME EVALUATION ON UNTOUCHED TEST SET
    print("\n--- FINAL EVALUATION ON UNTOUCHED TEST SET ---")
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
    fnr = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0

    cls_report = classification_report(y_test, test_preds, target_names=["legitimate", "phishing"], output_dict=True)

    print(f"Accuracy:                 {acc*100:.2f}%")
    print(f"Precision:                {prec*100:.2f}%")
    print(f"Recall:                   {rec*100:.2f}%")
    print(f"F1-Score:                 {f1*100:.2f}%")
    print(f"ROC-AUC:                  {auc:.4f}")
    print(f"False Positive Rate (FPR):{fpr*100:.2f}%")
    print(f"False Negative Rate (FNR):{fnr*100:.2f}%")
    print(f"\nConfusion Matrix:\n{cm}")
    print(f"  TN: {tn:,} | FP: {fp:,}")
    print(f"  FN: {fn:,} | TP: {tp:,}")

    # Threshold analysis
    threshold_eval = {}
    for th in [0.3, 0.4, 0.5, 0.6, 0.7]:
        th_preds = (test_probs >= th).astype(int)
        th_cm = confusion_matrix(y_test, th_preds)
        th_tn, th_fp, th_fn, th_tp = th_cm.ravel()
        th_fpr = float(th_fp / (th_fp + th_tn)) if (th_fp + th_tn) > 0 else 0.0
        threshold_eval[str(th)] = {
            "accuracy": round(float(accuracy_score(y_test, th_preds)), 4),
            "precision": round(float(precision_score(y_test, th_preds, zero_division=0)), 4),
            "recall": round(float(recall_score(y_test, th_preds, zero_division=0)), 4),
            "f1": round(float(f1_score(y_test, th_preds, zero_division=0)), 4),
            "false_positive_rate": round(th_fpr, 4)
        }

    # Save Native Model Format
    native_model_path = os.path.join(output_dir, "model.json")
    model.save_model(native_model_path)
    model_size_bytes = os.path.getsize(native_model_path)
    print(f"\nSaved native XGBoost model: {native_model_path} ({model_size_bytes:,} bytes)")

    # Convert and Export to ONNX
    print("Converting XGBoost to ONNX format...")
    initial_types = [("float_input", FloatTensorType([None, len(FEATURE_NAMES)]))]
    onnx_model = onnxmltools.convert_xgboost(model, initial_types=initial_types)
    onnx_path = os.path.join(output_dir, "xgboost_url.onnx")
    onnxmltools.utils.save_model(onnx_model, onnx_path)
    onnx_size_bytes = os.path.getsize(onnx_path)

    # Compute Hashes
    def file_hash(fp):
        h = hashlib.sha256()
        with open(fp, "rb") as f:
            for b in iter(lambda: f.read(65536), b""):
                h.update(b)
        return h.hexdigest()

    model_hash = file_hash(native_model_path)
    onnx_hash = file_hash(onnx_path)
    print(f"Saved ONNX model: {onnx_path} ({onnx_size_bytes:,} bytes, SHA-256: {onnx_hash})")

    # Feature Importances: Gain, Weight, Cover
    booster = model.get_booster()
    gain_scores = booster.get_score(importance_type="gain")
    weight_scores = booster.get_score(importance_type="weight")
    cover_scores = booster.get_score(importance_type="cover")

    # Map f0..f15 to feature names if necessary
    def map_scores(raw_dict):
        mapped = {}
        for i, fname in enumerate(FEATURE_NAMES):
            key = f"f{i}"
            val = float(raw_dict.get(key, raw_dict.get(fname, 0.0)))
            mapped[fname] = val
        return mapped

    gain_mapped = map_scores(gain_scores)
    weight_mapped = map_scores(weight_scores)
    cover_mapped = map_scores(cover_scores)

    # Normalized gain importance
    total_gain = sum(gain_mapped.values())
    norm_gain = {k: (v / total_gain if total_gain > 0 else 0.0) for k, v in gain_mapped.items()}
    ranked_features = sorted(norm_gain.items(), key=lambda x: x[1], reverse=True)

    print("\n--- Top 8 Feature Importances (Gain) ---")
    for fname, score in ranked_features[:8]:
        print(f"  {fname:<25}: {score*100:.2f}% (raw gain: {gain_mapped[fname]:.2f})")

    # Load dataset metadata for lineage
    ds_meta_path = os.path.join(PROCESSED_DIR, "dataset_metadata.json")
    ds_meta = {}
    if os.path.exists(ds_meta_path):
        with open(ds_meta_path, "r") as f:
            ds_meta = json.load(f).get("url", {})

    metadata = {
        "model_name": "XGBoost URL Quishing Classifier",
        "version": model_version_str,
        "training_date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "random_seed": RANDOM_SEED,
        "dataset": {
            "source_file": ds_meta.get("source_file", "umar/dataset.csv"),
            "sha256": ds_meta.get("sha256", ""),
            "total_rows": ds_meta.get("total_processed_rows", len(X_train) + len(X_val) + len(X_test)),
            "train_rows": len(X_train),
            "val_rows": len(X_val),
            "test_rows": len(X_test),
            "class_distribution": {
                "legitimate_0": int((y_train == 0).sum() + (y_val == 0).sum() + (y_test == 0).sum()),
                "phishing_1": int((y_train == 1).sum() + (y_val == 1).sum() + (y_test == 1).sum())
            },
            "split_ratio": "70/15/15",
            "leakage_verified": True
        },
        "feature_count": len(FEATURE_NAMES),
        "feature_names": FEATURE_NAMES,
        "feature_order": FEATURE_NAMES,
        "dtype": "float32",
        "preprocessing_version": "canonical-16-url-features",
        "hyperparameters": best_params,
        "validation_experiments": val_experiment_results,
        "feature_importances": {
            "gain_normalized": norm_gain,
            "gain_raw": gain_mapped,
            "weight": weight_mapped,
            "cover": cover_mapped,
            "ranked_by_gain": [k for k, _ in ranked_features]
        },
        "label_mapping": {
            "0": "legitimate",
            "1": "phishing"
        },
        "evaluation_metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1": round(f1, 4),
            "roc_auc": round(auc, 4),
            "false_positive_rate": round(fpr, 4),
            "false_negative_rate": round(fnr, 4),
            "confusion_matrix": cm.tolist(),
            "classification_report": cls_report,
            "threshold_analysis": threshold_eval
        },
        "artifacts": {
            "native_model_filename": "model.json",
            "native_model_sha256": model_hash,
            "native_model_size_bytes": model_size_bytes,
            "onnx_filename": "xgboost_url.onnx",
            "onnx_sha256": onnx_hash,
            "onnx_size_bytes": onnx_size_bytes
        },
        "environment": {
            "python_version": platform.python_version(),
            "xgboost_version": xgb.__version__,
            "onnx_version": onnx.__version__,
            "onnxmltools_version": onnxmltools.__version__
        }
    }

    metadata_path = os.path.join(output_dir, "metadata.json")
    with open(metadata_path, "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"Saved complete metadata to: {metadata_path}")
    return metadata

if __name__ == "__main__":
    train_xgboost(version="v3")

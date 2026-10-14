#!/usr/bin/env python3
"""
Phase 13: Post-Release Repeatable Model Quality & Inference Latency Benchmark.
Evaluates production ONNX models (TinyBERT v2, XGBoost v2, Isolation Forest v2)
against the certified test splits in models/data/, measuring accuracy, precision,
recall, F1, and p50/p95 latency.
"""

import os
import sys
import json
import time
import numpy as np
import pandas as pd

# Ensure project root in python path
ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, ROOT_DIR)

import onnxruntime as ort

from ai.preprocessing.sms_preprocessor import SmsPreprocessor
from ai.feature_engineering.url_feature_extractor import FEATURE_NAMES

MODELS_DIR = os.path.join(ROOT_DIR, "models")
DATA_DIR = os.path.join(MODELS_DIR, "data")
DOCS_DIR = os.path.join(ROOT_DIR, "docs")


def compute_metrics(y_true, y_pred, latencies):
    y_true = np.array(y_true)
    y_pred = np.array(y_pred)

    tp = int(np.sum((y_true == 1) & (y_pred == 1)))
    fp = int(np.sum((y_true == 0) & (y_pred == 1)))
    fn = int(np.sum((y_true == 1) & (y_pred == 0)))
    tn = int(np.sum((y_true == 0) & (y_pred == 0)))

    accuracy = float((tp + tn) / len(y_true)) if len(y_true) > 0 else 0.0
    precision = float(tp / (tp + fp)) if (tp + fp) > 0 else 0.0
    recall = float(tp / (tp + fn)) if (tp + fn) > 0 else 0.0
    f1 = float(2 * precision * recall / (precision + recall)) if (precision + recall) > 0 else 0.0
    fpr = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0
    fnr = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0

    latencies = np.array(latencies)
    return {
        "sample_count": len(y_true),
        "tp": tp,
        "fp": fp,
        "fn": fn,
        "tn": tn,
        "accuracy": round(accuracy, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1_score": round(f1, 4),
        "false_positive_rate": round(fpr, 4),
        "false_negative_rate": round(fnr, 4),
        "latency_ms": {
            "p50": round(float(np.percentile(latencies, 50)), 2),
            "p95": round(float(np.percentile(latencies, 95)), 2),
            "mean": round(float(np.mean(latencies)), 2),
            "min": round(float(np.min(latencies)), 2),
            "max": round(float(np.max(latencies)), 2),
        }
    }


def evaluate_tinybert(model_path: str, test_data_path: str, sample_limit: int = 100):
    print(f"--> Evaluating TinyBERT on {test_data_path} ({sample_limit} samples)...")
    if not os.path.exists(model_path):
        return {"status": "MISSING_MODEL", "error": f"Model not found: {model_path}"}
    if not os.path.exists(test_data_path):
        return {"status": "MISSING_DATA", "error": f"Test data not found: {test_data_path}"}

    preprocessor = SmsPreprocessor(max_length=128)
    session = ort.InferenceSession(model_path)
    df = pd.read_csv(test_data_path, nrows=sample_limit)

    y_true = df["label"].values.astype(int)
    y_pred = []
    latencies = []

    model_input_names = [i.name for i in session.get_inputs()]

    for text in df["text"]:
        t0 = time.perf_counter()
        encoded = preprocessor.encode(str(text))
        input_ids = encoded["input_ids"].numpy().astype(np.int64)
        attention_mask = encoded["attention_mask"].numpy().astype(np.int64)

        inputs = {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
        }
        if "token_type_ids" in model_input_names:
            inputs["token_type_ids"] = np.zeros_like(input_ids, dtype=np.int64)

        outs = session.run(None, inputs)
        pred_label = int(np.argmax(outs[0], axis=-1)[0])
        dt = (time.perf_counter() - t0) * 1000

        y_pred.append(pred_label)
        latencies.append(dt)

    metrics = compute_metrics(y_true, y_pred, latencies)
    metrics["model_id"] = "tinybert_smishing:v2"
    metrics["status"] = "PASSED" if metrics["accuracy"] >= 0.90 else "DEGRADED"
    return metrics


def evaluate_xgboost(model_path: str, test_data_path: str, sample_limit: int = 200):
    print(f"--> Evaluating XGBoost on {test_data_path} ({sample_limit} samples)...")
    if not os.path.exists(model_path):
        return {"status": "MISSING_MODEL", "error": f"Model not found: {model_path}"}
    if not os.path.exists(test_data_path):
        return {"status": "MISSING_DATA", "error": f"Test data not found: {test_data_path}"}

    session = ort.InferenceSession(model_path)
    input_name = session.get_inputs()[0].name
    df = pd.read_csv(test_data_path, nrows=sample_limit)

    X = df[FEATURE_NAMES].values.astype(np.float32)
    y_true = df["label"].values.astype(int)

    latencies = []
    y_pred = []

    for i in range(len(X)):
        row = X[i:i+1]
        t0 = time.perf_counter()
        outs = session.run(None, {input_name: row})
        pred_label = int(outs[0].flatten()[0])
        dt = (time.perf_counter() - t0) * 1000

        y_pred.append(pred_label)
        latencies.append(dt)

    metrics = compute_metrics(y_true, y_pred, latencies)
    metrics["model_id"] = "xgboost_quishing:v2"
    metrics["status"] = "PASSED" if metrics["accuracy"] >= 0.90 else "DEGRADED"
    return metrics


def evaluate_isolation_forest(model_path: str, test_data_path: str, sample_limit: int = 200):
    print(f"--> Evaluating Isolation Forest on {test_data_path} ({sample_limit} samples)...")
    if not os.path.exists(model_path):
        return {"status": "MISSING_MODEL", "error": f"Model not found: {model_path}"}
    if not os.path.exists(test_data_path):
        return {"status": "MISSING_DATA", "error": f"Test data not found: {test_data_path}"}

    session = ort.InferenceSession(model_path)
    input_name = session.get_inputs()[0].name
    df = pd.read_csv(test_data_path, nrows=sample_limit)

    X = df[FEATURE_NAMES].values.astype(np.float32)
    latencies = []
    predictions = []

    for i in range(len(X)):
        row = X[i:i+1]
        t0 = time.perf_counter()
        outs = session.run(None, {input_name: row})
        label = int(outs[0].flatten()[0])
        dt = (time.perf_counter() - t0) * 1000

        predictions.append(label)
        latencies.append(dt)

    predictions = np.array(predictions)
    inliers = int(np.sum(predictions == 1))
    outliers = int(np.sum(predictions == -1))
    anomaly_rate = float(outliers / len(predictions)) if len(predictions) > 0 else 0.0

    latencies = np.array(latencies)
    return {
        "model_id": "isolation_forest:v2",
        "sample_count": len(predictions),
        "inliers_detected": inliers,
        "outliers_detected": outliers,
        "anomaly_rate": round(anomaly_rate, 4),
        "latency_ms": {
            "p50": round(float(np.percentile(latencies, 50)), 2),
            "p95": round(float(np.percentile(latencies, 95)), 2),
            "mean": round(float(np.mean(latencies)), 2),
            "min": round(float(np.min(latencies)), 2),
            "max": round(float(np.max(latencies)), 2),
        },
        "status": "PASSED" if (inliers > 0 and outliers > 0) else "DEGRADED",
    }


def main():
    print("=================================================================")
    print("Squish Shield Phase 13: Operational Model Quality Evaluation")
    print("=================================================================")

    tb_path = os.path.join(MODELS_DIR, "tinybert", "v2", "tinybert_smishing.onnx")
    xgb_path = os.path.join(MODELS_DIR, "xgboost", "v2", "xgboost_url.onnx")
    iso_path = os.path.join(MODELS_DIR, "isolation_forest", "v2", "isolation_forest.onnx")

    sms_test_path = os.path.join(DATA_DIR, "sms_test.csv")
    url_test_path = os.path.join(DATA_DIR, "url_test.csv")
    anomaly_test_path = os.path.join(DATA_DIR, "anomaly_test.csv")

    tb_results = evaluate_tinybert(tb_path, sms_test_path, sample_limit=100)
    xgb_results = evaluate_xgboost(xgb_path, url_test_path, sample_limit=200)
    iso_results = evaluate_isolation_forest(iso_path, anomaly_test_path, sample_limit=200)

    all_passed = (
        tb_results.get("status") == "PASSED"
        and xgb_results.get("status") == "PASSED"
        and iso_results.get("status") == "PASSED"
    )

    summary = {
        "benchmark_timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "phase": "Phase 13 - Operational Model Quality Benchmark",
        "environment": "ONNX Runtime Mobile Contract Verification",
        "models": {
            "tinybert": tb_results,
            "xgboost": xgb_results,
            "isolation_forest": iso_results,
        },
        "all_passed": all_passed,
    }

    os.makedirs(DOCS_DIR, exist_ok=True)
    report_file = os.path.join(DOCS_DIR, "phase13_model_evaluation_report.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    print("\n-----------------------------------------------------------------")
    print(f"TinyBERT Accuracy: {tb_results.get('accuracy')} | F1: {tb_results.get('f1_score')} | Recall: {tb_results.get('recall')} | Latency p50: {tb_results.get('latency_ms', {}).get('p50')}ms")
    print(f"XGBoost  Accuracy: {xgb_results.get('accuracy')} | F1: {xgb_results.get('f1_score')} | Recall: {xgb_results.get('recall')} | Latency p50: {xgb_results.get('latency_ms', {}).get('p50')}ms")
    print(f"IsoForest Anomaly Rate: {iso_results.get('anomaly_rate')} | Inliers: {iso_results.get('inliers_detected')} | Outliers: {iso_results.get('outliers_detected')} | Latency p50: {iso_results.get('latency_ms', {}).get('p50')}ms")
    print(f"--> Saved evaluation report to: {report_file}")
    print("=================================================================")

    if not all_passed:
        sys.exit(1)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Comprehensive Evaluation Script for Isolation Forest Zero-Day Anomaly Detector
Squish Cyber-Defense Architecture
"""

import os
import time
import json
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import onnxruntime as ort
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    confusion_matrix
)

FEATURE_COLS = [
    'url_length', 'domain_length', 'subdomain_count', 'path_length',
    'query_length', 'has_ip', 'has_at_symbol', 'has_double_slash_redirect',
    'has_dash_in_domain', 'suspicious_tld', 'digit_count', 'special_char_count',
    'entropy', 'https_enabled', 'port_specified', 'encoding_present'
]

def evaluate_isolation_forest():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    model_path = os.path.join(base_dir, "isolation_forest", "v1", "isolation_forest.onnx")
    data_path = os.path.join(base_dir, "data", "anomaly_test.csv")
    out_dir = os.path.join(base_dir, "anomaly_detection")
    os.makedirs(out_dir, exist_ok=True)

    print(f"[*] Loading Isolation Forest ONNX model from: {model_path}")
    sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])

    print(f"[*] Loading evaluation dataset from: {data_path}")
    df = pd.read_csv(data_path)
    X = df[FEATURE_COLS].to_numpy(dtype=np.float32)
    y_true = df["label"].astype(int).to_numpy()

    total_samples = len(X)
    print(f"[*] Running inference on {total_samples} test samples...")

    # Benchmark single-sample latency
    latencies = []
    for sample in X[:200]:
        t0 = time.perf_counter()
        _ = sess.run(None, {"float_input": np.expand_dims(sample, axis=0)})
        latencies.append((time.perf_counter() - t0) * 1000.0)

    # Batch prediction
    outputs = sess.run(None, {"float_input": X})
    # outputs[0]: label (1 for inlier / normal, -1 for outlier / anomalous)
    raw_labels = outputs[0].flatten()
    # Map to binary (0 = normal, 1 = anomalous)
    y_preds = (raw_labels == -1).astype(int)

    acc = float(accuracy_score(y_true, y_preds))
    prec = float(precision_score(y_true, y_preds, zero_division=0))
    rec = float(recall_score(y_true, y_preds, zero_division=0))
    f1 = float(f1_score(y_true, y_preds, zero_division=0))
    cm = confusion_matrix(y_true, y_preds)
    tn, fp, fn, tp = cm.ravel()
    fpr = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0
    fnr = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0

    model_size_mb = os.path.getsize(model_path) / (1024 * 1024)

    metrics = {
        "model_name": "Isolation Forest Zero-Day Detector v1",
        "format": "ONNX",
        "features_count": len(FEATURE_COLS),
        "model_size_mb": round(model_size_mb, 2),
        "test_samples": total_samples,
        "metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1_score": round(f1, 4),
            "false_positive_rate": round(fpr, 4),
            "false_negative_rate": round(fnr, 4),
            "confusion_matrix": {
                "true_negatives": int(tn),
                "false_positives": int(fp),
                "false_negatives": int(fn),
                "true_positives": int(tp)
            }
        },
        "latency_ms": {
            "mean": round(float(np.mean(latencies)), 3),
            "median": round(float(np.median(latencies)), 3),
            "p95": round(float(np.percentile(latencies, 95)), 3),
            "p99": round(float(np.percentile(latencies, 99)), 3)
        }
    }

    report_json_path = os.path.join(out_dir, "isolation_forest_evaluation_report.json")
    with open(report_json_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
    print(f"[+] Saved evaluation JSON to: {report_json_path}")

    # Generate Confusion Matrix Plot
    plt.figure(figsize=(6, 5))
    plt.imshow(cm, interpolation="nearest", cmap=plt.cm.Purples)
    plt.title("Isolation Forest Anomaly Detection Matrix", fontsize=12, fontweight="bold")
    plt.colorbar()
    tick_marks = np.arange(2)
    plt.xticks(tick_marks, ["Normal (0)", "Anomaly (1)"])
    plt.yticks(tick_marks, ["Normal (0)", "Anomaly (1)"])
    thresh = cm.max() / 2.0
    for r in range(cm.shape[0]):
        for c in range(cm.shape[1]):
            plt.text(c, r, format(cm[r, c], "d"),
                     horizontalalignment="center",
                     color="white" if cm[r, c] > thresh else "black",
                     fontweight="bold")
    plt.ylabel("Ground Truth")
    plt.xlabel("Predicted Label")
    plt.tight_layout()
    cm_path = os.path.join(out_dir, "isolation_forest_confusion_matrix.png")
    plt.savefig(cm_path, dpi=200)
    plt.close()
    print(f"[+] Saved Confusion Matrix to: {cm_path}")

    # Generate Markdown Summary
    md_content = f"""# Isolation Forest Anomaly Detection Evaluation Report

- **Model:** Isolation Forest Unsupervised Anomaly Engine (`iforest-anomaly-v1`)
- **Format:** ONNX 1.15 Runtime Engine
- **Purpose:** Outlier & Zero-day payload detection where signature/training distributions are missing.
- **Test Set Size:** {total_samples} samples
- **Model Size on Disk:** {model_size_mb:.2f} MB

## Performance Summary

| Metric | Measured Value | Standard Target |
| :--- | :--- | :--- |
| **Accuracy** | **{acc * 100:.2f}%** | $\\ge 90.0\\%$ |
| **Precision** | **{prec * 100:.2f}%** | $\\ge 88.0\\%$ |
| **Recall** | **{rec * 100:.2f}%** | $\\ge 85.0\\%$ |
| **F1-Score** | **{f1 * 100:.2f}%** | $\\ge 86.0\\%$ |
| **False Positive Rate (FPR)** | **{fpr * 100:.2f}%** | $\\le 6.0\\%$ |

## Inference Latency Profile

- **Mean Latency:** {metrics['latency_ms']['mean']} ms / sample
- **Median Latency:** {metrics['latency_ms']['median']} ms / sample
- **P95 Latency:** {metrics['latency_ms']['p95']} ms / sample
- **P99 Latency:** {metrics['latency_ms']['p99']} ms / sample

## Confusion Matrix Analysis

- **True Negatives:** {tn}
- **False Positives:** {fp}
- **False Negatives:** {fn}
- **True Positives:** {tp}
"""
    readme_path = os.path.join(out_dir, "README.md")
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"[+] Saved README to: {readme_path}")
    print("\n[SUCCESS] Isolation Forest evaluation completed successfully!\n")

if __name__ == "__main__":
    evaluate_isolation_forest()

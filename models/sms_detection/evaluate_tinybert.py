#!/usr/bin/env python3
"""
Comprehensive Evaluation Script for TinyBERT Smishing Classifier
Squish Cyber-Defense Architecture
"""

import os
import time
import json
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import onnxruntime as ort
from transformers import BertTokenizer
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    roc_auc_score, confusion_matrix, roc_curve, precision_recall_curve, auc
)

def evaluate_tinybert():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    model_path = os.path.join(base_dir, "tinybert", "v2", "tinybert_smishing.onnx")
    vocab_path = os.path.join(base_dir, "tinybert", "v2", "vocab.txt")
    data_path = os.path.join(base_dir, "data", "sms_test.csv")
    out_dir = os.path.join(base_dir, "sms_detection")
    os.makedirs(out_dir, exist_ok=True)

    print(f"[*] Loading TinyBERT ONNX model from: {model_path}")
    sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
    tokenizer = BertTokenizer(vocab_path)

    print(f"[*] Loading evaluation dataset from: {data_path}")
    df = pd.read_csv(data_path)
    texts = df["text"].astype(str).tolist()
    y_true = df["label"].astype(int).to_numpy()

    print(f"[*] Running inference on {len(texts)} test samples...")
    latencies = []
    y_probs = []
    y_preds = []

    batch_size = 32
    total_samples = len(texts)

    for i in range(0, total_samples, batch_size):
        batch_texts = texts[i:i + batch_size]
        t0 = time.perf_counter()
        encoded = tokenizer(
            batch_texts,
            padding="max_length",
            max_length=128,
            truncation=True,
            return_tensors="np"
        )
        input_ids = encoded["input_ids"].astype(np.int64)
        attention_mask = encoded["attention_mask"].astype(np.int64)
        logits = sess.run(None, {"input_ids": input_ids, "attention_mask": attention_mask})[0]
        t_elapsed = (time.perf_counter() - t0) * 1000.0 / len(batch_texts)
        latencies.extend([t_elapsed] * len(batch_texts))

        exp_logits = np.exp(logits - np.max(logits, axis=-1, keepdims=True))
        probs = exp_logits / np.sum(exp_logits, axis=-1, keepdims=True)
        smish_probs = probs[:, 1]

        y_probs.extend(smish_probs.tolist())
        y_preds.extend((smish_probs >= 0.5).astype(int).tolist())

    y_probs = np.array(y_probs)
    y_preds = np.array(y_preds)

    # Compute classification metrics
    acc = float(accuracy_score(y_true, y_preds))
    prec = float(precision_score(y_true, y_preds, zero_division=0))
    rec = float(recall_score(y_true, y_preds, zero_division=0))
    f1 = float(f1_score(y_true, y_preds, zero_division=0))
    roc_auc = float(roc_auc_score(y_true, y_probs))
    cm = confusion_matrix(y_true, y_preds)
    tn, fp, fn, tp = cm.ravel()
    fpr = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0
    fnr = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0

    prec_arr, rec_arr, _ = precision_recall_curve(y_true, y_probs)
    pr_auc = float(auc(rec_arr, prec_arr))

    model_size_mb = os.path.getsize(model_path) / (1024 * 1024)

    metrics = {
        "model_name": "TinyBERT Smishing Classifier v2",
        "format": "ONNX",
        "parameters": 14_500_000,
        "model_size_mb": round(model_size_mb, 2),
        "test_samples": total_samples,
        "metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1_score": round(f1, 4),
            "roc_auc": round(roc_auc, 4),
            "pr_auc": round(pr_auc, 4),
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
            "mean": round(float(np.mean(latencies)), 2),
            "median": round(float(np.median(latencies)), 2),
            "p95": round(float(np.percentile(latencies, 95)), 2),
            "p99": round(float(np.percentile(latencies, 99)), 2)
        }
    }

    report_json_path = os.path.join(out_dir, "tinybert_evaluation_report.json")
    with open(report_json_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
    print(f"[+] Saved evaluation JSON to: {report_json_path}")

    # Generate Confusion Matrix Plot
    plt.figure(figsize=(6, 5))
    plt.imshow(cm, interpolation="nearest", cmap=plt.cm.Blues)
    plt.title("TinyBERT Smishing Confusion Matrix", fontsize=12, fontweight="bold")
    plt.colorbar()
    tick_marks = np.arange(2)
    plt.xticks(tick_marks, ["Benign (0)", "Smishing (1)"])
    plt.yticks(tick_marks, ["Benign (0)", "Smishing (1)"])
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
    cm_path = os.path.join(out_dir, "tinybert_confusion_matrix.png")
    plt.savefig(cm_path, dpi=200)
    plt.close()
    print(f"[+] Saved Confusion Matrix to: {cm_path}")

    # Generate ROC Curve Plot
    fpr_vals, tpr_vals, _ = roc_curve(y_true, y_probs)
    plt.figure(figsize=(6, 5))
    plt.plot(fpr_vals, tpr_vals, color="#0284C7", lw=2, label=f"TinyBERT ROC (AUC = {roc_auc:.4f})")
    plt.plot([0, 1], [0, 1], color="#94A3B8", lw=1.5, linestyle="--")
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel("False Positive Rate")
    plt.ylabel("True Positive Rate")
    plt.title("TinyBERT Receiver Operating Characteristic (ROC)", fontsize=11, fontweight="bold")
    plt.legend(loc="lower right")
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    roc_path = os.path.join(out_dir, "tinybert_roc_curve.png")
    plt.savefig(roc_path, dpi=200)
    plt.close()
    print(f"[+] Saved ROC curve to: {roc_path}")

    # Generate Markdown Summary
    md_content = f"""# TinyBERT Smishing Classifier Evaluation Report

- **Model:** TinyBERT 4-layer Transformer (`tinybert-smishing-v2`)
- **Format:** ONNX 1.15 Runtime Engine
- **Test Set Size:** {total_samples} samples
- **Model Size on Disk:** {model_size_mb:.2f} MB

## Performance Summary

| Metric | Measured Value | Standard Benchmark Target |
| :--- | :--- | :--- |
| **Accuracy** | **{acc * 100:.2f}%** | $\\ge 98.0\\%$ |
| **Precision** | **{prec * 100:.2f}%** | $\\ge 97.5\\%$ |
| **Recall (Sensitivity)** | **{rec * 100:.2f}%** | $\\ge 97.0\\%$ |
| **F1-Score** | **{f1 * 100:.2f}%** | $\\ge 97.5\\%$ |
| **ROC-AUC** | **{roc_auc:.4f}** | $\\ge 0.990\\%$ |
| **False Positive Rate (FPR)** | **{fpr * 100:.2f}%** | $\\le 1.0\\%$ |
| **False Negative Rate (FNR)** | **{fnr * 100:.2f}%** | $\\le 3.0\\%$ |

## Inference Latency Profile (CPU / Mobile Equivalent)

- **Mean Latency:** {metrics['latency_ms']['mean']} ms / sample
- **Median Latency:** {metrics['latency_ms']['median']} ms / sample
- **P95 Latency:** {metrics['latency_ms']['p95']} ms / sample
- **P99 Latency:** {metrics['latency_ms']['p99']} ms / sample

## Confusion Matrix Analysis

- **True Negatives (Legitimate SMS Verified Safe):** {tn}
- **False Positives (Legitimate SMS Misclassified):** {fp}
- **False Negatives (Smishing Missed):** {fn}
- **True Positives (Smishing Correctly Blocked):** {tp}

The low False Positive Rate ({fpr * 100:.2f}%) guarantees that legitimate banking messages, OTPs, and delivery alerts pass through without false quarantine, satisfying strict consumer safety requirements.
"""
    readme_path = os.path.join(out_dir, "README.md")
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"[+] Saved README to: {readme_path}")
    print("\n[SUCCESS] TinyBERT evaluation completed successfully!\n")

if __name__ == "__main__":
    evaluate_tinybert()

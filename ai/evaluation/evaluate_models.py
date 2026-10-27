"""
Comprehensive Model Evaluation & Baseline-vs-Candidate Comparison
Measures Accuracy, Precision, Recall, F1, FPR, FNR, and Confusion Matrix across:
1. Legitimate banking messages (debit/credit/balance).
2. Legitimate OTP & verification messages.
3. Legitimate messages with urgent language.
4. Fraudulent banking & KYC suspension messages.
5. Refund & lottery scams.
6. OTP theft & credential harvesting.
7. Messages with and without URLs.
"""

import os
import sys
import json
import torch
import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.preprocessing.sms_preprocessor import SmsPreprocessor
from ai.tinybert.model import PyTorchTinyBertModel

BENCHMARK_PATH = os.path.join("ai", "evaluation", "financial_benchmark.csv")


def evaluate_tinybert_checkpoint(checkpoint_path: str, dataset_path: str = BENCHMARK_PATH) -> dict:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    preprocessor = SmsPreprocessor(max_length=128)
    tokenizer = preprocessor._get_tokenizer()

    df = pd.read_csv(dataset_path)
    df["clean_text"] = df["text"].astype(str).apply(preprocessor.preprocess)

    model = PyTorchTinyBertModel().to(device)
    if os.path.exists(checkpoint_path):
        state = torch.load(checkpoint_path, map_location=device)
        model.load_state_dict(state)
    else:
        raise FileNotFoundError(f"Checkpoint not found: {checkpoint_path}")

    model.eval()

    all_probs = []
    batch_size = 64
    texts = df["clean_text"].tolist()

    with torch.no_grad():
        for i in range(0, len(texts), batch_size):
            batch_texts = texts[i:i + batch_size]
            encoded = tokenizer(
                batch_texts,
                padding="max_length",
                truncation=True,
                max_length=128,
                return_tensors="pt"
            )
            input_ids = encoded["input_ids"].to(device)
            attention_mask = encoded["attention_mask"].to(device)
            logits = model(input_ids=input_ids, attention_mask=attention_mask)
            probs = torch.softmax(logits, dim=-1)[:, 1].cpu().numpy()
            all_probs.extend(probs)

    df["pred_prob"] = all_probs
    df["pred_label"] = (df["pred_prob"] >= 0.5).astype(int)

    y_true = df["label"].values
    y_pred = df["pred_label"].values

    acc = accuracy_score(y_true, y_pred)
    prec = precision_score(y_true, y_pred, zero_division=0)
    rec = recall_score(y_true, y_pred, zero_division=0)
    f1 = f1_score(y_true, y_pred, zero_division=0)

    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0
    fnr = fn / (fn + tp) if (fn + tp) > 0 else 0.0

    # Category Breakdown
    cat_metrics = {}
    for cat, grp in df.groupby("category"):
        c_true = grp["label"].values
        c_pred = grp["pred_label"].values
        c_acc = accuracy_score(c_true, c_pred)
        c_fp = int(((c_true == 0) & (c_pred == 1)).sum())
        c_fn = int(((c_true == 1) & (c_pred == 0)).sum())
        c_total = len(grp)
        cat_metrics[cat] = {
            "total": c_total,
            "accuracy": round(c_acc, 4),
            "false_positives": c_fp,
            "false_negatives": c_fn,
            "avg_probability": round(float(grp["pred_prob"].mean()), 4)
        }

    return {
        "checkpoint": checkpoint_path,
        "dataset_rows": len(df),
        "overall": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1": round(f1, 4),
            "false_positive_rate": round(fpr, 4),
            "false_negative_rate": round(fnr, 4),
            "tn": int(tn),
            "fp": int(fp),
            "fn": int(fn),
            "tp": int(tp)
        },
        "by_category": cat_metrics
    }


if __name__ == "__main__":
    v2_path = os.path.join("models", "tinybert", "v2", "model.pth")
    v3_path = os.path.join("models", "tinybert", "v3", "model.pth")

    print("\n" + "="*80)
    print("FINANCIAL BENCHMARK EVALUATION: BASELINE (V2) vs CANDIDATE (V3)")
    print("="*80)

    results = {}
    if os.path.exists(v2_path):
        print(f"\n[1/2] Evaluating Baseline TinyBERT v2 ({v2_path})...")
        results["v2"] = evaluate_tinybert_checkpoint(v2_path)
    if os.path.exists(v3_path):
        print(f"\n[2/2] Evaluating Candidate TinyBERT v3 ({v3_path})...")
        results["v3"] = evaluate_tinybert_checkpoint(v3_path)

    print("\n" + "="*80)
    print(f"{'Metric':<25} | {'Baseline (v2)':<15} | {'Candidate (v3)':<15} | {'Delta':<15}")
    print("-" * 80)
    for m in ["accuracy", "precision", "recall", "f1", "false_positive_rate", "false_negative_rate"]:
        v2_val = results["v2"]["overall"][m] if "v2" in results else 0.0
        v3_val = results["v3"]["overall"][m] if "v3" in results else 0.0
        delta = v3_val - v2_val
        delta_str = f"{delta:+.4f}"
        print(f"{m:<25} | {v2_val:<15.4f} | {v3_val:<15.4f} | {delta_str:<15}")

    print("\n" + "="*80)
    print("CATEGORY BREAKDOWN COMPARISON (Accuracy & False Positives)")
    print("="*80)
    print(f"{'Category':<32} | {'Total':<6} | {'v2 Acc':<8} | {'v2 FP':<6} | {'v3 Acc':<8} | {'v3 FP':<6}")
    print("-" * 80)
    all_cats = sorted(results["v3"]["by_category"].keys())
    for cat in all_cats:
        c_v2 = results["v2"]["by_category"].get(cat, {})
        c_v3 = results["v3"]["by_category"].get(cat, {})
        tot = c_v3.get("total", 0)
        v2_a = c_v2.get("accuracy", 0.0)
        v2_fp = c_v2.get("false_positives", 0)
        v3_a = c_v3.get("accuracy", 0.0)
        v3_fp = c_v3.get("false_positives", 0)
        print(f"{cat:<32} | {tot:<6} | {v2_a:<8.2f} | {v2_fp:<6} | {v3_a:<8.2f} | {v3_fp:<6}")


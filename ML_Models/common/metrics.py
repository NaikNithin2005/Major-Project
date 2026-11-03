"""
ML_Models/common/metrics.py
Standardized metric computation and formatting for classification evaluations.
"""

import json
import numpy as np
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix,
    classification_report
)


def compute_classification_metrics(y_true, y_pred, y_probs=None, class_names=None, model_name="Model"):
    """
    Computes a comprehensive suite of evaluation metrics with macro and weighted averages.
    Returns a dictionary of raw/formatted values suitable for JSON storage and display.
    """
    y_true = np.asarray(y_true, dtype=int)
    y_pred = np.asarray(y_pred, dtype=int)
    n_samples = len(y_true)
    
    unique_classes = sorted(list(set(np.unique(y_true)).union(set(np.unique(y_pred)))))
    n_classes = len(unique_classes)
    is_binary = (n_classes <= 2)

    # Core Global Metrics
    acc = float(accuracy_score(y_true, y_pred))
    
    # Macro averages
    macro_prec = float(precision_score(y_true, y_pred, average="macro", zero_division=0))
    macro_rec = float(recall_score(y_true, y_pred, average="macro", zero_division=0))
    macro_f1 = float(f1_score(y_true, y_pred, average="macro", zero_division=0))
    
    # Weighted averages
    weighted_prec = float(precision_score(y_true, y_pred, average="weighted", zero_division=0))
    weighted_rec = float(recall_score(y_true, y_pred, average="weighted", zero_division=0))
    weighted_f1 = float(f1_score(y_true, y_pred, average="weighted", zero_division=0))

    # Confusion Matrix
    cm = confusion_matrix(y_true, y_pred, labels=unique_classes)
    
    # Per-class breakdown
    target_names = [class_names[c] if class_names and c in class_names else str(c) for c in unique_classes]
    
    report_dict = classification_report(
        y_true, y_pred,
        labels=unique_classes,
        target_names=target_names,
        output_dict=True,
        zero_division=0
    )
    
    report_text = classification_report(
        y_true, y_pred,
        labels=unique_classes,
        target_names=target_names,
        digits=4,
        zero_division=0
    )

    per_class_metrics = {}
    for c_id, c_name in zip(unique_classes, target_names):
        if c_name in report_dict:
            per_class_metrics[c_name] = {
                "class_id": int(c_id),
                "precision": float(report_dict[c_name]["precision"]),
                "recall": float(report_dict[c_name]["recall"]),
                "f1_score": float(report_dict[c_name]["f1-score"]),
                "support": int(report_dict[c_name]["support"])
            }

    # Binary specifics (FPR, FNR)
    binary_metrics = {}
    if is_binary and cm.shape == (2, 2):
        tn, fp, fn, tp = cm.ravel()
        fpr = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0
        fnr = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0
        binary_metrics = {
            "true_negatives": int(tn),
            "false_positives": int(fp),
            "false_negatives": int(fn),
            "true_positives": int(tp),
            "false_positive_rate": fpr,
            "false_negative_rate": fnr
        }

    metrics_payload = {
        "model_name": model_name,
        "n_samples": n_samples,
        "n_classes": n_classes,
        "accuracy": acc,
        "accuracy_pct": round(acc * 100, 2),
        "macro_metrics": {
            "precision": macro_prec,
            "recall": macro_rec,
            "f1_score": macro_f1
        },
        "weighted_metrics": {
            "precision": weighted_prec,
            "recall": weighted_rec,
            "f1_score": weighted_f1
        },
        "per_class_metrics": per_class_metrics,
        "binary_specifics": binary_metrics,
        "confusion_matrix": cm.tolist(),
        "class_labels": target_names,
        "classification_report": report_text
    }

    return metrics_payload


def format_evaluation_summary(metrics_dict: dict) -> str:
    """Formats standard terminal evaluation display conforming to project requirements."""
    model_name = metrics_dict.get("model_name", "MODEL").upper()
    acc_pct = metrics_dict.get("accuracy_pct", 0.0)
    
    # If multiclass, use weighted/macro; if binary, primary metrics
    macro = metrics_dict.get("macro_metrics", {})
    prec_pct = round(macro.get("precision", 0.0) * 100, 2)
    rec_pct = round(macro.get("recall", 0.0) * 100, 2)
    f1_pct = round(macro.get("f1_score", 0.0) * 100, 2)

    cm = np.array(metrics_dict.get("confusion_matrix", []))
    report_text = metrics_dict.get("classification_report", "")

    lines = [
        "========================================",
        f"{model_name} MODEL EVALUATION",
        "========================================",
        "",
        f"Accuracy  : {acc_pct:.2f}%",
        f"Precision : {prec_pct:.2f}%",
        f"Recall    : {rec_pct:.2f}%",
        f"F1 Score  : {f1_pct:.2f}%",
        "",
        "Confusion Matrix:",
        str(cm),
        "",
        "Classification Report:",
        report_text,
        "Evaluation completed successfully.",
        "========================================"
    ]
    return "\n".join(lines)


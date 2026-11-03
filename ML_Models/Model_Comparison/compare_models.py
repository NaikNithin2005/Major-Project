"""
ML_Models/Model_Comparison/compare_models.py
Cross-Model Performance Comparison and Visualization Suite.
Aggregates metrics across TinyBERT, XGBoost, and CNN to generate comparative reports and publication-grade plots.
"""

import sys
import json
from pathlib import Path
import pandas as pd
import numpy as np

# Resolve repository root
CURRENT_DIR = Path(__file__).resolve().parent
ML_MODELS_ROOT = CURRENT_DIR.parent
if str(ML_MODELS_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_MODELS_ROOT))

from common.config import (
    COMPARISON_DIR,
    TINYBERT_EVAL_DIR,
    XGBOOST_EVAL_DIR,
    CNN_EVAL_DIR,
)
from common.visualization import (
    plot_metric_bar_chart,
    plot_grouped_metrics,
    plot_combined_confusion_matrices,
)


def load_model_metrics(eval_dir: Path, model_name: str) -> dict:
    """Loads metrics.json from an evaluation directory."""
    metrics_file = eval_dir / "metrics.json"
    if not metrics_file.exists():
        raise FileNotFoundError(
            f"Metrics file for {model_name} not found at {metrics_file}. "
            f"Please run the model evaluation script first."
        )
    with open(metrics_file, "r", encoding="utf-8") as f:
        return json.load(f)


def run_comparison(output_dir=None):
    out_dir = Path(output_dir or COMPARISON_DIR)
    out_dir.mkdir(parents=True, exist_ok=True)

    print("Loading evaluation metrics from all three models...")
    tb_metrics = load_model_metrics(TINYBERT_EVAL_DIR, "TinyBERT")
    xgb_metrics = load_model_metrics(XGBOOST_EVAL_DIR, "XGBoost")
    cnn_metrics = load_model_metrics(CNN_EVAL_DIR, "CNN")

    models = ["TinyBERT", "XGBoost", "CNN"]
    metrics_map = {
        "TinyBERT": tb_metrics,
        "XGBoost": xgb_metrics,
        "CNN": cnn_metrics
    }

    # Extract primary macro/weighted scores for side-by-side comparison
    # Note: TinyBERT is 4-class SMS NLP; XGBoost and CNN are binary URL Quishing.
    comparison_records = []
    plot_metrics_data = {}

    for name in models:
        m = metrics_map[name]
        acc = m["accuracy"]
        macro = m.get("macro_metrics", {})
        weighted = m.get("weighted_metrics", {})

        prec = macro.get("precision", 0.0)
        rec = macro.get("recall", 0.0)
        f1 = macro.get("f1_score", 0.0)

        task_desc = "4-Class SMS Smishing" if name == "TinyBERT" else "Binary URL Quishing"
        input_type = "SMS Text" if name == "TinyBERT" else "URL String"

        comparison_records.append({
            "Model": name,
            "Domain_Task": task_desc,
            "Input_Type": input_type,
            "Test_Samples": m["n_samples"],
            "Classes": m["n_classes"],
            "Accuracy": round(acc, 4),
            "Accuracy_Pct": round(acc * 100, 2),
            "Precision_Macro": round(prec, 4),
            "Recall_Macro": round(rec, 4),
            "F1_Score_Macro": round(f1, 4),
            "Precision_Weighted": round(weighted.get("precision", prec), 4),
            "Recall_Weighted": round(weighted.get("recall", rec), 4),
            "F1_Score_Weighted": round(weighted.get("f1_score", f1), 4),
        })

        plot_metrics_data[name] = {
            "accuracy": acc,
            "precision": prec,
            "recall": rec,
            "f1": f1
        }

    # 1. Save comparison_metrics.csv
    df_comp = pd.DataFrame(comparison_records)
    csv_path = out_dir / "comparison_metrics.csv"
    df_comp.to_csv(csv_path, index=False)
    print(f"Saved comparison metrics table to: {csv_path}")

    # 2. Save comparison_results.json
    json_path = out_dir / "comparison_results.json"
    comparison_payload = {
        "summary": comparison_records,
        "detailed_metrics": metrics_map,
        "notes": (
            "TinyBERT evaluates 4-class SMS smishing. XGBoost and CNN evaluate binary URL quishing. "
            "Reported Precision, Recall, and F1 utilize macro-averaging for unbiased class representation."
        )
    }
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(comparison_payload, f, indent=2)
    print(f"Saved comparison JSON to: {json_path}")

    # 3. Generate Visualizations
    print("Generating comparison charts...")

    # A. Accuracy Comparison
    plot_metric_bar_chart(
        models=models,
        values=[plot_metrics_data[m]["accuracy"] for m in models],
        metric_name="Accuracy",
        title="Model Accuracy Comparison (Test Datasets)",
        output_path=str(out_dir / "accuracy_comparison.png")
    )

    # B. Precision Comparison
    plot_metric_bar_chart(
        models=models,
        values=[plot_metrics_data[m]["precision"] for m in models],
        metric_name="Macro Precision",
        title="Model Precision Comparison (Macro Average)",
        output_path=str(out_dir / "precision_comparison.png")
    )

    # C. Recall Comparison
    plot_metric_bar_chart(
        models=models,
        values=[plot_metrics_data[m]["recall"] for m in models],
        metric_name="Macro Recall",
        title="Model Recall Comparison (Macro Average)",
        output_path=str(out_dir / "recall_comparison.png")
    )

    # D. F1 Score Comparison
    plot_metric_bar_chart(
        models=models,
        values=[plot_metrics_data[m]["f1"] for m in models],
        metric_name="Macro F1 Score",
        title="Model F1 Score Comparison (Macro Average)",
        output_path=str(out_dir / "f1_score_comparison.png")
    )

    # E. Overall Grouped Performance Comparison
    plot_grouped_metrics(
        models=models,
        metrics_data=plot_metrics_data,
        title="Squish Multi-Model Performance Comparison",
        output_path=str(out_dir / "overall_comparison.png")
    )

    # F. Confusion Matrix Comparison (3-Panel Unified Figure)
    matrices_dict = {
        "TinyBERT": (
            tb_metrics["confusion_matrix"],
            ["BANKING", "GENERAL", "SMISHING", "SUSPICIOUS"],
            "SMS Smishing (4-Class)"
        ),
        "XGBoost": (
            xgb_metrics["confusion_matrix"],
            ["Legitimate", "Phishing"],
            "URL Quishing (Binary)"
        ),
        "CNN": (
            cnn_metrics["confusion_matrix"],
            ["Legitimate", "Phishing"],
            "URL Quishing (Binary)"
        )
    }
    plot_combined_confusion_matrices(
        matrices_dict=matrices_dict,
        output_path=str(out_dir / "confusion_matrix_comparison.png")
    )

    print("All comparison visualizations generated successfully.")
    return df_comp


if __name__ == "__main__":
    run_comparison()


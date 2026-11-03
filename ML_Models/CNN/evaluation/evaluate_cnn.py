"""
ML_Models/CNN/evaluation/evaluate_cnn.py
Executes the comprehensive evaluation pipeline for the 1D Character-Level CNN URL Quishing Classifier.
Generates metrics.json, classification_report.txt, confusion_matrix.png, and evaluation_results.csv.
"""

import sys
import json
from pathlib import Path

# Resolve repository root
CURRENT_DIR = Path(__file__).resolve().parent
CNN_DIR = CURRENT_DIR.parent
ML_MODELS_ROOT = CNN_DIR.parent
if str(ML_MODELS_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_MODELS_ROOT))

from CNN.algorithm.cnn_algorithm import CNNURLClassifier
from common.config import CNN_EVAL_DIR, URL_TEST_PATH, URL_CLASS_MAP
from common.metrics import format_evaluation_summary
from common.visualization import plot_confusion_matrix


def run_evaluation(test_csv=None, output_dir=None):
    out_dir = Path(output_dir or CNN_EVAL_DIR)
    out_dir.mkdir(parents=True, exist_ok=True)
    test_path = Path(test_csv or URL_TEST_PATH)

    print(f"Loading CNN Classifier and evaluating against: {test_path}")
    classifier = CNNURLClassifier()
    metrics, results_df = classifier.evaluate(test_csv=test_path)

    # 1. Save metrics.json
    metrics_file = out_dir / "metrics.json"
    with open(metrics_file, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
    print(f"Saved evaluation metrics to: {metrics_file}")

    # 2. Save classification_report.txt
    report_file = out_dir / "classification_report.txt"
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(metrics["classification_report"])
    print(f"Saved classification report to: {report_file}")

    # 3. Save confusion_matrix.png
    cm_plot_file = out_dir / "confusion_matrix.png"
    class_names = [URL_CLASS_MAP[0], URL_CLASS_MAP[1]]
    plot_confusion_matrix(
        cm=metrics["confusion_matrix"],
        class_names=class_names,
        title="CNN Confusion Matrix (Binary URL Quishing)",
        output_path=str(cm_plot_file)
    )
    print(f"Saved confusion matrix visualization to: {cm_plot_file}")

    # 4. Save evaluation_results.csv
    results_csv_file = out_dir / "evaluation_results.csv"
    results_df.to_csv(results_csv_file, index=False)
    print(f"Saved sample-level evaluation predictions to: {results_csv_file}")

    # Display summary
    print("\n" + format_evaluation_summary(metrics))
    return metrics


if __name__ == "__main__":
    run_evaluation()


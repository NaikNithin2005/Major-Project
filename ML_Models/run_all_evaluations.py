"""
ML_Models/run_all_evaluations.py
Unified execution pipeline for the entire Squish Machine Learning Model Suite.
Sequentially evaluates TinyBERT, XGBoost, and CNN, then triggers cross-model comparison and reporting.
"""

import sys
import time
from pathlib import Path

# Add ML_Models root to sys.path
CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from TinyBERT.evaluation.evaluate_tinybert import run_evaluation as eval_tinybert
from XGBoost.evaluation.evaluate_xgboost import run_evaluation as eval_xgboost
from CNN.evaluation.evaluate_cnn import run_evaluation as eval_cnn
from Model_Comparison.compare_models import run_comparison


def main():
    start_time = time.time()
    print("=" * 60)
    print("STARTING SQUISH MULTI-MODEL EVALUATION PIPELINE")
    print("=" * 60)

    # 1. TinyBERT Evaluation
    print("\n>>> [1/3] EVALUATING RETRAINED TINYBERT (SMS SMISHING)...")
    t0 = time.time()
    tb_metrics = eval_tinybert()
    print(f"TinyBERT evaluation completed in {time.time() - t0:.2f}s.")

    # 2. XGBoost Evaluation
    print("\n>>> [2/3] EVALUATING XGBOOST (URL / QR QUISHING)...")
    t0 = time.time()
    xgb_metrics = eval_xgboost()
    print(f"XGBoost evaluation completed in {time.time() - t0:.2f}s.")

    # 3. CNN Evaluation
    print("\n>>> [3/3] EVALUATING 1D CHAR-CNN (URL / QR QUISHING)...")
    t0 = time.time()
    cnn_metrics = eval_cnn()
    print(f"CNN evaluation completed in {time.time() - t0:.2f}s.")

    # 4. Model Comparison and Visualization Generation
    print("\n>>> GENERATING CROSS-MODEL COMPARISON & VISUALIZATIONS...")
    t0 = time.time()
    df_comp = run_comparison()
    print(f"Comparison completed in {time.time() - t0:.2f}s.")

    # 5. Formatted Summary Display (Matching exact requirement)
    tb_acc = f"{tb_metrics['accuracy_pct']:.2f}%"
    tb_prec = f"{tb_metrics['macro_metrics']['precision'] * 100:.2f}%"
    tb_rec = f"{tb_metrics['macro_metrics']['recall'] * 100:.2f}%"
    tb_f1 = f"{tb_metrics['macro_metrics']['f1_score'] * 100:.2f}%"

    xgb_acc = f"{xgb_metrics['accuracy_pct']:.2f}%"
    xgb_prec = f"{xgb_metrics['macro_metrics']['precision'] * 100:.2f}%"
    xgb_rec = f"{xgb_metrics['macro_metrics']['recall'] * 100:.2f}%"
    xgb_f1 = f"{xgb_metrics['macro_metrics']['f1_score'] * 100:.2f}%"

    cnn_acc = f"{cnn_metrics['accuracy_pct']:.2f}%"
    cnn_prec = f"{cnn_metrics['macro_metrics']['precision'] * 100:.2f}%"
    cnn_rec = f"{cnn_metrics['macro_metrics']['recall'] * 100:.2f}%"
    cnn_f1 = f"{cnn_metrics['macro_metrics']['f1_score'] * 100:.2f}%"

    print("\n" + "=" * 40)
    print("SQUISH ML SUITE EVALUATION SUMMARY")
    print("=" * 40)
    print(f"\n{'MODEL':<15}{'ACCURACY':<12}{'PRECISION':<13}{'RECALL':<10}{'F1 SCORE':<10}\n")
    print(f"{'TinyBERT':<15}{tb_acc:<12}{tb_prec:<13}{tb_rec:<10}{tb_f1:<10}")
    print(f"{'XGBoost':<15}{xgb_acc:<12}{xgb_prec:<13}{xgb_rec:<10}{xgb_f1:<10}")
    print(f"{'CNN':<15}{cnn_acc:<12}{cnn_prec:<13}{cnn_rec:<10}{cnn_f1:<10}")
    print("\n" + "=" * 40)
    print("MODEL COMPARISON COMPLETED")
    print("=" * 40)
    print("\nAll metrics and visualizations saved successfully.")
    print(f"Total pipeline elapsed time: {time.time() - start_time:.2f}s.\n")


if __name__ == "__main__":
    main()


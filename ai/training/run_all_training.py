import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from ai.training.prepare_data import prepare_all_data
from ai.training.train_tinybert import train_tinybert
from ai.training.train_xgboost import train_xgboost
from ai.training.train_isolation_forest import train_isolation_forest
from ai.deployment.onnx_exporter import verify_all_onnx_exports
from ai.evaluation.report_generator import generate_phase5_report

def main():
    print("=" * 80)
    print("STARTING PHASE 5 AI/ML DETECTION ENGINE PIPELINE")
    print("=" * 80)

    print("\n[Step 1/6] Preparing & splitting datasets (70/15/15)...")
    prepare_all_data()

    print("\n[Step 2/6] Training & exporting TinyBERT Smishing Classifier...")
    train_tinybert()

    print("\n[Step 3/6] Training & exporting XGBoost URL Quishing Classifier...")
    train_xgboost()

    print("\n[Step 4/6] Training & exporting Isolation Forest Anomaly Detector...")
    train_isolation_forest()

    print("\n[Step 5/6] Verifying ONNX model exports...")
    verify_all_onnx_exports()

    print("\n[Step 6/6] Generating Phase 5 Model Evaluation Report...")
    generate_phase5_report()

    print("\n" + "=" * 80)
    print("PHASE 5 AI/ML DETECTION ENGINE PIPELINE COMPLETED SUCCESSFULLY!")
    print("=" * 80)

if __name__ == "__main__":
    main()

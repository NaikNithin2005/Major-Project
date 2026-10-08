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
    version = sys.argv[1] if len(sys.argv) > 1 else "v2"
    print("=" * 80)
    print(f"STARTING PHASE 5 AI/ML DETECTION ENGINE PIPELINE ({version.upper()})")
    print("=" * 80)

    print("\n[Step 1/6] Preparing & splitting datasets with Umar Quishing Data (70/15/15)...")
    prepare_all_data(use_umar=True)

    print(f"\n[Step 2/6] Training & exporting TinyBERT Smishing Classifier ({version})...")
    train_tinybert(version=version)

    print(f"\n[Step 3/6] Training & exporting XGBoost URL Quishing Classifier ({version})...")
    train_xgboost(version=version)

    print(f"\n[Step 4/6] Training & exporting Isolation Forest Anomaly Detector ({version})...")
    train_isolation_forest(version=version)

    print(f"\n[Step 5/6] Verifying ONNX model exports ({version})...")
    verify_all_onnx_exports(version=version)

    print(f"\n[Step 6/6] Generating Phase 5 Model Evaluation Report...")
    generate_phase5_report()

    print("\n" + "=" * 80)
    print(f"PHASE 5 AI/ML DETECTION ENGINE PIPELINE ({version.upper()}) COMPLETED SUCCESSFULLY!")
    print("=" * 80)

if __name__ == "__main__":
    main()

"""
ML_Models/TinyBERT/algorithm/tinybert_algorithm.py
Standalone inference and evaluation engine for retrained 4-class TinyBERT Smishing Classifier.
"""

import os
import sys
import argparse
import json
from pathlib import Path
import numpy as np
import pandas as pd
import onnxruntime as ort
from transformers import AutoTokenizer

# Resolve repository root
CURRENT_DIR = Path(__file__).resolve().parent
TINYBERT_DIR = CURRENT_DIR.parent
ML_MODELS_ROOT = TINYBERT_DIR.parent
if str(ML_MODELS_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_MODELS_ROOT))

from common.config import (
    TINYBERT_ONNX_PATH,
    TINYBERT_MODEL_DIR,
    TINYBERT_CLASS_MAP,
    SMS_TEST_PATH,
    MAX_SEQUENCE_LENGTH,
)
from common.metrics import compute_classification_metrics, format_evaluation_summary
from common.preprocessing import clean_sms_text


class TinyBERTClassifier:
    """
    Inference and evaluation handler for retrained TinyBERT Smishing Detection Model.
    Supports ONNX Runtime execution for sub-millisecond mobile and backend latency.
    """

    def __init__(self, model_path=None, model_dir=None):
        self.model_path = Path(model_path or TINYBERT_ONNX_PATH)
        self.model_dir = Path(model_dir or TINYBERT_MODEL_DIR)

        if not self.model_path.exists():
            raise FileNotFoundError(f"TinyBERT ONNX model not found at {self.model_path}")

        # Load label mapping
        label_map_file = self.model_dir / "label_mapping.json"
        if label_map_file.exists():
            with open(label_map_file, "r", encoding="utf-8") as f:
                raw_map = json.load(f)
                self.class_map = {int(k): v for k, v in raw_map.get("id2label", raw_map).items()}
        else:
            self.class_map = TINYBERT_CLASS_MAP

        # Initialize Tokenizer and ONNX Session
        self.tokenizer = AutoTokenizer.from_pretrained(str(self.model_dir))
        session_options = ort.SessionOptions()
        session_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(
            str(self.model_path),
            sess_options=session_options,
            providers=["CPUExecutionProvider"]
        )

        # Inspect required inputs
        self.input_names = [inp.name for inp in self.session.get_inputs()]
        self.output_names = [out.name for out in self.session.get_outputs()]

    def predict(self, text: str) -> dict:
        """Runs single-text smishing classification."""
        results = self.predict_batch([text])
        return results[0]

    def predict_batch(self, texts: list, batch_size: int = 128) -> list:
        """Batched inference returning probabilities and threat classifications."""
        cleaned_texts = [clean_sms_text(t) for t in texts]
        all_results = []

        for i in range(0, len(cleaned_texts), batch_size):
            batch = cleaned_texts[i : i + batch_size]
            encoded = self.tokenizer(
                batch,
                padding="max_length",
                truncation=True,
                max_length=MAX_SEQUENCE_LENGTH,
                return_tensors="np"
            )

            feed = {
                "input_ids": encoded["input_ids"].astype(np.int64),
                "attention_mask": encoded["attention_mask"].astype(np.int64)
            }
            if "token_type_ids" in self.input_names and "token_type_ids" in encoded:
                feed["token_type_ids"] = encoded["token_type_ids"].astype(np.int64)

            logits = self.session.run(self.output_names, feed)[0]
            exp_logits = np.exp(logits - np.max(logits, axis=1, keepdims=True))
            probs = exp_logits / np.sum(exp_logits, axis=1, keepdims=True)

            for text_orig, p in zip(texts[i : i + batch_size], probs):
                pred_idx = int(np.argmax(p))
                label_name = self.class_map.get(pred_idx, f"CLASS_{pred_idx}")
                conf = float(p[pred_idx])
                
                # Check smishing or suspicious threat indicators
                is_threat = (label_name in ["SMISHING", "SUSPICIOUS"])
                prob_dict = {self.class_map[idx]: float(round(p[idx], 4)) for idx in range(len(p))}

                all_results.append({
                    "text": text_orig,
                    "predicted_class_id": pred_idx,
                    "predicted_label": label_name,
                    "confidence": round(conf, 4),
                    "is_threat": is_threat,
                    "probabilities": prob_dict
                })

        return all_results

    def evaluate(self, test_csv=None, batch_size=128) -> tuple:
        """
        Executes evaluation against held-out SMS test dataset.
        Returns: (metrics_dict, results_dataframe)
        """
        csv_path = Path(test_csv or SMS_TEST_PATH)
        if not csv_path.exists():
            raise FileNotFoundError(f"Test dataset not found at {csv_path}")

        df = pd.read_csv(csv_path)
        if "message" not in df.columns or "label" not in df.columns:
            raise ValueError(f"CSV must contain 'message' and 'label' columns. Found: {df.columns.tolist()}")

        texts = df["message"].astype(str).tolist()
        y_true = df["label"].astype(int).to_numpy()

        predictions = self.predict_batch(texts, batch_size=batch_size)
        y_pred = np.array([p["predicted_class_id"] for p in predictions], dtype=int)
        confidences = [p["confidence"] for p in predictions]
        pred_labels = [p["predicted_label"] for p in predictions]

        metrics = compute_classification_metrics(
            y_true=y_true,
            y_pred=y_pred,
            class_names=self.class_map,
            model_name="TinyBERT"
        )

        # Build evaluation dataframe
        results_df = df.copy()
        results_df["predicted_label_id"] = y_pred
        results_df["predicted_label_name"] = pred_labels
        results_df["prediction_confidence"] = confidences
        results_df["is_correct"] = (y_true == y_pred).astype(int)

        return metrics, results_df


def main():
    parser = argparse.ArgumentParser(description="TinyBERT Smishing Detection Inference & Evaluation")
    parser.add_argument("--text", type=str, help="Single SMS message to evaluate")
    parser.add_argument("--csv", type=str, help="CSV file for batch scoring or evaluation")
    parser.add_argument("--batch-size", type=int, default=128, help="Batch size for inference")
    args = parser.parse_args()

    classifier = TinyBERTClassifier()

    if args.text:
        res = classifier.predict(args.text)
        print("\n--- TinyBERT SMS Analysis ---")
        print(f"Message   : {res['text']}")
        print(f"Verdict   : {res['predicted_label']} (Confidence: {res['confidence'] * 100:.2f}%)")
        print(f"Threat    : {'YES (Action Required)' if res['is_threat'] else 'NO (Safe/Legitimate)'}")
        print("Probabilities:")
        for k, v in res["probabilities"].items():
            print(f"  - {k:<20}: {v * 100:.2f}%")
        return

    if args.csv and not args.csv.endswith("test.csv") and "label" not in pd.read_csv(args.csv, nrows=1).columns:
        # Just score without ground truth
        df = pd.read_csv(args.csv)
        col = "message" if "message" in df.columns else df.columns[0]
        preds = classifier.predict_batch(df[col].tolist())
        for p in preds[:10]:
            print(f"[{p['predicted_label']} - {p['confidence']*100:.1f}%] {p['text'][:60]}...")
        return

    # Default action: Execute evaluation pipeline
    metrics, _ = classifier.evaluate(test_csv=args.csv, batch_size=args.batch_size)
    print(format_evaluation_summary(metrics))


if __name__ == "__main__":
    main()

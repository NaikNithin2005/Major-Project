"""
ML_Models/CNN/algorithm/cnn_algorithm.py
Independent 1D Character-Level CNN URL/QR Quishing Classifier and Evaluation Engine.
Built using raw NumPy with Embedding, Conv1D, Global Max-Pooling, and Dense Softmax layers.
"""

import os
import sys
import argparse
import json
import pickle
from pathlib import Path
import numpy as np
import pandas as pd
import h5py

# Add ML_Models root and CNN model directory to sys.path
CURRENT_DIR = Path(__file__).resolve().parent
CNN_DIR = CURRENT_DIR.parent
ML_MODELS_ROOT = CNN_DIR.parent
CNN_MODEL_DIR = CNN_DIR / "model"

for p in [str(ML_MODELS_ROOT), str(CNN_MODEL_DIR)]:
    if p not in sys.path:
        sys.path.insert(0, p)

import cnn_scratch
from common.config import (
    CNN_PKL_PATH,
    CNN_H5_PATH,
    URL_TEST_PATH,
    URL_CLASS_MAP,
    DEFAULT_PROB_THRESHOLD,
    MAX_SEQUENCE_LENGTH,
)
from common.metrics import compute_classification_metrics, format_evaluation_summary
from common.preprocessing import encode_url


class CNNURLClassifier:
    """
    Independent 1D Character-level Convolutional Neural Network URL Classifier.
    Extracts deep morphological and lexical character patterns directly from raw URL strings.
    """

    def __init__(self, model_pkl_path=None, model_h5_path=None, config_path=None):
        self.pkl_path = Path(model_pkl_path or CNN_PKL_PATH)
        self.h5_path = Path(model_h5_path or CNN_H5_PATH)
        self.config_path = Path(config_path or (CNN_MODEL_DIR / "cnn_config.json"))

        # Load configuration
        if self.config_path.exists():
            with open(self.config_path, "r", encoding="utf-8") as f:
                self.config = json.load(f)
        else:
            self.config = {
                "vocab_size": 47,
                "embed_dim": 16,
                "conv_filters": 32,
                "kernel_size": 4,
                "output_classes": 2,
                "max_seq_len": MAX_SEQUENCE_LENGTH
            }

        # Load weights: prefer PKL for speed, fallback to H5
        if self.pkl_path.exists():
            with open(self.pkl_path, "rb") as f:
                self.model = pickle.load(f)
        elif self.h5_path.exists():
            self.model = self._load_from_h5(self.h5_path)
        else:
            raise FileNotFoundError(f"CNN model weights not found at {self.pkl_path} or {self.h5_path}")

    def _load_from_h5(self, h5_path: Path):
        """Initializes architecture and populates weights from HDF5 file."""
        model = cnn_scratch.CharCNN(
            vocab_size=self.config.get("vocab_size", 47),
            embed_dim=self.config.get("embed_dim", 16),
            num_filters=self.config.get("conv_filters", 32),
            kernel_size=self.config.get("kernel_size", 4),
            num_classes=self.config.get("output_classes", 2)
        )
        with h5py.File(h5_path, "r") as h5f:
            model.embed.W = h5f["embedding/W"][:]
            model.conv.W = h5f["conv1d/W"][:]
            model.conv.b = h5f["conv1d/b"][:]
            model.dense.W = h5f["dense/W"][:]
            model.dense.b = h5f["dense/b"][:]
        return model

    def encode(self, url: str) -> np.ndarray:
        """Encodes URL to 128-dim integer sequence."""
        return encode_url(url, max_len=self.config.get("max_seq_len", 128))

    def predict(self, url: str, threshold: float = DEFAULT_PROB_THRESHOLD) -> dict:
        """Scores a single URL and returns threat analysis."""
        x_ids = np.stack([self.encode(url)])
        probs = self.model.forward(x_ids)[0]
        prob_phish = float(probs[1])
        pred_idx = 1 if prob_phish >= threshold else 0
        label_name = URL_CLASS_MAP[pred_idx]

        return {
            "url": url,
            "phishing_probability": round(prob_phish, 4),
            "legitimate_probability": round(float(probs[0]), 4),
            "predicted_class_id": pred_idx,
            "predicted_label": label_name,
            "is_threat": (pred_idx == 1),
            "threshold_applied": threshold
        }

    def predict_batch(self, urls: list, threshold: float = DEFAULT_PROB_THRESHOLD, batch_size: int = 1000) -> list:
        """Batched character encoding and neural forward pass."""
        all_results = []
        n = len(urls)

        for i in range(0, n, batch_size):
            batch_urls = urls[i : i + batch_size]
            X_ids = np.stack([self.encode(u) for u in batch_urls])
            probs = self.model.forward(X_ids)

            for u, p_row in zip(batch_urls, probs):
                p_val = float(p_row[1])
                pred_idx = 1 if p_val >= threshold else 0
                all_results.append({
                    "url": u,
                    "phishing_probability": round(p_val, 4),
                    "legitimate_probability": round(float(p_row[0]), 4),
                    "predicted_class_id": pred_idx,
                    "predicted_label": URL_CLASS_MAP[pred_idx],
                    "is_threat": (pred_idx == 1),
                    "threshold_applied": threshold
                })

        return all_results

    def evaluate(self, test_csv=None, threshold: float = DEFAULT_PROB_THRESHOLD, batch_size: int = 1000) -> tuple:
        """
        Executes evaluation against held-out URL test set.
        Returns: (metrics_dict, results_dataframe)
        """
        csv_path = Path(test_csv or URL_TEST_PATH)
        if not csv_path.exists():
            raise FileNotFoundError(f"URL Test dataset not found at {csv_path}")

        df = pd.read_csv(csv_path)
        if "url" not in df.columns:
            raise ValueError(f"CSV must contain 'url' column. Found: {df.columns.tolist()}")

        urls = df["url"].astype(str).tolist()

        if "target" in df.columns:
            y_true = df["target"].astype(int).to_numpy()
        elif "label" in df.columns:
            y_true = df["label"].map(lambda x: 1 if str(x).lower() in ["phishing", "1", "malicious"] else 0).to_numpy()
        else:
            raise ValueError("CSV must contain 'label' or 'target' column for evaluation.")

        predictions = self.predict_batch(urls, threshold=threshold, batch_size=batch_size)
        y_pred = np.array([p["predicted_class_id"] for p in predictions], dtype=int)
        y_probs = np.array([p["phishing_probability"] for p in predictions], dtype=float)

        metrics = compute_classification_metrics(
            y_true=y_true,
            y_pred=y_pred,
            y_probs=y_probs,
            class_names=URL_CLASS_MAP,
            model_name="CNN"
        )

        results_df = df.copy()
        results_df["predicted_label_id"] = y_pred
        results_df["predicted_label"] = [p["predicted_label"] for p in predictions]
        results_df["phishing_probability"] = y_probs
        results_df["is_correct"] = (y_true == y_pred).astype(int)

        return metrics, results_df


def main():
    parser = argparse.ArgumentParser(description="1D Char-CNN URL Quishing Classifier Inference & Evaluation")
    parser.add_argument("--url", type=str, help="Single URL to evaluate")
    parser.add_argument("--csv", type=str, help="CSV file for batch scoring or evaluation")
    parser.add_argument("--threshold", type=float, default=DEFAULT_PROB_THRESHOLD, help="Classification threshold")
    parser.add_argument("--batch-size", type=int, default=1000, help="Inference batch size")
    args = parser.parse_args()

    classifier = CNNURLClassifier()

    if args.url:
        res = classifier.predict(args.url, threshold=args.threshold)
        print("\n--- Char-CNN URL Threat Analysis ---")
        print(f"URL         : {res['url']}")
        print(f"Verdict     : {res['predicted_label'].upper()}")
        print(f"Phishing P  : {res['phishing_probability'] * 100:.2f}%")
        print(f"Legitimate P: {res['legitimate_probability'] * 100:.2f}%")
        print(f"Threat Flag : {'MALICIOUS (Quishing Threat Detected)' if res['is_threat'] else 'BENIGN / SAFE'}")
        return

    if args.csv and not args.csv.endswith("test.csv") and "label" not in pd.read_csv(args.csv, nrows=1).columns:
        df = pd.read_csv(args.csv)
        u_col = "url" if "url" in df.columns else df.columns[0]
        preds = classifier.predict_batch(df[u_col].tolist(), threshold=args.threshold)
        for p in preds[:10]:
            print(f"[{p['predicted_label']} - {p['phishing_probability']*100:.1f}%] {p['url']}")
        return

    # Default action: Execute evaluation against test dataset
    metrics, _ = classifier.evaluate(test_csv=args.csv, threshold=args.threshold, batch_size=args.batch_size)
    print(format_evaluation_summary(metrics))


if __name__ == "__main__":
    main()


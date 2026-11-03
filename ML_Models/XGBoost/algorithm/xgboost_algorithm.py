"""
ML_Models/XGBoost/algorithm/xgboost_algorithm.py
Independent XGBoost URL/QR Quishing Detection Algorithm and Evaluation Engine.
Built using scratch gradient-boosted decision trees with 2nd-order Taylor expansion loss.
"""

import os
import sys
import argparse
import json
import pickle
from pathlib import Path
import numpy as np
import pandas as pd

# Add ML_Models root and XGBoost model directory to sys.path
CURRENT_DIR = Path(__file__).resolve().parent
XGBOOST_DIR = CURRENT_DIR.parent
ML_MODELS_ROOT = XGBOOST_DIR.parent
XGBOOST_MODEL_DIR = XGBOOST_DIR / "model"

for p in [str(ML_MODELS_ROOT), str(XGBOOST_MODEL_DIR)]:
    if p not in sys.path:
        sys.path.insert(0, p)

import xgboost_scratch
from common.config import (
    XGBOOST_PKL_PATH,
    XGBOOST_JSON_PATH,
    URL_TEST_PATH,
    URL_CLASS_MAP,
    DEFAULT_PROB_THRESHOLD,
)
from common.metrics import compute_classification_metrics, format_evaluation_summary
from common.preprocessing import featurize_to_vector, FEATURE_ORDER


class XGBoostURLClassifier:
    """
    Independent XGBoost URL Quishing Classifier.
    Evaluates 22 hand-engineered lexical, entropy, host, and brand edit distance features.
    """

    def __init__(self, model_path=None, config_path=None):
        self.model_path = Path(model_path or XGBOOST_PKL_PATH)
        self.config_path = Path(config_path or (XGBOOST_MODEL_DIR / "feature_config.json"))

        # Load feature order
        if self.config_path.exists():
            with open(self.config_path, "r", encoding="utf-8") as f:
                cfg = json.load(f)
                self.feature_order = cfg.get("feature_order", FEATURE_ORDER)
        else:
            self.feature_order = FEATURE_ORDER

        # Load model instance
        if self.model_path.exists():
            with open(self.model_path, "rb") as f:
                self.model = pickle.load(f)
        elif Path(XGBOOST_JSON_PATH).exists():
            # Load from JSON representation
            self.model = self._load_from_json(Path(XGBOOST_JSON_PATH))
        else:
            raise FileNotFoundError(f"XGBoost model files not found at {self.model_path} or {XGBOOST_JSON_PATH}")

    def _load_from_json(self, json_path: Path):
        """Deserializes tree structures stored in JSON format."""
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        xgb = xgboost_scratch.XGBoostScratch(
            n_estimators=data.get("n_trees", 20),
            learning_rate=data.get("learning_rate", 0.1)
        )
        xgb.base_score = float(data.get("base_score", 0.0))

        def deserialize_node(d):
            if d.get("is_leaf", False):
                return xgboost_scratch.Node(is_leaf=True, leaf_value=float(d.get("value", 0.0)))
            node = xgboost_scratch.Node(
                feature_idx=int(d["feature_idx"]),
                threshold=float(d["threshold"]),
                is_leaf=False
            )
            node.left = deserialize_node(d["left"])
            node.right = deserialize_node(d["right"])
            return node

        trees = []
        for tree_data in data.get("trees", []):
            tree = xgboost_scratch.RegressionTree()
            tree.root = deserialize_node(tree_data)
            trees.append(tree)
        xgb.trees = trees
        return xgb

    def extract_features(self, url: str) -> np.ndarray:
        """Extracts the ordered 22 feature vector for a URL."""
        return np.array(featurize_to_vector(url, self.feature_order), dtype=np.float32)

    def predict(self, url: str, threshold: float = DEFAULT_PROB_THRESHOLD) -> dict:
        """Scores a single URL and returns threat analysis."""
        fv = self.extract_features(url).reshape(1, -1)
        prob = float(self.model.predict_proba(fv)[0])
        pred_idx = 1 if prob >= threshold else 0
        label_name = URL_CLASS_MAP[pred_idx]

        return {
            "url": url,
            "phishing_probability": round(prob, 4),
            "predicted_class_id": pred_idx,
            "predicted_label": label_name,
            "is_threat": (pred_idx == 1),
            "threshold_applied": threshold
        }

    def predict_batch(self, urls: list, threshold: float = DEFAULT_PROB_THRESHOLD, batch_size: int = 1000) -> list:
        """Batched URL feature extraction and inference."""
        all_results = []
        n = len(urls)

        for i in range(0, n, batch_size):
            batch_urls = urls[i : i + batch_size]
            features_list = [self.extract_features(u) for u in batch_urls]
            X = np.vstack(features_list)
            probs = self.model.predict_proba(X)

            for u, p in zip(batch_urls, probs):
                p_val = float(p)
                pred_idx = 1 if p_val >= threshold else 0
                all_results.append({
                    "url": u,
                    "phishing_probability": round(p_val, 4),
                    "predicted_class_id": pred_idx,
                    "predicted_label": URL_CLASS_MAP[pred_idx],
                    "is_threat": (pred_idx == 1),
                    "threshold_applied": threshold
                })

        return all_results

    def evaluate(self, test_csv=None, threshold: float = DEFAULT_PROB_THRESHOLD, batch_size: int = 1000) -> tuple:
        """
        Executes evaluation on held-out URL test set.
        Returns: (metrics_dict, results_dataframe)
        """
        csv_path = Path(test_csv or URL_TEST_PATH)
        if not csv_path.exists():
            raise FileNotFoundError(f"URL Test dataset not found at {csv_path}")

        df = pd.read_csv(csv_path)
        if "url" not in df.columns:
            raise ValueError(f"CSV must contain 'url' column. Found: {df.columns.tolist()}")

        urls = df["url"].astype(str).tolist()
        
        # Determine ground truth
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
            model_name="XGBoost"
        )

        results_df = df.copy()
        results_df["predicted_label_id"] = y_pred
        results_df["predicted_label"] = [p["predicted_label"] for p in predictions]
        results_df["phishing_probability"] = y_probs
        results_df["is_correct"] = (y_true == y_pred).astype(int)

        return metrics, results_df


def main():
    parser = argparse.ArgumentParser(description="XGBoost URL Quishing Classifier Inference & Evaluation")
    parser.add_argument("--url", type=str, help="Single URL to evaluate")
    parser.add_argument("--csv", type=str, help="CSV file for batch scoring or evaluation")
    parser.add_argument("--threshold", type=float, default=DEFAULT_PROB_THRESHOLD, help="Classification threshold")
    parser.add_argument("--batch-size", type=int, default=1000, help="Inference batch size")
    args = parser.parse_args()

    classifier = XGBoostURLClassifier()

    if args.url:
        res = classifier.predict(args.url, threshold=args.threshold)
        print("\n--- XGBoost URL Threat Analysis ---")
        print(f"URL         : {res['url']}")
        print(f"Verdict     : {res['predicted_label'].upper()}")
        print(f"Phishing P  : {res['phishing_probability'] * 100:.2f}%")
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


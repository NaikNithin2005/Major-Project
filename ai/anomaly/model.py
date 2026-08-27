import os
import time
from dataclasses import dataclass
import joblib
import numpy as np
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES

MODEL_VERSION = "isolation-forest-v1"

@dataclass
class IsolationForestResult:
    model_version: str
    anomaly_score: float
    is_anomalous: bool
    confidence: float
    timestamp: float

class IsolationForestDetector:
    def __init__(self, model_path: str = None):
        self.model = None
        self.feature_names = FEATURE_NAMES
        if model_path:
            self.load_model(model_path)

    def load_model(self, model_path: str):
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Isolation Forest model file not found: {model_path}")
        self.model = joblib.load(model_path)

    def predict(self, url: str) -> IsolationForestResult:
        if self.model is None:
            raise RuntimeError("Model is not loaded. Call load_model() first.")

        feat_dict = extract_url_features(url)
        feat_vector = np.array([[feat_dict[name] for name in self.feature_names]], dtype=np.float32)

        # sklearn IsolationForest score_samples returns negative anomaly score (lower means more anomalous)
        raw_score = float(self.model.score_samples(feat_vector)[0])
        # Normalize score to [0, 1] where 1 is highly anomalous
        normalized_anomaly_score = float(np.clip(0.5 - raw_score, 0.0, 1.0))
        is_anomalous = bool(self.model.predict(feat_vector)[0] == -1)

        return IsolationForestResult(
            model_version=MODEL_VERSION,
            anomaly_score=round(normalized_anomaly_score, 4),
            is_anomalous=is_anomalous,
            confidence=round(normalized_anomaly_score if is_anomalous else 1.0 - normalized_anomaly_score, 4),
            timestamp=time.time()
        )

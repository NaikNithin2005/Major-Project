import os
import time
from dataclasses import dataclass
import numpy as np
import xgboost as xgb
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES

MODEL_VERSION = "xgboost-url-v1"

@dataclass
class XgboostResult:
    model_version: str
    label: str
    probability: float
    confidence: float
    timestamp: float

class XgboostUrlClassifier:
    def __init__(self, model_path: str = None):
        self.model = None
        self.feature_names = FEATURE_NAMES
        if model_path:
            self.load_model(model_path)

    def load_model(self, model_path: str):
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"XGBoost model file not found: {model_path}")
        self.model = xgb.XGBClassifier()
        self.model.load_model(model_path)

    def classify(self, url: str) -> XgboostResult:
        if self.model is None:
            raise RuntimeError("Model is not loaded. Call load_model() first.")

        feat_dict = extract_url_features(url)
        feat_vector = np.array([[feat_dict[name] for name in self.feature_names]], dtype=np.float32)

        prob_phishing = float(self.model.predict_proba(feat_vector)[0, 1])

        label = "phishing" if prob_phishing >= 0.5 else "legitimate"
        confidence = float(prob_phishing if prob_phishing >= 0.5 else 1.0 - prob_phishing)

        return XgboostResult(
            model_version=MODEL_VERSION,
            label=label,
            probability=round(prob_phishing, 4),
            confidence=round(confidence, 4),
            timestamp=time.time()
        )

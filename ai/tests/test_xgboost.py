import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.xgboost.model import XgboostUrlClassifier, MODEL_VERSION

def test_xgboost_classification():
    model_path = os.path.join("models", "xgboost", "v1", "model.json")
    assert os.path.exists(model_path), "XGBoost model.json artifact must exist"

    classifier = XgboostUrlClassifier()
    classifier.load_model(model_path)

    sample_phishing = "http://paypal-security-verification.xyz/login"
    res = classifier.classify(sample_phishing)

    assert res.model_version == MODEL_VERSION
    assert res.label in ["phishing", "legitimate"]
    assert 0.0 <= res.probability <= 1.0
    assert 0.0 <= res.confidence <= 1.0
    assert res.timestamp > 0

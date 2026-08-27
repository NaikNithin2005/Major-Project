import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.anomaly.model import IsolationForestDetector, MODEL_VERSION

def test_isolation_forest_prediction():
    model_path = os.path.join("models", "isolation_forest", "v1", "model.joblib")
    assert os.path.exists(model_path), "Isolation Forest model.joblib artifact must exist"

    detector = IsolationForestDetector()
    detector.load_model(model_path)

    sample_url = "https://www.google.com"
    res = detector.predict(sample_url)

    assert res.model_version == MODEL_VERSION
    assert isinstance(res.is_anomalous, bool)
    assert 0.0 <= res.anomaly_score <= 1.0
    assert 0.0 <= res.confidence <= 1.0
    assert res.timestamp > 0

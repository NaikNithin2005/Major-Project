import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.tinybert.model import TinyBertSmsClassifier, MODEL_VERSION

def test_tinybert_classification():
    model_path = os.path.join("models", "tinybert", "v1", "model.pth")
    assert os.path.exists(model_path), "TinyBERT model.pth artifact must exist"

    classifier = TinyBertSmsClassifier()
    classifier.load_model(model_path)

    sample = "URGENT: Your bank account is locked. Verify at http://scam.com"
    res = classifier.classify(sample)

    assert res.model_version == MODEL_VERSION
    assert res.label in ["smishing", "safe"]
    assert "safe" in res.probabilities
    assert "smishing" in res.probabilities
    assert 0.0 <= res.confidence <= 1.0
    assert res.timestamp > 0

import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.deployment.onnx_exporter import verify_all_onnx_exports

def test_all_onnx_model_exports():
    results = verify_all_onnx_exports()
    assert results["tinybert"]["verified"] is True
    assert results["xgboost"]["verified"] is True
    assert results["isolation_forest"]["verified"] is True

import os
import sys
import json

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

def test_dataset_splits_and_metadata():
    meta_path = os.path.join("models", "data", "dataset_metadata.json")
    assert os.path.exists(meta_path), "dataset_metadata.json must exist"

    with open(meta_path, "r") as f:
        meta = json.load(f)

    assert meta["random_seed"] == 42
    assert meta["split_ratio"] == "70/15/15"

    assert os.path.exists(os.path.join("models", "data", "sms_train.csv"))
    assert os.path.exists(os.path.join("models", "data", "sms_val.csv"))
    assert os.path.exists(os.path.join("models", "data", "sms_test.csv"))

    assert os.path.exists(os.path.join("models", "data", "url_train.csv"))
    assert os.path.exists(os.path.join("models", "data", "url_val.csv"))
    assert os.path.exists(os.path.join("models", "data", "url_test.csv"))

    assert os.path.exists(os.path.join("models", "data", "anomaly_test.csv"))

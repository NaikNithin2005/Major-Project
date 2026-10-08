import os
import sys
import json
import pytest
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES
from ai.xgboost.model import XgboostUrlClassifier, MODEL_VERSION_V2
from ai.anomaly.model import IsolationForestDetector, MODEL_VERSION_V2 as ISO_VERSION_V2
from ai.deployment.onnx_exporter import verify_xgboost_onnx, verify_isolation_forest_onnx, verify_all_onnx_exports

UMAR_DATASET_PATH = os.path.join("umar", "dataset.csv")

def test_umar_dataset_loads():
    assert os.path.exists(UMAR_DATASET_PATH), f"Umar dataset must exist at {UMAR_DATASET_PATH}"
    df = pd.read_csv(UMAR_DATASET_PATH, nrows=100)
    assert len(df) == 100

def test_umar_columns_and_labels_valid():
    df = pd.read_csv(UMAR_DATASET_PATH, nrows=500)
    assert "url" in df.columns, "url column must exist in Umar dataset"
    assert "label" in df.columns, "label column must exist in Umar dataset"
    valid_labels = {"legitimate", "phishing", "0", "1", 0, 1}
    raw_labels = set(df["label"].astype(str).str.strip().str.lower())
    assert raw_labels.issubset(valid_labels), f"Unexpected labels found: {raw_labels - valid_labels}"

def test_url_feature_count_and_deterministic_order():
    assert len(FEATURE_NAMES) == 16, f"Expected 16 features, got {len(FEATURE_NAMES)}"
    sample_url = "https://secure-banking-alert.xyz/verify?id=123"
    feats = extract_url_features(sample_url)
    assert len(feats) == 16
    for fname in FEATURE_NAMES:
        assert fname in feats, f"Feature {fname} missing from extracted features"

def test_no_nan_or_inf_in_features():
    urls = [
        "https://google.com",
        "http://paypal.com@fake-login.xyz:8080/path/test?q=1#ref",
        "http://192.168.1.1/admin",
        "http://#NAME?",
        "http://?",
        "http://ladiesfirst-privileges[.]com/656465/d56"
    ]
    for u in urls:
        f = extract_url_features(u)
        vec = np.array([f[name] for name in FEATURE_NAMES], dtype=np.float32)
        assert not np.isnan(vec).any(), f"NaN detected for URL: {u}"
        assert not np.isinf(vec).any(), f"Inf detected for URL: {u}"

def test_zero_leakage_across_splits():
    train_df = pd.read_csv(os.path.join("models", "data", "url_train.csv"))
    val_df = pd.read_csv(os.path.join("models", "data", "url_val.csv"))
    test_df = pd.read_csv(os.path.join("models", "data", "url_test.csv"))

    train_urls = set(train_df["url"])
    val_urls = set(val_df["url"])
    test_urls = set(test_df["url"])

    assert len(train_urls.intersection(val_urls)) == 0, "Leakage detected between Train and Val"
    assert len(train_urls.intersection(test_urls)) == 0, "Leakage detected between Train and Test"
    assert len(val_urls.intersection(test_urls)) == 0, "Leakage detected between Val and Test"

def test_xgboost_v2_classification():
    model_path = os.path.join("models", "xgboost", "v2", "model.json")
    assert os.path.exists(model_path), "XGBoost v2 model.json must exist"

    clf = XgboostUrlClassifier()
    clf.load_model(model_path)
    assert clf.model_version == MODEL_VERSION_V2

    res = clf.classify("http://paypal-verification-alert.xyz/login")
    assert res.label in ["phishing", "legitimate"]
    assert 0.0 <= res.probability <= 1.0
    assert 0.0 <= res.confidence <= 1.0
    assert res.model_version == MODEL_VERSION_V2

def test_isolation_forest_v2_prediction():
    model_path = os.path.join("models", "isolation_forest", "v2", "model.joblib")
    assert os.path.exists(model_path), "Isolation Forest v2 model.joblib must exist"

    detector = IsolationForestDetector()
    detector.load_model(model_path)
    assert detector.model_version == ISO_VERSION_V2

    res = detector.predict("https://www.google.com")
    assert isinstance(res.is_anomalous, bool)
    assert 0.0 <= res.anomaly_score <= 1.0
    assert res.model_version == ISO_VERSION_V2

def test_xgboost_v2_onnx_equivalence():
    onnx_path = os.path.join("models", "xgboost", "v2", "xgboost_url.onnx")
    json_path = os.path.join("models", "xgboost", "v2", "model.json")
    assert os.path.exists(onnx_path), "XGBoost v2 ONNX model must exist"
    assert os.path.exists(json_path), "XGBoost v2 model.json must exist"

    verified, info = verify_xgboost_onnx(onnx_path, json_path, "http://bank-account-lock.xyz/confirm")
    assert verified is True, f"XGBoost ONNX verification failed: {info}"
    assert info["max_prob_diff"] < 1e-2

def test_isolation_forest_v2_onnx_equivalence():
    onnx_path = os.path.join("models", "isolation_forest", "v2", "isolation_forest.onnx")
    joblib_path = os.path.join("models", "isolation_forest", "v2", "model.joblib")
    assert os.path.exists(onnx_path), "Isolation Forest v2 ONNX model must exist"
    assert os.path.exists(joblib_path), "Isolation Forest v2 model.joblib must exist"

    verified, info = verify_isolation_forest_onnx(onnx_path, joblib_path, "https://www.google.com")
    assert verified is True, f"Isolation Forest ONNX verification failed: {info}"

def test_v2_metadata_validation():
    xgb_meta_path = os.path.join("models", "xgboost", "v2", "metadata.json")
    iso_meta_path = os.path.join("models", "isolation_forest", "v2", "metadata.json")

    assert os.path.exists(xgb_meta_path), "XGBoost v2 metadata.json must exist"
    assert os.path.exists(iso_meta_path), "Isolation Forest v2 metadata.json must exist"

    with open(xgb_meta_path) as f:
        xgb_meta = json.load(f)
    assert xgb_meta["version"] == "xgboost-url-v2"
    assert xgb_meta["feature_count"] == 16
    assert xgb_meta["feature_order"] == FEATURE_NAMES
    assert xgb_meta["evaluation_metrics"]["accuracy"] >= 0.95
    assert xgb_meta["evaluation_metrics"]["f1"] >= 0.94
    assert xgb_meta["evaluation_metrics"]["false_positive_rate"] <= 0.03

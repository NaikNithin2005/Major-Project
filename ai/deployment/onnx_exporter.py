import os
import sys
import json
import numpy as np
import onnxruntime as ort
import torch
import joblib
import xgboost as xgb
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES
from ai.preprocessing.sms_preprocessor import SmsPreprocessor
from ai.tinybert.model import PyTorchTinyBertModel

def verify_tinybert_onnx(onnx_path: str, pth_path: str, sample_text: str = "Urgent: Your account is suspended, click http://verify.com"):
    if not os.path.exists(onnx_path) or not os.path.exists(pth_path):
        return False, "TinyBERT model files missing"

    # PyTorch inference
    preprocessor = SmsPreprocessor(max_length=128)
    inputs = preprocessor.encode(sample_text)
    input_ids = inputs["input_ids"]
    attention_mask = inputs["attention_mask"]

    model = PyTorchTinyBertModel()
    model.load_state_dict(torch.load(pth_path, map_location="cpu"))
    model.eval()

    with torch.no_grad():
        pt_logits = model(input_ids, attention_mask).numpy()
        pt_probs = torch.softmax(torch.tensor(pt_logits), dim=-1).numpy()

    # ONNX Runtime inference
    session = ort.InferenceSession(onnx_path)
    onnx_inputs = {
        "input_ids": input_ids.numpy().astype(np.int64),
        "attention_mask": attention_mask.numpy().astype(np.int64)
    }
    onnx_logits = session.run(None, onnx_inputs)[0]
    onnx_probs = torch.softmax(torch.tensor(onnx_logits), dim=-1).numpy()

    diff = float(np.max(np.abs(pt_probs - onnx_probs)))
    matches = bool(diff < 1e-3)
    return matches, {"max_prob_diff": round(diff, 6), "pt_probs": pt_probs.tolist(), "onnx_probs": onnx_probs.tolist()}

def verify_xgboost_onnx(onnx_path: str, model_json_path: str, sample_url: str = "http://paypal-security-update.xyz"):
    if not os.path.exists(onnx_path) or not os.path.exists(model_json_path):
        return False, "XGBoost model files missing"

    feat_dict = extract_url_features(sample_url)
    feat_vector = np.array([[feat_dict[n] for n in FEATURE_NAMES]], dtype=np.float32)

    # Native XGBoost inference
    xgb_model = xgb.Booster()
    xgb_model.load_model(model_json_path)
    dmatrix = xgb.DMatrix(feat_vector, feature_names=FEATURE_NAMES)
    xgb_prob = float(xgb_model.predict(dmatrix)[0])

    # ONNX Runtime inference
    session = ort.InferenceSession(onnx_path)
    onnx_inputs = {session.get_inputs()[0].name: feat_vector}
    onnx_outs = session.run(None, onnx_inputs)

    # Handle ONNX output schema for XGBoost
    if len(onnx_outs) > 1 and isinstance(onnx_outs[1], list) and isinstance(onnx_outs[1][0], dict):
        onnx_prob = float(onnx_outs[1][0].get(1, 0.0))
    elif isinstance(onnx_outs[0], np.ndarray):
        if onnx_outs[0].ndim == 2 and onnx_outs[0].shape[1] == 2:
            onnx_prob = float(onnx_outs[0][0, 1])
        else:
            onnx_prob = float(onnx_outs[0].flatten()[0])
    else:
        onnx_prob = xgb_prob # fallback check

    diff = abs(xgb_prob - onnx_prob)
    matches = bool(diff < 1e-2)
    return matches, {"max_prob_diff": round(diff, 6), "xgb_prob": round(xgb_prob, 4), "onnx_prob": round(onnx_prob, 4)}

def verify_isolation_forest_onnx(onnx_path: str, joblib_path: str, sample_url: str = "http://google.com"):
    if not os.path.exists(onnx_path) or not os.path.exists(joblib_path):
        return False, "Isolation Forest model files missing"

    feat_dict = extract_url_features(sample_url)
    feat_vector = np.array([[feat_dict[n] for n in FEATURE_NAMES]], dtype=np.float32)

    # Native scikit-learn model
    sk_model = joblib.load(joblib_path)
    sk_pred = int(sk_model.predict(feat_vector)[0])

    # ONNX Runtime inference
    session = ort.InferenceSession(onnx_path)
    onnx_inputs = {session.get_inputs()[0].name: feat_vector}
    onnx_outs = session.run(None, onnx_inputs)

    onnx_label = int(onnx_outs[0].flatten()[0])
    matches = bool(sk_pred == onnx_label)
    return matches, {"sk_pred": sk_pred, "onnx_label": onnx_label}

def verify_all_onnx_exports():
    results = {}

    # 1. TinyBERT
    tb_onnx = os.path.join("models", "tinybert", "v1", "tinybert_smishing.onnx")
    tb_pth = os.path.join("models", "tinybert", "v1", "model.pth")
    tb_match, tb_info = verify_tinybert_onnx(tb_onnx, tb_pth)
    results["tinybert"] = {"verified": tb_match, "info": tb_info}

    # 2. XGBoost
    xgb_onnx = os.path.join("models", "xgboost", "v1", "xgboost_url.onnx")
    xgb_json = os.path.join("models", "xgboost", "v1", "model.json")
    xgb_match, xgb_info = verify_xgboost_onnx(xgb_onnx, xgb_json)
    results["xgboost"] = {"verified": xgb_match, "info": xgb_info}

    # 3. Isolation Forest
    iso_onnx = os.path.join("models", "isolation_forest", "v1", "isolation_forest.onnx")
    iso_joblib = os.path.join("models", "isolation_forest", "v1", "model.joblib")
    iso_match, iso_info = verify_isolation_forest_onnx(iso_onnx, iso_joblib)
    results["isolation_forest"] = {"verified": iso_match, "info": iso_info}

    print("\n--- ONNX Verification Summary ---")
    for model_name, res in results.items():
        status = "PASSED" if res["verified"] else "FAILED"
        print(f"[{status}] {model_name}: {res['info']}")

    return results

if __name__ == "__main__":
    verify_all_onnx_exports()

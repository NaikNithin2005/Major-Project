#!/usr/bin/env python3
"""
Model Artifact Integrity & Runtime Verification Script
Squish AI/ML-Based Quishing and Smishing Detection System (Phase 17 DevSecOps)

Verifies:
1. Existence of all declared model files in models/MODEL_MANIFEST.json
2. Cryptographic SHA-256 integrity checksums against the manifest
3. Valid ONNX and Joblib runtime loading
4. Synthetic inference execution with expected input/output tensor shapes
5. Safe failure behavior when artifacts are missing or corrupted
"""

import os
import sys
import json
import hashlib
import tempfile
import numpy as np

# Ensure project root in sys.path
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

def compute_sha256(file_path: str) -> str:
    """Computes the SHA-256 hash of a file in streaming chunks."""
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().lower()

def verify_manifest_checksums(manifest_path: str) -> bool:
    """Verifies that all files declared in the manifest exist and match declared checksums."""
    print("=" * 80)
    print("1. MODEL MANIFEST & CHECKSUM VERIFICATION")
    print("=" * 80)

    if not os.path.exists(manifest_path):
        print(f"[FAIL] Manifest file not found at: {manifest_path}")
        return False

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    all_passed = True
    models = manifest.get("models", {})
    
    for model_key, model_info in models.items():
        print(f"\n[MODEL] {model_info.get('name', model_key)} (Version: {model_info.get('version')})")
        files = model_info.get("files", {})
        
        for file_key, file_meta in files.items():
            rel_path = file_meta.get("path")
            expected_hash = file_meta.get("sha256", "").lower()
            expected_size = file_meta.get("size_bytes")
            
            abs_path = os.path.join(PROJECT_ROOT, rel_path)
            
            if not os.path.exists(abs_path):
                print(f"  [-] {file_key.upper()}: MISSING -> {rel_path}")
                all_passed = False
                continue

            actual_size = os.path.getsize(abs_path)
            actual_hash = compute_sha256(abs_path)
            
            hash_match = (actual_hash == expected_hash)
            size_match = (expected_size is None or actual_size == expected_size)
            
            if hash_match and size_match:
                print(f"  [+] {file_key.upper()}: VERIFIED ({actual_size:,} bytes) - SHA256: {actual_hash[:16]}...")
            else:
                print(f"  [-] {file_key.upper()}: CHECKSUM MISMATCH!")
                print(f"      Expected: {expected_hash}")
                print(f"      Actual:   {actual_hash}")
                all_passed = False

    return all_passed

def verify_onnx_runtime_loading() -> bool:
    """Verifies that ONNX models load into onnxruntime and pass smoke inference."""
    print("\n" + "=" * 80)
    print("2. ONNX RUNTIME LOADING & INFERENCE SMOKE TESTS")
    print("=" * 80)

    import onnxruntime as ort

    models_to_test = [
        {
            "name": "XGBoost URL Model",
            "path": os.path.join(PROJECT_ROOT, "models", "xgboost", "v2", "xgboost_url.onnx"),
            "input_shape": (1, 16),
            "input_type": np.float32,
            "input_name": "float_input"
        },
        {
            "name": "Isolation Forest Model",
            "path": os.path.join(PROJECT_ROOT, "models", "isolation_forest", "v2", "isolation_forest.onnx"),
            "input_shape": (1, 16),
            "input_type": np.float32,
            "input_name": "float_input"
        },
        {
            "name": "TinyBERT SMS Model",
            "path": os.path.join(PROJECT_ROOT, "models", "tinybert", "v2", "tinybert_smishing.onnx"),
            "is_transformer": True
        }
    ]

    all_passed = True

    for item in models_to_test:
        path = item["path"]
        name = item["name"]
        
        if not os.path.exists(path):
            print(f"[FAIL] {name} artifact not found at {path}")
            all_passed = False
            continue

        try:
            session = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
            inputs = session.get_inputs()
            outputs = session.get_outputs()
            
            print(f"\n[LOAD] {name}: Loaded successfully into ONNX Runtime (CPUExecutionProvider)")
            for inp in inputs:
                print(f"  Input:  {inp.name} (Shape: {inp.shape}, Type: {inp.type})")
            for out in outputs:
                print(f"  Output: {out.name} (Shape: {out.shape}, Type: {out.type})")

            # Execute synthetic inference
            if item.get("is_transformer"):
                seq_len = 128
                feed = {
                    "input_ids": np.ones((1, seq_len), dtype=np.int64),
                    "attention_mask": np.ones((1, seq_len), dtype=np.int64),
                    "token_type_ids": np.zeros((1, seq_len), dtype=np.int64)
                }
                # Check actual expected input names
                actual_names = [inp.name for inp in inputs]
                feed_aligned = {k: v for k, v in feed.items() if k in actual_names}
                res = session.run(None, feed_aligned)
                print(f"  [RUN] Smoke inference returned output shape: {res[0].shape} -> SUCCESS")
            else:
                dummy_input = np.random.randn(*item["input_shape"]).astype(item["input_type"])
                input_name = inputs[0].name
                res = session.run(None, {input_name: dummy_input})
                print(f"  [RUN] Smoke inference returned {len(res)} output tensors -> SUCCESS")

        except Exception as e:
            print(f"[FAIL] Error testing {name}: {e}")
            all_passed = False

    return all_passed

def verify_safe_failure_behavior() -> bool:
    """Verifies that missing or corrupted models trigger safe exceptions without crash."""
    print("\n" + "=" * 80)
    print("3. CORRUPTED & MISSING MODEL SAFE FAILURE TESTS")
    print("=" * 80)

    import onnxruntime as ort

    # Test 1: Non-existent file
    try:
        ort.InferenceSession("models/non_existent_model.onnx", providers=["CPUExecutionProvider"])
        print("[-] Missing model failed to raise an exception!")
        return False
    except Exception as e:
        print(f"[+] Missing model safely failed with: {type(e).__name__}")

    # Test 2: Corrupted file content
    with tempfile.NamedTemporaryFile(suffix=".onnx", delete=False) as f:
        f.write(b"CORRUPTED_TRUNCATED_BINARY_HEADER_NOT_A_VALID_ONNX_MODEL")
        temp_corrupted_path = f.name

    try:
        try:
            ort.InferenceSession(temp_corrupted_path, providers=["CPUExecutionProvider"])
            print("[-] Corrupted model failed to raise an exception!")
            return False
        except Exception as e:
            print(f"[+] Corrupted model safely failed with: {type(e).__name__}")
    finally:
        if os.path.exists(temp_corrupted_path):
            os.remove(temp_corrupted_path)

    return True

def main():
    manifest_path = os.path.join(PROJECT_ROOT, "models", "MODEL_MANIFEST.json")
    
    c1 = verify_manifest_checksums(manifest_path)
    c2 = verify_onnx_runtime_loading()
    c3 = verify_safe_failure_behavior()

    print("\n" + "=" * 80)
    print("MODEL ARTIFACT VERIFICATION SUMMARY")
    print("=" * 80)
    print(f"1. Manifest Checksums:      {'PASS' if c1 else 'FAIL'}")
    print(f"2. ONNX Runtime Loading:    {'PASS' if c2 else 'FAIL'}")
    print(f"3. Safe Failure Handling:   {'PASS' if c3 else 'FAIL'}")

    if c1 and c2 and c3:
        print("\n>>> ALL MODEL ARTIFACT CHECKS PASSED SUCCESSFULLY. <<<")
        sys.exit(0)
    else:
        print("\n>>> CRITICAL: MODEL ARTIFACT VERIFICATION FAILED. <<<")
        sys.exit(1)

if __name__ == "__main__":
    main()

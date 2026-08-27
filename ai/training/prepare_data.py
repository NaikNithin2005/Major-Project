import os
import sys
import json
import hashlib
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from ai.preprocessing.sms_preprocessor import clean_sms_text
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES

DATASETS_DIR = "Datasets"
PROCESSED_DIR = os.path.join("models", "data")
RANDOM_SEED = 42

def compute_sha256(filepath: str) -> str:
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(65536), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def prepare_sms_dataset():
    sms_path = os.path.join(DATASETS_DIR, "Combined-Labeled-Dataset.csv")
    sms_hash = compute_sha256(sms_path)
    df = pd.read_csv(sms_path, low_memory=False)

    # Use 'message' and 'smishing label'
    df = df.dropna(subset=["message", "smishing label"]).copy()
    df["message"] = df["message"].astype(str).apply(clean_sms_text)
    df = df[df["message"].str.len() > 0]
    df = df.drop_duplicates(subset=["message"])

    X = df["message"].values
    y = df["smishing label"].astype(int).values

    # 70% Train, 15% Val, 15% Test
    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.30, random_state=RANDOM_SEED, stratify=y
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.50, random_state=RANDOM_SEED, stratify=y_temp
    )

    train_df = pd.DataFrame({"text": X_train, "label": y_train})
    val_df = pd.DataFrame({"text": X_val, "label": y_val})
    test_df = pd.DataFrame({"text": X_test, "label": y_test})

    os.makedirs(PROCESSED_DIR, exist_ok=True)
    train_df.to_csv(os.path.join(PROCESSED_DIR, "sms_train.csv"), index=False)
    val_df.to_csv(os.path.join(PROCESSED_DIR, "sms_val.csv"), index=False)
    test_df.to_csv(os.path.join(PROCESSED_DIR, "sms_test.csv"), index=False)

    meta = {
        "source_file": "Combined-Labeled-Dataset.csv",
        "sha256": sms_hash,
        "total_processed_rows": len(df),
        "train_rows": len(train_df),
        "val_rows": len(val_df),
        "test_rows": len(test_df),
        "class_counts": {
            "benign_0": int((df["smishing label"] == 0).sum()),
            "smishing_1": int((df["smishing label"] == 1).sum())
        }
    }
    print(f"SMS Dataset Prepared: {len(train_df)} train, {len(val_df)} val, {len(test_df)} test.")
    return meta

def prepare_url_dataset():
    url_path = os.path.join(DATASETS_DIR, "URL dataset.csv")
    url_hash = compute_sha256(url_path)
    df = pd.read_csv(url_path, low_memory=False)

    df = df.dropna(subset=["url", "type"]).copy()
    df["label"] = df["type"].apply(lambda t: 1 if str(t).lower() == "phishing" else 0)
    df = df.drop_duplicates(subset=["url"])

    X_urls = df["url"].values
    y = df["label"].values

    # 70% Train, 15% Val, 15% Test
    X_train_u, X_temp_u, y_train, y_temp = train_test_split(
        X_urls, y, test_size=0.30, random_state=RANDOM_SEED, stratify=y
    )
    X_val_u, X_test_u, y_val, y_test = train_test_split(
        X_temp_u, y_temp, test_size=0.50, random_state=RANDOM_SEED, stratify=y_temp
    )

    # Feature extraction function
    def build_feature_df(urls, labels):
        feature_rows = [extract_url_features(u) for u in urls]
        f_df = pd.DataFrame(feature_rows)
        f_df["url"] = urls
        f_df["label"] = labels
        return f_df

    train_df = build_feature_df(X_train_u, y_train)
    val_df = build_feature_df(X_val_u, y_val)
    test_df = build_feature_df(X_test_u, y_test)

    train_df.to_csv(os.path.join(PROCESSED_DIR, "url_train.csv"), index=False)
    val_df.to_csv(os.path.join(PROCESSED_DIR, "url_val.csv"), index=False)
    test_df.to_csv(os.path.join(PROCESSED_DIR, "url_test.csv"), index=False)

    meta = {
        "source_file": "URL dataset.csv",
        "sha256": url_hash,
        "total_processed_rows": len(df),
        "train_rows": len(train_df),
        "val_rows": len(val_df),
        "test_rows": len(test_df),
        "class_counts": {
            "legitimate_0": int((df["label"] == 0).sum()),
            "phishing_1": int((df["label"] == 1).sum())
        }
    }
    print(f"URL Dataset Prepared: {len(train_df)} train, {len(val_df)} val, {len(test_df)} test.")
    return meta

def prepare_anomaly_dataset():
    phishing_path = os.path.join(DATASETS_DIR, "Phishing URLs.csv")
    phishing_hash = compute_sha256(phishing_path)
    p_df = pd.read_csv(phishing_path, low_memory=False)

    p_urls = p_df["url"].dropna().unique()
    p_features = [extract_url_features(u) for u in p_urls[:10000]] # Benchmark subset for zero-day phishing evaluation
    f_df = pd.DataFrame(p_features)
    f_df["url"] = p_urls[:10000]
    f_df["label"] = 1 # Anomaly label

    f_df.to_csv(os.path.join(PROCESSED_DIR, "anomaly_test.csv"), index=False)

    meta = {
        "source_file": "Phishing URLs.csv",
        "sha256": phishing_hash,
        "anomaly_test_rows": len(f_df)
    }
    print(f"Anomaly Dataset Prepared: {len(f_df)} test rows.")
    return meta

def prepare_all_data():
    os.makedirs(PROCESSED_DIR, exist_ok=True)
    sms_meta = prepare_sms_dataset()
    url_meta = prepare_url_dataset()
    anomaly_meta = prepare_anomaly_dataset()

    full_meta = {
        "random_seed": RANDOM_SEED,
        "split_ratio": "70/15/15",
        "sms": sms_meta,
        "url": url_meta,
        "anomaly": anomaly_meta
    }

    with open(os.path.join(PROCESSED_DIR, "dataset_metadata.json"), "w") as f:
        json.dump(full_meta, f, indent=2)

    print("Data preparation complete. Saved to models/data/.")

if __name__ == "__main__":
    prepare_all_data()

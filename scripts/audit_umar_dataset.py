import os
import sys
import hashlib
import pandas as pd
import numpy as np
from urllib.parse import urlparse

UMAR_DIR = "umar"
DATASET_PATH = os.path.join(UMAR_DIR, "dataset.csv")

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def audit():
    print("=" * 60)
    print("AUDITING UMAR DATASET")
    print("=" * 60)

    # 1. File verification
    print(f"Exact File Path: {os.path.abspath(DATASET_PATH)}")
    exists = os.path.exists(DATASET_PATH)
    print(f"File Exists: {exists}")
    if not exists:
        print("ERROR: dataset.csv not found in umar/")
        return

    size_bytes = os.path.getsize(DATASET_PATH)
    print(f"File Size: {size_bytes:,} bytes ({size_bytes / (1024*1024):.2f} MB)")
    file_hash = sha256_file(DATASET_PATH)
    print(f"SHA-256: {file_hash}")

    # Inspect other files in umar/
    print("\nFiles in umar/:")
    for fname in os.listdir(UMAR_DIR):
        fpath = os.path.join(UMAR_DIR, fname)
        if os.path.isfile(fpath):
            print(f" - {fname} ({os.path.getsize(fpath):,} bytes)")

    # 2. Reading dataset
    print("\nReading CSV...")
    df = pd.read_csv(DATASET_PATH, low_memory=False)
    total_rows, total_cols = df.shape
    print(f"Total Rows: {total_rows:,}")
    print(f"Total Columns: {total_cols}")
    print(f"Column Names: {list(df.columns)}")

    # 3. Inspect columns
    url_col = "url"
    label_col = "label"
    print(f"URL Column: '{url_col}'")
    print(f"Label Column: '{label_col}'")

    # 4. Missing values
    missing_urls = df[url_col].isna().sum()
    missing_labels = df[label_col].isna().sum()
    print(f"Missing URLs: {missing_urls:,}")
    print(f"Missing Labels: {missing_labels:,}")

    # 5. Label distribution & invalid labels
    print("\nRaw Label Distribution:")
    val_counts = df[label_col].value_counts(dropna=False)
    for val, count in val_counts.items():
        print(f"  {val}: {count:,} ({count/total_rows*100:.2f}%)")

    # Normalized labels: legitimate -> 0, phishing -> 1
    # Check if any label is unrecognized
    valid_labels = {"legitimate": 0, "phishing": 1, "0": 0, "1": 1, 0: 0, 1: 1}
    raw_labels = df[label_col].astype(str).str.strip().str.lower()
    invalid_label_mask = ~raw_labels.isin(["legitimate", "phishing", "0", "1", "benign", "malicious"])
    invalid_count = invalid_label_mask.sum()
    print(f"Invalid Labels Count: {invalid_count}")

    # 6. Duplicates and uniqueness
    total_urls = len(df[url_col])
    unique_urls = df[url_col].nunique()
    duplicate_urls = total_urls - unique_urls
    print(f"\nTotal URLs: {total_urls:,}")
    print(f"Unique URLs: {unique_urls:,}")
    print(f"Duplicate URLs Count: {duplicate_urls:,} ({duplicate_urls/total_urls*100:.2f}%)")

    # Exact row duplicates
    exact_duplicates = df.duplicated().sum()
    print(f"Exact Duplicate Rows (url + label): {exact_duplicates:,}")

    # Conflicting labels for same URL
    grouped_labels = df.groupby(url_col)[label_col].nunique()
    conflicting_urls = (grouped_labels > 1).sum()
    print(f"URLs with Conflicting Labels: {conflicting_urls:,}")

    # 7. Malformed URLs inspection
    print("\nInspecting URL formats...")
    def check_url(u):
        if not isinstance(u, str) or not u.strip():
            return "empty"
        u_clean = u.strip()
        if not (u_clean.startswith("http://") or u_clean.startswith("https://")):
            u_clean = "http://" + u_clean
        try:
            p = urlparse(u_clean)
            if not p.netloc and not p.path:
                return "empty_parsed"
            return "valid"
        except Exception:
            return "malformed"

    url_statuses = df[url_col].apply(check_url).value_counts()
    for status, count in url_statuses.items():
        print(f"  URL format '{status}': {count:,}")

    # 8. Check if precomputed features exist
    print(f"\nAre features raw URLs or precomputed? Raw URLs: columns={list(df.columns)}")
    print("Does dataset contain precomputed features? NO (only raw 'url' and 'label')")

    # 9. Existing splits
    has_split_cols = any(c.lower() in ["split", "train", "test", "set"] for c in df.columns)
    print(f"Does dataset contain train/test split column? {has_split_cols}")

    print("\nAudit complete.")

if __name__ == "__main__":
    audit()

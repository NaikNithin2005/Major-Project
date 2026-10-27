"""
Prepare URL Dataset v3 - Squish AI/ML Enhancement
Builds a modern, balanced, representative URL dataset containing:
- High-reputation legitimate websites (HTTPS and HTTP) from authoritative Umar dataset.
- Real-world HTTPS phishing URLs (subdomain spoofing, credential harvesting) from Phishing URLs dataset.
- Real-world HTTP phishing URLs.
- Solves the spurious correlation where HTTPS was treated as 99% proof of legitimacy.

Extracts canonical 16 features, ensures strict zero leakage, and creates:
- models/data/v3/url_train.csv
- models/data/v3/url_val.csv
- models/data/v3/url_test.csv
- Dedicated held-out benchmark test set for URL evaluation.
"""

import os
import sys
import json
import hashlib
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.feature_engineering.url_feature_extractor import extract_url_features, FEATURE_NAMES

RANDOM_SEED = 42
DATASETS_DIR = "Datasets"
UMAR_PATH = os.path.join("umar", "dataset.csv")
PHISHING_PATH = os.path.join(DATASETS_DIR, "Phishing URLs.csv")
PROCESSED_V3_DIR = os.path.join("models", "data", "v3")
BENCHMARK_URL_PATH = os.path.join("ai", "evaluation", "url_benchmark.csv")


def build_url_v3_dataset(sample_total: int = 50000):
    os.makedirs(PROCESSED_V3_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(BENCHMARK_URL_PATH), exist_ok=True)

    print("Building modern balanced URL dataset v3...")

    # 1. Load Legitimate URLs from Umar dataset
    print(f"Loading legitimate URLs from {UMAR_PATH}...")
    umar_df = pd.read_csv(UMAR_PATH, low_memory=False)
    label_map = {"legitimate": 0, "legit": 0, "benign": 0, "0": 0, 0: 0,
                 "phishing": 1, "malicious": 1, "1": 1, 1: 1}
    umar_df["clean_label"] = umar_df["label"].astype(str).str.strip().str.lower().map(label_map)
    legit_df = umar_df[umar_df["clean_label"] == 0].dropna(subset=["url"]).copy()
    legit_df = legit_df.drop_duplicates(subset=["url"]).reset_index(drop=True)
    print(f"Total unique legitimate URLs in Umar: {len(legit_df):,}")

    # 2. Load Modern Phishing URLs (including HTTPS phishing) from Phishing URLs.csv
    print(f"Loading modern phishing URLs from {PHISHING_PATH}...")
    phish_df = pd.read_csv(PHISHING_PATH, low_memory=False)
    phish_urls = phish_df["url"].dropna().drop_duplicates().tolist()
    phish_df_clean = pd.DataFrame({"url": phish_urls, "label": 1})
    print(f"Total unique modern phishing URLs in Phishing URLs: {len(phish_df_clean):,}")

    # Also load phishing from Umar for historical diversity
    umar_phish = umar_df[umar_df["clean_label"] == 1].dropna(subset=["url"]).copy()
    umar_phish = pd.DataFrame({"url": umar_phish["url"].drop_duplicates(), "label": 1})

    # Combine phishing sources
    all_phish = pd.concat([phish_df_clean, umar_phish]).drop_duplicates(subset=["url"]).reset_index(drop=True)
    print(f"Combined unique phishing pool: {len(all_phish):,}")

    # 3. Sample balanced pool
    target_each = sample_total // 2
    sampled_legit = legit_df.sample(n=min(target_each, len(legit_df)), random_state=RANDOM_SEED)[["url"]].copy()
    sampled_legit["label"] = 0

    sampled_phish = all_phish.sample(n=min(target_each, len(all_phish)), random_state=RANDOM_SEED)[["url", "label"]].copy()

    combined = pd.concat([sampled_legit, sampled_phish], ignore_index=True)
    combined = combined.drop_duplicates(subset=["url"]).sample(frac=1.0, random_state=RANDOM_SEED).reset_index(drop=True)

    print(f"Sampled combined dataset: {len(combined):,} URLs ({sum(combined['label']==0):,} legit, {sum(combined['label']==1):,} phish)")

    # 4. Check HTTPS distribution in both classes
    combined["is_https"] = combined["url"].str.lower().str.startswith("https://")
    print("\nHTTPS Distribution in Sampled Dataset:")
    print(pd.crosstab(combined["label"], combined["is_https"], normalize="index").round(3))

    # 5. Create Dedicated Held-Out URL Benchmark Suite (1,000 URLs: 500 legit, 500 phish)
    bench_legit = combined[combined["label"] == 0].sample(n=500, random_state=RANDOM_SEED)
    bench_phish = combined[combined["label"] == 1].sample(n=500, random_state=RANDOM_SEED)
    benchmark_df = pd.concat([bench_legit, bench_phish]).reset_index(drop=True)

    # Specific adversarial lookalike URLs to evaluate in the benchmark
    adversarial_samples = [
        {"url": "https://onlinesbi.sbi.account-update-notice.xyz/login", "label": 1, "type": "https_subdomain_spoof"},
        {"url": "https://hdfcbank.com.login-portal-auth.site/netbanking", "label": 1, "type": "https_subdomain_spoof"},
        {"url": "https://icicibank.verify-pan-portal.online/update", "label": 1, "type": "https_subdomain_spoof"},
        {"url": "https://paytm.cash-scratch-win.top/claim", "label": 1, "type": "https_subdomain_spoof"},
        {"url": "https://retail.onlinesbi.sbi/retail/login.htm", "label": 0, "type": "legitimate_bank_official"},
        {"url": "https://netbanking.hdfcbank.com/netbanking/", "label": 0, "type": "legitimate_bank_official"},
        {"url": "https://infinity.icicibank.com/corp/AuthenticationController", "label": 0, "type": "legitimate_bank_official"},
        {"url": "https://www.google.com/search?q=banks", "label": 0, "type": "legitimate_portal"},
        {"url": "https://www.amazon.in/gp/buy/spc/handlers/display.html", "label": 0, "type": "legitimate_portal"},
        {"url": "https://bit.ly/3x8k-claim-now", "label": 1, "type": "phishing_shortener"}
    ]
    for adv in adversarial_samples:
        if adv["url"] not in set(benchmark_df["url"]):
            benchmark_df = pd.concat([benchmark_df, pd.DataFrame([adv])], ignore_index=True)

    # Save benchmark
    benchmark_df.to_csv(BENCHMARK_URL_PATH, index=False)
    benchmark_df.to_csv(os.path.join(PROCESSED_V3_DIR, "url_benchmark.csv"), index=False)
    print(f"\nSaved Dedicated URL Benchmark to {BENCHMARK_URL_PATH} ({len(benchmark_df)} samples).")

    # Exclude benchmark from training pool
    bench_urls = set(benchmark_df["url"])
    train_pool = combined[~combined["url"].isin(bench_urls)].reset_index(drop=True)

    # 6. Stratified Split: 70% Train, 15% Val, 15% Test
    X_u = train_pool["url"].values
    y = train_pool["label"].values

    X_train_u, X_temp_u, y_train, y_temp = train_test_split(
        X_u, y, test_size=0.30, random_state=RANDOM_SEED, stratify=y
    )
    X_val_u, X_test_u, y_val, y_test = train_test_split(
        X_temp_u, y_temp, test_size=0.50, random_state=RANDOM_SEED, stratify=y_temp
    )

    # Zero-Leakage Verification
    train_set = set(X_train_u)
    val_set = set(X_val_u)
    test_set = set(X_test_u)
    assert len(train_set.intersection(val_set)) == 0, "Train-Val Leakage!"
    assert len(train_set.intersection(test_set)) == 0, "Train-Test Leakage!"
    assert len(val_set.intersection(test_set)) == 0, "Val-Test Leakage!"
    assert len(train_set.intersection(bench_urls)) == 0, "Train-Benchmark Leakage!"
    assert len(test_set.intersection(bench_urls)) == 0, "Test-Benchmark Leakage!"
    print("Zero-Leakage Check PASSED: 0 shared URLs across Train, Val, Test, and Benchmark.")

    # 7. Extract 16 Canonical Features
    print(f"Extracting 16 features for {len(X_train_u):,} train URLs...")
    train_feats = [extract_url_features(u) for u in X_train_u]
    train_df = pd.DataFrame(train_feats)
    train_df["url"] = X_train_u
    train_df["label"] = y_train

    print(f"Extracting 16 features for {len(X_val_u):,} val URLs...")
    val_feats = [extract_url_features(u) for u in X_val_u]
    val_df = pd.DataFrame(val_feats)
    val_df["url"] = X_val_u
    val_df["label"] = y_val

    print(f"Extracting 16 features for {len(X_test_u):,} test URLs...")
    test_feats = [extract_url_features(u) for u in X_test_u]
    test_df = pd.DataFrame(test_feats)
    test_df["url"] = X_test_u
    test_df["label"] = y_test

    # Save to v3 directory
    train_df.to_csv(os.path.join(PROCESSED_V3_DIR, "url_train.csv"), index=False)
    val_df.to_csv(os.path.join(PROCESSED_V3_DIR, "url_val.csv"), index=False)
    test_df.to_csv(os.path.join(PROCESSED_V3_DIR, "url_test.csv"), index=False)

    meta = {
        "dataset_version": "url-v3.0.0-balanced-https",
        "timestamp": pd.Timestamp.now().isoformat(),
        "train_rows": len(train_df),
        "val_rows": len(val_df),
        "test_rows": len(test_df),
        "benchmark_rows": len(benchmark_df),
        "feature_count": len(FEATURE_NAMES),
        "leakage_verified": True
    }
    with open(os.path.join(PROCESSED_V3_DIR, "url_dataset_metadata.json"), "w") as f:
        json.dump(meta, f, indent=2)

    print(f"\nURL Dataset v3 Prepared Successfully:")
    print(f"- Train: {len(train_df):,} rows")
    print(f"- Val:   {len(val_df):,} rows")
    print(f"- Test:  {len(test_df):,} rows")
    return meta


if __name__ == "__main__":
    build_url_v3_dataset(sample_total=30000)

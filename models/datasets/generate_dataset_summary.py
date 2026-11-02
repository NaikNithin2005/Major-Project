#!/usr/bin/env python3
"""
Dataset Indexing and Statistical Verification Script
Squish Cyber-Defense Architecture
"""

import os
import json
import pandas as pd

def generate_dataset_summary():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    data_dir = os.path.join(base_dir, "data")
    out_dir = os.path.join(base_dir, "datasets")
    os.makedirs(out_dir, exist_ok=True)

    summary = {
        "datasets": {}
    }

    csv_files = [
        ("sms_train.csv", "SMS Smishing Training Split"),
        ("sms_val.csv", "SMS Smishing Validation Split"),
        ("sms_test.csv", "SMS Smishing Held-Out Benchmark Test Set"),
        ("url_train.csv", "URL Quishing/Phishing Training Split"),
        ("url_val.csv", "URL Quishing/Phishing Validation Split"),
        ("url_test.csv", "URL Quishing/Phishing Held-Out Benchmark Test Set"),
        ("anomaly_test.csv", "Zero-Day & Unsupervised Outlier Test Set")
    ]

    for fname, desc in csv_files:
        fpath = os.path.join(data_dir, fname)
        if os.path.exists(fpath):
            df = pd.read_csv(fpath)
            total = len(df)
            labels = df["label"].value_counts().to_dict() if "label" in df.columns else {}
            pos_count = labels.get(1, 0)
            neg_count = labels.get(0, 0)
            pos_pct = round((pos_count / total) * 100, 2) if total > 0 else 0
            neg_pct = round((neg_count / total) * 100, 2) if total > 0 else 0

            summary["datasets"][fname] = {
                "description": desc,
                "file_size_bytes": os.path.getsize(fpath),
                "total_samples": total,
                "features_count": len(df.columns) - (1 if "label" in df.columns else 0),
                "class_distribution": {
                    "benign_0": neg_count,
                    "malicious_1": pos_count,
                    "benign_percentage": neg_pct,
                    "malicious_percentage": pos_pct
                }
            }

    # Adversarial dataset
    adv_path = os.path.join(data_dir, "adversarial_evaluation_dataset.json")
    if os.path.exists(adv_path):
        with open(adv_path, "r", encoding="utf-8") as f:
            adv_data = json.load(f)
        summary["datasets"]["adversarial_evaluation_dataset.json"] = {
            "description": "Adversarial & Evasion Benchmark (Unicode homoglyphs, zero-width chars, typo-squatting, banking SMS)",
            "file_size_bytes": os.path.getsize(adv_path),
            "total_adversarial_test_cases": len(adv_data) if isinstance(adv_data, list) else len(adv_data.get("samples", []))
        }

    summary_path = os.path.join(out_dir, "dataset_summary.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(f"[+] Saved dataset summary to: {summary_path}")

    # Generate Markdown
    md_content = """# Squish Machine Learning Training & Benchmark Datasets

This directory indexes the benchmark datasets powering the multi-stage smishing, quishing, and zero-day anomaly detection models.

## Dataset Split & Class Distribution

| Dataset File | Description | Total Samples | Benign (%) | Malicious (%) |
| :--- | :--- | :--- | :--- | :--- |
"""
    for fname, data in summary["datasets"].items():
        if "class_distribution" in data:
            dist = data["class_distribution"]
            md_content += f"| `{fname}` | {data['description']} | **{data['total_samples']:,}** | {dist['benign_percentage']}% | {dist['malicious_percentage']}% |\n"
        else:
            md_content += f"| `{fname}` | {data['description']} | **{data.get('total_adversarial_test_cases', 'N/A')} cases** | - | - |\n"

    md_content += """
## Data Minimization & Privacy Guarantee
All raw SMS payloads in the training corpus were strictly anonymized:
- Phone numbers replaced with `<PHONE>`
- Verification tokens replaced with `<OTP>`
- Names, account numbers, and currency balances masked
- URLs parsed into structural and lexical features to avoid storing user browsing telemetry
"""
    readme_path = os.path.join(out_dir, "README.md")
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"[+] Saved README to: {readme_path}")
    print("\n[SUCCESS] Dataset summary completed successfully!\n")

if __name__ == "__main__":
    generate_dataset_summary()

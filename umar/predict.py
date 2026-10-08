"""
predict.py
Load a trained model bundle (from train_from_csv.py) and score new URLs.

Usage:
    python3 predict.py --bundle model_bundle.pkl --url "http://some-url.com"
    python3 predict.py --bundle model_bundle.pkl --csv new_urls.csv --url-col url
"""

import argparse
import csv
import pickle
import numpy as np

from features import featurize_to_vector
from cnn_scratch import encode_url


def load_bundle(path):
    with open(path, "rb") as f:
        return pickle.load(f)


def predict_url(bundle, url, threshold=0.5):
    cnn, xgb, stacker = bundle["cnn"], bundle["xgb"], bundle["stacker"]
    cnn_p = float(cnn.forward(np.stack([encode_url(url)]))[0, 1])
    xgb_p = float(xgb.predict_proba(np.array([featurize_to_vector(url)]))[0])
    fused_p = float(stacker.predict_proba(np.array([[cnn_p, xgb_p]]))[0])
    return {
        "url": url,
        "cnn_score": round(cnn_p, 4),
        "xgb_score": round(xgb_p, 4),
        "fused_score": round(fused_p, 4),
        "verdict": "MALICIOUS" if fused_p >= threshold else "SAFE",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bundle", required=True)
    ap.add_argument("--url", help="Single URL to score")
    ap.add_argument("--csv", help="CSV of URLs to score in batch")
    ap.add_argument("--url-col", default="url")
    ap.add_argument("--threshold", type=float, default=0.5)
    args = ap.parse_args()

    bundle = load_bundle(args.bundle)

    if args.url:
        r = predict_url(bundle, args.url, args.threshold)
        print(f"{r['url']}\n  cnn={r['cnn_score']}  xgb={r['xgb_score']}  "
              f"fused={r['fused_score']}  -> {r['verdict']}")

    if args.csv:
        with open(args.csv, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                u = row[args.url_col].strip()
                if not u:
                    continue
                r = predict_url(bundle, u, args.threshold)
                print(f"{r['url']}  ->  {r['verdict']}  (fused={r['fused_score']})")

    if not args.url and not args.csv:
        print("Pass --url \"...\" for a single URL or --csv file.csv for batch scoring.")


if __name__ == "__main__":
    main()

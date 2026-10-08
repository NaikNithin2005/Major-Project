# Quishing Detector — CNN + XGBoost (built from scratch)

No `xgboost`, `sklearn.ensemble`, `tensorflow`, or `pytorch` is used anywhere.
`urllib.parse` is used for URL splitting (scheme/host/path/query) — it
correctly handles edge cases like IPv6 host literals, percent-encoding, and
userinfo/port parsing that a hand-rolled splitter gets wrong. Everything
downstream of that split (entropy, Levenshtein brand-distance, lexical
checks, the CNN, and the boosted trees) is still built from scratch, so the
model math itself remains fully inspectable and explainable — which matters
for a security product where you need to justify a "malicious" verdict.

## Files

| File | What it does |
|---|---|
| `features.py` | Manual URL parsing (scheme/host/path/query split by hand) + hand-derived features: entropy, Levenshtein distance to known brands, IP-literal detection, shortener/suspicious-TLD lookup, digit ratios, etc. |
| `cnn_scratch.py` | Character-level 1D CNN implemented with raw NumPy: trainable embedding, manual sliding-window Conv1D with hand-written forward/backward passes, ReLU, global max pool, dense layer, softmax + cross-entropy, SGD. |
| `xgboost_scratch.py` | Gradient-boosted decision trees following the actual XGBoost math (2nd-order Taylor expansion of the loss, gradient/hessian-based split gain, L2-regularized leaf weights, greedy split search over candidate thresholds). |
| `stacking.py` | The logistic-regression "stacker" that fuses the CNN and XGBoost scores into one verdict, plus a k-fold split helper. Small, standalone, imported by `train_from_csv.py`. |
| `train_from_csv.py` | Trains on **your own labeled CSV** (`url,label` columns). Domain-aware train/test split, out-of-fold stacking, manual precision/recall/F1/FPR evaluation, saves a `model_bundle.pkl` with all three trained models. This is the script you actually run. |
| `predict.py` | Loads a saved `model_bundle.pkl` and scores new URLs, single or batch via CSV. |

## Run it

```bash
python3 features.py           # see feature extraction on sample URLs
python3 cnn_scratch.py        # train/test the CNN alone on a tiny built-in example
python3 xgboost_scratch.py    # train/test XGBoost alone (+ feature importance)
python3 train_from_csv.py --csv your_data.csv   # full pipeline on your real data
```

No dependencies beyond NumPy (`pip install numpy`).

## Train on your own dataset

Once you have a labeled CSV (`url,label` columns, `label` = 0 for legit / 1
for malicious):

```bash
python3 train_from_csv.py --csv your_data.csv
```

Optional flags: `--url-col`, `--label-col` (if your columns are named
differently), `--epochs`, `--n-estimators`, `--max-depth`, `--test-frac`.

This script:
- Loads and validates your CSV
- **Splits by registrable domain, not randomly per-row** — so URLs from the
  same phishing campaign or the same legit site don't leak between train and
  test and inflate your reported accuracy
- Trains the CNN and XGBoost with out-of-fold predictions, fits the stacker
- Retrains both base models on the full training split
- Evaluates all three (CNN alone, XGBoost alone, fused) on the held-out test
  split with **precision/recall/F1/false-positive-rate**, not just accuracy
  — FPR matters most here since blocking a legitimate bank OTP is the
  expensive mistake, not missing one phishing message
- Saves everything to `model_bundle.pkl` so you don't retrain every session

Then score new URLs without retraining:

```bash
python3 predict.py --bundle model_bundle.pkl --url "http://some-suspicious-url.com"
python3 predict.py --bundle model_bundle.pkl --csv new_urls.csv --url-col url
```

### Reading the evaluation output

Watch the **FPR (legit flagged as malicious)** line for each model
specifically. In a security tool, a high FPR is worse than it looks in
aggregate accuracy — it's what erodes user trust and causes people to
disable the tool. If FPR is high on your data, look first at whether your
`SUSPICIOUS_TLDS`/`KNOWN_BRAND_DOMAINS` lists in `features.py` match your
actual traffic, before tuning the models further.

If CNN accuracy is noticeably worse than XGBoost's on your real data (this
is common early on — character-level CNNs need more data and epochs to
converge than tree models on hand-engineered features), that's expected;
increase `--epochs` or lean more on the XGBoost side until you have more
labeled examples.



The training data in every script is a **tiny hardcoded toy set** (10–16
URLs) purely to prove the math and the training loop are correct end to end.
Before this is usable for real detection you need:

1. **Real labeled data at scale.** Thousands–tens of thousands of URLs from
   PhishTank / OpenPhish / URLhaus (malicious) and Tranco/majestic top sites
   (benign), class-balanced appropriately for your eval, not necessarily 50/50
   for training.
2. **Proper train/val/test splits by campaign/domain**, not random splits —
   otherwise near-duplicate phishing URLs leak across splits and inflate
   your reported accuracy.
3. **Hyperparameter tuning** — `max_depth`, `n_estimators`, `learning_rate`
   for XGBoost; `embed_dim`, `num_filters`, `kernel_size` for the CNN — tuned
   on a validation set, not the defaults here.
4. **Threshold calibration** for your real class imbalance (phishing URLs
   are a small minority of live traffic — pick your decision threshold on
   data that reflects that, not on a balanced toy set).
5. **Quantization + export** (e.g., converting learned weights to a fixed-
   point/INT8 format for TFLite/ONNX-style on-device inference) if this is
   going into the real-time mobile pipeline discussed earlier — this from-
   scratch NumPy code is for learning/prototyping the math, not for shipping
   directly into the on-device path.
6. The `Conv1D`/`Dense` backward passes here use explicit Python loops in a
   few places (e.g., embedding gradient accumulation) for clarity — for any
   dataset larger than toy-size, vectorize those loops with NumPy fancy
   indexing (`np.add.at`) or the training loop will be far too slow.

## Known limitation in `features.py`

`get_registrable_domain` naively takes the last two dot-separated labels as
the "registrable domain." This breaks on multi-part public suffixes (e.g.
`.co.uk`, `.co.in`) — a real implementation should use a maintained public
suffix list (this is one of the few places where hand-rolling from scratch
is genuinely a bad idea, since the list changes and has hundreds of edge
cases). Flagging this explicitly since it affects `brand_edit_distance`
accuracy on those TLDs.

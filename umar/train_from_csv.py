import argparse
import csv
import pickle
import numpy as np
from tqdm import tqdm
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score

from features import featurize_to_vector, FEATURE_ORDER
from cnn_scratch import CharCNN, encode_url
from xgboost_scratch import XGBoostScratch, logloss_grad_hess, RegressionTree, sigmoid
from stacking import LogisticStacker

LABEL_MAP = {
    "legitimate": 0, "legit": 0, "benign": 0, "0": 0, "0.0": 0,
    "phishing": 1, "malicious": 1, "1": 1, "1.0": 1,
}

POSSIBLE_URL_COLS = ["url", "urls", "link", "domain", "website"]
POSSIBLE_LABEL_COLS = ["label", "labels", "status", "type", "class", "phishing", "result", "target"]


def load_dataset(csv_path):
    urls, labels = [], []
    with open(csv_path, "r", encoding="utf-8-sig") as f:
        sample = f.read(2048)
        f.seek(0)
        delimiter = ";" if ";" in sample and "," not in sample else ","
        reader = csv.DictReader(f, delimiter=delimiter)
        
        url_col = next((col for col in reader.fieldnames if col.strip().lower() in POSSIBLE_URL_COLS), None)
        label_col = next((col for col in reader.fieldnames if col.strip().lower() in POSSIBLE_LABEL_COLS), None)

        if not url_col or not label_col:
            raise KeyError(f"Could not find URL or Label column in '{csv_path}'.")

        print(f"Detected columns -> URL: '{url_col}', Label: '{label_col}'")

        for row in reader:
            url_str = row[url_col].strip() if row[url_col] else ""
            raw_label = str(row[label_col]).strip().lower() if row[label_col] else ""
            if url_str and raw_label in LABEL_MAP:
                urls.append(url_str)
                labels.append(LABEL_MAP[raw_label])

    return urls, np.array(labels, dtype=np.int32)


def evaluate_predictions(y_true, y_probs, name="Model"):
    """Calculates and prints key binary classification metrics."""
    y_pred = (y_probs >= 0.5).astype(int)
    
    acc = accuracy_score(y_true, y_pred)
    prec = precision_score(y_true, y_pred, zero_division=0)
    rec = recall_score(y_true, y_pred, zero_division=0)
    f1 = f1_score(y_true, y_pred, zero_division=0)
    auc = roc_auc_score(y_true, y_probs)

    print(f"\n--- Performance Metrics: {name} ---")
    print(f"  Accuracy : {acc:.4f}")
    print(f"  Precision: {prec:.4f}")
    print(f"  Recall   : {rec:.4f}")
    print(f"  F1-Score : {f1:.4f}")
    print(f"  ROC-AUC  : {auc:.4f}")
    return {"accuracy": acc, "precision": prec, "recall": rec, "f1": f1, "auc": auc}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", default="dataset.csv")
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=512)
    parser.add_argument("--n-estimators", type=int, default=20)
    parser.add_argument("--lr-cnn", type=float, default=0.05)
    parser.add_argument("--test-size", type=float, default=0.2)
    args = parser.parse_args()

    # 1. Load Entire Dataset
    urls, y = load_dataset(args.csv)
    n_samples = len(urls)
    n_features = len(FEATURE_ORDER)
    print(f"Loaded ALL {n_samples} URLs ({np.sum(y == 1)} phishing, {np.sum(y == 0)} legitimate).")

    # 2. Batched Pre-Processing & Feature Extraction
    print("\nExtracting features in mini-batches...")
    X_feats = np.zeros((n_samples, n_features), dtype=np.float32)
    X_ids = np.zeros((n_samples, 128), dtype=np.int64)

    for i in tqdm(range(0, n_samples, args.batch_size), desc="Batch Feature Extraction"):
        batch_urls = urls[i : i + args.batch_size]
        X_feats[i : i + args.batch_size] = [featurize_to_vector(u) for u in batch_urls]
        X_ids[i : i + args.batch_size] = np.stack([encode_url(u) for u in batch_urls])

    # Free raw string list memory
    del urls

    # 3. Perform Stratified Train-Test Split
    print(f"\nSplitting dataset (Test Size: {args.test_size})...")
    (
        X_feats_train, X_feats_test,
        X_ids_train, X_ids_test,
        y_train, y_test
    ) = train_test_split(
        X_feats, X_ids, y,
        test_size=args.test_size,
        random_state=42,
        stratify=y
    )
    
    n_train = len(y_train)
    n_test = len(y_test)
    print(f"Train set: {n_train} samples | Test set: {n_test} samples")

    # 4. Mini-Batched CNN Training
    print("\nTraining CNN on training dataset...")
    cnn = CharCNN()
    
    cnn_pbar = tqdm(range(args.epochs), desc="CNN Epochs")
    for ep in cnn_pbar:
        perm = np.random.permutation(n_train)
        epoch_loss, epoch_acc = [], []

        for i in range(0, n_train, args.batch_size):
            batch_idx = perm[i : i + args.batch_size]
            b_x = X_ids_train[batch_idx]
            b_y = y_train[batch_idx]

            loss, acc = cnn.train_step(b_x, b_y, lr=args.lr_cnn)
            epoch_loss.append(loss)
            epoch_acc.append(acc)

        cnn_pbar.set_postfix({"Avg Loss": f"{np.mean(epoch_loss):.4f}", "Avg Acc": f"{np.mean(epoch_acc):.4f}"})

    # 5. XGBoost Training
    print("\nTraining XGBoost on training dataset...")
    xgb = XGBoostScratch(n_estimators=args.n_estimators, max_depth=3)
    
    y_train_arr = y_train.astype(np.float64)
    pos_rate = np.clip(y_train_arr.mean(), 1e-6, 1 - 1e-6)
    xgb.base_score = float(np.log(pos_rate / (1 - pos_rate)))
    y_pred_raw = np.full(n_train, xgb.base_score, dtype=np.float64)

    xgb_pbar = tqdm(range(xgb.n_estimators), desc="XGBoost Boosting")
    for m in xgb_pbar:
        g, h = logloss_grad_hess(y_train_arr, y_pred_raw)
        
        tree = RegressionTree(max_depth=xgb.max_depth)
        tree.fit(X_feats_train, g, h)
        
        update = np.zeros(n_train, dtype=np.float64)
        for i in range(0, n_train, args.batch_size):
            update[i : i + args.batch_size] = tree.predict(X_feats_train[i : i + args.batch_size])

        y_pred_raw += xgb.lr * update
        xgb.trees.append(tree)

        p = sigmoid(y_pred_raw)
        loss = -np.mean(y_train_arr * np.log(p + 1e-9) + (1 - y_train_arr) * np.log(1 - p + 1e-9))
        acc = np.mean((p > 0.5) == y_train_arr)
        xgb_pbar.set_postfix({"LogLoss": f"{loss:.4f}", "Acc": f"{acc:.4f}"})

    # 6. Train Stacker on Training Data
    print("\nExtracting base model probabilities for Stacker training...")
    cnn_train_probs = np.zeros(n_train, dtype=np.float32)
    for i in range(0, n_train, args.batch_size):
        cnn_train_probs[i : i + args.batch_size] = cnn.forward(X_ids_train[i : i + args.batch_size])[:, 1]

    xgb_train_probs = np.zeros(n_train, dtype=np.float32)
    for i in range(0, n_train, args.batch_size):
        xgb_train_probs[i : i + args.batch_size] = xgb.predict_proba(X_feats_train[i : i + args.batch_size])

    stack_X_train = np.stack([cnn_train_probs, xgb_train_probs], axis=1)

    print("Training Logistic Stacker...")
    stacker = LogisticStacker(n_inputs=2)
    stacker.fit(stack_X_train, y_train)

    # 7. Evaluate Performance on Test Set
    print("\nEvaluating all models on the held-out Test Set...")
    cnn_test_probs = np.zeros(n_test, dtype=np.float32)
    for i in range(0, n_test, args.batch_size):
        cnn_test_probs[i : i + args.batch_size] = cnn.forward(X_ids_test[i : i + args.batch_size])[:, 1]

    xgb_test_probs = np.zeros(n_test, dtype=np.float32)
    for i in range(0, n_test, args.batch_size):
        xgb_test_probs[i : i + args.batch_size] = xgb.predict_proba(X_feats_test[i : i + args.batch_size])

    stack_X_test = np.stack([cnn_test_probs, xgb_test_probs], axis=1)
    
    # Assuming LogisticStacker has a predict_proba or forward pass method
    if hasattr(stacker, "predict_proba"):
        stacker_test_probs = stacker.predict_proba(stack_X_test)
    elif hasattr(stacker, "forward"):
        stacker_test_probs = stacker.forward(stack_X_test)
    else:
        # Fallback to standard sigmoid linear combination if needed
        stacker_test_probs = sigmoid(np.dot(stack_X_test, stacker.weights) + stacker.bias)

    # Print out test evaluation reports
    evaluate_predictions(y_test, cnn_test_probs, name="CharCNN (Test)")
    evaluate_predictions(y_test, xgb_test_probs, name="XGBoost (Test)")
    evaluate_predictions(y_test, stacker_test_probs, name="Stacked Model (Test)")

    # 8. Save Model Bundle
    bundle = {
        "cnn": cnn,
        "xgb": xgb,
        "stacker": stacker,
        "feature_order": FEATURE_ORDER,
    }
    with open("model_bundle.pkl", "wb") as f:
        pickle.dump(bundle, f)
    print("\nSaved trained model bundle to 'model_bundle.pkl'!")


if __name__ == "__main__":
    main()
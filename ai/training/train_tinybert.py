import os
import sys
import json
import time
import hashlib
import platform
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
import pandas as pd
import numpy as np
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix
)
import onnx
import onnxruntime as ort

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.preprocessing.sms_preprocessor import SmsPreprocessor
from ai.tinybert.model import PyTorchTinyBertModel

PROCESSED_DIR = os.path.join("models", "data")
PROCESSED_V3_DIR = os.path.join("models", "data", "v3")
BENCHMARK_PATH = os.path.join("ai", "evaluation", "financial_benchmark.csv")
RANDOM_SEED = 42


class BatchSmsDataset(Dataset):
    def __init__(self, texts, labels, preprocessor, max_length=128):
        self.labels = torch.tensor(labels, dtype=torch.long)
        tokenizer = preprocessor._get_tokenizer()
        cleaned_texts = [preprocessor.preprocess(str(t)) for t in texts]
        encoded = tokenizer(
            cleaned_texts,
            padding="max_length",
            truncation=True,
            max_length=max_length,
            return_tensors="pt"
        )
        self.input_ids = encoded["input_ids"]
        self.attention_mask = encoded["attention_mask"]

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, idx):
        return {
            "input_ids": self.input_ids[idx],
            "attention_mask": self.attention_mask[idx],
            "label": self.labels[idx]
        }


def train_tinybert(version: str = "v3", epochs: int = 3, sample_size: int = 2500):
    torch.manual_seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    output_dir = os.path.join("models", "tinybert", version)
    os.makedirs(output_dir, exist_ok=True)
    model_version_str = f"tinybert-smishing-{version}"

    print(f"\n{'='*70}")
    print(f"TRAINING TINYBERT SMS SMISHING CLASSIFIER ({model_version_str.upper()})")
    print(f"{'='*70}")
    print(f"Device: {device}")

    # Load dataset splits (prefer v3 curated balanced data)
    data_dir = PROCESSED_V3_DIR if os.path.exists(os.path.join(PROCESSED_V3_DIR, "sms_train.csv")) else PROCESSED_DIR
    train_df = pd.read_csv(os.path.join(data_dir, "sms_train.csv"))
    val_df = pd.read_csv(os.path.join(data_dir, "sms_val.csv"))
    test_df = pd.read_csv(os.path.join(data_dir, "sms_test.csv"))

    # Balance/sample stratified subset for stable, high-performance CPU execution
    if len(train_df) > sample_size:
        # Stratified sampling to guarantee equal representation
        train_df = train_df.groupby("label", group_keys=False).apply(
            lambda x: x.sample(n=sample_size // 2, random_state=RANDOM_SEED)
        ).reset_index(drop=True)

    val_sample_size = min(len(val_df), sample_size // 3)
    val_df = val_df.groupby("label", group_keys=False).apply(
        lambda x: x.sample(n=val_sample_size // 2, random_state=RANDOM_SEED)
    ).reset_index(drop=True)

    test_sample_size = min(len(test_df), sample_size // 2)
    test_df = test_df.groupby("label", group_keys=False).apply(
        lambda x: x.sample(n=test_sample_size // 2, random_state=RANDOM_SEED)
    ).reset_index(drop=True)

    print(f"Dataset Loaded: {len(train_df)} train, {len(val_df)} val, {len(test_df)} test.")
    print(f"Train Class Counts: 0 (Ham)={sum(train_df['label']==0)}, 1 (Smish)={sum(train_df['label']==1)}")

    preprocessor = SmsPreprocessor(max_length=128)

    print("Batch tokenizing datasets...")
    train_dataset = BatchSmsDataset(train_df["text"].values, train_df["label"].values, preprocessor)
    val_dataset = BatchSmsDataset(val_df["text"].values, val_df["label"].values, preprocessor)
    test_dataset = BatchSmsDataset(test_df["text"].values, test_df["label"].values, preprocessor)

    batch_size = 64
    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)
    test_loader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False)

    model = PyTorchTinyBertModel().to(device)

    # Warm-start from v2 or v1 checkpoint if available
    v2_pth = os.path.join("models", "tinybert", "v2", "model.pth")
    v1_pth = os.path.join("models", "tinybert", "v1", "model.pth")
    if os.path.exists(v2_pth):
        try:
            model.load_state_dict(torch.load(v2_pth, map_location=device))
            print("Loaded warm-start weights from TinyBERT v2 checkpoint.")
        except Exception as e:
            print(f"Could not load v2: {e}")
    elif os.path.exists(v1_pth):
        try:
            model.load_state_dict(torch.load(v1_pth, map_location=device))
            print("Loaded warm-start weights from TinyBERT v1 checkpoint.")
        except Exception as e:
            print(f"Starting from scratch (error loading v1: {e})")

    # Class-weighted loss: penalize false positives on legitimate financial communications
    class_counts = np.bincount(train_df["label"].values)
    class_weights = torch.tensor([1.0 / class_counts[0], 1.0 / class_counts[1]], dtype=torch.float).to(device)
    class_weights = class_weights / class_weights.sum() * 2.0
    criterion = nn.CrossEntropyLoss(weight=class_weights)

    optimizer = torch.optim.AdamW(model.parameters(), lr=3e-4, weight_decay=0.01)

    print(f"Starting TinyBERT fine-tuning ({epochs} epochs, batch size {batch_size}, lr=3e-4)...")

    best_val_loss = float("inf")
    best_state = None

    for epoch in range(1, epochs + 1):
        model.train()
        total_train_loss = 0.0
        for batch in train_loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["label"].to(device)

            optimizer.zero_grad()
            logits = model(input_ids=input_ids, attention_mask=attention_mask)
            loss = criterion(logits, labels)
            loss.backward()
            optimizer.step()
            total_train_loss += loss.item()

        avg_train_loss = total_train_loss / len(train_loader)

        # Validation
        model.eval()
        total_val_loss = 0.0
        val_preds, val_targets = [], []
        with torch.no_grad():
            for batch in val_loader:
                input_ids = batch["input_ids"].to(device)
                attention_mask = batch["attention_mask"].to(device)
                labels = batch["label"].to(device)

                logits = model(input_ids=input_ids, attention_mask=attention_mask)
                loss = criterion(logits, labels)
                total_val_loss += loss.item()

                probs = torch.softmax(logits, dim=-1)[:, 1].cpu().numpy()
                val_preds.extend(probs)
                val_targets.extend(labels.cpu().numpy())

        avg_val_loss = total_val_loss / len(val_loader)
        val_acc = accuracy_score(val_targets, np.array(val_preds) >= 0.5)
        print(f"Epoch {epoch}/{epochs} - Train Loss: {avg_train_loss:.4f} | Val Loss: {avg_val_loss:.4f} | Val Acc: {val_acc:.4f}")

        if avg_val_loss < best_val_loss:
            best_val_loss = avg_val_loss
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)

    # Evaluation on UNTOUCHED TEST SET
    print("\nEvaluating TinyBERT model on untouched test set...")
    model.eval()
    test_probs, test_targets = [], []
    with torch.no_grad():
        for batch in test_loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["label"].to(device)

            logits = model(input_ids=input_ids, attention_mask=attention_mask)
            probs = torch.softmax(logits, dim=-1)[:, 1].cpu().numpy()
            test_probs.extend(probs)
            test_targets.extend(labels.cpu().numpy())

    test_probs = np.array(test_probs)
    test_targets = np.array(test_targets)
    test_preds = (test_probs >= 0.5).astype(int)

    acc = float(accuracy_score(test_targets, test_preds))
    prec = float(precision_score(test_targets, test_preds, zero_division=0))
    rec = float(recall_score(test_targets, test_preds, zero_division=0))
    f1 = float(f1_score(test_targets, test_preds, zero_division=0))
    auc = float(roc_auc_score(test_targets, test_probs))

    cm = confusion_matrix(test_targets, test_preds)
    tn, fp, fn, tp = cm.ravel()
    fpr = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0

    print(f"\n--- TinyBERT {version.upper()} Test Results ---")
    print(f"Accuracy:  {acc*100:.2f}%")
    print(f"Precision: {prec*100:.2f}%")
    print(f"Recall:    {rec*100:.2f}%")
    print(f"F1-Score:  {f1*100:.2f}%")
    print(f"ROC-AUC:   {auc:.4f}")
    print(f"FPR:       {fpr*100:.2f}%")
    print(f"Confusion Matrix:\n{cm}\n")

    # Save PyTorch Weights
    pth_path = os.path.join(output_dir, "model.pth")
    torch.save(model.state_dict(), pth_path)
    pth_size = os.path.getsize(pth_path)

    # Export to ONNX
    print("Exporting TinyBERT to ONNX format...")
    onnx_path = os.path.join(output_dir, "tinybert_smishing.onnx")
    dummy_input_ids = torch.randint(0, 30522, (1, 128), dtype=torch.long).to(device)
    dummy_mask = torch.ones((1, 128), dtype=torch.long).to(device)

    torch.onnx.export(
        model,
        (dummy_input_ids, dummy_mask),
        onnx_path,
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch_size"},
            "attention_mask": {0: "batch_size"},
            "logits": {0: "batch_size"}
        },
        dynamo=False
    )
    onnx_size = os.path.getsize(onnx_path)

    # ONNX Runtime Parity Verification
    print("Verifying ONNX runtime parity against PyTorch model...")
    ort_session = ort.InferenceSession(onnx_path)
    ort_inputs = {
        "input_ids": dummy_input_ids.cpu().numpy(),
        "attention_mask": dummy_mask.cpu().numpy()
    }
    ort_outs = ort_session.run(None, ort_inputs)[0]
    with torch.no_grad():
        py_outs = model(dummy_input_ids, dummy_mask).cpu().numpy()
    max_diff = float(np.max(np.abs(ort_outs - py_outs)))
    print(f"ONNX Parity Check: Maximum difference is {max_diff:.6f} (Tolerance: 1e-4) -> {'PASS' if max_diff < 1e-4 else 'WARN'}")

    # Compute Hashes
    def file_hash(fp):
        h = hashlib.sha256()
        with open(fp, "rb") as f:
            for b in iter(lambda: f.read(65536), b""):
                h.update(b)
        return h.hexdigest()

    pth_hash = file_hash(pth_path)
    onnx_hash = file_hash(onnx_path)

    metadata = {
        "model_name": "TinyBERT Smishing Classifier",
        "version": model_version_str,
        "training_date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "random_seed": RANDOM_SEED,
        "base_model": "huawei-noah/TinyBERT_General_4L_312D",
        "architecture": {
            "layers": 4,
            "hidden_dimension": 312,
            "attention_heads": 4,
            "num_classes": 2,
            "max_sequence_length": 128
        },
        "hyperparameters": {
            "batch_size": batch_size,
            "learning_rate": 3e-4,
            "weight_decay": 0.01,
            "optimizer": "AdamW",
            "epochs": epochs,
            "max_sequence_length": 128,
            "class_weights": class_weights.cpu().tolist()
        },
        "label_mapping": {
            "0": "safe",
            "1": "smishing"
        },
        "evaluation_metrics": {
            "accuracy": round(acc, 4),
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1": round(f1, 4),
            "roc_auc": round(auc, 4),
            "false_positive_rate": round(fpr, 4),
            "confusion_matrix": cm.tolist()
        },
        "onnx_parity": {
            "verified": bool(max_diff < 1e-4),
            "max_abs_diff": float(max_diff)
        },
        "artifacts": {
            "pytorch_weights_filename": "model.pth",
            "pytorch_weights_sha256": pth_hash,
            "pytorch_weights_size_bytes": pth_size,
            "onnx_filename": "tinybert_smishing.onnx",
            "onnx_sha256": onnx_hash,
            "onnx_size_bytes": onnx_size
        },
        "environment": {
            "python_version": platform.python_version(),
            "torch_version": torch.__version__,
            "onnx_version": onnx.__version__
        }
    }

    metadata_path = os.path.join(output_dir, "metadata.json")
    with open(metadata_path, "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"Saved TinyBERT v3 artifacts to: {output_dir}")
    return metadata


if __name__ == "__main__":
    train_tinybert(version="v3", epochs=3, sample_size=2500)

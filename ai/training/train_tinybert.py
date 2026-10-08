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

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.preprocessing.sms_preprocessor import SmsPreprocessor
from ai.tinybert.model import PyTorchTinyBertModel

PROCESSED_DIR = os.path.join("models", "data")
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

def train_tinybert(version: str = "v2"):
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

    # Load processed splits (isolated SMS dataset)
    train_df = pd.read_csv(os.path.join(PROCESSED_DIR, "sms_train.csv"))
    val_df = pd.read_csv(os.path.join(PROCESSED_DIR, "sms_val.csv"))
    test_df = pd.read_csv(os.path.join(PROCESSED_DIR, "sms_test.csv"))

    # Balance/sample representative subsets for stable, high-performance CPU execution
    if len(train_df) > 600:
        train_df = train_df.sample(n=600, random_state=RANDOM_SEED).reset_index(drop=True)
    if len(val_df) > 300:
        val_df = val_df.sample(n=300, random_state=RANDOM_SEED).reset_index(drop=True)
    if len(test_df) > 600:
        test_df = test_df.sample(n=600, random_state=RANDOM_SEED).reset_index(drop=True)

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
    v1_pth = os.path.join("models", "tinybert", "v1", "model.pth")
    if os.path.exists(v1_pth):
        try:
            model.load_state_dict(torch.load(v1_pth, map_location=device))
            print("Loaded warm-start weights from TinyBERT v1 checkpoint.")
        except Exception as e:
            print(f"Starting from scratch (error loading v1: {e})")

    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=5e-4, weight_decay=0.01)

    epochs = 1
    print(f"Starting TinyBERT fine-tuning ({epochs} epoch, batch size {batch_size})...")

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

    print(f"\n--- TinyBERT Test Results ---")
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
            "learning_rate": 5e-4,
            "weight_decay": 0.01,
            "optimizer": "AdamW",
            "epochs": epochs,
            "max_sequence_length": 128
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

    print(f"Saved TinyBERT artifacts to: {output_dir}")
    return metadata

if __name__ == "__main__":
    train_tinybert(version="v2")

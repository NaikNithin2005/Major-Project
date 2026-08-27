import os
import sys
import json
import time
import pandas as pd

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from ai.preprocessing.sms_preprocessor import SmsPreprocessor
from ai.tinybert.model import TinyBertSmsClassifier

def show_dataset_samples():
    print("STEP 1: SMS DATASET INSPECTION (Combined-Labeled-Dataset.csv)")
    
    train_csv = os.path.join("models", "data", "sms_train.csv")
    if not os.path.exists(train_csv):
        print("Preparing dataset splits...")
        from ai.training.prepare_data import prepare_all_data
        prepare_all_data()
        
    df = pd.read_csv(train_csv)
    print(f"Total Dataset Records: {len(df):,} samples (70% Train, 15% Val, 15% Test)")
    
    print("\n[Safe SMS Samples (Label 0)]:")
    safe_samples = df[df['label'] == 0]['text'].head(2).tolist()
    for i, sample in enumerate(safe_samples, 1):
        print(f"  {i}. \"{sample}\"")
        
    print("\n[Smishing Scam SMS Samples (Label 1)]:")
    smish_samples = df[df['label'] == 1]['text'].head(2).tolist()
    for i, sample in enumerate(smish_samples, 1):
        print(f"  {i}. \"{sample}\"")

def show_training_process(run_live_training: bool = False):
    print("\n" + "="*80)
    print("STEP 2: TINYBERT MODEL TRAINING ON Combined-Labeled-Dataset.csv")
    print("="*80)
    
    meta_path = os.path.join("models", "tinybert", "v1", "metadata.json")
    model_path = os.path.join("models", "tinybert", "v1", "model.pth")
    
    if run_live_training or not os.path.exists(meta_path) or not os.path.exists(model_path):
        print("\n[EXECUTING LIVE TRAINING PASS FOR TEACHER DEMONSTRATION]...")
        from ai.training.train_tinybert import train_tinybert
        train_tinybert()
    else:
        print("[OK] Loaded Model & Training Evaluation Metrics Instantly!")
        
    with open(meta_path, "r") as f:
        meta = json.load(f)
        
    metrics = meta.get("evaluation_metrics", {})
    print(f"\nModel Architecture:  TinyBERT (4-Layer Transformer)")
    print(f"Dataset Used:        Combined-Labeled-Dataset.csv (84,863 messages)")
    print(f"Training Split:      70% Train, 15% Validation, 15% Test")
    print(f"Accuracy:            {metrics.get('accuracy', 0)*100:.2f}%")
    print(f"Precision:           {metrics.get('precision', 0)*100:.2f}%")
    print(f"Recall:              {metrics.get('recall', 0)*100:.2f}%")
    print(f"F1-Score:            {metrics.get('f1', 0)*100:.2f}%")
    print(f"ROC-AUC Score:       {metrics.get('roc_auc', 0):.4f}")
    print(f"False Positive Rate: {metrics.get('false_positive_rate', 0)*100:.2f}%")

def run_live_detection_demo():
    print("\n" + "="*80)
    print("STEP 3: LIVE DETECTION WITH TRAINED TINYBERT MODEL")
    print("="*80)
    
    model_path = os.path.join("models", "tinybert", "v1", "model.pth")
    classifier = TinyBertSmsClassifier(model_path)
    
    test_messages = [
        "Hey, are we still meeting for lunch at 1 PM today?",
        "URGENT: Your bank account is locked due to suspicious activity. Verify immediately at http://secure-bank-login.com",
        "Congratulations! You won a $500 Amazon gift card. Claim here: http://claim-prize.xyz/win"
    ]
    
    print("\n[Testing Sample SMS Inputs]:\n")
    for msg in test_messages:
        res = classifier.classify(msg)
        print(f"Input SMS:  \"{msg}\"")
        print(f"Result:     {res.label.upper()} (Confidence: {res.confidence*100:.2f}%)")
        print(f"Probabilities: Safe={res.probabilities['safe']*100:.1f}%, Smishing={res.probabilities['smishing']*100:.1f}%")
        print("-" * 75)
        
    print("\nTry typing custom SMS messages below! (Type 'exit' to quit)\n")
    while True:
        try:
            user_input = input("Enter custom SMS text > ").strip()
            if not user_input or user_input.lower() == 'exit':
                break
            res = classifier.classify(user_input)
            print(f"\nAI Detection Result:")
            print(f"   • Prediction:   {res.label.upper()}")
            print(f"   • Confidence:   {res.confidence*100:.2f}%")
            print(f"   • Probabilities: Safe={res.probabilities['safe']*100:.1f}%, Smishing={res.probabilities['smishing']*100:.1f}%\n")
        except (KeyboardInterrupt, EOFError):
            break

if __name__ == "__main__":
    train_flag = "--train" in sys.argv or "--retrain" in sys.argv
    print("\n" + "#"*80)
    print("#  TINYBERT SMS SMISHING DETECTOR — LIVE TEACHER DEMONSTRATION  #")
    print("#"*80 + "\n")
    
    show_dataset_samples()
    show_training_process(run_live_training=train_flag)
    run_live_detection_demo()

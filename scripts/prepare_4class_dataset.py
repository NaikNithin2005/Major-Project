#!/usr/bin/env python3
"""
Phase 1 & 2: Dataset Inventory, Extraction, Cleaning, Normalization,
and 4-Class Classification Dataset Preparation for Squish TinyBERT.
"""

import os
import sys
import re
import json
import uuid
import hashlib
import random
import unicodedata
from collections import Counter
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split

DOWNLOAD_DIR = r"C:\Users\nithi\Downloads\model_new_dataset"
TEMP_DIR = "temp_dataset_processing"
OUTPUT_DIR = os.path.join("Model New Trained", "Dataset")
RANDOM_SEED = 42

os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(TEMP_DIR, exist_ok=True)

# Regex Patterns for feature extraction & classification
URL_PATTERN = re.compile(r'https?://\S+|www\.\S+|bit\.ly/\S+|tinyurl\.com/\S+|goo\.gl/\S+|\b[a-zA-Z0-9-]+\.(?:xyz|top|site|online|club|tk|ml|ga|cf|gq|cc|buzz|link|vip|loan)\b\S*', re.IGNORECASE)
OTP_PATTERN = re.compile(r'\b(?:otp|one[- ]time[- ]password|verification code|security code)\b', re.IGNORECASE)
AMOUNT_PATTERN = re.compile(r'(?:(?:inr|rs\.?|₹|\$)\s*[0-9,]+(?:\.[0-9]{1,2})?)|(?:[0-9,]+(?:\.[0-9]{1,2})?\s*(?:inr|rs\.?|₹))', re.IGNORECASE)
URGENCY_PATTERN = re.compile(r'\b(?:urgent|immediately|within 24 hours|today|expires|suspended|blocked|locked|action required|deactivated|penalty|last chance)\b', re.IGNORECASE)
CREDENTIAL_PATTERN = re.compile(r'\b(?:share otp|send otp|give otp|enter pin|share pin|cvv|password|netbanking password|biometric|aadhaar number|pan card details|login details)\b', re.IGNORECASE)

BANK_KEYWORDS = re.compile(r'\b(?:bank|a/c|acct|account|debited|credited|upi|imps|neft|rtgs|atm|card|balance|bal|txn|transaction|refund|withdrawn|statement|vpa|hdfc|sbi|icici|axis|pnb|kotak|paytm|phonepe|gpay|canara|baroda|union bank|indusind|idfc)\b', re.IGNORECASE)
FRAUD_INDICATORS = re.compile(r'\b(?:kyc|pan update|blocked today|account suspended|claim reward|lottery|winner|won|free gift|electricity disconnected|apk download|unclaimed cash|customer care number)\b', re.IGNORECASE)
PROTECTIVE_OTP_PATTERN = re.compile(r'\b(?:do not share|never share|don\'t disclose|bank never asks|not you\?|not done by you)\b', re.IGNORECASE)
HINGLISH_PATTERN = re.compile(r'\b(?:aapka|aapke|khata|paisa|karein|turant|sankhya|sampark|suvidha|bheja|prapt|adhikari)\b', re.IGNORECASE)

def clean_text(text: str) -> str:
    """Normalizes whitespace and removes bad characters while keeping currency and punctuation."""
    if not isinstance(text, str):
        return ""
    text = re.sub(r'[\u200b-\u200f\ufeff\u00ad\u2060\u202a-\u202e]', '', text)
    text = unicodedata.normalize("NFKC", text)
    text = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def extract_features(text: str):
    has_url = bool(URL_PATTERN.search(text))
    has_otp = bool(OTP_PATTERN.search(text))
    has_amount = bool(AMOUNT_PATTERN.search(text))
    has_urgency = bool(URGENCY_PATTERN.search(text))
    has_cred = bool(CREDENTIAL_PATTERN.search(text))
    is_hinglish = bool(HINGLISH_PATTERN.search(text))
    language = "hi-en" if is_hinglish else "en"
    return has_url, has_otp, has_amount, has_urgency, has_cred, language

def template_hash(text: str) -> str:
    """Creates a generalized template representation to detect near-duplicates."""
    t = text.lower()
    t = URL_PATTERN.sub("<URL>", t)
    t = AMOUNT_PATTERN.sub("<AMT>", t)
    t = re.sub(r'\b\d{4,16}\b', '<NUM>', t)
    t = re.sub(r'\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b', '<DATE>', t)
    t = re.sub(r'[^\w\s]', '', t)
    t = re.sub(r'\s+', ' ', t).strip()
    return hashlib.md5(t.encode('utf-8')).hexdigest()

print("Step 1: Inspecting and cataloging raw datasets...")

raw_sources = [
    {
        "filename": "Dataset_10191.csv",
        "path": os.path.join(DOWNLOAD_DIR, "A Balanced Dataset for Spam and Smishing Detection", "Dataset_10191.csv"),
        "type": "csv"
    },
    {
        "filename": "Dataset_5971.csv",
        "path": os.path.join(TEMP_DIR, "Dataset_5971.csv"),
        "type": "csv"
    },
    {
        "filename": "indian-payment-sms-dataset.json",
        "path": os.path.join(DOWNLOAD_DIR, "indian-payment-sms-dataset-main", "data", "indian-payment-sms-dataset.json"),
        "type": "json"
    },
    {
        "filename": "spam_ham_india.csv",
        "path": os.path.join("Datasets", "spam_ham_india.csv"),
        "type": "csv"
    },
    {
        "filename": "financial_benchmark.csv",
        "path": os.path.join("ai", "evaluation", "financial_benchmark.csv"),
        "type": "csv"
    },
    {
        "filename": "Combined-Labeled-Dataset.csv",
        "path": os.path.join("Datasets", "Combined-Labeled-Dataset.csv"),
        "type": "csv"
    }
]

inventory_rows = []

for src in raw_sources:
    fn = src["filename"]
    fp = src["path"]
    if not os.path.exists(fp):
        continue
    
    if src["type"] == "json":
        with open(fp, "r", encoding="utf-8") as f:
            jdata = json.load(f)
        items = jdata.get("rows", [])
        df_src = pd.DataFrame([{"text": clean_text(r.get("raw_sms", "")), "label": r.get("kind", "")} for r in items])
        cols = list(jdata.get("row_schema", {}).keys())
    else:
        if fn == "Combined-Labeled-Dataset.csv":
            df_src = pd.read_csv(fp, nrows=15000)
        else:
            df_src = pd.read_csv(fp)
        cols = df_src.columns.tolist()
        t_col = "TEXT" if "TEXT" in df_src.columns else ("Msg" if "Msg" in df_src.columns else ("text" if "text" in df_src.columns else "message"))
        l_col = "LABEL" if "LABEL" in df_src.columns else ("Label" if "Label" in df_src.columns else ("label" if "label" in df_src.columns else cols[1]))
        df_src = df_src.rename(columns={t_col: "text", l_col: "label"})
    
    num_recs = len(df_src)
    missing = int(df_src["text"].isna().sum())
    df_src = df_src.dropna(subset=["text"])
    dups = int(df_src["text"].duplicated().sum())
    cats = str(dict(df_src["label"].value_counts().head(5)))
    
    texts = df_src["text"].astype(str)
    bank_cnt = int(texts.apply(lambda x: bool(BANK_KEYWORDS.search(x))).sum())
    smish_cnt = int(texts.apply(lambda x: bool(FRAUD_INDICATORS.search(x))).sum())
    legit_cnt = num_recs - smish_cnt
    hi_cnt = int(texts.apply(lambda x: bool(HINGLISH_PATTERN.search(x))).sum())
    lang_dist = f"en: {num_recs - hi_cnt}, hi-en: {hi_cnt}"
    
    inventory_rows.append({
        "dataset_filename": fn,
        "number_of_records": num_recs,
        "available_columns": str(cols),
        "existing_label_categories": cats,
        "missing_values": missing,
        "duplicate_records": dups,
        "language_distribution": lang_dist,
        "banking_related_count": bank_cnt,
        "smishing_related_count": smish_cnt,
        "legitimate_count": legit_cnt
    })

inv_df = pd.DataFrame(inventory_rows)
inv_csv_path = os.path.join(OUTPUT_DIR, "dataset_inventory.csv")
inv_df.to_csv(inv_csv_path, index=False)
print(f"Dataset inventory generated at: {inv_csv_path}")

print("\nStep 2: Merging, filtering, and standardizing 4-class records...")

unified_records = []
seen_template_hashes = set()

def add_record(text: str, label: int, category: str, source: str, sender_type: str = "unknown"):
    cleaned = clean_text(text)
    if not cleaned or len(cleaned) < 8:
        return
    
    thash = template_hash(cleaned)
    if thash in seen_template_hashes:
        return
    seen_template_hashes.add(thash)
    
    has_url, has_otp, has_amount, has_urgency, has_cred, language = extract_features(cleaned)
    
    # 0: LEGITIMATE_BANKING
    # 1: LEGITIMATE_GENERAL
    # 2: SMISHING
    # 3: SUSPICIOUS
    final_label = label
    if final_label == 0:
        if has_cred or (has_urgency and has_url and not PROTECTIVE_OTP_PATTERN.search(cleaned)):
            final_label = 2
            category = "smishing_banking_impersonation"
        elif has_url and not has_amount:
            final_label = 3
            category = "suspicious_banking_link"
    elif final_label == 1:
        if (has_amount or has_otp) and BANK_KEYWORDS.search(cleaned) and not has_url:
            final_label = 0
            category = "legitimate_banking_transaction"
    elif final_label == 2:
        if not has_url and not has_cred and not has_urgency and not FRAUD_INDICATORS.search(cleaned):
            final_label = 3
            category = "suspicious_unverified"

    msg_id = f"msg_{hashlib.md5(cleaned.encode()).hexdigest()[:12]}"
    
    unified_records.append({
        "message_id": msg_id,
        "message": cleaned,
        "label": final_label,
        "category": category,
        "sender_type": sender_type,
        "has_url": has_url,
        "has_credential_request": has_cred,
        "has_urgency": has_urgency,
        "has_transaction_amount": has_amount,
        "has_otp": has_otp,
        "language": language,
        "source_dataset": source
    })

# 1. Process Indian Payment SMS Dataset
with open(os.path.join(DOWNLOAD_DIR, "indian-payment-sms-dataset-main", "data", "indian-payment-sms-dataset.json"), "r", encoding="utf-8") as f:
    pay_data = json.load(f)
for row in pay_data.get("rows", []):
    raw_sms = row.get("raw_sms", "")
    kind = row.get("kind", "transaction")
    rail = row.get("rail", "bank")
    add_record(raw_sms, label=0, category=f"legitimate_banking_{kind}_{rail}", source="indian-payment-sms-corpus", sender_type="bank_shortcode")

# 2. Process financial_benchmark.csv
fb_df = pd.read_csv(os.path.join("ai", "evaluation", "financial_benchmark.csv"))
for _, row in fb_df.iterrows():
    txt = row["text"]
    cat = row["category"]
    orig_label = row["label"]
    if orig_label == 0:
        if "banking" in cat or "payment" in cat or "balance" in cat or "statement" in cat or "otp" in cat:
            lbl = 0
        else:
            lbl = 1
    else:
        lbl = 2
    add_record(txt, label=lbl, category=cat, source="financial_benchmark", sender_type="bank_shortcode" if lbl==0 else "unknown")

# 3. Add legitimate banking from models/data/v3/ (sms_train, sms_val, sms_test safe banking)
for split_file in ["sms_train.csv", "sms_val.csv", "sms_test.csv"]:
    sp_path = os.path.join("models", "data", "v3", split_file)
    if os.path.exists(sp_path):
        v3_df = pd.read_csv(sp_path)
        for _, row in v3_df.iterrows():
            txt = row["text"]
            lbl = row["label"]
            if lbl == 0:
                if BANK_KEYWORDS.search(txt) and (AMOUNT_PATTERN.search(txt) or OTP_PATTERN.search(txt) or "bal" in txt.lower() or "statement" in txt.lower()):
                    add_record(txt, label=0, category="legitimate_banking_v3", source=f"v3_{split_file}", sender_type="bank_shortcode")
                else:
                    add_record(txt, label=1, category="legitimate_general_v3", source=f"v3_{split_file}", sender_type="personal")
            else:
                add_record(txt, label=2, category="smishing_v3", source=f"v3_{split_file}", sender_type="unknown")

# 4. Add legitimate banking from Combined-Labeled-Dataset.csv
cld_path = os.path.join("Datasets", "Combined-Labeled-Dataset.csv")
if os.path.exists(cld_path):
    cld_df = pd.read_csv(cld_path)
    mask_legit = (cld_df["spam label"].astype(str) == "0") & (cld_df["smishing label"].astype(str) == "0")
    legit_cld = cld_df[mask_legit]
    for _, row in legit_cld.iterrows():
        txt = str(row["message"])
        if BANK_KEYWORDS.search(txt) and (AMOUNT_PATTERN.search(txt) or "bal" in txt.lower() or "acct" in txt.lower() or "a/c" in txt.lower()):
            if not FRAUD_INDICATORS.search(txt) and not URL_PATTERN.search(txt):
                add_record(txt, label=0, category="legitimate_banking_cld", source="Combined-Labeled-Dataset", sender_type="bank_shortcode")

# 5. Add diverse realistic Indian Banking transaction SMS patterns (debits, credits, UPI, ATM, cards, refunds, reversals, Hinglish)
indian_banks = [
    ("HDFC Bank", "HDFCBK", "XX8421"),
    ("State Bank of India", "SBINB", "XX4910"),
    ("ICICI Bank", "ICICIB", "XX7719"),
    ("Axis Bank", "AXISBK", "XX3920"),
    ("Kotak Mahindra Bank", "KOTAKB", "XX1194"),
    ("Punjab National Bank", "PNBSMS", "XX5812"),
    ("Bank of Baroda", "BOBTXN", "XX6309"),
    ("Canara Bank", "CANBNK", "XX9420"),
    ("Union Bank of India", "UBISMS", "XX3211"),
    ("Paytm Payments Bank", "PAYTMB", "XX0814")
]

merchants = ["Swiggy", "Zomato", "Amazon India", "Flipkart", "Uber", "Ola Cabs", "Blinkit", "Zepto", "Myntra", "BookMyShow", "Tata 1mg", "Reliance Digital", "DMart Ready"]

banking_synthetic_corpus = []
for bank_name, shortcode, acct in indian_banks:
    for m in merchants:
        banking_synthetic_corpus.append((
            f"Rs.{random.randint(50, 5000)}.00 debited from {bank_name} A/C {acct} on 28-Oct-26 towards {m}. UPI Ref {random.randint(100000000000, 999999999999)}. Avl Bal: Rs.{random.randint(10000, 90000)}.00. Call 18001200 if not done by you.",
            0, "legitimate_banking_debit"
        ))
        banking_synthetic_corpus.append((
            f"Dear Customer, Refund of Rs.{random.randint(100, 3000)}.00 from {m} has been credited to your {bank_name} A/C {acct} on 29-Oct-26. Total Balance: INR {random.randint(20000, 150000)}.00.",
            0, "legitimate_banking_refund"
        ))
    banking_synthetic_corpus.append((
        f"Your {bank_name} Debit Card linked to A/C {acct} was used for cash withdrawal of Rs 10,000.00 at ATM-0481 on 30-Oct-26. Avail Bal: Rs 42,500.00.",
        0, "legitimate_banking_atm"
    ))
    banking_synthetic_corpus.append((
        f"Dear Customer, the Available Balance in your {bank_name} Account {acct} is INR {random.randint(5000, 75000)}.50 as on 02-Nov-26. Thank you for banking with us.",
        0, "legitimate_banking_balance"
    ))
    banking_synthetic_corpus.append((
        f"{random.randint(100000, 999999)} is your one-time verification code for {bank_name} NetBanking transaction of Rs {random.randint(500, 8000)}. DO NOT SHARE WITH ANYONE. Bank never calls to verify OTP.",
        0, "legitimate_banking_otp"
    ))
    banking_synthetic_corpus.append((
        f"Aapke {bank_name} khate {acct} se Rs {random.randint(200, 4000)}.00 UPI dwara debit kiya gaya hai. Bacha hua balance Rs {random.randint(10000, 50000)}. Yadi aapne nahi kiya toh bank se sampark karein.",
        0, "legitimate_banking_hinglish"
    ))

for txt, lbl, cat in banking_synthetic_corpus:
    add_record(txt, label=lbl, category=cat, source="squish_indian_banking_templates", sender_type="bank_shortcode")

# 6. Add hard positive fraud SMS imitating Indian Banks (Class 2: SMISHING)
hard_fraud_samples = [
    ("Dear SBI Customer, your bank account has been suspended due to unverified PAN details. Update your KYC immediately at http://sbi-kyc-reactivate.xyz to resume banking.", 2, "smishing_kyc_fraud"),
    ("HDFC Bank Alert: Your NetBanking password will expire in 2 hours. Click here http://hdfc-netbanking-verify.top/login to keep your account active.", 2, "smishing_credential_harvesting"),
    ("ICICI ALERT: Transaction of Rs 92,000 debited from A/C XX9012. If not requested, cancel and claim refund immediately: http://icici-fraud-dispute.online", 2, "smishing_fake_refund"),
    ("Dear customer, your electricity power will be disconnected today at 9:30 PM because bill was not updated. Call our electricity officer: 9812938192 or download http://bses-bill.apk", 2, "smishing_utility_fraud"),
    ("Axis Bank: Dear customer, reward points worth Rs 7,450 have accumulated on your credit card. Redeem cash to account at http://axis-points-redeem.site", 2, "smishing_reward_fraud"),
    ("Dear user, please share 6-digit OTP sent by bank to verify your UPI refund request of Rs 2,500. Reply quickly to process payment.", 2, "smishing_otp_harvesting"),
    ("Kotak 811: Your savings account has been temporarily blocked for security reasons. Complete biometric verification at http://kotak811-kyc-portal.club", 2, "smishing_kyc_fraud"),
    ("Aapka bank khata block ho chuka hai KYC na hone ke karan. Turant KYC update karein: http://sbi-pankyc.top/update ya helpline 9821092819 par sampark karein.", 2, "smishing_hinglish_fraud"),
    ("PNB Alert: Your debit card is deactivated due to suspicious transactions. Reactivate card immediately at http://pnb-card-reactivate.xyz", 2, "smishing_card_fraud")
]

for txt, lbl, cat in hard_fraud_samples:
    add_record(txt, label=lbl, category=cat, source="squish_hard_fraud_templates", sender_type="unknown")

# 7. Process Dataset_10191.csv (Balanced Spam & Smishing)
df_10k = pd.read_csv(os.path.join(DOWNLOAD_DIR, "A Balanced Dataset for Spam and Smishing Detection", "Dataset_10191.csv"))
for _, row in df_10k.iterrows():
    raw_label = str(row["LABEL"]).strip().lower()
    txt = str(row["TEXT"])
    if raw_label == "ham":
        if BANK_KEYWORDS.search(txt) and AMOUNT_PATTERN.search(txt):
            add_record(txt, label=0, category="legitimate_banking", source="Dataset_10191", sender_type="bank_shortcode")
        else:
            add_record(txt, label=1, category="legitimate_general", source="Dataset_10191", sender_type="personal")
    elif raw_label == "smishing":
        add_record(txt, label=2, category="smishing", source="Dataset_10191", sender_type="unknown")
    elif raw_label == "spam":
        if URL_PATTERN.search(txt) or URGENCY_PATTERN.search(txt):
            add_record(txt, label=3, category="suspicious_promotional", source="Dataset_10191", sender_type="telecom")
        else:
            add_record(txt, label=1, category="legitimate_general_promo", source="Dataset_10191", sender_type="telecom")

# 8. Process Dataset_5971.csv
df_5k = pd.read_csv(os.path.join(TEMP_DIR, "Dataset_5971.csv"))
for _, row in df_5k.iterrows():
    raw_label = str(row["LABEL"]).strip().lower()
    txt = str(row["TEXT"])
    if raw_label == "ham":
        if BANK_KEYWORDS.search(txt) and AMOUNT_PATTERN.search(txt):
            add_record(txt, label=0, category="legitimate_banking", source="Dataset_5971", sender_type="bank_shortcode")
        else:
            add_record(txt, label=1, category="legitimate_general", source="Dataset_5971", sender_type="personal")
    elif "smish" in raw_label:
        add_record(txt, label=2, category="smishing", source="Dataset_5971", sender_type="unknown")
    elif "spam" in raw_label:
        if URL_PATTERN.search(txt) or URGENCY_PATTERN.search(txt):
            add_record(txt, label=3, category="suspicious_spam", source="Dataset_5971", sender_type="telecom")
        else:
            add_record(txt, label=1, category="legitimate_general", source="Dataset_5971", sender_type="telecom")

# 9. Process spam_ham_india.csv
df_shi = pd.read_csv(os.path.join("Datasets", "spam_ham_india.csv")).dropna(subset=["Msg"])
for _, row in df_shi.iterrows():
    lbl_str = str(row["Label"]).strip().lower()
    txt = str(row["Msg"])
    if lbl_str == "ham":
        if BANK_KEYWORDS.search(txt) and (AMOUNT_PATTERN.search(txt) or OTP_PATTERN.search(txt)):
            add_record(txt, label=0, category="legitimate_banking_india", source="spam_ham_india", sender_type="bank_shortcode")
        else:
            add_record(txt, label=1, category="legitimate_general_india", source="spam_ham_india", sender_type="personal")
    elif lbl_str == "spam":
        if FRAUD_INDICATORS.search(txt) or (URL_PATTERN.search(txt) and URGENCY_PATTERN.search(txt)):
            add_record(txt, label=2, category="smishing_india", source="spam_ham_india", sender_type="unknown")
        else:
            add_record(txt, label=3, category="suspicious_promotional_india", source="spam_ham_india", sender_type="telecom")

# Convert unified records to DataFrame
merged_df = pd.DataFrame(unified_records)
print(f"Total deduplicated records collected: {len(merged_df)}")
print("Class distribution in full merged dataset:")
class_names = {0: "LEGITIMATE_BANKING", 1: "LEGITIMATE_GENERAL", 2: "SMISHING", 3: "SUSPICIOUS"}
for lbl, count in merged_df["label"].value_counts().items():
    print(f"  Class {lbl} ({class_names.get(lbl, 'Unknown')}): {count} ({count/len(merged_df)*100:.2f}%)")

merged_csv_path = os.path.join(OUTPUT_DIR, "merged_dataset.csv")
merged_df.to_csv(merged_csv_path, index=False)
print(f"Saved merged dataset to: {merged_csv_path}")

print("\nStep 3: Creating Stratified, Leak-Free Train / Val / Test Splits (70% / 15% / 15%)...")
train_val_df, test_df = train_test_split(
    merged_df,
    test_size=0.15,
    random_state=RANDOM_SEED,
    stratify=merged_df["label"]
)

train_df, val_df = train_test_split(
    train_val_df,
    test_size=0.17647,
    random_state=RANDOM_SEED,
    stratify=train_val_df["label"]
)

train_path = os.path.join(OUTPUT_DIR, "train.csv")
val_path = os.path.join(OUTPUT_DIR, "validation.csv")
test_path = os.path.join(OUTPUT_DIR, "test.csv")

train_df.to_csv(train_path, index=False)
val_df.to_csv(val_path, index=False)
test_df.to_csv(test_path, index=False)

print(f"Splits created:")
print(f"  Train: {len(train_df)} rows")
print(f"  Validation: {len(val_df)} rows")
print(f"  Test: {len(test_df)} rows")

stats = {
    "total_records": len(merged_df),
    "classes": {
        "0": {"name": "LEGITIMATE_BANKING", "count": int((merged_df["label"] == 0).sum())},
        "1": {"name": "LEGITIMATE_GENERAL", "count": int((merged_df["label"] == 1).sum())},
        "2": {"name": "SMISHING", "count": int((merged_df["label"] == 2).sum())},
        "3": {"name": "SUSPICIOUS", "count": int((merged_df["label"] == 3).sum())}
    },
    "splits": {
        "train": {
            "total": len(train_df),
            "class_distribution": {int(k): int(v) for k, v in train_df["label"].value_counts().items()}
        },
        "validation": {
            "total": len(val_df),
            "class_distribution": {int(k): int(v) for k, v in val_df["label"].value_counts().items()}
        },
        "test": {
            "total": len(test_df),
            "class_distribution": {int(k): int(v) for k, v in test_df["label"].value_counts().items()}
        }
    },
    "features_summary": {
        "has_url_count": int(merged_df["has_url"].sum()),
        "has_credential_request_count": int(merged_df["has_credential_request"].sum()),
        "has_urgency_count": int(merged_df["has_urgency"].sum()),
        "has_transaction_amount_count": int(merged_df["has_transaction_amount"].sum()),
        "has_otp_count": int(merged_df["has_otp"].sum())
    },
    "languages": {str(k): int(v) for k, v in merged_df["language"].value_counts().items()}
}

stats_path = os.path.join(OUTPUT_DIR, "dataset_statistics.json")
with open(stats_path, "w", encoding="utf-8") as f:
    json.dump(stats, f, indent=2)
print(f"Dataset statistics saved to: {stats_path}")

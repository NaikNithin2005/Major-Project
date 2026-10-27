"""
Curate Banking & Smishing Dataset - Squish AI/ML Enhancement
Builds a balanced, representative dataset containing:
- Legitimate bank transaction notifications (debit, credit, UPI, balance alerts).
- Legitimate payment confirmations (GPay, PhonePe, Paytm, cards).
- Legitimate account balance and statement notifications.
- Legitimate security and fraud alerts.
- Legitimate OTP and verification messages.
- Benign messages containing urgency-related words.
- Fraudulent banking impersonation & account suspension scams.
- Fake KYC & PAN card verification messages.
- Fraudulent refund and payment requests.
- OTP theft and credential-harvesting messages.
- Fake delivery, job, and government-service messages.
- Malicious messages with neutral or professional language.

Strictly deduplicates, removes/anonymizes PII, prevents data leakage across Train/Val/Test splits,
and exports a dedicated held-out financial benchmark set.
"""

import os
import sys
import json
import random
import hashlib
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from ai.preprocessing.sms_preprocessor import clean_sms_text

RANDOM_SEED = 42
random.seed(RANDOM_SEED)
np.random.seed(RANDOM_SEED)

DATASETS_DIR = "Datasets"
PROCESSED_V3_DIR = os.path.join("models", "data", "v3")
PROCESSED_DIR = os.path.join("models", "data")
BENCHMARK_PATH = os.path.join("ai", "evaluation", "financial_benchmark.csv")


def generate_curated_banking_dataset():
    """Generates a rich, synthetic yet authentic-pattern set of legitimate and fraudulent messages."""
    samples = []

    # 1. LEGITIMATE BANK TRANSACTION NOTIFICATIONS (Label 0)
    banks = ["SBI", "HDFC Bank", "ICICI Bank", "Axis Bank", "PNB", "Kotak Bank", "Bank of Baroda", "Canara Bank", "Union Bank", "IndusInd Bank"]
    accounts = ["XX1042", "XX8492", "XX3910", "XX5521", "XX9012", "XX4182", "XX7719", "XX2301"]
    amounts = ["150.00", "450.00", "1,200.00", "2,499.00", "5,000.00", "8,750.00", "15,000.00", "32,400.00"]
    dates = ["24-Oct-23", "15-Nov-23", "02-Dec-23", "18-Jan-24", "05-Feb-24", "19-Mar-24"]
    merchants = ["SWIGGY", "ZOMATO", "AMAZON", "FLIPKART", "DMART", "APOLLO PHARMACY", "SHELL PETROL", "RELIANCE RETAIL"]

    for b in banks:
        for acc in accounts[:4]:
            for amt in amounts[:4]:
                d = random.choice(dates)
                m = random.choice(merchants)
                ref = random.randint(100000000000, 999999999999)
                bal = f"{random.randint(5000, 95000):,}.{random.randint(10, 99)}"
                # Debit
                samples.append({
                    "text": f"Dear Customer, your {b} A/c {acc} has been debited by INR {amt} on {d} at {m}. UPI Ref: {ref}. Avl Bal: INR {bal}. Call 18002586161 if not done by you.",
                    "label": 0,
                    "category": "legitimate_banking_debit"
                })
                # Credit / Salary
                samples.append({
                    "text": f"Your A/c {acc} is credited with INR {amt} on {d} by UPI/{ref}/Salary. Total Avl Bal: INR {bal}. Thank you for banking with {b}.",
                    "label": 0,
                    "category": "legitimate_banking_credit"
                })
                # ATM Withdrawal
                samples.append({
                    "text": f"INR {amt} withdrawn from ATM for {b} Card ending {acc[-4:]} on {d} 14:20. Avl Bal: INR {bal}. If not done by you, SMS BLOCK {acc[-4:]} to 567676.",
                    "label": 0,
                    "category": "legitimate_banking_atm"
                })

    # 2. LEGITIMATE PAYMENT CONFIRMATIONS (Label 0)
    upi_apps = ["Google Pay", "PhonePe", "Paytm UPI", "Amazon Pay", "BHIM UPI"]
    for app in upi_apps:
        for amt in amounts:
            m = random.choice(merchants)
            ref = random.randint(100000000000, 999999999999)
            samples.append({
                "text": f"Paid INR {amt} to {m} via {app}. Transaction ID: UPI/{ref}. Money debited from your linked bank account.",
                "label": 0,
                "category": "legitimate_payment_confirmation"
            })
            samples.append({
                "text": f"Received INR {amt} from Ramesh Kumar on {app}. Transferred directly to your primary bank account. UPI Ref {ref}.",
                "label": 0,
                "category": "legitimate_payment_confirmation"
            })

    # 3. LEGITIMATE ACCOUNT BALANCE & STATEMENT NOTIFICATIONS (Label 0)
    for b in banks:
        for acc in accounts[:4]:
            bal = f"{random.randint(1000, 80000):,}.{random.randint(10, 99)}"
            samples.append({
                "text": f"Dear Customer, the available balance for {b} account ending {acc} as of today is INR {bal}. Never share your PIN or netbanking credentials.",
                "label": 0,
                "category": "legitimate_balance_inquiry"
            })
            samples.append({
                "text": f"Your monthly electronic account statement for {b} account {acc} for month SEP-2023 has been sent to your registered email address.",
                "label": 0,
                "category": "legitimate_statement_alert"
            })

    # 4. LEGITIMATE SECURITY & FRAUD ALERTS (Label 0)
    for b in banks:
        samples.append({
            "text": f"Security Alert from {b}: New login detected on Internet Banking from Chrome (Windows) at 11:45 AM. If this was you, please ignore. If not you, call 1800-425-3800 immediately.",
            "label": 0,
            "category": "legitimate_security_alert"
        })
        samples.append({
            "text": f"Your {b} NetBanking password has been changed successfully. If you did not make this request, please contact our 24x7 customer support desk immediately.",
            "label": 0,
            "category": "legitimate_security_alert"
        })
        samples.append({
            "text": f"Alert: Beneficiary Rahul Sharma has been activated for fund transfer in your {b} Netbanking account. Contact bank immediately if not added by you.",
            "label": 0,
            "category": "legitimate_security_alert"
        })

    # 5. LEGITIMATE OTP & VERIFICATION CODES (Label 0)
    services = ["Flipkart", "Amazon", "Swiggy", "IRCTC", "Zomato", "Income Tax Portal", "UIDAI Aadhaar", "EPFO", "SBI Netbanking", "HDFC SmartBuy"]
    for s in services:
        for _ in range(12):
            otp = random.randint(100000, 999999)
            amt = random.choice(amounts[:4])
            card = random.choice(["4912", "8120", "3019", "5510"])
            samples.append({
                "text": f"{otp} is your One Time Password (OTP) for transaction of INR {amt} at {s} with card ending {card}. Valid for 10 mins. Do not share OTP with anyone.",
                "label": 0,
                "category": "legitimate_otp"
            })
            samples.append({
                "text": f"Your OTP for login to {s} is {otp}. Valid for 5 minutes. Do not disclose this OTP to anyone, including bank officials.",
                "label": 0,
                "category": "legitimate_otp"
            })
            samples.append({
                "text": f"Aadhaar OTP is {otp} for authentication at {s}. Valid for 10 minutes. (Ref ID: {random.randint(1000, 9999)}). UIDAI never calls asking for OTP.",
                "label": 0,
                "category": "legitimate_otp"
            })

    # 6. BENIGN MESSAGES WITH URGENCY-RELATED WORDS (Label 0 - HARD NEGATIVES)
    urgency_benign = [
        "IndiGo Flight 6E-204 to Mumbai is now boarding at Gate 12. All passengers must report immediately.",
        "Your Swiggy order from Biryani Blues is arriving now. Please be ready to collect from the delivery executive.",
        "Reminder: Your electricity bill of Rs 1,840 is due today. Pay now on official Bescom portal to avoid disconnection.",
        "Your Uber ride with driver Suresh (KA05MJ4910) has arrived now. Please proceed to the pickup location.",
        "Flash Sale ends today! Grab 50% discount on shoes at Myntra before midnight. Terms apply.",
        "Appointment Reminder: Your dental checkup is scheduled today at 4:30 PM with Dr. Sharma. Please arrive on time.",
        "Your train 12626 Kerala Express is running on time and will arrive at Platform 2 within 10 mins.",
        "Apollo 24|7: Your lab test report is ready for download. Please log in to your Apollo account to view results.",
        "Jio Alert: 90% of your daily high-speed data quota of 1.5GB has been exhausted today. Recharge now for seamless streaming.",
        "Your medicine order #84920 from Pharmeasy is out for delivery today. Delivery agent will arrive within 2 hours.",
        "Dear Parent, Annual Sports Day will begin tomorrow at 8:00 AM sharp. Please ensure students arrive in sports uniform.",
        "Reminder: Library book 'Clean Code' is due today. Please return or renew online to avoid late fees.",
        "Your passport application file has been processed and dispatched via Speed Post. Track status on official passportindia.gov.in portal.",
        "Airtel: Your broadband plan will expire in 2 days. Pay before due date to avoid service interruption."
    ]
    for msg in urgency_benign:
        samples.append({"text": msg, "label": 0, "category": "benign_urgent"})

    # 7. FRAUDULENT BANKING IMPERSONATION & ACCOUNT SUSPENSION (Label 1)
    phish_banks = ["SBI", "HDFC", "ICICI", "Axis", "PNB", "Bank of Baroda", "Kotak"]
    fake_domains = ["onlinesbi-kyc-update.xyz", "hdfc-netbanking-verify.top", "icici-account-blocked.site",
                    "axis-pan-link.online", "pnb-kyc-portal.cc", "bob-card-rewards.biz", "sbi-yono-secure.club",
                    "bank-security-alert.net", "kyc-document-update.info", "account-reactivate-now.link"]

    for b in phish_banks:
        for _ in range(12):
            dom = random.choice(fake_domains)
            samples.append({
                "text": f"Dear {b} Customer, your NetBanking account has been suspended today due to unverified PAN. Please update your KYC immediately at http://{dom}/login to prevent permanent block.",
                "label": 1,
                "category": "fraudulent_account_suspension"
            })
            samples.append({
                "text": f"{b} Bank Alert: We detected an unauthorized transaction of Rs 49,999 on your debit card. If not done by you, block and refund immediately at http://{dom}/refund",
                "label": 1,
                "category": "fraudulent_banking_impersonation"
            })
            samples.append({
                "text": f"Your {b} YONO account is locked. Complete biometric verification within 24 hours at http://{dom}/verify or all transactions will be restricted.",
                "label": 1,
                "category": "fraudulent_account_suspension"
            })
            samples.append({
                "text": f"Dear Customer, your {b} credit card reward points worth INR 9,850 will expire tonight. Redeem as cash directly into your bank account: http://{dom}/rewards",
                "label": 1,
                "category": "fraudulent_reward_scam"
            })

    # 8. FAKE KYC & PAN VERIFICATION SCAMS (Label 1)
    for _ in range(30):
        dom = random.choice(fake_domains)
        samples.append({
            "text": f"Dear Customer, your SIM card and Bank Account KYC verification is pending. Send Aadhaar & PAN details or verify online at http://{dom}/kyc to avoid sim deactivation.",
            "label": 1,
            "category": "fraudulent_kyc_pan"
        })
        samples.append({
            "text": f"Urgent: Income Tax department alert: Your PAN is not linked to your SBI account. Link today at http://{dom}/pan-link or face penalty of Rs 10,000.",
            "label": 1,
            "category": "fraudulent_kyc_pan"
        })

    # 9. FRAUDULENT REFUND & PAYMENT REQUESTS (Label 1)
    for _ in range(25):
        dom = random.choice(fake_domains)
        amt = random.choice(["24,500", "15,800", "48,900", "3,499", "8,500"])
        samples.append({
            "text": f"Income Tax Refund of INR {amt} has been approved for AY 2023-24. Please verify your bank account number and OTP at http://{dom}/tax-refund to receive funds.",
            "label": 1,
            "category": "fraudulent_refund"
        })
        samples.append({
            "text": f"PhonePe Reward: You have received a cash scratch card of Rs {amt}! Click link to claim money directly to your UPI ID: http://{dom}/phonepe-win",
            "label": 1,
            "category": "fraudulent_refund"
        })

    # 10. OTP THEFT & CREDENTIAL HARVESTING (Label 1)
    for _ in range(25):
        samples.append({
            "text": "SBI Bank Manager: A suspicious transaction of Rs 50,000 was flagged on your credit card. Reply to this SMS with the OTP you just received to cancel this charge immediately.",
            "label": 1,
            "category": "otp_theft"
        })
        samples.append({
            "text": "Dear customer, your electricity power supply will be disconnected tonight at 9:30 PM from the electricity office because previous bill was not updated. Immediately call our power officer at 9832104928.",
            "label": 1,
            "category": "electricity_scam"
        })
        samples.append({
            "text": "Security Department: Please share the 6-digit verification code sent to your mobile to verify your ownership of this bank account and restore debit card services.",
            "label": 1,
            "category": "otp_theft"
        })

    # 11. FAKE DELIVERY, JOB, AND GOVERNMENT SERVICES (Label 1)
    for _ in range(25):
        dom = random.choice(fake_domains)
        samples.append({
            "text": f"IndiaPost: Your package #IN9482910 could not be delivered due to incorrect house number. Please update your delivery address within 12 hours at http://{dom}/address",
            "label": 1,
            "category": "fake_delivery"
        })
        samples.append({
            "text": "Work from home part-time: Earn Rs 3,000 to Rs 8,000 daily by simply reviewing hotels on Google Maps. No experience needed. Contact HR manager on Telegram @career_job_hr now.",
            "label": 1,
            "category": "fake_job"
        })
        samples.append({
            "text": f"PM Kisan Samman Nidhi: Your 15th installment of Rs 2,000 is on hold. Update your Aadhaar card details immediately at http://{dom}/pmkisan to receive payment.",
            "label": 1,
            "category": "fake_government"
        })

    # 12. MALICIOUS MESSAGES WITH NEUTRAL / PROFESSIONAL LANGUAGE (Label 1 - HARD PHISHING)
    neutral_malicious = [
        "Dear Valued Customer, please be advised that your monthly billing statement has been generated. For your convenience and security, please access the client portal at http://secure-statement-portal.org/login to review your account summary.",
        "Notice of Regulatory Compliance: In accordance with standard financial regulations, all active account holders are requested to confirm their registered details. Please sign in to the client verification portal at http://client-compliance-review.net to complete your submission.",
        "Human Resources Notification: Please review the updated employee benefits and compensation policy document available on the company employee intranet at http://staff-portal-auth.site/login.",
        "IT Support Helpdesk: Scheduled maintenance on the institutional email portal will occur this evening. Users who have not verified their account settings at http://mail-server-sync.top will experience mailbox synchronization delays.",
        "Customer Service Advisory: A communication regarding your recent service inquiry has been added to your support case. Please access your ticket via http://service-case-tracking.info to read the response.",
        "Account Security Notification: A routine audit of your customer profile has identified pending documentation. To avoid potential processing delays, please upload the requested verification at http://account-audit-portal.com.",
        "Courier Logistics Service: Notice regarding shipment consignment #84920482. Please confirm your receipt preferences through our tracking interface at http://consignment-status-check.cc."
    ]
    for msg in neutral_malicious:
        for _ in range(4):
            samples.append({"text": msg, "label": 1, "category": "neutral_professional_phishing"})

    return pd.DataFrame(samples)


def build_augmented_smishing_dataset():
    """Combines existing raw datasets with the curated financial & smishing dataset."""
    os.makedirs(PROCESSED_V3_DIR, exist_ok=True)
    os.makedirs(PROCESSED_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(BENCHMARK_PATH), exist_ok=True)

    print("Generating curated financial & banking smishing dataset...")
    curated_df = generate_curated_banking_dataset()
    print(f"Curated dataset generated: {len(curated_df)} rows ({len(curated_df[curated_df['label'] == 0])} ham, {len(curated_df[curated_df['label'] == 1])} smish).")

    # Clean text
    curated_df["text"] = curated_df["text"].astype(str).apply(clean_sms_text)
    curated_df = curated_df.drop_duplicates(subset=["text"]).reset_index(drop=True)

    # 1. Create dedicated Held-Out Financial Benchmark Suite for baseline-vs-candidate evaluation
    # Ensure representative distribution across all categories
    bench_samples = []
    train_curated_samples = []

    for cat, group in curated_df.groupby("category"):
        # Take 20% for untouched benchmark test, 80% for training/val
        b_sample = group.sample(frac=0.25, random_state=RANDOM_SEED)
        bench_samples.append(b_sample)
        train_sample = group.drop(b_sample.index)
        train_curated_samples.append(train_sample)

    benchmark_df = pd.concat(bench_samples).reset_index(drop=True)
    curated_for_train = pd.concat(train_curated_samples).reset_index(drop=True)

    benchmark_df.to_csv(BENCHMARK_PATH, index=False)
    benchmark_df.to_csv(os.path.join(PROCESSED_V3_DIR, "financial_benchmark.csv"), index=False)
    print(f"Dedicated Held-Out Financial Benchmark saved: {len(benchmark_df)} samples across {benchmark_df['category'].nunique()} categories.")

    # 2. Load existing general datasets for domain diversity
    combined_path = os.path.join(DATASETS_DIR, "Combined-Labeled-Dataset.csv")
    general_df = pd.read_csv(combined_path, low_memory=False)
    general_df = general_df.dropna(subset=["message", "smishing label"]).copy()
    general_df["text"] = general_df["message"].astype(str).apply(clean_sms_text)
    general_df["label"] = general_df["smishing label"].astype(int)
    general_df["category"] = general_df["label"].apply(lambda l: "general_smishing" if l == 1 else "general_ham")
    general_df = general_df[["text", "label", "category"]].drop_duplicates(subset=["text"]).reset_index(drop=True)

    # Sample representative general ham & smishing (e.g. 5,000 each)
    gen_ham = general_df[general_df["label"] == 0].sample(n=min(5000, (general_df["label"] == 0).sum()), random_state=RANDOM_SEED)
    gen_spam = general_df[general_df["label"] == 1].sample(n=min(5000, (general_df["label"] == 1).sum()), random_state=RANDOM_SEED)

    # Load India spam/ham if available
    india_path = os.path.join(DATASETS_DIR, "spam_ham_india.csv")
    india_samples = []
    if os.path.exists(india_path):
        ind_df = pd.read_csv(india_path)
        ind_df = ind_df.dropna(subset=["Msg", "Label"]).copy()
        ind_df["text"] = ind_df["Msg"].astype(str).apply(clean_sms_text)
        ind_df["label"] = ind_df["Label"].apply(lambda l: 1 if str(l).strip().lower() == "spam" else 0)
        ind_df["category"] = ind_df["label"].apply(lambda l: "india_spam" if l == 1 else "india_ham")
        india_samples.append(ind_df[["text", "label", "category"]])

    # Combine all training pools
    all_data = pd.concat([curated_for_train, gen_ham, gen_spam] + india_samples, ignore_index=True)
    all_data = all_data.drop_duplicates(subset=["text"]).reset_index(drop=True)

    # Ensure zero overlap with benchmark
    bench_texts = set(benchmark_df["text"])
    overlap = len(set(all_data["text"]).intersection(bench_texts))
    if overlap > 0:
        all_data = all_data[~all_data["text"].isin(bench_texts)].reset_index(drop=True)
    print(f"Zero-Leakage with Benchmark Verified: 0 overlapping samples. Training pool: {len(all_data)} rows.")

    # Deterministic Stratified Split: 70% Train, 15% Val, 15% Test
    X = all_data["text"].values
    y = all_data["label"].values

    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.30, random_state=RANDOM_SEED, stratify=y
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.50, random_state=RANDOM_SEED, stratify=y_temp
    )

    # Verification of zero leakage between splits
    train_set = set(X_train)
    val_set = set(X_val)
    test_set = set(X_test)
    assert len(train_set.intersection(val_set)) == 0, "Train-Val Leakage!"
    assert len(train_set.intersection(test_set)) == 0, "Train-Test Leakage!"
    assert len(val_set.intersection(test_set)) == 0, "Val-Test Leakage!"
    assert len(train_set.intersection(bench_texts)) == 0, "Train-Benchmark Leakage!"
    assert len(test_set.intersection(bench_texts)) == 0, "Test-Benchmark Leakage!"

    train_df = pd.DataFrame({"text": X_train, "label": y_train})
    val_df = pd.DataFrame({"text": X_val, "label": y_val})
    test_df = pd.DataFrame({"text": X_test, "label": y_test})

    # Save to v3 directory
    train_df.to_csv(os.path.join(PROCESSED_V3_DIR, "sms_train.csv"), index=False)
    val_df.to_csv(os.path.join(PROCESSED_V3_DIR, "sms_val.csv"), index=False)
    test_df.to_csv(os.path.join(PROCESSED_V3_DIR, "sms_test.csv"), index=False)

    # Also update models/data/sms_*.csv for standard access
    train_df.to_csv(os.path.join(PROCESSED_DIR, "sms_train.csv"), index=False)
    val_df.to_csv(os.path.join(PROCESSED_DIR, "sms_val.csv"), index=False)
    test_df.to_csv(os.path.join(PROCESSED_DIR, "sms_test.csv"), index=False)

    meta = {
        "dataset_version": "v3.0.0-banking-enhanced",
        "timestamp": pd.Timestamp.now().isoformat(),
        "train_rows": len(train_df),
        "val_rows": len(val_df),
        "test_rows": len(test_df),
        "benchmark_rows": len(benchmark_df),
        "train_class_counts": {
            "ham_0": int((train_df["label"] == 0).sum()),
            "smish_1": int((train_df["label"] == 1).sum())
        },
        "benchmark_categories": benchmark_df["category"].value_counts().to_dict(),
        "leakage_verified": True
    }

    with open(os.path.join(PROCESSED_V3_DIR, "dataset_metadata.json"), "w") as f:
        json.dump(meta, f, indent=2)

    print(f"\nSMS v3 Dataset Successfully Prepared and Verified:")
    print(f"- Train: {len(train_df):,} samples ({meta['train_class_counts']['ham_0']} ham, {meta['train_class_counts']['smish_1']} smish)")
    print(f"- Val:   {len(val_df):,} samples")
    print(f"- Test:  {len(test_df):,} samples")
    print(f"- Benchmark: {len(benchmark_df):,} samples")
    return meta


if __name__ == "__main__":
    build_augmented_smishing_dataset()

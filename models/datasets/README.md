# Squish Machine Learning Training & Benchmark Datasets

This directory indexes the benchmark datasets powering the multi-stage smishing, quishing, and zero-day anomaly detection models.

## Dataset Split & Class Distribution

| Dataset File | Description | Total Samples | Benign (%) | Malicious (%) |
| :--- | :--- | :--- | :--- | :--- |
| `sms_train.csv` | SMS Smishing Training Split | **9,142** | 54.41% | 45.59% |
| `sms_val.csv` | SMS Smishing Validation Split | **1,959** | 54.42% | 45.58% |
| `sms_test.csv` | SMS Smishing Held-Out Benchmark Test Set | **1,959** | 54.42% | 45.58% |
| `url_train.csv` | URL Quishing/Phishing Training Split | **315,123** | 76.8% | 23.2% |
| `url_val.csv` | URL Quishing/Phishing Validation Split | **67,526** | 76.8% | 23.2% |
| `url_test.csv` | URL Quishing/Phishing Held-Out Benchmark Test Set | **67,527** | 76.8% | 23.2% |
| `anomaly_test.csv` | Zero-Day & Unsupervised Outlier Test Set | **10,000** | 0.0% | 100.0% |
| `adversarial_evaluation_dataset.json` | Adversarial & Evasion Benchmark (Unicode homoglyphs, zero-width chars, typo-squatting, banking SMS) | **60 cases** | - | - |

## Data Minimization & Privacy Guarantee
All raw SMS payloads in the training corpus were strictly anonymized:
- Phone numbers replaced with `<PHONE>`
- Verification tokens replaced with `<OTP>`
- Names, account numbers, and currency balances masked
- URLs parsed into structural and lexical features to avoid storing user browsing telemetry

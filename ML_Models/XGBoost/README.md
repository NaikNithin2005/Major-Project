# XGBoost URL Quishing Classifier

This folder contains the **XGBoost** URL classification model for QR code and link-based phishing (Quishing) detection in the **Squish** security system.

---

## 1. Directory Structure

```
ML_Models/XGBoost/
├── model/
│   ├── xgboost_model.pkl              # Serialized XGBoost scratch model object
│   ├── xgboost_model.json             # Serialized tree structure in JSON format
│   ├── feature_config.json            # 22-feature ordering and normalization specification
│   ├── features.py                    # Hand-crafted URL parsing and feature extraction
│   └── xgboost_scratch.py             # Pure NumPy gradient boosted regression tree implementation
├── algorithm/
│   └── xgboost_algorithm.py           # Standalone inference and evaluation engine
├── evaluation/
│   ├── evaluate_xgboost.py            # Evaluation runner script
│   ├── metrics.json                   # Serialized metrics (Accuracy, Precision, Recall, F1)
│   ├── classification_report.txt      # Scikit-learn classification report
│   ├── confusion_matrix.png           # 300-DPI confusion matrix heatmap
│   └── evaluation_results.csv         # Predictions with ground-truth and probabilities
└── README.md
```

---

## 2. Algorithm Details

The model was retrieved from the existing `umar/` folder and operates with a pure NumPy implementation of XGBoost without third-party black-box wrappers:
- **Loss Function**: Binary Logistic Log-Loss
- **Splitting Objective**: 2nd-order Taylor expansion using exact first-order gradients ($g_i$) and second-order hessians ($h_i$):
  $$\text{Gain} = \frac{1}{2} \left[ \frac{G_L^2}{H_L + \lambda} + \frac{G_R^2}{H_R + \lambda} - \frac{(G_L + G_R)^2}{H_L + H_R + \lambda} \right] - \gamma$$
- **Regularization**: $L_2$ leaf penalty ($\lambda = 1.0$)
- **Number of Estimators**: 20 gradient-boosted regression trees
- **Base Score**: ~$-1.20$ (reflecting natural class imbalance)
- **Learning Rate**: $0.1$

---

## 3. Extracted Feature Vector (22 Features)

1. `url_length`: Total character length of URL.
2. `host_length`: Length of hostname.
3. `path_length`: Length of path component.
4. `query_length`: Length of query string parameters.
5. `num_dots_host`: Number of dots in host (subdomain indicator).
6. `num_hyphens_host`: Hyphen count in hostname.
7. `num_subdomains`: Calculated subdomain depth.
8. `has_at_symbol`: Flag indicating `@` symbol (userinfo masking).
9. `has_userinfo`: Flag indicating embedded credentials.
10. `is_ip_host`: Flag indicating raw IPv4 address as host.
11. `is_https`: Flag indicating HTTPS scheme.
12. `has_port`: Flag indicating non-standard port specification.
13. `suspicious_tld`: Match against top abused TLD list (`.tk`, `.xyz`, etc.).
14. `is_shortener`: Match against URL shorteners (`bit.ly`, `tinyurl.com`, etc.).
15. `digit_ratio_host`: Ratio of numeric digits in host.
16. `digit_ratio_url`: Ratio of numeric digits in full URL.
17. `entropy_host`: Shannon entropy of host characters.
18. `entropy_url`: Shannon entropy of full URL string.
19. `num_query_params`: Count of key-value query parameters.
20. `brand_edit_distance`: Minimum Levenshtein edit distance to known target brands.
21. `path_depth`: Directory slash depth in URL path.
22. `has_double_slash_in_path`: Flag indicating redirection via embedded `//`.

---

## 4. Usage Instructions

### Run Evaluation on Test Set (9,999 Samples)
```bash
python ML_Models/XGBoost/algorithm/xgboost_algorithm.py
```
Or execute the evaluation script:
```bash
python ML_Models/XGBoost/evaluation/evaluate_xgboost.py
```

### Single URL Prediction
```bash
python ML_Models/XGBoost/algorithm/xgboost_algorithm.py --url "http://secure-paypal-verification.com/login"
```

### Batch URL Scoring
```bash
python ML_Models/XGBoost/algorithm/xgboost_algorithm.py --csv path/to/urls.csv
```


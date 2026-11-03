"""
ML_Models/common/config.py
Central configuration for all Machine Learning models in the Squish repository.
"""

import os
from pathlib import Path

# Base Paths
COMMON_DIR = Path(__file__).resolve().parent
ML_MODELS_ROOT = COMMON_DIR.parent
PROJECT_ROOT = ML_MODELS_ROOT.parent

# Subdirectory Paths
TINYBERT_DIR = ML_MODELS_ROOT / "TinyBERT"
XGBOOST_DIR = ML_MODELS_ROOT / "XGBoost"
CNN_DIR = ML_MODELS_ROOT / "CNN"
COMPARISON_DIR = ML_MODELS_ROOT / "Model_Comparison"
DATASET_DIR = ML_MODELS_ROOT / "Dataset"

# Specific Model Paths
TINYBERT_MODEL_DIR = TINYBERT_DIR / "model"
TINYBERT_ONNX_PATH = TINYBERT_MODEL_DIR / "model.onnx"
TINYBERT_CONFIG_PATH = TINYBERT_MODEL_DIR / "config.json"
TINYBERT_EVAL_DIR = TINYBERT_DIR / "evaluation"

XGBOOST_MODEL_DIR = XGBOOST_DIR / "model"
XGBOOST_PKL_PATH = XGBOOST_MODEL_DIR / "xgboost_model.pkl"
XGBOOST_JSON_PATH = XGBOOST_MODEL_DIR / "xgboost_model.json"
XGBOOST_EVAL_DIR = XGBOOST_DIR / "evaluation"

CNN_MODEL_DIR = CNN_DIR / "model"
CNN_PKL_PATH = CNN_MODEL_DIR / "cnn_model.pkl"
CNN_H5_PATH = CNN_MODEL_DIR / "cnn_model.h5"
CNN_EVAL_DIR = CNN_DIR / "evaluation"

# Dataset Paths
SMS_TRAIN_PATH = DATASET_DIR / "train" / "sms_train.csv"
SMS_VAL_PATH = DATASET_DIR / "validation" / "sms_validation.csv"
SMS_TEST_PATH = DATASET_DIR / "test" / "sms_test.csv"

URL_TRAIN_PATH = DATASET_DIR / "train" / "url_train.csv"
URL_VAL_PATH = DATASET_DIR / "validation" / "url_validation.csv"
URL_TEST_PATH = DATASET_DIR / "test" / "url_test.csv"

# Classification Mappings
TINYBERT_CLASS_MAP = {
    0: "LEGITIMATE_BANKING",
    1: "LEGITIMATE_GENERAL",
    2: "SMISHING",
    3: "SUSPICIOUS"
}

URL_CLASS_MAP = {
    0: "legitimate",
    1: "phishing"
}

# Global Settings
RANDOM_SEED = 42
MAX_SEQUENCE_LENGTH = 128
DEFAULT_PROB_THRESHOLD = 0.5


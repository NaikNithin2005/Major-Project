"""
ML_Models/common/preprocessing.py
Unified preprocessing pipelines for SMS and URL inputs across TinyBERT, XGBoost, and CNN.
"""

import math
import re
from urllib.parse import urlsplit
import numpy as np

# -----------------------------------------------------------------------------
# 1. URL FEATURE EXTRACTION (Used by XGBoost)
# -----------------------------------------------------------------------------

KNOWN_BRAND_DOMAINS = [
    "paypal.com", "google.com", "microsoft.com", "apple.com", "amazon.com",
    "facebook.com", "instagram.com", "netflix.com", "bankofamerica.com",
    "chase.com", "hdfcbank.com", "icicibank.com", "sbi.co.in", "whatsapp.com",
]

SUSPICIOUS_TLDS = {"tk", "ml", "ga", "cf", "gq", "xyz", "top", "loan", "click", "work"}
SHORTENER_HOSTS = {"bit.ly", "tinyurl.com", "goo.gl", "t.co", "ow.ly", "is.gd", "cutt.ly", "rebrand.ly"}

FEATURE_ORDER = [
    "url_length", "host_length", "path_length", "query_length",
    "num_dots_host", "num_hyphens_host", "num_subdomains", "has_at_symbol",
    "has_userinfo", "is_ip_host", "is_https", "has_port", "suspicious_tld",
    "is_shortener", "digit_ratio_host", "digit_ratio_url", "entropy_host",
    "entropy_url", "num_query_params", "brand_edit_distance", "path_depth",
    "has_double_slash_in_path",
]


def manual_split_url(url: str):
    url = url.strip()
    cleaned_url = re.sub(r'\[([^\]]*[^\:\da-fa-fA-F][^\]]*)\]', r'\1', url)
    try:
        parts = urlsplit(cleaned_url)
    except ValueError:
        if "://" in cleaned_url:
            cleaned_url = "http://" + cleaned_url.split("://", 1)[1]
        else:
            cleaned_url = "http://" + cleaned_url
        try:
            parts = urlsplit(cleaned_url)
        except Exception:
            parts = urlsplit("http://unknown-invalid-host/")
    
    scheme = parts.scheme or ""
    netloc = parts.netloc or ""
    path = parts.path or ""
    query = parts.query or ""

    userinfo, host, port = "", netloc, None
    if "@" in netloc:
        userinfo, host = netloc.split("@", 1)

    if ":" in host:
        raw_host, potential_port = host.rsplit(":", 1)
        if potential_port.isdigit():
            host = raw_host
            port = int(potential_port)

    return {
        "scheme": scheme.lower(),
        "userinfo": userinfo,
        "host": host.lower(),
        "port": port,
        "path": path,
        "query": query,
    }


def is_ip_address(host: str) -> bool:
    ipv4_pattern = r"^\d{1,3}(\.\d{1,3}){3}$"
    if re.match(ipv4_pattern, host):
        parts = host.split(".")
        return all(0 <= int(p) <= 255 for p in parts)
    return False


def shannon_entropy(s: str) -> float:
    if not s:
        return 0.0
    freqs = {}
    for c in s:
        freqs[c] = freqs.get(c, 0) + 1
    n = len(s)
    return -sum((cnt / n) * math.log2(cnt / n) for cnt in freqs.values())


def levenshtein(a: str, b: str) -> int:
    dp = list(range(len(b) + 1))
    for i, ca in enumerate(a, start=1):
        new_dp = [i] + [0] * len(b)
        for j, cb in enumerate(b, start=1):
            cost = 0 if ca == cb else 1
            new_dp[j] = min(dp[j] + 1, new_dp[j - 1] + 1, dp[j - 1] + cost)
        dp = new_dp
    return dp[-1]


def min_brand_distance(host: str) -> int:
    parts = host.split(".")
    candidates = [host]
    if len(parts) >= 2:
        candidates.append(".".join(parts[-2:]))
        candidates.append(parts[-2])
    best = 999
    for c in candidates:
        for brand in KNOWN_BRAND_DOMAINS:
            dist = levenshtein(c, brand)
            if dist < best:
                best = dist
    return best


def extract_features(url: str) -> dict:
    parts = manual_split_url(url)
    scheme = parts["scheme"]
    userinfo = parts["userinfo"]
    host = parts["host"]
    port = parts["port"]
    path = parts["path"]
    query = parts["query"]

    url_len = len(url)
    host_len = len(host)
    path_len = len(path)
    query_len = len(query)

    dots_host = host.count(".")
    hyphens_host = host.count("-")
    subdomains = max(0, dots_host - 1)
    has_at = 1 if "@" in url else 0
    has_uinfo = 1 if userinfo else 0
    ip_host = 1 if is_ip_address(host) else 0
    is_sec = 1 if scheme == "https" else 0
    has_p = 1 if port is not None else 0

    tld = host.split(".")[-1] if "." in host else ""
    susp_tld = 1 if tld in SUSPICIOUS_TLDS else 0
    shortener = 1 if host in SHORTENER_HOSTS else 0

    d_host = sum(c.isdigit() for c in host)
    d_url = sum(c.isdigit() for c in url)
    d_ratio_host = d_host / host_len if host_len else 0.0
    d_ratio_url = d_url / url_len if url_len else 0.0

    ent_host = shannon_entropy(host)
    ent_url = shannon_entropy(url)

    n_params = len(query.split("&")) if query else 0
    brand_dist = min_brand_distance(host)
    depth = len([seg for seg in path.split("/") if seg])
    double_slash = 1 if "//" in path else 0

    return {
        "url_length": float(url_len),
        "host_length": float(host_len),
        "path_length": float(path_len),
        "query_length": float(query_len),
        "num_dots_host": float(dots_host),
        "num_hyphens_host": float(hyphens_host),
        "num_subdomains": float(subdomains),
        "has_at_symbol": float(has_at),
        "has_userinfo": float(has_uinfo),
        "is_ip_host": float(ip_host),
        "is_https": float(is_sec),
        "has_port": float(has_p),
        "suspicious_tld": float(susp_tld),
        "is_shortener": float(shortener),
        "digit_ratio_host": float(d_ratio_host),
        "digit_ratio_url": float(d_ratio_url),
        "entropy_host": float(ent_host),
        "entropy_url": float(ent_url),
        "num_query_params": float(n_params),
        "brand_edit_distance": float(brand_dist),
        "path_depth": float(depth),
        "has_double_slash_in_path": float(double_slash),
    }


def featurize_to_vector(url: str, feature_order=None) -> list:
    fdict = extract_features(url)
    order = feature_order or FEATURE_ORDER
    return [fdict[k] for k in order]


# -----------------------------------------------------------------------------
# 2. CHARACTER-LEVEL URL ENCODING (Used by CNN)
# -----------------------------------------------------------------------------

VOCAB = list("abcdefghijklmnopqrstuvwxyz0123456789:/.-_?=&@%")
CHAR_TO_IDX = {c: i + 1 for i, c in enumerate(VOCAB)}  # 0 reserved for padding
VOCAB_SIZE = len(VOCAB) + 1
DEFAULT_MAX_URL_LEN = 128


def encode_url(url: str, max_len: int = DEFAULT_MAX_URL_LEN) -> np.ndarray:
    url = url.lower()[:max_len]
    ids = [CHAR_TO_IDX.get(c, 0) for c in url]
    ids += [0] * (max_len - len(ids))
    return np.array(ids, dtype=np.int64)


# -----------------------------------------------------------------------------
# 3. SMS TEXT PREPROCESSING (Used by TinyBERT)
# -----------------------------------------------------------------------------

def clean_sms_text(text: str) -> str:
    """Preprocesses and normalizes raw SMS text for TinyBERT tokenization."""
    if not isinstance(text, str):
        return ""
    text = text.strip()
    # Normalize excessive whitespaces
    text = re.sub(r'\s+', ' ', text)
    return text


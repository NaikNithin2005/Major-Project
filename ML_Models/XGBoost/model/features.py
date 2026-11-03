"""
features.py - Feature extraction using urllib.parse and manual metrics.
"""
import math
from urllib.parse import urlsplit
import re


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
    
    # Pre-clean invalid bracketed hosts (e.g., http://[.]/ or http://[invalid]/)
    cleaned_url = re.sub(r'\[([^\]]*[^\:\da-fa-fA-F][^\]]*)\]', r'\1', url)

    try:
        parts = urlsplit(cleaned_url)
    except ValueError:
        # Fallback if urllib still rejects severe parsing edge cases
        if "://" in cleaned_url:
            cleaned_url = "http://" + cleaned_url.split("://", 1)[1]
        else:
            cleaned_url = "http://" + cleaned_url
        try:
            parts = urlsplit(cleaned_url)
        except ValueError:
            return {
                "scheme": "",
                "userinfo": "",
                "host": "",
                "path": cleaned_url,
                "query": "",
                "port": "",
            }

    if not parts.netloc and "://" not in cleaned_url and cleaned_url:
        try:
            parts = urlsplit("//" + cleaned_url)
        except ValueError:
            pass

    userinfo = ""
    try:
        if parts.username:
            userinfo = parts.username + (":" + parts.password if parts.password else "")
    except (ValueError, Exception):
        userinfo = ""

    # Extract port safely without triggering parts.port (which forces int casting)
    port = ""
    try:
        netloc = parts.netloc or ""
        if ":" in netloc:
            possible_port = netloc.split(":")[-1].split("@")[-1]
            if possible_port.isdigit():
                port = possible_port
    except Exception:
        port = ""

    # Extract host safely without triggering parts.hostname validation errors
    host = ""
    try:
        host = (parts.hostname or "").lower()
    except ValueError:
        # Fallback for unparseable hostnames
        if parts.netloc:
            host = parts.netloc.split("@")[-1].split(":")[0].lower()

    return {
        "scheme": (parts.scheme or "").lower(),
        "userinfo": userinfo,
        "host": host,
        "path": parts.path or "",
        "query": parts.query or "",
        "port": port,
    }

def is_ip_literal(host: str) -> bool:
    parts = host.split(".")
    return len(parts) == 4 and all(p.isdigit() and 0 <= int(p) <= 255 for p in parts)

def get_tld(host: str) -> str:
    return host.split(".")[-1] if host else ""

def get_registrable_domain(host: str) -> str:
    parts = host.split(".")
    return ".".join(parts[-2:]) if len(parts) >= 2 else host

def char_entropy(s: str) -> float:
    if not s: return 0.0
    freq = {ch: s.count(ch) for ch in set(s)}
    n = len(s)
    return -sum((c / n) * math.log2(c / n) for c in freq.values())

def levenshtein(a: str, b: str) -> int:
    la, lb = len(a), len(b)
    if la == 0: return lb
    if lb == 0: return la
    prev = list(range(lb + 1))
    for i in range(1, la + 1):
        curr = [i] + [0] * lb
        for j in range(1, lb + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            curr[j] = min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        prev = curr
    return prev[lb]

def min_brand_distance(reg_domain: str):
    best_dist = min((levenshtein(reg_domain, b) for b in KNOWN_BRAND_DOMAINS), default=99)
    return best_dist

def featurize_to_vector(url: str):
    parts = manual_split_url(url)
    host, path, query = parts["host"], parts["path"], parts["query"]
    reg_domain = get_registrable_domain(host)
    tld = get_tld(host)
    
    feats = {
        "url_length": len(url),
        "host_length": len(host),
        "path_length": len(path),
        "query_length": len(query),
        "num_dots_host": host.count("."),
        "num_hyphens_host": host.count("-"),
        "num_subdomains": max(0, host.count(".") - 1),
        "has_at_symbol": 1 if "@" in url else 0,
        "has_userinfo": 1 if parts["userinfo"] else 0,
        "is_ip_host": 1 if is_ip_literal(host) else 0,
        "is_https": 1 if parts["scheme"] == "https" else 0,
        "has_port": 1 if parts["port"] else 0,
        "suspicious_tld": 1 if tld in SUSPICIOUS_TLDS else 0,
        "is_shortener": 1 if host in SHORTENER_HOSTS else 0,
        "digit_ratio_host": (sum(c.isdigit() for c in host) / len(host)) if host else 0.0,
        "digit_ratio_url": (sum(c.isdigit() for c in url) / len(url)) if url else 0.0,
        "entropy_host": char_entropy(host),
        "entropy_url": char_entropy(url),
        "num_query_params": query.count("&") + 1 if query else 0,
        "brand_edit_distance": min_brand_distance(reg_domain),
        "path_depth": path.count("/"),
        "has_double_slash_in_path": 1 if "//" in path else 0,
    }
    return [feats[k] for k in FEATURE_ORDER]
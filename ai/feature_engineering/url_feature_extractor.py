import math
import re
from urllib.parse import urlparse

SUSPICIOUS_TLDS = {".xyz", ".top", ".site", ".online", ".cc", ".tk", ".zip", ".club", ".link", ".tech", ".store", ".info"}

FEATURE_NAMES = [
    "url_length",
    "domain_length",
    "subdomain_count",
    "path_length",
    "query_length",
    "has_ip",
    "has_at_symbol",
    "has_double_slash_redirect",
    "has_dash_in_domain",
    "suspicious_tld",
    "digit_count",
    "special_char_count",
    "entropy",
    "https_enabled",
    "port_specified",
    "encoding_present"
]

def calculate_shannon_entropy(text: str) -> float:
    if not text:
        return 0.0
    prob = [float(text.count(c)) / len(text) for c in set(text)]
    return round(-sum(p * math.log2(p) for p in prob), 4)

def extract_url_features(url: str) -> dict:
    """
    Extracts 16 numerical/categorical features from a URL string.
    Safely handles malformed URLs, invalid ports, and IPv6 formatting errors.
    Aligned with Kotlin Phase 4 UrlFeatureExtractor.
    """
    if not url or not isinstance(url, str):
        return {name: 0.0 for name in FEATURE_NAMES}

    trimmed = url.strip()
    if not (trimmed.startswith("http://") or trimmed.startswith("https://")):
        trimmed = "http://" + trimmed

    try:
        parsed = urlparse(trimmed)
        hostname = parsed.hostname or ""
        path = parsed.path or ""
        query = parsed.query or ""
        scheme = parsed.scheme or ""
        has_port_err = False
        try:
            port_specified = 1.0 if parsed.port is not None else 0.0
        except ValueError:
            port_specified = 1.0
            has_port_err = True
    except Exception:
        hostname = ""
        path = ""
        query = ""
        scheme = "http"
        port_specified = 0.0

    # Feature 1: URL length
    url_length = float(len(url))

    # Feature 2: Domain length
    domain_length = float(len(hostname))

    # Feature 3: Subdomain count
    dots = hostname.count(".")
    subdomain_count = float(max(0, dots - 1)) if dots > 0 else 0.0

    # Feature 4: Path length
    path_length = float(len(path))

    # Feature 5: Query length
    query_length = float(len(query))

    # Feature 6: Has IP address
    ip_pattern = r'^(?:\d{1,3}\.){3}\d{1,3}$'
    has_ip = 1.0 if re.match(ip_pattern, hostname) else 0.0

    # Feature 7: Has @ symbol
    has_at_symbol = 1.0 if "@" in url else 0.0

    # Feature 8: Double slash redirect
    has_double_slash_redirect = 1.0 if "//" in trimmed[8:] else 0.0

    # Feature 9: Dash in domain
    has_dash_in_domain = 1.0 if "-" in hostname else 0.0

    # Feature 10: Suspicious TLD
    suspicious_tld = 1.0 if any(hostname.lower().endswith(tld) for tld in SUSPICIOUS_TLDS) else 0.0

    # Feature 11: Digit count
    digit_count = float(sum(c.isdigit() for c in url))

    # Feature 12: Special char count
    special_chars = set("-_@?=%&~#$!,;:*+[](){}<>")
    special_char_count = float(sum(c in special_chars for c in url))

    # Feature 13: Entropy
    entropy = calculate_shannon_entropy(url)

    # Feature 14: HTTPS enabled
    https_enabled = 1.0 if scheme.lower() == "https" else 0.0

    # Feature 15: Port specified (assigned above)

    # Feature 16: Encoding present (%xx)
    encoding_present = 1.0 if "%" in url else 0.0

    return {
        "url_length": url_length,
        "domain_length": domain_length,
        "subdomain_count": subdomain_count,
        "path_length": path_length,
        "query_length": query_length,
        "has_ip": has_ip,
        "has_at_symbol": has_at_symbol,
        "has_double_slash_redirect": has_double_slash_redirect,
        "has_dash_in_domain": has_dash_in_domain,
        "suspicious_tld": suspicious_tld,
        "digit_count": digit_count,
        "special_char_count": special_char_count,
        "entropy": entropy,
        "https_enabled": https_enabled,
        "port_specified": port_specified,
        "encoding_present": encoding_present
    }

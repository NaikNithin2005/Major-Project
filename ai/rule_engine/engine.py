"""
Rule Engine - Phase 6
Deterministic, testable evaluation of observable security indicators across SMS, URL, and QR payloads.
Produces structured, evidence-backed RuleResults.
"""

import re
from typing import Dict, List, Any, Optional
from urllib.parse import urlparse

from ai.risk_engine.contracts import RuleResult, EvidenceSource

SHORTENER_DOMAINS = {
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly",
    "buff.ly", "cutt.ly", "rebrand.ly", "tiny.cc", "rb.gy"
}

SUSPICIOUS_SCHEMES = {"javascript:", "data:", "intent:", "smsto:", "market:", "tel:"}

URGENCY_KEYWORDS = [
    "immediately", "urgent", "24 hours", "24hrs", "today", "now", "expires today",
    "suspended immediately", "final notice", "within 10 mins", "action required",
    "account suspended", "blocked", "deactivated", "compromised", "unauthorized"
]

BANKING_KEYWORDS = [
    "kyc", "bank", "account", "otp", "debit", "credit", "card", "refund", "upi",
    "pan", "aadhar", "sbi", "hdfc", "icici", "axis", "pnb", "bob", "cvv", "pin",
    "transaction", "netbanking"
]

SUSPICIOUS_TLDS = {
    ".xyz", ".top", ".site", ".online", ".cc", ".tk", ".zip", ".club",
    ".link", ".tech", ".store", ".info", ".buzz", ".loan", ".work", ".gq", ".cf"
}


class RuleEngine:
    """
    Evaluates deterministic security rules on extracted features and raw metadata.
    Does not produce fabricated explanations.
    """

    def evaluate(
        self,
        url: Optional[str] = None,
        url_features: Optional[Dict[str, float]] = None,
        sms_text: Optional[str] = None,
        sender: Optional[str] = None,
        qr_payload: Optional[str] = None,
    ) -> List[RuleResult]:
        results: List[RuleResult] = []

        # 1. URL Rules
        if url:
            self._evaluate_url_rules(url, url_features, results)

        # 2. SMS Rules
        if sms_text:
            self._evaluate_sms_rules(sms_text, sender, results)

        # 3. QR Payload Rules
        if qr_payload:
            self._evaluate_qr_rules(qr_payload, results)

        return results

    def _evaluate_url_rules(
        self,
        url: str,
        features: Optional[Dict[str, float]],
        results: List[RuleResult]
    ):
        raw_url = url.strip()
        lower_url = raw_url.lower()

        # Rule: Missing HTTPS
        if lower_url.startswith("http://"):
            results.append(
                RuleResult(
                    id="RULE_MISSING_HTTPS",
                    category="NETWORK_SECURITY",
                    severity="MEDIUM",
                    description="Insecure communication protocol (Missing HTTPS).",
                    score_contribution=15.0,
                    evidence=f"URL scheme is unencrypted HTTP: {raw_url[:35]}...",
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Parse URL
        target_to_parse = raw_url if "://" in raw_url else "http://" + raw_url
        try:
            parsed = urlparse(target_to_parse)
            hostname = (parsed.hostname or "").lower()
            port = parsed.port
        except Exception:
            hostname = ""
            port = None

        # Rule: IP-based Hostname
        is_ip = False
        if features and features.get("has_ip", 0.0) == 1.0:
            is_ip = True
        elif hostname and re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", hostname):
            is_ip = True

        if is_ip:
            results.append(
                RuleResult(
                    id="RULE_IP_HOSTNAME",
                    category="DOMAIN_REPUTATION",
                    severity="HIGH",
                    description="Host is directly addressed via numeric IP address instead of registered domain.",
                    score_contribution=25.0,
                    evidence=f"Numeric IP hostname detected: {hostname}",
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Rule: Punycode / Homograph Attack
        if "xn--" in hostname or (features and features.get("encoding_present", 0.0) == 1.0):
            results.append(
                RuleResult(
                    id="RULE_PUNYCODE_HOMOGRAPH",
                    category="DOMAIN_REPUTATION",
                    severity="HIGH",
                    description="Punycode/IDN homograph indicator detected; possible visual domain spoofing.",
                    score_contribution=25.0,
                    evidence=f"Punycode encoded domain structure: {hostname}",
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Rule: Unusual Port
        if port is not None and port not in (80, 443):
            results.append(
                RuleResult(
                    id="RULE_UNUSUAL_PORT",
                    category="NETWORK_SECURITY",
                    severity="MEDIUM",
                    description=f"Non-standard web port ({port}) specified in destination address.",
                    score_contribution=15.0,
                    evidence=f"Explicit non-standard port: {port}",
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Rule: Excessive Subdomains
        subdomain_count = 0
        if features and "subdomain_count" in features:
            subdomain_count = int(features["subdomain_count"])
        elif hostname:
            dots = hostname.count(".")
            subdomain_count = max(0, dots - 1) if not is_ip else 0

        if subdomain_count > 2:
            results.append(
                RuleResult(
                    id="RULE_EXCESSIVE_SUBDOMAINS",
                    category="DOMAIN_STRUCTURE",
                    severity="MEDIUM",
                    description=f"Excessive subdomain depth ({subdomain_count} levels); frequently used to mask destination domain.",
                    score_contribution=15.0,
                    evidence=f"Subdomain count: {subdomain_count} levels in {hostname}",
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Rule: Suspicious Symbols (@, //, %2f, excess hyphens)
        has_symbols = False
        sym_evidence = []
        if "@" in raw_url:
            has_symbols = True
            sym_evidence.append("embedded '@' credential symbol")
        if "//" in raw_url[8:]:  # beyond scheme
            has_symbols = True
            sym_evidence.append("multiple redirection slashes '//'")
        if hostname.count("-") > 2:
            has_symbols = True
            sym_evidence.append(f"excessive hyphens ({hostname.count('-')}) in domain")

        if has_symbols:
            results.append(
                RuleResult(
                    id="RULE_SUSPICIOUS_SYMBOLS",
                    category="URL_STRUCTURE",
                    severity="MEDIUM",
                    description="URL contains suspicious symbols or characters designed to deceive path parsing.",
                    score_contribution=15.0,
                    evidence=", ".join(sym_evidence),
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Rule: Shortened URL
        base_host = hostname.split(":")[-1]
        for shortener in SHORTENER_DOMAINS:
            if hostname == shortener or hostname.endswith("." + shortener):
                results.append(
                    RuleResult(
                        id="RULE_SHORTENED_URL",
                        category="DOMAIN_REPUTATION",
                        severity="LOW",
                        description="URL shortening service obscures true target destination.",
                        score_contribution=10.0,
                        evidence=f"Known shortening domain: {hostname}",
                        source=EvidenceSource.URL_FEATURE
                    )
                )
                break

        # Rule: Suspicious TLD
        for tld in SUSPICIOUS_TLDS:
            if hostname.endswith(tld):
                results.append(
                    RuleResult(
                        id="RULE_SUSPICIOUS_TLD",
                        category="DOMAIN_REPUTATION",
                        severity="MEDIUM",
                        description=f"Domain registered under high-abuse Top-Level Domain ({tld}).",
                        score_contribution=15.0,
                        evidence=f"TLD matched high-abuse list: {tld}",
                        source=EvidenceSource.URL_FEATURE
                    )
                )
                break

        # Rule: High Shannon Entropy
        entropy = features.get("entropy", 0.0) if features else 0.0
        if entropy > 4.2:
            results.append(
                RuleResult(
                    id="RULE_HIGH_ENTROPY",
                    category="URL_STRUCTURE",
                    severity="LOW",
                    description="High character randomness/entropy; characteristic of algorithmically generated domain (DGA).",
                    score_contribution=10.0,
                    evidence=f"Shannon entropy score: {entropy:.2f} (threshold: 4.2)",
                    source=EvidenceSource.URL_FEATURE
                )
            )

        # Rule: Extreme URL Length
        if len(raw_url) > 75:
            results.append(
                RuleResult(
                    id="RULE_LONG_URL",
                    category="URL_STRUCTURE",
                    severity="LOW",
                    description=f"Abnormally long URL length ({len(raw_url)} characters); used to conceal redirect parameters.",
                    score_contribution=10.0,
                    evidence=f"Length is {len(raw_url)} characters",
                    source=EvidenceSource.URL_FEATURE
                )
            )

    def _evaluate_sms_rules(
        self,
        sms_text: str,
        sender: Optional[str],
        results: List[RuleResult]
    ):
        clean_text = sms_text.lower()

        # Rule: Unknown or Suspicious Sender Pattern
        if sender:
            s_clean = sender.strip()
            # Alphanumeric shortcode without standard verified prefix or international unknown format
            if len(s_clean) >= 4 and not s_clean.startswith("+") and not s_clean.isdigit():
                # Check for spoofed bank/company lookalikes
                if re.search(r"[0-9]", s_clean) and re.search(r"[a-zA-Z]", s_clean):
                    results.append(
                        RuleResult(
                            id="RULE_SUSPICIOUS_SENDER_HEADER",
                            category="SENDER_REPUTATION",
                            severity="MEDIUM",
                            description="Sender ID uses mixed alphanumeric string typical of unverified bulk SMS gateways.",
                            score_contribution=15.0,
                            evidence=f"Sender header: {s_clean}",
                            source=EvidenceSource.SMS_FEATURE
                        )
                    )
            elif s_clean.startswith("+") and len(s_clean) > 8:
                # Private mobile number claiming organizational notifications
                pass

        # Rule: Urgency Language
        detected_urgency = [w for w in URGENCY_KEYWORDS if w in clean_text]
        if detected_urgency:
            results.append(
                RuleResult(
                    id="RULE_URGENCY_LANGUAGE",
                    category="SOCIAL_ENGINEERING",
                    severity="HIGH",
                    description="Message employs psychological pressure / urgency language forcing rapid action.",
                    score_contribution=25.0,
                    evidence=f"Urgency phrases detected: {', '.join(detected_urgency[:4])}",
                    source=EvidenceSource.SMS_FEATURE
                )
            )

        # Rule: Banking / Payment Context
        detected_banking = [w for w in BANKING_KEYWORDS if w in clean_text]
        if detected_banking:
            results.append(
                RuleResult(
                    id="RULE_BANKING_CONTEXT",
                    category="FINANCIAL_RISK",
                    severity="HIGH",
                    description="Message references financial, banking, or credential verification operations.",
                    score_contribution=20.0,
                    evidence=f"Financial keywords detected: {', '.join(detected_banking[:4])}",
                    source=EvidenceSource.SMS_FEATURE
                )
            )

    def _evaluate_qr_rules(
        self,
        qr_payload: str,
        results: List[RuleResult]
    ):
        raw = qr_payload.strip()
        lower = raw.lower()

        # Check for dangerous non-URL schemes
        for scheme in SUSPICIOUS_SCHEMES:
            if lower.startswith(scheme):
                results.append(
                    RuleResult(
                        id="RULE_SUSPICIOUS_QR_SCHEME",
                        category="PAYLOAD_SAFETY",
                        severity="CRITICAL",
                        description=f"QR payload uses dangerous protocol scheme ({scheme}) capable of executing arbitrary actions.",
                        score_contribution=50.0,
                        evidence=f"Direct scheme execution: {scheme}",
                        source=EvidenceSource.QR_FEATURE
                    )
                )
                break

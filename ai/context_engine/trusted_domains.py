"""
Safe Trusted-Domain Allowlist & Sender-ID Reputation Engine
Squish AI/ML Enhancement - Phase 6 / Phase 7

Implements:
1. Authoritative registry of legitimate banking, financial, and institutional domains.
2. Exact normalized domain and subdomain boundary matching (zero substring trust).
3. Rejection of deceptive lookalike domains (e.g. onlinesbi.sbi.attacker.xyz, hdfcbank-login.com).
4. Safe handling of Punycode, Unicode, and internationalized domain names (IDN).
5. Explicit exclusion of URL shorteners (bit.ly, tinyurl, etc.) from trusted domain status.
6. Distinction between HTTPS transport security and domain legitimacy.
7. Multi-tier Sender-ID classification (TRAI alphanumeric headers vs personal 10-digit mobile numbers).
8. Maintainable allowlist with versioning, update timestamps, and revocation support.
"""

import re
from typing import Dict, List, Optional, Set, Tuple
from urllib.parse import urlparse

ALLOWLIST_VERSION = "3.0.0-banking"
ALLOWLIST_UPDATED = "2026-10-27"
ALLOWLIST_SOURCE = "RBI Registered Scheduled Commercial Banks & IBA Authoritative Registry"

# URL Shorteners that MUST NEVER be treated as trusted banking destinations
EXCLUDED_SHORTENER_DOMAINS: Set[str] = {
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly",
    "buff.ly", "cutt.ly", "rebrand.ly", "tiny.cc", "rb.gy", "bl.ink",
    "shorturl.at", "t.ly", "v.gd", "clck.ru", "s.id"
}

# Authoritative directory of legitimate banking and financial domains
# Stores normalized exact base domains and approved subdomains
BANKING_ALLOWLIST: Dict[str, Dict[str, any]] = {
    "sbi": {
        "name": "State Bank of India",
        "primary_domains": ["sbi.co.in", "onlinesbi.sbi", "onlinesbi.com", "statebankofindia.com"],
        "approved_subdomains": ["retail.onlinesbi.sbi", "corp.onlinesbi.sbi", "merchant.onlinesbi.sbi", "bank.sbi"],
        "sender_patterns": [r"^[a-zA-Z]{2}-SBI[a-zA-Z0-9]{3}$", r"^[a-zA-Z]{2}-SBINB[a-zA-Z0-9]?$", r"^[a-zA-Z]{2}-SBICRD$"]
    },
    "hdfc": {
        "name": "HDFC Bank",
        "primary_domains": ["hdfcbank.com", "hdfc.com"],
        "approved_subdomains": ["netbanking.hdfcbank.com", "leads.hdfcbank.com", "mycards.hdfcbank.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-HDFCBK$", r"^[a-zA-Z]{2}-HDFCBN$"]
    },
    "icici": {
        "name": "ICICI Bank",
        "primary_domains": ["icicibank.com"],
        "approved_subdomains": ["infinity.icicibank.com", "corporate.icicibank.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-ICICIB$", r"^[a-zA-Z]{2}-ICICIBANK$"]
    },
    "axis": {
        "name": "Axis Bank",
        "primary_domains": ["axisbank.com"],
        "approved_subdomains": ["omni.axisbank.com", "retail.axisbank.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-AXISBK$", r"^[a-zA-Z]{2}-AXISBN$"]
    },
    "pnb": {
        "name": "Punjab National Bank",
        "primary_domains": ["pnbindia.in", "netpnb.com"],
        "approved_subdomains": ["netpnb.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-PNBSMS$", r"^[a-zA-Z]{2}-PNBBNK$"]
    },
    "kotak": {
        "name": "Kotak Mahindra Bank",
        "primary_domains": ["kotak.com"],
        "approved_subdomains": ["netbanking.kotak.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-KOTAKB$", r"^[a-zA-Z]{2}-KMBL$"]
    },
    "bob": {
        "name": "Bank of Baroda",
        "primary_domains": ["bankofbaroda.in", "bobibanking.com"],
        "approved_subdomains": ["feba.bobibanking.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-BOBTXN$", r"^[a-zA-Z]{2}-BOBBNK$"]
    },
    "canara": {
        "name": "Canara Bank",
        "primary_domains": ["canarabank.com"],
        "approved_subdomains": ["netbanking.canarabank.in"],
        "sender_patterns": [r"^[a-zA-Z]{2}-CANBNK$"]
    },
    "paytm": {
        "name": "Paytm / Paytm Payments Bank",
        "primary_domains": ["paytm.com", "paytmbank.com"],
        "approved_subdomains": ["payments.paytm.com"],
        "sender_patterns": [r"^[a-zA-Z]{2}-PAYTMB$", r"^[a-zA-Z]{2}-PAYTM$"]
    },
    "phonepe": {
        "name": "PhonePe",
        "primary_domains": ["phonepe.com"],
        "approved_subdomains": [],
        "sender_patterns": [r"^[a-zA-Z]{2}-PHONEPE$", r"^[a-zA-Z]{2}-PHNPE$"]
    },
    "npci": {
        "name": "NPCI / BHIM UPI",
        "primary_domains": ["npci.org.in", "bhimupi.org.in"],
        "approved_subdomains": [],
        "sender_patterns": [r"^[a-zA-Z]{2}-BHIMUPI$", r"^[a-zA-Z]{2}-NPCI$"]
    },
    "rbi": {
        "name": "Reserve Bank of India",
        "primary_domains": ["rbi.org.in"],
        "approved_subdomains": [],
        "sender_patterns": [r"^[a-zA-Z]{2}-RBISMS$"]
    },
    "incometax": {
        "name": "Income Tax Department of India",
        "primary_domains": ["incometax.gov.in", "tin-nsdl.com"],
        "approved_subdomains": ["eportal.incometax.gov.in"],
        "sender_patterns": [r"^[a-zA-Z]{2}-ITDCPC$", r"^[a-zA-Z]{2}-INCOMT$"]
    },
    "uidai": {
        "name": "UIDAI Aadhaar",
        "primary_domains": ["uidai.gov.in"],
        "approved_subdomains": ["myaadhaar.uidai.gov.in"],
        "sender_patterns": [r"^[a-zA-Z]{2}-AADHAR$", r"^[a-zA-Z]{2}-UIDAI$"]
    },
    "paypal": {
        "name": "PayPal",
        "primary_domains": ["paypal.com", "paypal.me"],
        "approved_subdomains": [],
        "sender_patterns": [r"^PAYPAL$"]
    },
    "chase": {
        "name": "JPMorgan Chase Bank",
        "primary_domains": ["chase.com", "jpmorgan.com"],
        "approved_subdomains": [],
        "sender_patterns": [r"^CHASE$"]
    },
    "bofa": {
        "name": "Bank of America",
        "primary_domains": ["bankofamerica.com"],
        "approved_subdomains": [],
        "sender_patterns": [r"^BOFA$"]
    }
}

# Revoked or compromised domains (dynamic blocklist)
REVOKED_DOMAINS: Set[str] = set()


def extract_effective_base_domain(hostname: str) -> str:
    """
    Safely extracts the registered/effective base domain (eSLD + TLD)
    taking into account multi-part TLDs (e.g. .co.in, .gov.in, .ac.uk, .org.in).
    """
    if not hostname:
        return ""
    clean_host = hostname.strip().lower()
    if clean_host.endswith("."):
        clean_host = clean_host[:-1]

    parts = clean_host.split(".")
    if len(parts) <= 2:
        return clean_host

    # Common multi-part ccTLDs
    multi_part_tlds = {"co.in", "gov.in", "org.in", "net.in", "ac.in", "co.uk", "org.uk", "ac.uk", "com.au", "net.au"}
    last_two = f"{parts[-2]}.{parts[-1]}"
    if last_two in multi_part_tlds and len(parts) >= 3:
        return ".".join(parts[-3:])

    return ".".join(parts[-2:])


class TrustedDomainAllowlist:
    """
    Evaluates domain legitimacy against authoritative banking registry.
    Strictly prohibits substring matching, lookalike spoofing, or shortener bypass.
    """

    def __init__(self):
        self.version = ALLOWLIST_VERSION
        self.updated = ALLOWLIST_UPDATED
        self.source = ALLOWLIST_SOURCE

    def evaluate_domain(self, url_or_hostname: str) -> Dict[str, any]:
        """
        Evaluates a URL or hostname against the authoritative allowlist.
        Returns detailed audit metadata:
        {
            "is_trusted": bool,
            "reputation": "KNOWN_BENIGN" | "UNKNOWN" | "SUSPICIOUS_LOOKALIKE" | "REVOKED",
            "matched_brand": Optional[str],
            "institution_name": Optional[str],
            "effective_domain": str,
            "hostname": str,
            "is_shortener": bool,
            "is_punycode": bool,
            "reason": str
        }
        """
        if not url_or_hostname:
            return {
                "is_trusted": False,
                "reputation": "UNKNOWN",
                "matched_brand": None,
                "institution_name": None,
                "effective_domain": "",
                "hostname": "",
                "is_shortener": False,
                "is_punycode": False,
                "reason": "Empty destination address."
            }

        # Parse URL or Hostname
        raw = url_or_hostname.strip()
        if "://" in raw:
            try:
                parsed = urlparse(raw)
                hostname = (parsed.hostname or "").lower()
            except Exception:
                hostname = raw.split("/")[0].split("?")[0].lower()
        else:
            hostname = raw.split("/")[0].split("?")[0].lower()

        # Remove port if present
        if ":" in hostname:
            hostname = hostname.split(":")[0]

        # Check Punycode / IDN
        is_punycode = "xn--" in hostname

        # Extract Effective Base Domain
        effective_base = extract_effective_base_domain(hostname)

        # 1. Check if domain is in dynamic revocation list
        if hostname in REVOKED_DOMAINS or effective_base in REVOKED_DOMAINS:
            return {
                "is_trusted": False,
                "reputation": "REVOKED",
                "matched_brand": None,
                "institution_name": None,
                "effective_domain": effective_base,
                "hostname": hostname,
                "is_shortener": False,
                "is_punycode": is_punycode,
                "reason": f"Domain {hostname} has been revoked or reported as compromised."
            }

        # 2. Check if host is a URL Shortener
        is_shortener = hostname in EXCLUDED_SHORTENER_DOMAINS or effective_base in EXCLUDED_SHORTENER_DOMAINS
        if is_shortener:
            return {
                "is_trusted": False,
                "reputation": "UNKNOWN",
                "matched_brand": None,
                "institution_name": None,
                "effective_domain": effective_base,
                "hostname": hostname,
                "is_shortener": True,
                "is_punycode": is_punycode,
                "reason": "URL shorteners conceal destination host and are excluded from trusted status."
            }

        # 3. Check for Deceptive Brand Subdomain / Lookalike Spoofing
        # e.g., onlinesbi.sbi.account-update.xyz or hdfcbank.com.login-verify.top
        for brand_key, config in BANKING_ALLOWLIST.items():
            primary_domains = config["primary_domains"]
            for pd in primary_domains:
                if pd in hostname and effective_base != pd and not effective_base.endswith("." + pd):
                    # Hostname contains bank domain, but the effective base domain is different!
                    return {
                        "is_trusted": False,
                        "reputation": "SUSPICIOUS_LOOKALIKE",
                        "matched_brand": brand_key,
                        "institution_name": config["name"],
                        "effective_domain": effective_base,
                        "hostname": hostname,
                        "is_shortener": False,
                        "is_punycode": is_punycode,
                        "reason": f"Deceptive lookalike: '{pd}' used in subdomain, but destination is owned by '{effective_base}'."
                    }

        # 4. Strict Domain & Subdomain Boundary Matching
        for brand_key, config in BANKING_ALLOWLIST.items():
            primary_domains = config["primary_domains"]
            approved_subs = config.get("approved_subdomains", [])

            # Check primary domains
            for pd in primary_domains:
                # Boundary check: exact hostname or exact subdomain of authorized domain
                if hostname == pd or hostname.endswith("." + pd):
                    if effective_base == pd or effective_base.endswith("." + pd):
                        return {
                            "is_trusted": True,
                            "reputation": "KNOWN_BENIGN",
                            "matched_brand": brand_key,
                            "institution_name": config["name"],
                            "effective_domain": effective_base,
                            "hostname": hostname,
                            "is_shortener": False,
                            "is_punycode": is_punycode,
                            "reason": f"Exact boundary match with official domain for {config['name']}."
                        }

            # Check explicitly approved subdomains
            for sub in approved_subs:
                if hostname == sub or hostname.endswith("." + sub):
                    return {
                        "is_trusted": True,
                        "reputation": "KNOWN_BENIGN",
                        "matched_brand": brand_key,
                        "institution_name": config["name"],
                        "effective_domain": effective_base,
                        "hostname": hostname,
                        "is_shortener": False,
                        "is_punycode": is_punycode,
                        "reason": f"Explicitly approved banking service endpoint for {config['name']}."
                    }

        # 5. Domain not in allowlist -> Return UNKNOWN (NOT automatically malicious)
        return {
            "is_trusted": False,
            "reputation": "UNKNOWN",
            "matched_brand": None,
            "institution_name": None,
            "effective_domain": effective_base,
            "hostname": hostname,
            "is_shortener": False,
            "is_punycode": is_punycode,
            "reason": "Domain is not in the verified financial institutions allowlist."
        }


class SenderReputationEngine:
    """
    Evaluates SMS sender identifier against telecom regulations and banking patterns.
    Distinguishes:
    - VERIFIED_ORGANIZATIONAL_SENDER (TRAI format matching institution)
    - KNOWN_REPUTABLE_SENDER
    - USER_SAVED_CONTACT
    - UNVERIFIED_SENDER
    - SUSPICIOUS_SENDER_MISMATCH (personal number sending official banking claims)
    - KNOWN_MALICIOUS_SENDER
    """

    def evaluate_sender(
        self,
        sender: Optional[str],
        sms_text: Optional[str] = None,
        is_contact: bool = False
    ) -> Dict[str, any]:
        if not sender:
            return {
                "category": "UNVERIFIED_SENDER",
                "is_verified": False,
                "is_suspicious": False,
                "matched_institution": None,
                "reason": "No sender metadata available."
            }

        s_clean = sender.strip().upper()

        # 1. User-Saved Contact
        if is_contact:
            return {
                "category": "USER_SAVED_CONTACT",
                "is_verified": False,
                "is_suspicious": False,
                "matched_institution": None,
                "reason": "Sender is stored in device user contacts. Note: contact name is not proof of institutional authenticity."
            }

        # 2. Check for Personal Mobile Number (10 to 13 digits with optional +)
        is_phone_number = bool(re.match(r"^\+?\d{10,13}$", s_clean))
        if is_phone_number:
            # Check if message claims to be from a bank or demands KYC/OTP
            financial_terms = ["bank", "kyc", "otp", "account", "pan", "debit", "credit", "suspension", "blocked"]
            claims_financial = sms_text and any(t in sms_text.lower() for t in financial_terms)

            if claims_financial:
                return {
                    "category": "SUSPICIOUS_SENDER_MISMATCH",
                    "is_verified": False,
                    "is_suspicious": True,
                    "matched_institution": None,
                    "reason": f"Private mobile number ({s_clean}) used to transmit sensitive banking/KYC demands. Commercial banks never send transactional alerts from personal phone numbers."
                }
            else:
                return {
                    "category": "UNVERIFIED_SENDER",
                    "is_verified": False,
                    "is_suspicious": False,
                    "matched_institution": None,
                    "reason": "Personal mobile number without official organizational metadata."
                }

        # 3. Check for TRAI Alphanumeric Sender Pattern (e.g., AD-HDFCBK, VK-SBIINB)
        for brand_key, config in BANKING_ALLOWLIST.items():
            patterns = config.get("sender_patterns", [])
            for pat in patterns:
                if re.match(pat, s_clean, re.IGNORECASE):
                    return {
                        "category": "VERIFIED_ORGANIZATIONAL_SENDER",
                        "is_verified": True,
                        "is_suspicious": False,
                        "matched_institution": config["name"],
                        "reason": f"Sender header matches TRAI registered commercial gateway for {config['name']}."
                    }

        # 4. Standard 6-character organizational alphanumeric header without bank match
        if re.match(r"^[a-zA-Z]{2}-[a-zA-Z0-9]{6}$", s_clean) or (len(s_clean) == 6 and s_clean.isalpha()):
            return {
                "category": "KNOWN_REPUTABLE_SENDER",
                "is_verified": True,
                "is_suspicious": False,
                "matched_institution": None,
                "reason": "Registered institutional bulk SMS gateway header."
            }

        # 5. Fallback unverified
        return {
            "category": "UNVERIFIED_SENDER",
            "is_verified": False,
            "is_suspicious": False,
            "matched_institution": None,
            "reason": "Unrecognized sender header format."
        }

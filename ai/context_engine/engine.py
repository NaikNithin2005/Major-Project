"""
Context Correlation Engine - Phase 6
Performs cross-verification between SMS sender/content, extracted URLs, QR payloads, and brand identities.
Produces deterministic context scores (0 - 100) and structured ContextFindings.
"""

import re
from typing import Dict, List, Optional, Tuple
from urllib.parse import urlparse

from ai.risk_engine.contracts import ContextFinding, EvidenceSource

# Authoritative directory of recognized brands and their legitimate domains
TRUSTED_BRAND_DOMAINS: Dict[str, List[str]] = {
    "sbi": ["sbi.co.in", "onlinesbi.sbi", "onlinesbi.com", "statebankofindia.com"],
    "hdfc": ["hdfcbank.com", "hdfc.com"],
    "icici": ["icicibank.com"],
    "axis": ["axisbank.com"],
    "pnb": ["pnbindia.in"],
    "paypal": ["paypal.com", "paypal.me"],
    "amazon": ["amazon.in", "amazon.com", "amzn.to", "amazon.co.uk"],
    "netflix": ["netflix.com"],
    "apple": ["apple.com", "icloud.com"],
    "google": ["google.com", "google.co.in", "gmail.com"],
    "microsoft": ["microsoft.com", "live.com", "office.com", "outlook.com"],
    "paytm": ["paytm.com"],
    "phonepe": ["phonepe.com"],
    "github": ["github.com", "github.io", "githubusercontent.com"],
    "python": ["python.org", "pypi.org"],
    "android": ["android.com", "developer.android.com"],
    "uidai": ["uidai.gov.in"],
    "incometax": ["incometax.gov.in"],
}

FINANCIAL_TERMS = {
    "bank", "kyc", "account", "otp", "debit", "credit", "card", "pan", "cvv",
    "pin", "netbanking", "beneficiary", "funds", "transaction", "suspended", "blocked"
}


def extract_base_domain(hostname: str) -> str:
    """Extracts second-level domain + TLD from a hostname."""
    if not hostname:
        return ""
    parts = hostname.lower().split(".")
    if len(parts) <= 2:
        return hostname.lower()
    # Handle common multi-part TLDs like .co.in, .gov.in, .ac.uk
    if len(parts) >= 3 and parts[-2] in ("co", "gov", "ac", "org", "net", "com") and len(parts[-1]) == 2:
        return ".".join(parts[-3:])
    return ".".join(parts[-2:])


from ai.context_engine.trusted_domains import (
    TrustedDomainAllowlist,
    SenderReputationEngine,
    extract_effective_base_domain
)


class ContextCorrelationEngine:
    """
    Evaluates contextual consistency across multi-modal indicators (SMS, Sender, URL, QR).
    Produces an explainable context score from 0 to 100.
    """

    def __init__(self):
        self.allowlist = TrustedDomainAllowlist()
        self.sender_engine = SenderReputationEngine()

    def correlate(
        self,
        sms_text: Optional[str] = None,
        sender: Optional[str] = None,
        url: Optional[str] = None,
        qr_payload: Optional[str] = None,
    ) -> Tuple[float, List[ContextFinding]]:
        findings: List[ContextFinding] = []
        inconsistency_points = 0.0

        # Extract URL to verify (either direct URL or URL inside QR payload)
        target_url = url
        if not target_url and qr_payload:
            trimmed_qr = qr_payload.strip()
            if trimmed_qr.startswith("http://") or trimmed_qr.startswith("https://") or "://" in trimmed_qr:
                target_url = trimmed_qr

        url_hostname = ""
        url_base_domain = ""
        domain_eval = None
        if target_url:
            parsed = urlparse(target_url if "://" in target_url else "http://" + target_url)
            url_hostname = (parsed.hostname or "").lower()
            url_base_domain = extract_effective_base_domain(url_hostname)
            domain_eval = self.allowlist.evaluate_domain(target_url)

            # Check for deceptive lookalike domain spoofing
            if domain_eval.get("reputation") == "SUSPICIOUS_LOOKALIKE":
                findings.append(
                    ContextFinding(
                        check_name="LOOKALIKE_DOMAIN_SPOOF",
                        matched=True,
                        severity="CRITICAL",
                        description=domain_eval["reason"],
                        evidence=f"Deceptive host: {url_hostname} | Destination Domain: {domain_eval.get('effective_domain')}",
                        source=EvidenceSource.CONTEXT
                    )
                )
                inconsistency_points += 75.0
            elif domain_eval.get("is_trusted"):
                findings.append(
                    ContextFinding(
                        check_name="TRUSTED_BANKING_DOMAIN",
                        matched=True,
                        severity="INFO",
                        description=domain_eval["reason"],
                        evidence=f"Verified legitimate domain: {url_hostname}",
                        source=EvidenceSource.CONTEXT
                    )
                )

        # 1. Detect Claimed Brands in Sender or SMS Text
        detected_brands = self._detect_brands(sms_text, sender)

        # 2. Check Brand vs Domain Mismatch
        if detected_brands and url_hostname:
            for brand in detected_brands:
                allowed_domains = TRUSTED_BRAND_DOMAINS.get(brand, [])
                domain_matches = any(
                    url_base_domain == allowed or url_base_domain.endswith("." + allowed) or url_hostname == allowed or url_hostname.endswith("." + allowed)
                    for allowed in allowed_domains
                )

                if not domain_matches:
                    findings.append(
                        ContextFinding(
                            check_name="BRAND_DOMAIN_MISMATCH",
                            matched=True,
                            severity="CRITICAL",
                            description=f"Message references brand '{brand.upper()}', but destination domain '{url_base_domain or url_hostname}' is not an authorized domain.",
                            evidence=f"Claimed: {brand.upper()} | Destination Domain: {url_base_domain or url_hostname}",
                            source=EvidenceSource.CONTEXT
                        )
                    )
                    inconsistency_points += 75.0
                    break
                else:
                    findings.append(
                        ContextFinding(
                            check_name="BRAND_DOMAIN_MATCH",
                            matched=True,
                            severity="INFO",
                            description=f"Destination domain matches official infrastructure for '{brand.upper()}'.",
                            evidence=f"Verified legitimate domain: {url_hostname}",
                            source=EvidenceSource.CONTEXT
                        )
                    )



        # 3. Check Banking / Financial Context vs Non-Banking Destination
        if sms_text and target_url:
            has_financial_context = any(term in sms_text.lower() for term in FINANCIAL_TERMS)
            if has_financial_context and not detected_brands:
                # Unbranded banking urgency directing to external non-bank host
                findings.append(
                    ContextFinding(
                        check_name="BANKING_CONTEXT_MISMATCH",
                        matched=True,
                        severity="HIGH",
                        description="Message contains sensitive banking/KYC urgency, directing to an unverified external domain.",
                        evidence=f"Financial keywords with unverified host: {url_hostname}",
                        source=EvidenceSource.CONTEXT
                    )
                )
                inconsistency_points += 50.0

        # 4. Check SMS + QR Cross-Channel Consistency
        if sms_text and qr_payload:
            has_qr_prompt = any(w in sms_text.lower() for w in ["scan qr", "scan code", "qr code", "attached qr"])
            if has_qr_prompt and target_url and (inconsistency_points > 0 or not target_url.startswith("https://")):
                findings.append(
                    ContextFinding(
                        check_name="SMS_QR_CROSS_CHANNEL_ANOMALY",
                        matched=True,
                        severity="HIGH",
                        description="Cross-channel prompt (SMS instructing QR scan) leads to unverified or insecure external destination.",
                        evidence=f"SMS instructs QR scan -> Payload URL: {target_url[:40]}...",
                        source=EvidenceSource.CONTEXT
                    )
                )
                inconsistency_points += 25.0

        # 5. Check Sender Channel Inconsistency (e.g. private number sending official alert)
        if sender and (detected_brands or (sms_text and any(t in sms_text.lower() for t in ["kyc", "bank", "otp"]))):
            s_clean = sender.strip()
            # If sender is standard 10+ digit personal phone number
            if re.match(r"^\+?\d{10,13}$", s_clean):
                findings.append(
                    ContextFinding(
                        check_name="PRIVATE_NUMBER_ORGANIZATIONAL_SENDER",
                        matched=True,
                        severity="MEDIUM",
                        description="Personal mobile number used to transmit official institutional / banking communication.",
                        evidence=f"Personal telephone sender format: {s_clean}",
                        source=EvidenceSource.CONTEXT
                    )
                )
                inconsistency_points += 25.0

        # Normalize context score into 0, 25, 50, 75, 100 tiers
        if inconsistency_points >= 100.0:
            context_score = 100.0
        elif inconsistency_points >= 75.0:
            context_score = 75.0
        elif inconsistency_points >= 50.0:
            context_score = 50.0
        elif inconsistency_points >= 25.0:
            context_score = 25.0
        else:
            context_score = 0.0

        return context_score, findings

    def _detect_brands(self, sms_text: Optional[str], sender: Optional[str]) -> List[str]:
        brands = []
        combined_text = f"{sender or ''} {sms_text or ''}".lower()

        for brand in TRUSTED_BRAND_DOMAINS.keys():
            # Match brand as word boundary or within sender ID
            if re.search(r'\b' + re.escape(brand) + r'\b', combined_text):
                brands.append(brand)
            elif sender and brand in sender.lower():
                brands.append(brand)

        return list(set(brands))

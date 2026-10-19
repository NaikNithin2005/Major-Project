"""
Explainable Evidence Aggregator & Risk Policy Engine for Phase 14.
Aggregates multi-modal AI predictions, URL lexical structures, threat intelligence feeds,
and sender/context findings into a transparent, bounded risk assessment.
"""

from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from app.services.threat_intel_providers import ReputationStatus


class EvidenceItem:
    def __init__(
        self,
        category: str,             # AI_MODEL, URL_STRUCTURE, THREAT_INTEL, CONTEXT, ANOMALY
        source: str,               # e.g. "XGBOOST", "URLHAUS", "LEXICAL", "TINYBERT"
        severity: str,             # INFO, LOW, MEDIUM, HIGH, CRITICAL
        title: str,
        explanation: str,
        confidence: float = 1.0,
        timestamp: Optional[str] = None
    ):
        self.category = category
        self.source = source
        self.severity = severity
        self.title = title
        self.explanation = explanation
        self.confidence = confidence
        self.timestamp = timestamp or datetime.now(timezone.utc).isoformat()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "category": self.category,
            "source": self.source,
            "severity": self.severity,
            "title": self.title,
            "explanation": self.explanation,
            "confidence": self.confidence,
            "timestamp": self.timestamp
        }


class EnrichedAssessment:
    def __init__(
        self,
        final_risk_score: int,
        threat_category: str,
        recommended_action: str,
        evidence_items: List[EvidenceItem],
        score_breakdown: Dict[str, Any],
        reputation_status: str,
        intel_provenance: Optional[Dict[str, Any]] = None
    ):
        self.final_risk_score = final_risk_score
        self.threat_category = threat_category
        self.recommended_action = recommended_action
        self.evidence_items = evidence_items
        self.score_breakdown = score_breakdown
        self.reputation_status = reputation_status
        self.intel_provenance = intel_provenance or {}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "final_risk_score": self.final_risk_score,
            "threat_category": self.threat_category,
            "recommended_action": self.recommended_action,
            "evidence_items": [e.to_dict() for e in self.evidence_items],
            "score_breakdown": self.score_breakdown,
            "reputation_status": self.reputation_status,
            "intel_provenance": self.intel_provenance
        }


class EvidenceAggregationService:
    @staticmethod
    def aggregate_evidence(
        model_results: Optional[Dict[str, Any]] = None,
        url_metadata: Optional[Dict[str, Any]] = None,
        threat_intel: Optional[Dict[str, Any]] = None,
        context_results: Optional[Dict[str, Any]] = None
    ) -> EnrichedAssessment:
        """
        Aggregate all analytical signals into an explainable assessment.
        Follows strict Phase 14 scoring policy:
        1. AI Models provide the core baseline score (0-100).
        2. Verified Threat Intelligence matches clamp score to HIGH (>= 85).
        3. Missing or clean intelligence NEVER decreases risk score.
        4. Correlated evidence is categorized without double-counting.
        """
        model_res = model_results or {}
        url_meta = url_metadata or {}
        intel = threat_intel or {}
        context = context_results or {}

        evidence_items: List[EvidenceItem] = []

        # 1. Evaluate AI/ML outputs
        tinybert_prob = float(model_res.get("tinybert_prob", 0.0))
        xgboost_prob = float(model_res.get("xgboost_prob", 0.0))
        is_anomaly = bool(model_res.get("is_anomaly", False))
        anomaly_score = float(model_res.get("anomaly_score", 0.0))

        if tinybert_prob >= 0.75:
            evidence_items.append(EvidenceItem(
                category="AI_MODEL",
                source="TINYBERT",
                severity="HIGH",
                title="Smishing Language Patterns Detected",
                explanation=f"TinyBERT classifier identified suspicious SMS phrasing with {int(tinybert_prob * 100)}% probability.",
                confidence=tinybert_prob
            ))
        elif tinybert_prob >= 0.50:
            evidence_items.append(EvidenceItem(
                category="AI_MODEL",
                source="TINYBERT",
                severity="MEDIUM",
                title="Borderline SMS Phrasing",
                explanation=f"TinyBERT classifier detected potential smishing cues ({int(tinybert_prob * 100)}% confidence).",
                confidence=tinybert_prob
            ))

        if xgboost_prob >= 0.80:
            evidence_items.append(EvidenceItem(
                category="AI_MODEL",
                source="XGBOOST",
                severity="HIGH",
                title="Malicious URL Lexical Structure",
                explanation=f"XGBoost classifier determined high probability of quishing/phishing ({int(xgboost_prob * 100)}%).",
                confidence=xgboost_prob
            ))
        elif xgboost_prob >= 0.50:
            evidence_items.append(EvidenceItem(
                category="AI_MODEL",
                source="XGBOOST",
                severity="MEDIUM",
                title="Suspicious URL Lexical Attributes",
                explanation=f"XGBoost identified questionable URL characteristics ({int(xgboost_prob * 100)}% confidence).",
                confidence=xgboost_prob
            ))

        if is_anomaly:
            evidence_items.append(EvidenceItem(
                category="ANOMALY",
                source="ISOLATION_FOREST",
                severity="MEDIUM",
                title="Zero-Day URL Anomaly",
                explanation="Isolation Forest flagged feature divergence from legitimate URL distribution baseline.",
                confidence=anomaly_score
            ))

        # 2. Evaluate URL Structural Findings
        if url_meta.get("is_ip_hostname"):
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="LEXICAL",
                severity="HIGH",
                title="Direct IP Hostname Used",
                explanation="URL targets a raw numeric IP address instead of a recognized domain name.",
                confidence=0.90
            ))

        if url_meta.get("is_punycode"):
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="LEXICAL",
                severity="HIGH",
                title="Internationalized Domain (IDN / Punycode)",
                explanation="Domain utilizes Punycode encoding, a common indicator of Cyrillic/Greek homoglyph spoofing.",
                confidence=0.85
            ))

        if url_meta.get("has_unusual_port"):
            port = url_meta.get("port")
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="LEXICAL",
                severity="MEDIUM",
                title=f"Non-Standard Service Port ({port})",
                explanation=f"Target URL specifies non-standard web port {port}, bypassing standard filtering.",
                confidence=0.75
            ))

        if intel.get("is_ssrf_risk"):
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="SSRF_GUARD",
                severity="CRITICAL",
                title="Internal Network SSRF Target",
                explanation=f"Target URL points to internal, private, or loopback address ({intel.get('ssrf_reason')}).",
                confidence=1.0
            ))

        # 3. Evaluate Threat Intelligence Reputation
        reputation_status = intel.get("reputation_status", ReputationStatus.REPUTATION_UNAVAILABLE)
        is_intel_malicious = bool(intel.get("is_malicious", False))
        intel_source = intel.get("source") or "THREAT_FEED"
        intel_confidence = float(intel.get("confidence", 0.0))

        if is_intel_malicious:
            evidence_items.append(EvidenceItem(
                category="THREAT_INTEL",
                source=intel_source,
                severity="CRITICAL",
                title=f"Confirmed Threat in {intel_source} Feed",
                explanation=f"Target identified as active {intel.get('threat_type', 'threat')} by {intel_source} (Confidence: {int(intel_confidence * 100)}%).",
                confidence=intel_confidence
            ))
        elif reputation_status == ReputationStatus.NO_KNOWN_THREAT:
            evidence_items.append(EvidenceItem(
                category="THREAT_INTEL",
                source="INTELLIGENCE_FEEDS",
                severity="INFO",
                title="No Threat Match in Feeds",
                explanation="URL is unlisted in active threat intelligence feeds (Note: zero-day attacks may not yet be cataloged).",
                confidence=0.50
            ))
        elif reputation_status == ReputationStatus.REPUTATION_UNAVAILABLE:
            evidence_items.append(EvidenceItem(
                category="THREAT_INTEL",
                source="LOCAL_CACHE",
                severity="INFO",
                title="External Reputation Unavailable",
                explanation="Threat feeds were unreachable; relied entirely on local-first AI and heuristic analysis.",
                confidence=0.0
            ))

        # 4. Compute Final Weighted Score and Clamping Policy
        # Baseline model score
        raw_sms_score = tinybert_prob * 100
        raw_url_score = xgboost_prob * 100
        raw_anomaly_score = (anomaly_score if is_anomaly else 0.0) * 100

        # Weighted combination
        if raw_sms_score > 0 and raw_url_score > 0:
            composite_score = (raw_sms_score * 0.40) + (raw_url_score * 0.45) + (raw_anomaly_score * 0.15)
        elif raw_url_score > 0:
            composite_score = (raw_url_score * 0.80) + (raw_anomaly_score * 0.20)
        elif raw_sms_score > 0:
            composite_score = raw_sms_score
        else:
            composite_score = 0.0

        # Non-URL QR Payload (vCard, MeCard, WiFi config)
        if url_meta.get("is_non_url_qr"):
            composite_score = 0.0
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="QR_PARSER",
                severity="INFO",
                title="Plain Text or Contact QR Payload",
                explanation="QR payload does not contain a web URL or executable scheme.",
                confidence=1.0
            ))

        # Add structure penalties
        if url_meta.get("is_ip_hostname"):
            composite_score += 20
        if url_meta.get("is_punycode"):
            composite_score += 20
        if url_meta.get("subdomain_depth", 0) > 2:
            composite_score += 25
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="LEXICAL",
                severity="HIGH",
                title="Excessive Subdomain Nesting / Masking",
                explanation=f"URL contains excessive subdomain nesting ({url_meta.get('subdomain_depth')} levels) used to disguise destination domain.",
                confidence=0.85
            ))
        if url_meta.get("is_suspicious_tld"):
            composite_score += 20
            evidence_items.append(EvidenceItem(
                category="URL_STRUCTURE",
                source="LEXICAL",
                severity="HIGH",
                title="High-Risk Top-Level Domain (TLD)",
                explanation="Destination domain utilizes high-abuse top-level domain (.xyz, .top, .work, .click).",
                confidence=0.80
            ))
        if context.get("brand_domain_mismatch"):
            composite_score += 40
            evidence_items.append(EvidenceItem(
                category="CONTEXT",
                source="BRAND_REGISTRY",
                severity="CRITICAL",
                title="Brand Identity vs Destination Mismatch",
                explanation="Message references recognized brand but targets unverified destination domain.",
                confidence=0.95
            ))
        if intel.get("is_ssrf_risk"):
            composite_score += 45

        # Check for verified official domain match
        if context.get("brand_domain_match") or url_meta.get("is_trusted_domain"):
            evidence_items.append(EvidenceItem(
                category="CONTEXT",
                source="BRAND_REGISTRY",
                severity="INFO",
                title="Verified Official Domain",
                explanation="Destination domain matches certified infrastructure for recognized organization.",
                confidence=1.0
            ))
            # If no structural hazards, IP hostname, Punycode, or threat intel hits exist, clamp to safe tier
            if not is_intel_malicious and not url_meta.get("is_ip_hostname") and not url_meta.get("is_punycode") and not intel.get("is_ssrf_risk") and url_meta.get("subdomain_depth", 0) <= 2 and not url_meta.get("is_suspicious_tld"):
                composite_score = min(composite_score, 20.0)

        # Informational SMS transaction notification without URLs or threats
        if url_meta.get("is_informational_statement") and raw_url_score == 0:
            composite_score = min(composite_score, 20.0)

        # CLAMPING POLICY: If verified malicious by threat intel, clamp to at least 85
        if is_intel_malicious and intel_confidence >= 0.85:
            composite_score = max(composite_score, 90.0)
        elif is_intel_malicious:
            composite_score = max(composite_score, 80.0)

        # Clamping to [0, 100]
        final_score = int(round(max(0.0, min(100.0, composite_score))))

        # Determine Category & Action
        if final_score <= 25:
            category = "SAFE"
            action = "Allow"
        elif final_score <= 50:
            category = "LOW"
            action = "Allow with caution"
        elif final_score <= 75:
            category = "MEDIUM"
            action = "Warn user and require explicit confirmation"
        else:
            category = "HIGH"
            action = "Block or strongly warn"

        breakdown = {
            "sms_score": round(raw_sms_score, 1),
            "url_score": round(raw_url_score, 1),
            "anomaly_score": round(raw_anomaly_score, 1),
            "threat_intel_boost": 90 - int(composite_score) if (is_intel_malicious and composite_score < 90) else 0,
            "final_score": final_score
        }

        provenance = {
            "source": intel.get("source"),
            "first_seen": intel.get("first_seen"),
            "last_seen": intel.get("last_seen"),
            "confidence": intel.get("confidence"),
            "tags": intel.get("tags", [])
        }

        return EnrichedAssessment(
            final_risk_score=final_score,
            threat_category=category,
            recommended_action=action,
            evidence_items=evidence_items,
            score_breakdown=breakdown,
            reputation_status=reputation_status,
            intel_provenance=provenance
        )

"""
Phase 6 Contracts - Structured schemas for Risk Engine, XAI, Rules, Context, and Threat Reports.
Adheres to PRD Section 4.16 - 4.18, Rules.md, and Phases.md.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional, List, Dict, Any
import time


class ThreatCategory(str, Enum):
    SAFE = "SAFE"            # 0 - 25
    LOW = "LOW"              # 26 - 50
    MEDIUM = "MEDIUM"        # 51 - 75
    HIGH = "HIGH"            # 76 - 100


class RecommendedAction(str, Enum):
    ALLOW = "Allow"
    ALLOW_WITH_CAUTION = "Allow with caution"
    WARN_USER = "Warn user and require explicit confirmation"
    BLOCK_OR_STRONGLY_WARN = "Block or strongly warn"


class EvidenceSource(str, Enum):
    MODEL = "MODEL"
    RULE = "RULE"
    URL_FEATURE = "URL_FEATURE"
    QR_FEATURE = "QR_FEATURE"
    SMS_FEATURE = "SMS_FEATURE"
    CONTEXT = "CONTEXT"
    ANOMALY = "ANOMALY"


@dataclass
class ScoreBreakdown:
    sms_score: float             # 0 - 100
    url_score: float             # 0 - 100
    qr_score: float              # 0 - 100
    context_score: float         # 0 - 100
    anomaly_score: float         # 0 - 100
    sms_contribution: float      # e.g. 0.35 * sms_score
    url_contribution: float      # e.g. 0.30 * url_score
    qr_contribution: float       # e.g. 0.15 * qr_score
    context_contribution: float  # e.g. 0.10 * context_score
    anomaly_contribution: float  # e.g. 0.10 * anomaly_score
    final_score: float           # clamped [0, 100]
    category: ThreatCategory


@dataclass
class ModelOutputContract:
    model_name: str
    model_version: str
    probability: Optional[float] = None
    predicted_class: Optional[str] = None
    confidence: Optional[float] = None
    inference_timestamp: float = field(default_factory=time.time)
    is_available: bool = True
    error_message: Optional[str] = None
    features_used: List[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RuleResult:
    id: str
    category: str
    severity: str                # INFO, LOW, MEDIUM, HIGH, CRITICAL
    description: str
    score_contribution: float    # points added to rule evaluation
    evidence: str
    source: EvidenceSource = EvidenceSource.RULE


@dataclass
class ContextFinding:
    check_name: str
    matched: bool
    severity: str                # INFO, LOW, MEDIUM, HIGH, CRITICAL
    description: str
    evidence: str
    source: EvidenceSource = EvidenceSource.CONTEXT


@dataclass
class ExplanationReason:
    id: str
    title: str
    description: str
    severity: str
    source: EvidenceSource
    confidence: float
    debug_detail: Optional[str] = None


@dataclass
class RiskAssessment:
    risk_score: float
    category: ThreatCategory
    score_breakdown: ScoreBreakdown
    model_results: Dict[str, ModelOutputContract]
    rule_results: List[RuleResult]
    context_findings: List[ContextFinding]
    reasons: List[str]
    structured_reasons: List[ExplanationReason]
    recommended_action: RecommendedAction
    timestamp: float
    model_versions: Dict[str, str]
    unavailable_models: List[str] = field(default_factory=list)

    def to_event_summary(self) -> str:
        """
        Formatted text output complying with Phase 6 Acceptance Criteria:
        Every analyzed event produces:
        - Risk Score
        - Threat Category
        - Model Results
        - Reasons
        - Recommended Action
        - Timestamp
        """
        model_lines = []
        for name, contract in self.model_results.items():
            prob_str = f"{round(contract.probability * 100)}%" if contract.probability is not None else "N/A"
            avail = "available" if contract.is_available else "unavailable"
            model_lines.append(f"  - {name} ({contract.model_version}): {prob_str} ({avail})")
        if not model_lines:
            model_lines.append("  - Multi-modal local pipeline")

        reasons_lines = [f"  - {r}" for r in self.reasons] if self.reasons else ["  - No anomalous indicators detected."]

        formatted_time = time.strftime("%d %b %Y, %H:%M:%S", time.gmtime(self.timestamp))

        return (
            f"Risk Score: {round(self.risk_score)} / 100\n"
            f"Threat Category: {self.category.value}\n"
            f"Model Results:\n" + "\n".join(model_lines) + "\n"
            f"Reasons:\n" + "\n".join(reasons_lines) + "\n"
            f"Recommended Action: {self.recommended_action.value}\n"
            f"Timestamp: {formatted_time}"
        )


@dataclass
class ThreatReport:
    report_id: str
    timestamp: float
    detection_type: str          # SMS, QR, URL, COMBINED_SMS_QR
    risk_score: float
    category: ThreatCategory
    score_breakdown: ScoreBreakdown
    model_results: Dict[str, ModelOutputContract]
    rule_results: List[RuleResult]
    context_findings: List[ContextFinding]
    reasons: List[str]
    recommended_action: RecommendedAction
    model_versions: Dict[str, str]
    url_metadata: Dict[str, Any] = field(default_factory=dict)
    qr_metadata: Dict[str, Any] = field(default_factory=dict)
    privacy_notice: str = "Raw SMS content is sanitized and kept strictly local-first."

    def to_event_summary(self) -> str:
        """
        Formatted text output complying with Phase 6 Acceptance Criteria:
        Every analyzed event produces:
        - Risk Score
        - Threat Category
        - Model Results
        - Reasons
        - Recommended Action
        - Timestamp
        """
        model_lines = []
        for name, contract in self.model_results.items():
            prob_str = f"{round(contract.probability * 100)}%" if contract.probability is not None else "N/A"
            avail = "available" if contract.is_available else "unavailable"
            model_lines.append(f"  - {name} ({contract.model_version}): {prob_str} ({avail})")
        if not model_lines:
            model_lines.append("  - Multi-modal local pipeline")

        reasons_lines = [f"  - {r}" for r in self.reasons] if self.reasons else ["  - No anomalous indicators detected."]

        formatted_time = time.strftime("%d %b %Y, %H:%M:%S", time.gmtime(self.timestamp))

        return (
            f"Risk Score: {round(self.risk_score)} / 100\n"
            f"Threat Category: {self.category.value}\n"
            f"Model Results:\n" + "\n".join(model_lines) + "\n"
            f"Reasons:\n" + "\n".join(reasons_lines) + "\n"
            f"Recommended Action: {self.recommended_action.value}\n"
            f"Timestamp: {formatted_time}"
        )

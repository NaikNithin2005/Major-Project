"""
Risk Engine Module - Phase 6
Integrates AI model outputs, deterministic rule engine, context correlation engine,
and explainable AI (XAI) to produce verified risk assessments.
"""

from ai.risk_engine.contracts import (
    ThreatCategory,
    RecommendedAction,
    EvidenceSource,
    ScoreBreakdown,
    ModelOutputContract,
    RuleResult,
    ContextFinding,
    ExplanationReason,
    RiskAssessment,
    ThreatReport,
)
from ai.risk_engine.engine import RiskEngine

__all__ = [
    "ThreatCategory",
    "RecommendedAction",
    "EvidenceSource",
    "ScoreBreakdown",
    "ModelOutputContract",
    "RuleResult",
    "ContextFinding",
    "ExplanationReason",
    "RiskAssessment",
    "ThreatReport",
    "RiskEngine",
]

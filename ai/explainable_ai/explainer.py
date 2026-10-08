"""
Explainable AI (XAI) Engine - Phase 6
Generates evidence-backed, human-readable explanations from model inferences,
deterministic rule triggers, and context correlation findings.
Strictly adheres to PRD Section 4.17 and Rules.md Section 14 (No fabricated reasons).
"""

import json
import os
from typing import Dict, List, Optional, Any

from ai.risk_engine.contracts import (
    ExplanationReason,
    EvidenceSource,
    ModelOutputContract,
    RuleResult,
    ContextFinding,
)

SEVERITY_WEIGHTS = {
    "CRITICAL": 5,
    "HIGH": 4,
    "MEDIUM": 3,
    "LOW": 2,
    "INFO": 1
}


class ExplainableAiEngine:
    """
    Synthesizes and prioritizes human-readable reasons from multi-modal evidence.
    Exposes feature importance for debugging and transparency.
    """

    def __init__(self, metadata_path: str = "models/xgboost/v2/metadata.json"):
        self.feature_importances: List[Dict[str, Any]] = []
        self._load_feature_importance(metadata_path)

    def _load_feature_importance(self, metadata_path: str):
        if os.path.exists(metadata_path):
            try:
                with open(metadata_path, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                    self.feature_importances = meta.get("feature_importances", [])
            except Exception:
                self.feature_importances = []

    def get_feature_importance_debug(self) -> List[Dict[str, Any]]:
        """Returns feature importance rankings for transparency without claiming proof."""
        return self.feature_importances

    def generate_explanations(
        self,
        model_results: Dict[str, ModelOutputContract],
        rule_results: List[RuleResult],
        context_findings: List[ContextFinding],
        url_features: Optional[Dict[str, float]] = None,
        max_user_reasons: int = 5,
    ) -> List[ExplanationReason]:
        """
        Gathers evidence across all subsystems, prioritizes reasons, and selects top 3-6 reasons.
        """
        all_reasons: List[ExplanationReason] = []

        # 1. Model Evidence
        # XGBoost URL Model
        xgb_res = model_results.get("xgboost")
        if xgb_res and xgb_res.is_available and xgb_res.probability is not None:
            if xgb_res.probability >= 0.80:
                all_reasons.append(
                    ExplanationReason(
                        id="EXP_MODEL_XGBOOST_HIGH",
                        title="High Malicious Probability by URL Classifier",
                        description=f"Machine learning URL model identified strong phishing patterns ({xgb_res.probability*100:.1f}% confidence).",
                        severity="CRITICAL",
                        source=EvidenceSource.MODEL,
                        confidence=xgb_res.probability,
                        debug_detail=f"Model: {xgb_res.model_version}, Prob: {xgb_res.probability:.4f}"
                    )
                )
            elif xgb_res.probability >= 0.50:
                all_reasons.append(
                    ExplanationReason(
                        id="EXP_MODEL_XGBOOST_SUSPICIOUS",
                        title="Suspicious URL Patterns Detected",
                        description=f"URL classifier detected suspicious structural patterns ({xgb_res.probability*100:.1f}% risk probability).",
                        severity="MEDIUM",
                        source=EvidenceSource.MODEL,
                        confidence=xgb_res.probability,
                        debug_detail=f"Model: {xgb_res.model_version}, Prob: {xgb_res.probability:.4f}"
                    )
                )

        # TinyBERT SMS Model
        tb_res = model_results.get("tinybert")
        if tb_res and tb_res.is_available and tb_res.probability is not None:
            if tb_res.probability >= 0.80:
                all_reasons.append(
                    ExplanationReason(
                        id="EXP_MODEL_TINYBERT_HIGH",
                        title="Smishing Language Patterns Identified",
                        description=f"Natural language model detected deceitful social engineering content ({tb_res.probability*100:.1f}% smishing probability).",
                        severity="CRITICAL",
                        source=EvidenceSource.MODEL,
                        confidence=tb_res.probability,
                        debug_detail=f"Model: {tb_res.model_version}, Prob: {tb_res.probability:.4f}"
                    )
                )
            elif tb_res.probability >= 0.50:
                all_reasons.append(
                    ExplanationReason(
                        id="EXP_MODEL_TINYBERT_SUSPICIOUS",
                        title="Potentially Deceptive Message Text",
                        description=f"Message formulation exhibits characteristics common in unsolicited smishing ({tb_res.probability*100:.1f}% risk).",
                        severity="MEDIUM",
                        source=EvidenceSource.MODEL,
                        confidence=tb_res.probability,
                        debug_detail=f"Model: {tb_res.model_version}, Prob: {tb_res.probability:.4f}"
                    )
                )

        # Isolation Forest Anomaly Model
        iso_res = model_results.get("isolation_forest")
        if iso_res and iso_res.is_available and iso_res.probability is not None:
            if iso_res.metadata.get("is_anomalous", False) or iso_res.probability >= 0.80:
                all_reasons.append(
                    ExplanationReason(
                        id="EXP_MODEL_ANOMALY_OUTLIER",
                        title="Zero-Day Structural Anomaly Detected",
                        description="Unsupervised anomaly detector flagged abnormal structural deviations from standard legitimate URLs.",
                        severity="HIGH",
                        source=EvidenceSource.ANOMALY,
                        confidence=iso_res.probability,
                        debug_detail=f"Model: {iso_res.model_version}, Anomaly Score: {iso_res.probability:.4f}"
                    )
                )

        # 2. Context Findings
        for finding in context_findings:
            if finding.matched and finding.severity in ("CRITICAL", "HIGH", "MEDIUM"):
                all_reasons.append(
                    ExplanationReason(
                        id=f"EXP_CTX_{finding.check_name}",
                        title=finding.description.split(".")[0],
                        description=finding.description,
                        severity=finding.severity,
                        source=EvidenceSource.CONTEXT,
                        confidence=0.90,
                        debug_detail=finding.evidence
                    )
                )

        # 3. Rule Results
        for rule in rule_results:
            if rule.severity in ("CRITICAL", "HIGH", "MEDIUM"):
                all_reasons.append(
                    ExplanationReason(
                        id=f"EXP_{rule.id}",
                        title=rule.description.split(";")[0].split(".")[0],
                        description=rule.description,
                        severity=rule.severity,
                        source=rule.source,
                        confidence=0.95,
                        debug_detail=rule.evidence
                    )
                )

        # 4. Deduplicate Reasons
        unique_reasons: List[ExplanationReason] = []
        seen_titles = set()
        for r in all_reasons:
            if r.title not in seen_titles:
                seen_titles.add(r.title)
                unique_reasons.append(r)

        # 5. Prioritize by:
        # 1. Severity weight (CRITICAL > HIGH > MEDIUM > LOW)
        # 2. Source priority (MODEL / CONTEXT > RULE / FEATURE)
        # 3. Confidence
        source_priority = {
            EvidenceSource.MODEL: 4,
            EvidenceSource.CONTEXT: 4,
            EvidenceSource.ANOMALY: 3,
            EvidenceSource.RULE: 2,
            EvidenceSource.URL_FEATURE: 1,
            EvidenceSource.SMS_FEATURE: 1,
            EvidenceSource.QR_FEATURE: 1,
        }

        unique_reasons.sort(
            key=lambda r: (
                SEVERITY_WEIGHTS.get(r.severity, 0),
                source_priority.get(r.source, 0),
                r.confidence
            ),
            reverse=True
        )

        return unique_reasons[:max(3, max_user_reasons)]

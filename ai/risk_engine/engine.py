"""
Risk Engine - Phase 6
Integrates multi-modal AI inferences, rule-based indicators, context correlation,
and explainability to produce deterministic, explainable risk assessments.
Strictly adheres to PRD Section 4.16, Rules.md Section 13-16, and Phases.md.
"""

import time
import uuid
from typing import Dict, List, Optional, Any, Tuple

from ai.risk_engine.contracts import (
    ThreatCategory,
    RecommendedAction,
    ScoreBreakdown,
    ModelOutputContract,
    RiskAssessment,
    ThreatReport,
    RuleResult,
    ContextFinding,
    ExplanationReason,
)
from ai.rule_engine.engine import RuleEngine
from ai.context_engine.engine import ContextCorrelationEngine
from ai.explainable_ai.explainer import ExplainableAiEngine
from ai.feature_engineering.url_feature_extractor import extract_url_features


class RiskEngine:
    """
    Local-first Risk Engine implementing the PRD weighted multi-modal risk formula:
    finalScore = 0.35 * smsScore + 0.30 * urlScore + 0.15 * qrScore + 0.10 * contextScore + 0.10 * anomalyScore
    """

    # Baseline conceptual weights
    WEIGHT_SMS = 0.35
    WEIGHT_URL = 0.30
    WEIGHT_QR = 0.15
    WEIGHT_CONTEXT = 0.10
    WEIGHT_ANOMALY = 0.10

    def __init__(
        self,
        rule_engine: Optional[RuleEngine] = None,
        context_engine: Optional[ContextCorrelationEngine] = None,
        xai_engine: Optional[ExplainableAiEngine] = None,
    ):
        self.rule_engine = rule_engine or RuleEngine()
        self.context_engine = context_engine or ContextCorrelationEngine()
        self.xai_engine = xai_engine or ExplainableAiEngine()

    def categorize_score(self, score: float) -> ThreatCategory:
        """
        Maps a clamped [0, 100] risk score into the 4 authoritative PRD categories.
        0 - 25:   SAFE
        26 - 50:  LOW
        51 - 75:  MEDIUM
        76 - 100: HIGH
        """
        clamped = max(0.0, min(100.0, score))
        if clamped <= 25.0:
            return ThreatCategory.SAFE
        elif clamped <= 50.0:
            return ThreatCategory.LOW
        elif clamped <= 75.0:
            return ThreatCategory.MEDIUM
        else:
            return ThreatCategory.HIGH

    def determine_action(self, category: ThreatCategory) -> RecommendedAction:
        """
        Maps ThreatCategory to standardized recommended action without automated destructive execution.
        """
        if category == ThreatCategory.SAFE:
            return RecommendedAction.ALLOW
        elif category == ThreatCategory.LOW:
            return RecommendedAction.ALLOW_WITH_CAUTION
        elif category == ThreatCategory.MEDIUM:
            return RecommendedAction.WARN_USER
        else:
            return RecommendedAction.BLOCK_OR_STRONGLY_WARN

    def compute_qr_feature_score(
        self,
        qr_payload: Optional[str],
        url_features: Optional[Dict[str, float]]
    ) -> float:
        """
        Computes a normalized 0 - 100 score for QR structural and payload features.
        Avoids redundant double-counting with XGBoost.
        """
        if not qr_payload:
            return 0.0

        raw = qr_payload.strip()
        lower = raw.lower()

        # Non-URL dangerous payload schemes
        if any(lower.startswith(s) for s in ["javascript:", "data:", "intent:", "smsto:"]):
            return 80.0

        # Plain text non-URL payload
        if not (lower.startswith("http://") or lower.startswith("https://") or "://" in lower or "." in lower):
            return 0.0

        # URL-based QR payload structural evaluation
        score = 0.0
        if url_features:
            if url_features.get("https_enabled", 1.0) == 0.0:
                score += 20.0
            if url_features.get("has_ip", 0.0) == 1.0:
                score += 30.0
            if url_features.get("encoding_present", 0.0) == 1.0:
                score += 20.0
            if url_features.get("port_specified", 0.0) == 1.0:
                score += 15.0
            if url_features.get("subdomain_count", 0.0) > 2:
                score += 15.0
            if url_features.get("suspicious_tld", 0.0) == 1.0:
                score += 15.0
            if url_features.get("has_at_symbol", 0.0) == 1.0:
                score += 15.0
            if url_features.get("entropy", 0.0) > 4.2:
                score += 10.0

        return max(0.0, min(100.0, score))

    def assess(
        self,
        # Inputs
        sms_text: Optional[str] = None,
        sender: Optional[str] = None,
        url: Optional[str] = None,
        qr_payload: Optional[str] = None,
        # Direct model results (or will be inferred if passed or simulated)
        model_results: Optional[Dict[str, ModelOutputContract]] = None,
        url_features: Optional[Dict[str, float]] = None,
        # Precomputed scores (for testing / direct override)
        sms_score: Optional[float] = None,
        url_score: Optional[float] = None,
        qr_score: Optional[float] = None,
        context_score: Optional[float] = None,
        anomaly_score: Optional[float] = None,
    ) -> RiskAssessment:
        timestamp = time.time()
        unavailable_models: List[str] = []
        active_model_results: Dict[str, ModelOutputContract] = dict(model_results or {})

        # Extract features if URL present and features not provided
        if url and not url_features:
            url_features = extract_url_features(url)
        elif qr_payload and not url and not url_features:
            if "://" in qr_payload or qr_payload.strip().startswith("www."):
                url_features = extract_url_features(qr_payload)

        # 1. Execute Rule Engine
        rule_results: List[RuleResult] = self.rule_engine.evaluate(
            url=url,
            url_features=url_features,
            sms_text=sms_text,
            sender=sender,
            qr_payload=qr_payload
        )

        # 2. Execute Context Correlation Engine
        computed_ctx_score, context_findings = self.context_engine.correlate(
            sms_text=sms_text,
            sender=sender,
            url=url,
            qr_payload=qr_payload
        )
        if context_score is None:
            context_score = computed_ctx_score

        # 3. Model Score Normalization (0 - 100)
        # SMS Model (TinyBERT)
        tb_contract = active_model_results.get("tinybert")
        if sms_score is None:
            if tb_contract is not None:
                if tb_contract.is_available and tb_contract.probability is not None:
                    sms_score = float(tb_contract.probability * 100.0)
                else:
                    unavailable_models.append("tinybert")
                    sms_score = None
            else:
                # If no model contract and no SMS provided, treat as not applicable
                sms_score = 0.0 if sms_text is None else 0.0

        # URL Model (XGBoost)
        xgb_contract = active_model_results.get("xgboost")
        if url_score is None:
            if xgb_contract is not None:
                if xgb_contract.is_available and xgb_contract.probability is not None:
                    url_score = float(xgb_contract.probability * 100.0)
                else:
                    unavailable_models.append("xgboost")
                    url_score = None
            else:
                url_score = 0.0 if url is None else 0.0

        # Anomaly Model (Isolation Forest)
        iso_contract = active_model_results.get("isolation_forest")
        if anomaly_score is None:
            if iso_contract is not None:
                if iso_contract.is_available and iso_contract.probability is not None:
                    # Anomaly score is [0.0, 1.0] -> [0, 100]
                    anomaly_score = float(iso_contract.probability * 100.0)
                else:
                    unavailable_models.append("isolation_forest")
                    anomaly_score = None
            else:
                anomaly_score = 0.0 if url is None else 0.0

        # QR Score
        if qr_score is None:
            qr_score = self.compute_qr_feature_score(qr_payload, url_features)

        # 4. Weighted Calculation with Model Failure Handling
        # Standard weights
        weights = {
            "sms": (self.WEIGHT_SMS, sms_score),
            "url": (self.WEIGHT_URL, url_score),
            "qr": (self.WEIGHT_QR, qr_score),
            "context": (self.WEIGHT_CONTEXT, context_score),
            "anomaly": (self.WEIGHT_ANOMALY, anomaly_score),
        }

        # Filter out unavailable models (marked as None)
        active_weight_sum = sum(w for w, val in weights.values() if val is not None)
        if active_weight_sum <= 0.0:
            active_weight_sum = 1.0

        # Calculate contributions
        # When all components available: scale_factor = 1.0
        # When some model is unavailable: reweight dynamically by 1.0 / active_weight_sum
        scale_factor = 1.0 / active_weight_sum

        sms_eff = (sms_score or 0.0) if sms_score is not None else 0.0
        url_eff = (url_score or 0.0) if url_score is not None else 0.0
        qr_eff = (qr_score or 0.0) if qr_score is not None else 0.0
        ctx_eff = (context_score or 0.0) if context_score is not None else 0.0
        anom_eff = (anomaly_score or 0.0) if anomaly_score is not None else 0.0

        # Base contributions
        sms_contrib = round((self.WEIGHT_SMS * scale_factor) * sms_eff, 2) if sms_score is not None else 0.0
        url_contrib = round((self.WEIGHT_URL * scale_factor) * url_eff, 2) if url_score is not None else 0.0
        qr_contrib = round((self.WEIGHT_QR * scale_factor) * qr_eff, 2) if qr_score is not None else 0.0
        ctx_contrib = round((self.WEIGHT_CONTEXT * scale_factor) * ctx_eff, 2) if context_score is not None else 0.0
        anom_contrib = round((self.WEIGHT_ANOMALY * scale_factor) * anom_eff, 2) if anomaly_score is not None else 0.0

        final_raw = sms_contrib + url_contrib + qr_contrib + ctx_contrib + anom_contrib
        final_clamped = round(max(0.0, min(100.0, final_raw)), 1)

        category = self.categorize_score(final_clamped)
        recommended_action = self.determine_action(category)

        breakdown = ScoreBreakdown(
            sms_score=round(sms_eff, 1),
            url_score=round(url_eff, 1),
            qr_score=round(qr_eff, 1),
            context_score=round(ctx_eff, 1),
            anomaly_score=round(anom_eff, 1),
            sms_contribution=sms_contrib,
            url_contribution=url_contrib,
            qr_contribution=qr_contrib,
            context_contribution=ctx_contrib,
            anomaly_contribution=anom_contrib,
            final_score=final_clamped,
            category=category
        )

        # 5. Generate Evidence-Backed Explanations (XAI)
        structured_reasons = self.xai_engine.generate_explanations(
            model_results=active_model_results,
            rule_results=rule_results,
            context_findings=context_findings,
            url_features=url_features,
            max_user_reasons=5
        )
        reasons_text = [r.title for r in structured_reasons]
        if not reasons_text and category == ThreatCategory.SAFE:
            reasons_text = ["No known threat indicators detected; all security checks passed."]

        model_versions = {
            name: c.model_version for name, c in active_model_results.items()
        }

        return RiskAssessment(
            risk_score=final_clamped,
            category=category,
            score_breakdown=breakdown,
            model_results=active_model_results,
            rule_results=rule_results,
            context_findings=context_findings,
            reasons=reasons_text,
            structured_reasons=structured_reasons,
            recommended_action=recommended_action,
            timestamp=timestamp,
            model_versions=model_versions,
            unavailable_models=unavailable_models
        )

    def generate_threat_report(
        self,
        assessment: RiskAssessment,
        detection_type: str,
        url_metadata: Optional[Dict[str, Any]] = None,
        qr_metadata: Optional[Dict[str, Any]] = None,
    ) -> ThreatReport:
        """
        Creates a structured Threat Report adhering strictly to privacy requirements
        (no raw SMS body retained).
        """
        report_id = f"TR-{uuid.uuid4().hex[:12].upper()}"

        return ThreatReport(
            report_id=report_id,
            timestamp=assessment.timestamp,
            detection_type=detection_type,
            risk_score=assessment.risk_score,
            category=assessment.category,
            score_breakdown=assessment.score_breakdown,
            model_results=assessment.model_results,
            rule_results=assessment.rule_results,
            context_findings=assessment.context_findings,
            reasons=assessment.reasons,
            recommended_action=assessment.recommended_action,
            model_versions=assessment.model_versions,
            url_metadata=url_metadata or {},
            qr_metadata=qr_metadata or {},
            privacy_notice="Raw SMS content is sanitized and kept strictly local-first."
        )

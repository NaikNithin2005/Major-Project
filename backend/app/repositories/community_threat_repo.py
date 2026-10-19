"""
Community Threat Report Repository (Phase 18).
Handles persistence, deduplication, consensus aggregation,
and privacy-preserving retrieval for user-shared threat intelligence.
"""

from datetime import datetime, timezone, timedelta
from typing import List, Optional, Tuple, Dict, Any
from sqlalchemy.orm import Session
from sqlalchemy import select, func, desc, or_, and_

from app.models.threat_intel import CommunityThreatReport, UserSharingPreference, ThreatIntel


class CommunityThreatRepository:
    def __init__(self, db: Session):
        self.db = db

    # -------------------------------------------------------------------------
    # User Sharing Preferences & Consent
    # -------------------------------------------------------------------------
    def get_user_preferences(self, user_id: str) -> Optional[UserSharingPreference]:
        return self.db.query(UserSharingPreference).filter(UserSharingPreference.user_id == user_id).first()

    def set_user_preferences(
        self,
        user_id: str,
        is_sharing_enabled: bool,
        consent_version: str = "v1.0",
        allow_campaign_correlation: bool = True
    ) -> UserSharingPreference:
        now = datetime.now(timezone.utc)
        pref = self.get_user_preferences(user_id)
        if not pref:
            pref = UserSharingPreference(
                user_id=user_id,
                is_sharing_enabled=is_sharing_enabled,
                consent_version=consent_version,
                consent_timestamp=now if is_sharing_enabled else None,
                allow_campaign_correlation=allow_campaign_correlation,
                withdrawn_at=None if is_sharing_enabled else now,
                updated_at=now
            )
            self.db.add(pref)
        else:
            pref.is_sharing_enabled = is_sharing_enabled
            pref.consent_version = consent_version
            pref.allow_campaign_correlation = allow_campaign_correlation
            pref.updated_at = now
            if is_sharing_enabled:
                pref.consent_timestamp = now
                pref.withdrawn_at = None
            else:
                pref.withdrawn_at = now

        self.db.commit()
        self.db.refresh(pref)
        return pref

    # -------------------------------------------------------------------------
    # Community Threat Report Ingestion & Corroboration
    # -------------------------------------------------------------------------
    def find_recent_by_hash(self, indicator_hash: str, within_hours: int = 72) -> Optional[CommunityThreatReport]:
        cutoff = datetime.now(timezone.utc) - timedelta(hours=within_hours)
        return (
            self.db.query(CommunityThreatReport)
            .filter(
                CommunityThreatReport.indicator_hash == indicator_hash,
                CommunityThreatReport.submitted_at >= cutoff
            )
            .order_by(desc(CommunityThreatReport.submitted_at))
            .first()
        )

    def submit_report(
        self,
        indicator_type: str,
        indicator_value: str,
        indicator_hash: str,
        threat_category: str,
        user_risk_score: int,
        reporter_user_id: Optional[str] = None,
        model_verdicts: Optional[Dict[str, Any]] = None,
        context_metadata: Optional[Dict[str, Any]] = None,
        ip_origin_masked: Optional[str] = None
    ) -> Tuple[CommunityThreatReport, bool]:
        """
        Saves a community threat report.
        If an identical indicator hash was reported within 72 hours, corroborates the existing report
        to avoid poisoning and calculate dynamic multi-user consensus.
        Returns (report, is_new_entry).
        """
        now = datetime.now(timezone.utc)
        existing = self.find_recent_by_hash(indicator_hash)

        if existing:
            # Corroborate existing report
            existing.corroboration_count += 1
            existing.updated_at = now
            # Update consensus score dynamically based on reports and mean risk score
            # Formula: min(1.0, 0.4 + (corroborations * 0.15) + (risk / 300))
            new_consensus = min(1.0, 0.4 + (existing.corroboration_count * 0.15) + (user_risk_score / 300.0))
            existing.consensus_score = round(new_consensus, 2)
            
            # Merge model verdicts if provided
            if model_verdicts and not existing.model_verdicts:
                existing.model_verdicts = model_verdicts
                
            self.db.commit()
            self.db.refresh(existing)
            return existing, False

        # Fresh report
        initial_consensus = round(min(0.7, (user_risk_score / 150.0)), 2)
        report = CommunityThreatReport(
            reporter_user_id=reporter_user_id,
            indicator_type=indicator_type.upper(),
            indicator_value=indicator_value,
            indicator_hash=indicator_hash,
            threat_category=threat_category.upper(),
            user_risk_score=user_risk_score,
            model_verdicts=model_verdicts or {},
            context_metadata=context_metadata or {},
            status="PENDING_REVIEW",
            consensus_score=initial_consensus,
            corroboration_count=1,
            submitted_at=now,
            updated_at=now,
            ip_origin_masked=ip_origin_masked
        )
        self.db.add(report)
        self.db.commit()
        self.db.refresh(report)
        return report, True

    def get_user_reports(self, user_id: str, skip: int = 0, limit: int = 50) -> Tuple[List[CommunityThreatReport], int]:
        q = self.db.query(CommunityThreatReport).filter(CommunityThreatReport.reporter_user_id == user_id)
        total = q.count()
        items = q.order_by(desc(CommunityThreatReport.submitted_at)).offset(skip).limit(limit).all()
        return items, total

    def get_report_by_id(self, report_id: str) -> Optional[CommunityThreatReport]:
        return self.db.query(CommunityThreatReport).filter(CommunityThreatReport.id == report_id).first()

    def withdraw_user_report(self, report_id: str, user_id: str) -> bool:
        """Allows a user to withdraw their own pending report for privacy compliance."""
        report = (
            self.db.query(CommunityThreatReport)
            .filter(
                CommunityThreatReport.id == report_id,
                CommunityThreatReport.reporter_user_id == user_id,
                CommunityThreatReport.status == "PENDING_REVIEW"
            )
            .first()
        )
        if not report:
            return False
        
        self.db.delete(report)
        self.db.commit()
        return True

    # -------------------------------------------------------------------------
    # SOC Admin Triage & Management
    # -------------------------------------------------------------------------
    def list_reports(
        self,
        status: Optional[str] = None,
        indicator_type: Optional[str] = None,
        threat_category: Optional[str] = None,
        search: Optional[str] = None,
        skip: int = 0,
        limit: int = 50
    ) -> Tuple[List[CommunityThreatReport], int]:
        q = self.db.query(CommunityThreatReport)

        if status:
            q = q.filter(CommunityThreatReport.status == status.upper())
        if indicator_type:
            q = q.filter(CommunityThreatReport.indicator_type == indicator_type.upper())
        if threat_category:
            q = q.filter(CommunityThreatReport.threat_category == threat_category.upper())
        if search:
            term = f"%{search.strip()}%"
            q = q.filter(
                or_(
                    CommunityThreatReport.indicator_value.ilike(term),
                    CommunityThreatReport.anonymous_submission_id.ilike(term)
                )
            )

        total = q.count()
        items = q.order_by(desc(CommunityThreatReport.submitted_at)).offset(skip).limit(limit).all()
        return items, total

    def triage_report(
        self,
        report_id: str,
        admin_user_id: str,
        action: str,
        admin_notes: Optional[str] = None,
        promoted_indicator_id: Optional[str] = None
    ) -> Optional[CommunityThreatReport]:
        report = self.get_report_by_id(report_id)
        if not report:
            return None

        now = datetime.now(timezone.utc)
        report.reviewed_by_admin_id = admin_user_id
        report.reviewed_at = now
        report.admin_notes = admin_notes
        report.updated_at = now

        if action == "APPROVE_AND_PROMOTE":
            report.status = "PROMOTED"
            report.promoted_indicator_id = promoted_indicator_id
        elif action == "REJECT":
            report.status = "REJECTED"
        elif action == "MARK_DUPLICATE":
            report.status = "REJECTED"
            if not report.admin_notes:
                report.admin_notes = "Marked as duplicate"
        else:
            report.status = action.upper()

        self.db.commit()
        self.db.refresh(report)
        return report

    def get_community_statistics(self) -> Dict[str, int]:
        total = self.db.query(func.count(CommunityThreatReport.id)).scalar() or 0
        pending = (
            self.db.query(func.count(CommunityThreatReport.id))
            .filter(CommunityThreatReport.status == "PENDING_REVIEW")
            .scalar() or 0
        )
        approved = (
            self.db.query(func.count(CommunityThreatReport.id))
            .filter(CommunityThreatReport.status.in_(["APPROVED", "PROMOTED"]))
            .scalar() or 0
        )
        rejected = (
            self.db.query(func.count(CommunityThreatReport.id))
            .filter(CommunityThreatReport.status == "REJECTED")
            .scalar() or 0
        )
        return {
            "total_community_reports": total,
            "pending_community_reports": pending,
            "approved_community_reports": approved,
            "rejected_community_reports": rejected
        }

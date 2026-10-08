from typing import Dict, List, Optional, Any
from sqlalchemy.orm import Session
from sqlalchemy import select, func, and_
from app.models.threat_event import ThreatEvent
from app.models.feedback import Feedback


class AnalyticsRepository:
    def __init__(self, db: Session):
        self.db = db

    def get_summary(self, user_id: Optional[str] = None) -> Dict[str, Any]:
        """Aggregate detection statistics, optionally filtered to a single user."""
        filter_cond = [ThreatEvent.user_id == user_id] if user_id else []

        total_stmt = select(func.count()).select_from(ThreatEvent)
        if filter_cond:
            total_stmt = total_stmt.where(*filter_cond)
        total_detections = self.db.scalar(total_stmt) or 0

        # Category Counts
        cat_stmt = (
            select(ThreatEvent.threat_category, func.count())
            .select_from(ThreatEvent)
        )
        if filter_cond:
            cat_stmt = cat_stmt.where(*filter_cond)
        cat_counts = dict(self.db.execute(cat_stmt.group_by(ThreatEvent.threat_category)).all())

        # Channel Counts
        chan_stmt = (
            select(ThreatEvent.event_type, func.count())
            .select_from(ThreatEvent)
        )
        if filter_cond:
            chan_stmt = chan_stmt.where(*filter_cond)
        chan_counts = dict(self.db.execute(chan_stmt.group_by(ThreatEvent.event_type)).all())

        # Average Risk Score
        avg_stmt = select(func.avg(ThreatEvent.risk_score)).select_from(ThreatEvent)
        if filter_cond:
            avg_stmt = avg_stmt.where(*filter_cond)
        avg_score = float(self.db.scalar(avg_stmt) or 0.0)

        # Feedback stats
        fb_total_stmt = select(func.count()).select_from(Feedback)
        if user_id:
            fb_total_stmt = fb_total_stmt.where(Feedback.user_id == user_id)
        total_feedback = self.db.scalar(fb_total_stmt) or 0

        fp_stmt = select(func.count()).select_from(Feedback).where(Feedback.classification == "FALSE_POSITIVE")
        if user_id:
            fp_stmt = fp_stmt.where(Feedback.user_id == user_id)
        false_positives = self.db.scalar(fp_stmt) or 0

        # User stats (for global admin analytics)
        total_users = None
        active_users = None
        if not user_id:
            from app.models.user import User
            total_users = self.db.scalar(select(func.count()).select_from(User)) or 0
            active_users = self.db.scalar(select(func.count()).select_from(User).where(User.is_active == True)) or 0

        return {
            "total_detections": total_detections,
            "safe_count": cat_counts.get("SAFE", 0),
            "low_count": cat_counts.get("LOW", 0),
            "medium_count": cat_counts.get("MEDIUM", 0),
            "high_count": cat_counts.get("HIGH", 0),
            "sms_count": chan_counts.get("SMS", 0),
            "qr_count": chan_counts.get("QR", 0),
            "average_risk_score": round(avg_score, 1),
            "total_feedback_count": total_feedback,
            "false_positive_count": false_positives,
            "total_users": total_users,
            "active_users": active_users,
        }


    def get_threat_trends(self, days: int = 7, user_id: Optional[str] = None) -> List[Dict[str, Any]]:
        """Calculate daily threat aggregation."""
        # Lightweight portable aggregation for test & production compatibility
        filter_cond = [ThreatEvent.user_id == user_id] if user_id else []
        stmt = (
            select(
                func.date(ThreatEvent.timestamp).label("day"),
                func.count().label("count"),
                func.avg(ThreatEvent.risk_score).label("avg_risk"),
            )
            .select_from(ThreatEvent)
        )
        if filter_cond:
            stmt = stmt.where(*filter_cond)
        rows = self.db.execute(stmt.group_by(func.date(ThreatEvent.timestamp)).order_by("day")).all()

        points = []
        for r in rows:
            day_str = str(r[0])
            count = int(r[1])
            avg_risk = float(r[2] or 0.0)
            
            # Count high risk events for day
            high_stmt = select(func.count()).select_from(ThreatEvent).where(
                func.date(ThreatEvent.timestamp) == r[0],
                ThreatEvent.threat_category == "HIGH"
            )
            if filter_cond:
                high_stmt = high_stmt.where(*filter_cond)
            high_count = self.db.scalar(high_stmt) or 0

            points.append({
                "date": day_str,
                "count": count,
                "avg_risk": round(avg_risk, 1),
                "high_risk_count": high_count
            })
        return points

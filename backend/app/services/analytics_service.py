from typing import Dict, Any, Optional
from sqlalchemy.orm import Session
from app.repositories.analytics_repo import AnalyticsRepository
from app.schemas.analytics import AnalyticsSummaryResponse, ThreatTrendsResponse, ThreatTrendPoint


class AnalyticsService:
    def __init__(self, db: Session):
        self.db = db
        self.analytics_repo = AnalyticsRepository(db)

    def get_user_summary(self, user_id: str) -> AnalyticsSummaryResponse:
        data = self.analytics_repo.get_summary(user_id=user_id)
        return AnalyticsSummaryResponse(**data)

    def get_global_summary(self) -> AnalyticsSummaryResponse:
        data = self.analytics_repo.get_summary(user_id=None)
        return AnalyticsSummaryResponse(**data)

    def get_threat_trends(self, days: int = 7, user_id: Optional[str] = None) -> ThreatTrendsResponse:
        raw_points = self.analytics_repo.get_threat_trends(days=days, user_id=user_id)
        summary = self.analytics_repo.get_summary(user_id=user_id)

        points = [ThreatTrendPoint(**p) for p in raw_points]
        cat_dist = {
            "SAFE": summary["safe_count"],
            "LOW": summary["low_count"],
            "MEDIUM": summary["medium_count"],
            "HIGH": summary["high_count"],
        }
        chan_dist = {
            "SMS": summary["sms_count"],
            "QR": summary["qr_count"],
        }

        return ThreatTrendsResponse(
            period=f"Last {days} days",
            data_points=points,
            category_distribution=cat_dist,
            channel_distribution=chan_dist
        )

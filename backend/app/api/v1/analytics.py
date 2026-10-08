from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.analytics import AnalyticsSummaryResponse, ThreatTrendsResponse
from app.services.analytics_service import AnalyticsService

router = APIRouter(prefix="/analytics", tags=["Analytics"])


@router.get("/summary", response_model=AnalyticsSummaryResponse)
def get_analytics_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Get aggregated analytics summary metrics.
    For regular users: returns metrics scoped to their own telemetry.
    For admin users: returns global system-wide telemetry summary.
    """
    service = AnalyticsService(db)
    if current_user.role == "ADMIN":
        return service.get_global_summary()
    else:
        return service.get_user_summary(user_id=current_user.id)


@router.get("/trends", response_model=ThreatTrendsResponse)
def get_threat_trends(
    days: int = Query(default=7, ge=1, le=90, description="Trend lookback window in days"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Get detection trends over time.
    For regular users: trends for their own detections.
    For admin users: global trend metrics.
    """
    service = AnalyticsService(db)
    user_id = None if current_user.role == "ADMIN" else current_user.id
    return service.get_threat_trends(days=days, user_id=user_id)

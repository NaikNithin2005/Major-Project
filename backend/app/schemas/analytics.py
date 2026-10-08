from typing import Dict, List, Optional
from pydantic import BaseModel


class AnalyticsSummaryResponse(BaseModel):
    total_detections: int
    safe_count: int
    low_count: int
    medium_count: int
    high_count: int
    sms_count: int
    qr_count: int
    average_risk_score: float
    total_feedback_count: int
    false_positive_count: int
    total_users: Optional[int] = None
    active_users: Optional[int] = None



class ThreatTrendPoint(BaseModel):
    date: str
    count: int
    avg_risk: float
    high_risk_count: int


class ThreatTrendsResponse(BaseModel):
    period: str
    data_points: List[ThreatTrendPoint]
    category_distribution: Dict[str, int]
    channel_distribution: Dict[str, int]

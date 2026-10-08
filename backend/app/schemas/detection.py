from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field, field_validator


class DetectionCreateRequest(BaseModel):
    device_id: Optional[str] = Field(default=None, description="Optional registered device ID")
    event_type: str = Field(..., description="Telemetry source: QR or SMS")
    risk_score: int = Field(..., ge=0, le=100, description="Risk Score from 0 to 100")
    threat_category: str = Field(..., description="Threat category: SAFE, LOW, MEDIUM, or HIGH")
    recommended_action: str = Field(..., description="Recommended action policy")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), description="Event occurrence timestamp")

    # Minimized AI & Context telemetry (no raw SMS text!)
    model_versions: Optional[Dict[str, Any]] = None
    model_results: Optional[Dict[str, Any]] = None
    rule_results: Optional[List[Dict[str, Any]]] = None
    context_results: Optional[Dict[str, Any]] = None
    reasons: Optional[List[str]] = None
    url_metadata: Optional[Dict[str, Any]] = None
    qr_metadata: Optional[Dict[str, Any]] = None
    client_event_id: Optional[str] = Field(default=None, description="Idempotency key / local client event ID")
    related_threat_id: Optional[str] = None
    campaign_id: Optional[str] = None

    @field_validator("event_type")
    @classmethod
    def validate_event_type(cls, v: str) -> str:
        upper = v.strip().upper()
        if upper not in ("QR", "SMS"):
            raise ValueError("event_type must be either 'QR' or 'SMS'")
        return upper

    @field_validator("threat_category")
    @classmethod
    def validate_category(cls, v: str) -> str:
        upper = v.strip().upper()
        if upper in ("SAFE", "LOW", "MEDIUM", "HIGH"):
            return upper
        if upper in ("QUISHING", "SMISHING", "MALICIOUS", "CRITICAL", "BLOCKED"):
            return "HIGH"
        if upper in ("SUSPICIOUS", "WARNING", "WARNED", "SPAM"):
            return "MEDIUM"
        if upper in ("CLEAN", "BENIGN", "ALLOWED", "HAM"):
            return "SAFE"
        raise ValueError("threat_category must be one of: SAFE, LOW, MEDIUM, HIGH, SMISHING, QUISHING")


class DetectionResponse(BaseModel):
    id: str
    client_event_id: Optional[str] = None
    user_id: Optional[str] = None
    device_id: Optional[str] = None
    event_type: str
    risk_score: int
    threat_category: str
    recommended_action: str
    timestamp: datetime
    model_versions: Optional[Dict[str, Any]] = None
    model_results: Optional[Dict[str, Any]] = None
    rule_results: Optional[List[Dict[str, Any]]] = None
    context_results: Optional[Dict[str, Any]] = None
    reasons: Optional[List[str]] = None
    url_metadata: Optional[Dict[str, Any]] = None
    qr_metadata: Optional[Dict[str, Any]] = None
    related_threat_id: Optional[str] = None
    campaign_id: Optional[str] = None
    status: str
    user_action: Optional[str] = None
    created_at: datetime
    updated_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class DetectionFilterParams(BaseModel):
    event_type: Optional[str] = None
    threat_category: Optional[str] = None
    min_risk_score: Optional[int] = Field(default=None, ge=0, le=100)
    max_risk_score: Optional[int] = Field(default=None, ge=0, le=100)
    status: Optional[str] = None
    search: Optional[str] = None
    start_date: Optional[datetime] = None
    end_date: Optional[datetime] = None


class ThreatStatusUpdateRequest(BaseModel):
    status: str = Field(..., description="Target status: NEW, REVIEWED, RESOLVED, FALSE_POSITIVE, or BLOCKED")

    @field_validator("status")
    @classmethod
    def validate_status(cls, v: str) -> str:
        upper = v.strip().upper()
        if upper not in ("NEW", "REPORTED", "REVIEWED", "RESOLVED", "FALSE_POSITIVE", "BLOCKED"):
            raise ValueError("status must be one of: NEW, REPORTED, REVIEWED, RESOLVED, FALSE_POSITIVE, BLOCKED")
        return upper


class UserActionRequest(BaseModel):
    action: str = Field(..., description="User decision action: BLOCK, REPORT, IGNORE, MARK_SAFE, RESOLVED, FALSE_POSITIVE")
    notes: Optional[str] = None

    @field_validator("action")
    @classmethod
    def validate_action(cls, v: str) -> str:
        upper = v.strip().upper()
        if upper not in ("BLOCK", "REPORT", "IGNORE", "MARK_SAFE", "RESOLVED", "FALSE_POSITIVE"):
            raise ValueError("action must be one of: BLOCK, REPORT, IGNORE, MARK_SAFE, RESOLVED, FALSE_POSITIVE")
        return upper


class UserStatsResponse(BaseModel):
    total_scans: int
    threats_detected: int
    threats_blocked: int
    safe_detections: int
    avg_risk_score: float
    high_risk_count: int
    medium_risk_count: int
    low_risk_count: int
    safe_count: int
    recent_qr_count: int
    recent_sms_count: int


from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field


class SafetyAlertBase(BaseModel):
    title: str
    sender: Optional[str] = None
    message_preview: Optional[str] = None
    threat_category: str = "HIGH"
    severity: str = "HIGH"
    risk_score: int = 85
    detection_reason: Optional[str] = None
    threat_event_id: Optional[str] = None


class SafetyAlertCreate(SafetyAlertBase):
    pass


class SafetyAlertResolve(BaseModel):
    action: str = Field(default="RESOLVED", description="RESOLVED or ACKNOWLEDGED")
    resolution_notes: Optional[str] = None


class SafetyAlertResponse(BaseModel):
    id: str
    user_id: str
    threat_event_id: Optional[str] = None
    title: str
    sender: Optional[str] = None
    message_preview: Optional[str] = None
    threat_category: str
    severity: str
    risk_score: int
    detection_reason: Optional[str] = None
    status: str
    resolved_at: Optional[datetime] = None
    resolved_by: Optional[str] = None
    resolution_notes: Optional[str] = None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


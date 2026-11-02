from datetime import datetime
from typing import List, Optional, Any, Dict
from pydantic import BaseModel, Field


class SenderSecurityBase(BaseModel):
    sender_id: str
    display_name: Optional[str] = None
    trust_status: str = "UNVERIFIED" # TRUSTED, UNTRUSTED, BLOCKED, UNVERIFIED
    verification_status: str = "UNVERIFIED" # VERIFIED, UNVERIFIED, SUSPICIOUS, CONFIRMED_MALICIOUS
    reason: Optional[str] = None
    risk_score: int = 0


class SenderSecurityCreate(BaseModel):
    sender_id: str
    display_name: Optional[str] = None
    trust_status: str = "TRUSTED"
    verification_status: Optional[str] = "VERIFIED"
    reason: Optional[str] = None


class SenderSecurityUpdate(BaseModel):
    display_name: Optional[str] = None
    trust_status: Optional[str] = None
    verification_status: Optional[str] = None
    reason: Optional[str] = None
    is_blocked: Optional[bool] = None
    block_reason: Optional[str] = None


class SenderSecurityResponse(BaseModel):
    id: str
    user_id: str
    sender_id: str
    display_name: Optional[str] = None
    trust_status: str
    verification_status: str
    reason: Optional[str] = None
    marked_by: str
    risk_score: int
    total_messages_count: int
    legitimate_messages_count: int
    suspicious_messages_count: int
    dangerous_messages_count: int
    last_message_preview: Optional[str] = None
    last_message_date: Optional[datetime] = None
    is_blocked: bool
    blocked_at: Optional[datetime] = None
    blocked_by: Optional[str] = None
    block_reason: Optional[str] = None
    trusted_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class QuarantinedSmsResponse(BaseModel):
    id: str
    sender: Optional[str] = None
    raw_content: Optional[str] = None
    risk_score: int
    threat_category: str
    recommended_action: str
    timestamp: datetime
    quarantined_at: Optional[datetime] = None
    quarantine_reason: Optional[str] = None
    is_quarantined: bool
    extracted_url: Optional[str] = None
    model_results: Optional[Dict[str, Any]] = None
    reasons: Optional[List[str]] = None
    status: str

    class Config:
        from_attributes = True


class BlockedSmsResponse(BaseModel):
    id: str
    sender: Optional[str] = None
    raw_content: Optional[str] = None
    risk_score: int
    threat_category: str
    blocked_at: Optional[datetime] = None
    block_reason: Optional[str] = None
    is_blocked: bool
    model_results: Optional[Dict[str, Any]] = None
    user_action: Optional[str] = None

    class Config:
        from_attributes = True


class BlockSenderRequest(BaseModel):
    sender_id: str
    reason: Optional[str] = "User blocked sender"


class TrustSenderRequest(BaseModel):
    sender_id: str
    display_name: Optional[str] = None
    reason: Optional[str] = "User verified sender as trusted"


class QuarantineActionRequest(BaseModel):
    action: str = Field(..., description="RELEASE, MARK_SAFE, CONFIRM_SMISHING, BLOCK_SENDER")
    reason: Optional[str] = None


class SmsBlockActionRequest(BaseModel):
    action: str = Field(..., description="BLOCK, UNBLOCK, RESTORE, MARK_SAFE")
    reason: Optional[str] = None


class SenderDetailsResponse(BaseModel):
    sender: SenderSecurityResponse
    message_history: List[Dict[str, Any]]
    threat_history: List[Dict[str, Any]]
    feedback_history: List[Dict[str, Any]]
    audit_trail: List[Dict[str, Any]]


class SmsDetailsResponse(BaseModel):
    threat_event: Dict[str, Any]
    sender_info: Optional[SenderSecurityResponse] = None
    audit_trail: List[Dict[str, Any]]
    feedback_history: List[Dict[str, Any]]


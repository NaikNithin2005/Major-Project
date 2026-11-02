from datetime import datetime
from typing import Optional, Any, Dict, List
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class FeedbackCreateRequest(BaseModel):
    threat_event_id: Optional[str] = Field(default=None, description="Optional ID of the threat event being evaluated")
    classification: str = Field(default="FALSE_POSITIVE", description="User assessment: SAFE, SMISHING, SUSPICIOUS, FALSE_POSITIVE, etc.")
    comment: Optional[str] = Field(default=None, max_length=1000)
    feedback_type: Optional[str] = None
    comments: Optional[str] = None
    
    # Extended Phase 8 Intelligent Feedback Fields (Hashed / Privacy-safe)
    message_hash: Optional[str] = Field(default=None, max_length=64)
    sender_hash: Optional[str] = Field(default=None, max_length=64)
    message_text: Optional[str] = Field(default=None, description="Optional raw text which is hashed for privacy")
    sender_id: Optional[str] = Field(default=None, description="Optional sender which is hashed for privacy")
    original_prediction: Optional[str] = Field(default=None, max_length=32)
    corrected_label: Optional[str] = Field(default=None, max_length=32)
    original_confidence: Optional[float] = None
    feedback_source: Optional[str] = Field(default="ANDROID", max_length=16)

    @model_validator(mode="before")
    @classmethod
    def populate_aliases(cls, data: Any) -> Any:
        import hashlib
        if isinstance(data, dict):
            data = dict(data)
            if "feedback_type" in data and "classification" not in data:
                data["classification"] = data["feedback_type"]
            if "comments" in data and "comment" not in data:
                data["comment"] = data["comments"]
            if data.get("message_text") and not data.get("message_hash"):
                data["message_hash"] = hashlib.sha256(str(data["message_text"]).strip().lower().encode("utf-8")).hexdigest()
            if data.get("sender_id") and not data.get("sender_hash"):
                data["sender_hash"] = hashlib.sha256(str(data["sender_id"]).strip().upper().encode("utf-8")).hexdigest()
            # Default threat_event_id to message_hash or dummy if not provided
            if not data.get("threat_event_id") and data.get("message_hash"):
                data["threat_event_id"] = None
        return data

    @field_validator("classification")
    @classmethod
    def validate_classification(cls, v: str) -> str:
        upper = v.strip().upper()
        valid = {
            "TRUE_POSITIVE", "FALSE_POSITIVE", "TRUE_NEGATIVE", "FALSE_NEGATIVE", "OTHER",
            "SAFE", "SMISHING", "SUSPICIOUS", "LEGITIMATE_BANKING", "LEGITIMATE_GENERAL",
            "REPORT_INCORRECT", "MARK_AS_SAFE", "MARK_AS_SMISHING", "MARK_AS_SUSPICIOUS"
        }
        if upper not in valid:
            raise ValueError(f"classification must be one of: {', '.join(sorted(valid))}")
        return upper


class FeedbackReviewRequest(BaseModel):
    review_status: str = Field(..., description="APPROVED or REJECTED")
    comment: Optional[str] = None

    @field_validator("review_status")
    @classmethod
    def validate_status(cls, v: str) -> str:
        upper = v.strip().upper()
        if upper not in {"APPROVED", "REJECTED", "PENDING"}:
            raise ValueError("review_status must be APPROVED, REJECTED, or PENDING")
        return upper


class FeedbackResponse(BaseModel):
    id: str
    threat_event_id: Optional[str] = None
    user_id: str
    classification: str
    comment: Optional[str] = None
    feedback_type: Optional[str] = None
    comments: Optional[str] = None
    created_at: datetime
    message_hash: Optional[str] = None
    sender_hash: Optional[str] = None
    original_prediction: Optional[str] = None
    corrected_label: Optional[str] = None
    original_confidence: Optional[float] = None
    feedback_source: str = "ANDROID"
    review_status: str = "PENDING"
    training_status: str = "UNPROCESSED"
    sender_reputation_score: Optional[float] = None

    @model_validator(mode="before")
    @classmethod
    def populate_response_aliases(cls, data: Any) -> Any:
        if hasattr(data, "__table__"):
            d = {c.name: getattr(data, c.name) for c in data.__table__.columns}
        elif hasattr(data, "__dict__"):
            d = data.__dict__.copy()
        elif isinstance(data, dict):
            d = dict(data)
        else:
            return data
        d["feedback_type"] = d.get("feedback_type") or d.get("classification")
        d["comments"] = d.get("comments") or d.get("comment")
        return d

    model_config = ConfigDict(from_attributes=True)


class FeedbackStatsResponse(BaseModel):
    total_submissions: int
    safe_corrections: int
    smishing_corrections: int
    suspicious_corrections: int
    pending_reviews: int
    approved_training_samples: int
    rejected_samples: int
    last_retraining_date: Optional[str] = None


class FeedbackRetrainResponse(BaseModel):
    job_id: str
    status: str
    approved_samples_count: int
    message: str
    model_candidate_version: Optional[str] = None

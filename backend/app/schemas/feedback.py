from datetime import datetime
from typing import Optional, Any
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class FeedbackCreateRequest(BaseModel):
    threat_event_id: str = Field(..., description="ID of the threat event being evaluated")
    classification: str = Field(default="FALSE_POSITIVE", description="User assessment: TRUE_POSITIVE, FALSE_POSITIVE, etc.")
    comment: Optional[str] = Field(default=None, max_length=1000)
    feedback_type: Optional[str] = None
    comments: Optional[str] = None

    @model_validator(mode="before")
    @classmethod
    def populate_aliases(cls, data: Any) -> Any:
        if isinstance(data, dict):
            data = dict(data)
            if "feedback_type" in data and "classification" not in data:
                data["classification"] = data["feedback_type"]
            if "comments" in data and "comment" not in data:
                data["comment"] = data["comments"]
        return data

    @field_validator("classification")
    @classmethod
    def validate_classification(cls, v: str) -> str:
        upper = v.strip().upper()
        valid = {"TRUE_POSITIVE", "FALSE_POSITIVE", "TRUE_NEGATIVE", "FALSE_NEGATIVE", "OTHER"}
        if upper not in valid:
            raise ValueError(f"classification must be one of: {', '.join(sorted(valid))}")
        return upper


class FeedbackResponse(BaseModel):
    id: str
    threat_event_id: str
    user_id: str
    classification: str
    comment: Optional[str] = None
    feedback_type: Optional[str] = None
    comments: Optional[str] = None
    created_at: datetime

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

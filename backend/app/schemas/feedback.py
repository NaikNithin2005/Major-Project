from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field, field_validator


class FeedbackCreateRequest(BaseModel):
    threat_event_id: str = Field(..., description="ID of the threat event being evaluated")
    classification: str = Field(..., description="User assessment: TRUE_POSITIVE, FALSE_POSITIVE, etc.")
    comment: Optional[str] = Field(default=None, max_length=1000)

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
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

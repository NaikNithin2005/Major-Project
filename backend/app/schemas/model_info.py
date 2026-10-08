from datetime import datetime
from typing import Any, Dict, Optional
from pydantic import BaseModel, ConfigDict, Field


class ModelInfoResponse(BaseModel):
    id: str
    name: str
    version: str
    type: str
    status: str
    artifact_hash: Optional[str] = None
    metadata_json: Optional[Dict[str, Any]] = None
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class ModelRegisterRequest(BaseModel):
    name: str = Field(..., max_length=64)
    version: str = Field(..., max_length=32)
    type: str = Field(..., max_length=32)
    status: str = Field(default="ACTIVE", max_length=32)
    artifact_hash: Optional[str] = Field(default=None, max_length=128)
    metadata_json: Optional[Dict[str, Any]] = None

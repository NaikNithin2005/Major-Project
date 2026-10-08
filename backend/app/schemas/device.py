from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field


class DeviceRegisterRequest(BaseModel):
    device_identifier: str = Field(..., min_length=4, max_length=128, description="Privacy-preserving device hash")
    platform: str = Field(default="ANDROID", max_length=32)
    app_version: str = Field(default="1.0.0", max_length=32)
    model_version: str = Field(default="v2.0.0", max_length=64)


class DeviceResponse(BaseModel):
    id: str
    user_id: str
    device_identifier: str
    platform: str
    app_version: str
    model_version: str
    created_at: datetime
    updated_at: datetime
    last_sync_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)

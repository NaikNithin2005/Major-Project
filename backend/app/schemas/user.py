from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field


class UserProfileUpdate(BaseModel):
    display_name: Optional[str] = Field(default=None, max_length=255)
    phone_number: Optional[str] = Field(default=None, max_length=32)


class UserResponse(BaseModel):
    id: str
    firebase_uid: str
    email: str = Field(..., max_length=255)
    display_name: Optional[str] = None
    phone_number: Optional[str] = None
    role: str
    is_active: bool
    created_at: datetime
    updated_at: datetime
    last_seen_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class UserStatusUpdateRequest(BaseModel):
    is_active: bool = Field(..., description="Active status for the user account")


class UserRoleUpdateRequest(BaseModel):
    role: str = Field(..., description="Role assignment: USER or ADMIN")


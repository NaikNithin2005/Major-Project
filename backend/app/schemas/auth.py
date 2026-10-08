from typing import Optional
from pydantic import BaseModel, Field
from app.schemas.user import UserResponse


class TokenVerifyRequest(BaseModel):
    id_token: str = Field(..., min_length=10, description="Firebase ID token or JWT bearer token")


class TokenVerifyResponse(BaseModel):
    valid: bool = True
    user: UserResponse

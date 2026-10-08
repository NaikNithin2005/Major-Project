from fastapi import APIRouter, Depends
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.user import UserResponse
from app.schemas.auth import TokenVerifyResponse

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/verify", response_model=TokenVerifyResponse)
def verify_token(
    current_user: User = Depends(get_current_user)
):
    """
    Verify an incoming Firebase/Bearer token and return the authenticated user record.
    """
    return TokenVerifyResponse(
        valid=True,
        user=UserResponse.model_validate(current_user)
    )


@router.get("/me", response_model=UserResponse)
def get_auth_me(
    current_user: User = Depends(get_current_user)
):
    """
    Retrieve profile details for the currently authenticated user.
    """
    return UserResponse.model_validate(current_user)

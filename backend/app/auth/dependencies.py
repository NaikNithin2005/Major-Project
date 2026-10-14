from datetime import datetime, timezone
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from app.config import settings
from app.database.session import get_db
from app.models.user import User
from app.auth.firebase import verify_id_token

security = HTTPBearer(auto_error=False)


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(security),
    db: Session = Depends(get_db)
) -> User:
    """
    Authenticate request via Bearer token (Firebase ID token or JWT).
    Raises HTTP 401 if token is missing, invalid, or expired.
    """
    if not credentials or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "code": "UNAUTHORIZED",
                "message": "Authentication required. Missing Bearer token."
            },
            headers={"WWW-Authenticate": "Bearer"},
        )

    token_data = verify_id_token(credentials.credentials)
    if not token_data or not token_data.get("uid"):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "code": "INVALID_TOKEN",
                "message": "Invalid or expired authentication credentials."
            },
            headers={"WWW-Authenticate": "Bearer"},
        )

    firebase_uid = token_data["uid"]
    email = token_data.get("email") or f"{firebase_uid}@squishshield.local"
    display_name = token_data.get("name")

    # Authoritative Role Resolution:
    # 1. Configured system administrators in settings.admin_emails_list are assigned ADMIN.
    # 2. In development mode with mock auth active, explicit mock-admin tokens retain ADMIN.
    # 3. All other users strictly default to USER. Client-crafted tokens or unverified claims cannot self-elevate.
    is_configured_admin = bool(email and email.lower() in settings.admin_emails_list)
    is_trusted_mock_admin = bool(
        settings.allow_mock_auth
        and settings.environment != "production"
        and token_data.get("role") == "ADMIN"
        and firebase_uid == "admin-mock-uid-0001"
    )

    initial_role = "ADMIN" if (is_configured_admin or is_trusted_mock_admin) else "USER"

    # Look up user or auto-provision on first valid login
    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        user = User(
            firebase_uid=firebase_uid,
            email=email,
            display_name=display_name,
            role=initial_role,
            is_active=True,
            last_seen_at=datetime.now(timezone.utc)
        )
        db.add(user)
        db.flush()
    else:
        user.last_seen_at = datetime.now(timezone.utc)
        # Only authoritative configured admins or trusted mock admins can be auto-promoted
        if (is_configured_admin or is_trusted_mock_admin) and user.role != "ADMIN":
            user.role = "ADMIN"
        db.flush()

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={
                "code": "ACCOUNT_DISABLED",
                "message": "User account has been deactivated."
            }
        )

    return user


def require_user(current_user: User = Depends(get_current_user)) -> User:
    """Ensure the requester is an active authenticated user."""
    return current_user


def require_admin(current_user: User = Depends(get_current_user)) -> User:
    """
    Role-Based Access Control: ensure requester possesses ADMIN privileges.
    Raises HTTP 403 Forbidden if user is standard USER.
    """
    if current_user.role.upper() != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={
                "code": "FORBIDDEN",
                "message": "Administrative privileges required to access this resource."
            }
        )
    return current_user

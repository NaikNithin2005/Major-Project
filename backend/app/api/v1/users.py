from typing import List
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.models.device import Device
from app.auth.dependencies import get_current_user
from app.schemas.user import UserResponse, UserProfileUpdate
from app.schemas.device import DeviceRegisterRequest, DeviceResponse
from app.repositories.user_repo import UserRepository
from app.repositories.device_repo import DeviceRepository
from app.repositories.audit_repo import AuditRepository

router = APIRouter(prefix="/users", tags=["Users"])


@router.get("/me", response_model=UserResponse)
def get_user_profile(
    current_user: User = Depends(get_current_user)
):
    """
    Get profile of the currently authenticated user.
    """
    return UserResponse.model_validate(current_user)


@router.patch("/me", response_model=UserResponse)
def update_user_profile(
    payload: UserProfileUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Update profile details for the authenticated user.
    Security rule: Users cannot arbitrarily change roles, email, or firebase UID.
    """
    if payload.display_name is not None:
        current_user.display_name = payload.display_name.strip()
    current_user.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(current_user)
    return UserResponse.model_validate(current_user)


@router.post("/devices", response_model=DeviceResponse)
def register_or_update_device(
    payload: DeviceRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Register or update a user device for telemetry without collecting unnecessary personal data.
    """
    device_repo = DeviceRepository(db)
    existing_device = device_repo.get_by_identifier(current_user.id, payload.device_identifier)

    if existing_device:
        existing_device.platform = payload.platform
        existing_device.app_version = payload.app_version
        existing_device.model_version = payload.model_version
        existing_device.last_sync_at = datetime.now(timezone.utc)
        existing_device.updated_at = datetime.now(timezone.utc)
        db.commit()
        db.refresh(existing_device)
        return DeviceResponse.model_validate(existing_device)
    else:
        new_device = Device(
            user_id=current_user.id,
            device_identifier=payload.device_identifier,
            platform=payload.platform,
            app_version=payload.app_version,
            model_version=payload.model_version,
            last_sync_at=datetime.now(timezone.utc)
        )
        saved = device_repo.create(new_device)
        return DeviceResponse.model_validate(saved)


@router.get("/devices", response_model=List[DeviceResponse])
def list_user_devices(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    List all devices registered to the currently authenticated user.
    """
    device_repo = DeviceRepository(db)
    devices = device_repo.list_by_user(current_user.id)
    return [DeviceResponse.model_validate(d) for d in devices]

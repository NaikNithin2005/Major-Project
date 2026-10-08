import math
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, Depends, Query, Request, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import require_admin
from app.schemas.user import UserResponse, UserStatusUpdateRequest, UserRoleUpdateRequest
from app.schemas.detection import DetectionResponse, DetectionFilterParams, ThreatStatusUpdateRequest
from app.schemas.model_info import ModelInfoResponse, ModelRegisterRequest
from app.schemas.audit_log import AuditLogResponse
from app.schemas.analytics import AnalyticsSummaryResponse
from app.schemas.common import PaginatedResponse
from app.services.detection_service import DetectionService
from app.services.model_service import ModelService
from app.services.audit_service import AuditService
from app.services.analytics_service import AnalyticsService
from app.repositories.user_repo import UserRepository

router = APIRouter(prefix="/admin", tags=["Administration"])


@router.get("/users", response_model=PaginatedResponse[UserResponse])
def admin_list_users(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    List registered users (administrative access only).
    """
    user_repo = UserRepository(db)
    skip = (page - 1) * page_size
    users = user_repo.list_users(skip=skip, limit=page_size)
    total = user_repo.count()
    pages = math.ceil(total / page_size) if total > 0 else 1

    return PaginatedResponse[UserResponse](
        items=[UserResponse.model_validate(u) for u in users],
        total=total,
        page=page,
        page_size=page_size,
        pages=pages
    )


@router.patch("/users/{user_id}/status", response_model=UserResponse)
def admin_update_user_status(
    user_id: str,
    payload: UserStatusUpdateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Activate or suspend a user account.
    Generates an authoritative backend audit log.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    user_repo = UserRepository(db)
    user = user_repo.get_by_id(user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "USER_NOT_FOUND", "message": f"User {user_id} not found."}
        )
    old_status = user.is_active
    user.is_active = payload.is_active
    db.commit()
    db.refresh(user)

    audit_service = AuditService(db)
    audit_service.audit_repo.log(
        action="USER_STATUS_UPDATED",
        resource_type="USER",
        actor_user_id=admin.id,
        resource_id=user.id,
        ip_address=client_ip,
        metadata={"old_active": old_status, "new_active": payload.is_active}
    )
    return UserResponse.model_validate(user)


@router.patch("/users/{user_id}/role", response_model=UserResponse)
def admin_update_user_role(
    user_id: str,
    payload: UserRoleUpdateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Change user role (USER or ADMIN).
    Generates an authoritative backend audit log.
    """
    target_role = payload.role.strip().upper()
    if target_role not in ("USER", "ADMIN"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "INVALID_ROLE", "message": "Role must be 'USER' or 'ADMIN'."}
        )
    client_ip = req.client.host if req.client else "127.0.0.1"
    user_repo = UserRepository(db)
    user = user_repo.get_by_id(user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "USER_NOT_FOUND", "message": f"User {user_id} not found."}
        )
    old_role = user.role
    user.role = target_role
    db.commit()
    db.refresh(user)

    audit_service = AuditService(db)
    audit_service.audit_repo.log(
        action="USER_ROLE_UPDATED",
        resource_type="USER",
        actor_user_id=admin.id,
        resource_id=user.id,
        ip_address=client_ip,
        metadata={"old_role": old_role, "new_role": target_role}
    )
    return UserResponse.model_validate(user)


@router.get("/threat-events", response_model=PaginatedResponse[DetectionResponse])
def admin_list_threat_events(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    event_type: Optional[str] = Query(default=None),
    threat_category: Optional[str] = Query(default=None),
    min_risk_score: Optional[int] = Query(default=None, ge=0, le=100),
    max_risk_score: Optional[int] = Query(default=None, ge=0, le=100),
    status: Optional[str] = Query(default=None),
    search: Optional[str] = Query(default=None),
    start_date: Optional[datetime] = Query(default=None),
    end_date: Optional[datetime] = Query(default=None),
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    List all telemetry threat events across all users for security monitoring.
    """
    filters = DetectionFilterParams(
        event_type=event_type,
        threat_category=threat_category,
        min_risk_score=min_risk_score,
        max_risk_score=max_risk_score,
        status=status,
        search=search,
        start_date=start_date,
        end_date=end_date
    )
    skip = (page - 1) * page_size
    service = DetectionService(db)
    items, total = service.list_admin_detections(skip=skip, limit=page_size, filters=filters)
    pages = math.ceil(total / page_size) if total > 0 else 1

    return PaginatedResponse[DetectionResponse](
        items=[DetectionResponse.model_validate(i) for i in items],
        total=total,
        page=page,
        page_size=page_size,
        pages=pages
    )


@router.get("/threat-events/{event_id}", response_model=DetectionResponse)
def admin_get_threat_event(
    event_id: str,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Get full threat event details for administrator security inspection.
    """
    service = DetectionService(db)
    event = service.get_any_detection(event_id)
    if not event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DETECTION_NOT_FOUND", "message": f"Threat event {event_id} not found."}
        )
    return DetectionResponse.model_validate(event)


@router.patch("/threat-events/{event_id}/status", response_model=DetectionResponse)
def admin_update_threat_status(
    event_id: str,
    payload: ThreatStatusUpdateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Update threat event status (e.g. REVIEWED, RESOLVED, FALSE_POSITIVE, BLOCKED).
    Generates an authoritative backend audit log.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = DetectionService(db)
    updated = service.update_threat_status(
        event_id=event_id,
        new_status=payload.status,
        admin_user_id=admin.id,
        ip_address=client_ip
    )
    if not updated:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DETECTION_NOT_FOUND", "message": f"Threat event {event_id} not found."}
        )
    return DetectionResponse.model_validate(updated)



@router.post("/models", response_model=ModelInfoResponse, status_code=status.HTTP_201_CREATED)
def admin_register_model(
    payload: ModelRegisterRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Register or update AI model metadata (TinyBERT, XGBoost, etc.).
    Generates an audit log entry.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = ModelService(db)
    model = service.register_model(
        request=payload,
        actor_user_id=admin.id,
        ip_address=client_ip
    )
    return ModelInfoResponse.model_validate(model)


@router.get("/audit-logs", response_model=PaginatedResponse[AuditLogResponse])
def admin_list_audit_logs(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    action: Optional[str] = Query(default=None),
    resource_type: Optional[str] = Query(default=None),
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Audit log search and retrieval for administrative compliance and security tracking.
    """
    skip = (page - 1) * page_size
    service = AuditService(db)
    logs, total = service.list_logs(
        skip=skip,
        limit=page_size,
        action=action,
        resource_type=resource_type
    )
    pages = math.ceil(total / page_size) if total > 0 else 1

    return PaginatedResponse[AuditLogResponse](
        items=[AuditLogResponse.model_validate(l) for l in logs],
        total=total,
        page=page,
        page_size=page_size,
        pages=pages
    )


@router.get("/analytics/summary", response_model=AnalyticsSummaryResponse)
def admin_analytics_summary(
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Get system-wide aggregated telemetry and threat statistics.
    """
    service = AnalyticsService(db)
    return service.get_global_summary()

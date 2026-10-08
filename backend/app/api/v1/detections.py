import math
from typing import Optional
from fastapi import APIRouter, Depends, Query, Request, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.detection import (
    DetectionCreateRequest,
    DetectionResponse,
    DetectionFilterParams,
    UserActionRequest,
    UserStatsResponse,
)
from app.schemas.common import PaginatedResponse
from app.services.detection_service import DetectionService

router = APIRouter(prefix="/detections", tags=["Detections & Telemetry"])


@router.post("", response_model=DetectionResponse, status_code=status.HTTP_201_CREATED)
def submit_detection(
    request_data: DetectionCreateRequest,
    req: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Ingest structured threat detection telemetry from Android client.
    Enforces privacy by storing only risk assessment output, not raw SMS text.
    """
    service = DetectionService(db)
    client_ip = req.client.host if req.client else "127.0.0.1"
    event = service.record_detection(
        request=request_data,
        user=current_user,
        ip_address=client_ip
    )
    return DetectionResponse.model_validate(event)


@router.get("", response_model=PaginatedResponse[DetectionResponse])
def list_user_detections(
    page: int = Query(default=1, ge=1, description="Page number"),
    page_size: int = Query(default=20, ge=1, le=100, description="Items per page"),
    event_type: Optional[str] = Query(default=None, description="Filter: QR or SMS"),
    threat_category: Optional[str] = Query(default=None, description="Filter: SAFE, LOW, MEDIUM, HIGH"),
    min_risk_score: Optional[int] = Query(default=None, ge=0, le=100),
    max_risk_score: Optional[int] = Query(default=None, ge=0, le=100),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    List threat detections for the authenticated user with filtering and pagination.
    Strictly isolated: users only receive their own detections.
    """
    filters = DetectionFilterParams(
        event_type=event_type,
        threat_category=threat_category,
        min_risk_score=min_risk_score,
        max_risk_score=max_risk_score
    )
    skip = (page - 1) * page_size
    service = DetectionService(db)
    items, total = service.list_user_detections(
        user_id=current_user.id,
        skip=skip,
        limit=page_size,
        filters=filters
    )
    pages = math.ceil(total / page_size) if total > 0 else 1

    return PaginatedResponse[DetectionResponse](
        items=[DetectionResponse.model_validate(i) for i in items],
        total=total,
        page=page,
        page_size=page_size,
        pages=pages
    )


@router.get("/stats", response_model=UserStatsResponse)
def get_user_threat_stats(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Retrieve personal security statistics for the authenticated user.
    """
    service = DetectionService(db)
    stats_data = service.get_user_stats(current_user.id)
    return UserStatsResponse(**stats_data)


@router.get("/{detection_id}", response_model=DetectionResponse)
def get_detection(
    detection_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Get a specific detection by UUID.
    Restricted to the owner of the detection (or an ADMIN).
    """
    service = DetectionService(db)
    if current_user.role == "ADMIN":
        event = service.get_any_detection(detection_id)
    else:
        event = service.get_user_detection(detection_id, current_user.id)

    if not event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DETECTION_NOT_FOUND", "message": f"Detection event {detection_id} not found."}
        )

    return DetectionResponse.model_validate(event)


@router.post("/{detection_id}/action", response_model=DetectionResponse)
def record_user_detection_action(
    detection_id: str,
    action_data: UserActionRequest,
    req: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Record user decision (BLOCK, REPORT, IGNORE, MARK_SAFE, etc.) on their own threat detection.
    Strictly isolated: users cannot act on another user's threats.
    """
    service = DetectionService(db)
    client_ip = req.client.host if req.client else "127.0.0.1"
    
    event = service.record_user_action(
        event_id=detection_id,
        user_id=current_user.id,
        action=action_data.action,
        notes=action_data.notes,
        ip_address=client_ip
    )
    if not event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DETECTION_NOT_FOUND", "message": f"Detection event {detection_id} not found or belongs to another account."}
        )
    return DetectionResponse.model_validate(event)


@router.delete("/{detection_id}", status_code=status.HTTP_200_OK)
def delete_user_detection(
    detection_id: str,
    req: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Delete a threat detection record belonging to the authenticated user.
    """
    service = DetectionService(db)
    client_ip = req.client.host if req.client else "127.0.0.1"
    success = service.delete_user_detection(detection_id, current_user.id, client_ip)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "DETECTION_NOT_FOUND", "message": f"Detection event {detection_id} not found or belongs to another account."}
        )
    return {"success": True, "message": "Detection deleted successfully."}


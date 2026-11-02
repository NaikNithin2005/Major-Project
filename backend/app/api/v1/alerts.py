from typing import List, Optional
from fastapi import APIRouter, Depends, status, HTTPException, Query
from sqlalchemy.orm import Session

from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.safety_alert import (
    SafetyAlertCreate,
    SafetyAlertResponse,
    SafetyAlertResolve,
)
from app.services.alert_service import AlertService

router = APIRouter(prefix="/alerts", tags=["Safety Alerts"])


@router.get("", response_model=List[SafetyAlertResponse])
def get_alerts(
    status: Optional[str] = Query(default=None, description="ACTIVE, ACKNOWLEDGED, RESOLVED, or ALL"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = AlertService(db)
    alerts = service.get_alerts(user_id=current_user.id, status_filter=status)
    return [SafetyAlertResponse.model_validate(a) for a in alerts]


@router.get("/{alert_id}", response_model=SafetyAlertResponse)
def get_alert_by_id(
    alert_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = AlertService(db)
    alert = service.get_alert_by_id(user_id=current_user.id, alert_id=alert_id)
    return SafetyAlertResponse.model_validate(alert)


@router.post("/{alert_id}/resolve", response_model=SafetyAlertResponse)
def resolve_alert(
    alert_id: str,
    req: SafetyAlertResolve,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = AlertService(db)
    alert = service.resolve_alert(
        user_id=current_user.id,
        alert_id=alert_id,
        resolution_notes=req.resolution_notes
    )
    return SafetyAlertResponse.model_validate(alert)


@router.post("/{alert_id}/acknowledge", response_model=SafetyAlertResponse)
def acknowledge_alert(
    alert_id: str,
    req: SafetyAlertResolve,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = AlertService(db)
    alert = service.acknowledge_alert(
        user_id=current_user.id,
        alert_id=alert_id,
        notes=req.resolution_notes
    )
    return SafetyAlertResponse.model_validate(alert)


@router.post("", response_model=SafetyAlertResponse, status_code=status.HTTP_201_CREATED)
def create_alert(
    req: SafetyAlertCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = AlertService(db)
    alert = service.create_alert(user_id=current_user.id, alert_in=req)
    return SafetyAlertResponse.model_validate(alert)


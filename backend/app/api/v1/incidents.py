"""
Security Incident Management API Router for Phase 15.
All endpoints enforce strict server-side ADMIN Role-Based Access Control (RBAC).
"""

import math
from datetime import datetime
from typing import Optional, List
from fastapi import APIRouter, Depends, Query, Request, HTTPException, status
from sqlalchemy.orm import Session

from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import require_admin
from app.services.incident_service import IncidentService
from app.schemas.incident import (
    IncidentCreateRequest,
    IncidentStatusUpdateRequest,
    IncidentAssignRequest,
    IncidentSeverityUpdateRequest,
    IncidentNoteCreateRequest,
    IncidentThreatLinkRequest,
    IncidentResponse,
    IncidentDetailResponse,
    IncidentEventResponse,
    IncidentMetricsResponse,
    CorrelationSuggestionsResponse
)
from app.schemas.common import PaginatedResponse

router = APIRouter(prefix="/admin/incidents", tags=["Security Incidents & SOC"])


@router.get("", response_model=PaginatedResponse[IncidentResponse], summary="List Security Incidents")
def list_incidents(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    status: Optional[str] = Query(default=None, description="NEW, TRIAGED, INVESTIGATING, CONTAINED, RESOLVED, CLOSED"),
    severity: Optional[str] = Query(default=None, description="LOW, MEDIUM, HIGH, CRITICAL"),
    category: Optional[str] = Query(default=None),
    assigned_admin_id: Optional[str] = Query(default=None),
    search: Optional[str] = Query(default=None),
    start_date: Optional[datetime] = Query(default=None),
    end_date: Optional[datetime] = Query(default=None),
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    List and filter security incidents with pagination.
    Restricted to authorized SOC administrators.
    """
    service = IncidentService(db)
    skip = (page - 1) * page_size
    items, total = service.list_incidents(
        skip=skip,
        limit=page_size,
        status=status,
        severity=severity,
        category=category,
        assigned_admin_id=assigned_admin_id,
        search=search,
        start_date=start_date,
        end_date=end_date
    )
    pages = math.ceil(total / page_size) if total > 0 else 1

    return PaginatedResponse[IncidentResponse](
        items=items,
        total=total,
        page=page,
        page_size=page_size,
        pages=pages
    )


@router.post("", response_model=IncidentDetailResponse, status_code=status.HTTP_201_CREATED, summary="Create Security Incident")
def create_incident(
    payload: IncidentCreateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Manually create a new security incident or seed from an existing threat event.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.create_incident(
        request=payload,
        creator_admin=admin,
        ip_address=client_ip
    )


@router.get("/metrics", response_model=IncidentMetricsResponse, summary="SOC Incident Metrics & Trends")
def get_incident_metrics(
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Retrieve real-time SOC incident statistics:
    - Active incident counts (open, critical, awaiting triage)
    - Mean Time to Triage (MTTT) and Mean Time to Resolution (MTTR)
    - Status, severity, and category distributions
    - 14-day daily incident trend
    """
    service = IncidentService(db)
    return service.get_metrics()


@router.get("/{incident_id}", response_model=IncidentDetailResponse, summary="Get Incident Details")
def get_incident_detail(
    incident_id: str,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Retrieve detailed incident dossier, including all linked threat detections,
    investigation notes, and chronological audit events.
    """
    service = IncidentService(db)
    return service.get_incident_detail(incident_id)


@router.patch("/{incident_id}/status", response_model=IncidentDetailResponse, summary="Update Incident Status")
def update_incident_status(
    incident_id: str,
    payload: IncidentStatusUpdateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Transition incident lifecycle status.
    Enforces legal state transitions and requires resolution summaries for RESOLVED,
    and reasons for REOPENED.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.update_status(
        incident_id=incident_id,
        request=payload,
        actor_admin=admin,
        ip_address=client_ip
    )


@router.patch("/{incident_id}/assign", response_model=IncidentDetailResponse, summary="Assign Incident")
def assign_incident(
    incident_id: str,
    payload: IncidentAssignRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Assign or reassign an incident to a designated SOC administrator.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.assign_admin(
        incident_id=incident_id,
        request=payload,
        actor_admin=admin,
        ip_address=client_ip
    )


@router.patch("/{incident_id}/severity", response_model=IncidentDetailResponse, summary="Update Incident Severity")
def update_incident_severity(
    incident_id: str,
    payload: IncidentSeverityUpdateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Adjust incident severity level (LOW, MEDIUM, HIGH, CRITICAL) with recorded justification.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.update_severity(
        incident_id=incident_id,
        request=payload,
        actor_admin=admin,
        ip_address=client_ip
    )


@router.post("/{incident_id}/notes", response_model=IncidentDetailResponse, summary="Add Investigation Note")
def add_investigation_note(
    incident_id: str,
    payload: IncidentNoteCreateRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Append an immutable investigation note, analysis, or remediation entry.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.add_note(
        incident_id=incident_id,
        request=payload,
        author_admin=admin,
        ip_address=client_ip
    )


@router.post("/{incident_id}/threats", response_model=IncidentDetailResponse, summary="Link Threat Event to Incident")
def link_threat_event(
    incident_id: str,
    payload: IncidentThreatLinkRequest,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Correlate and link a threat event (smishing/quishing detection) to the incident.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.link_threat(
        incident_id=incident_id,
        request=payload,
        actor_admin=admin,
        ip_address=client_ip
    )


@router.delete("/{incident_id}/threats/{threat_event_id}", response_model=IncidentDetailResponse, summary="Unlink Threat Event from Incident")
def unlink_threat_event(
    incident_id: str,
    threat_event_id: str,
    req: Request,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Remove an incorrectly correlated threat event link from the incident.
    """
    client_ip = req.client.host if req.client else "127.0.0.1"
    service = IncidentService(db)
    return service.unlink_threat(
        incident_id=incident_id,
        threat_event_id=threat_event_id,
        actor_admin=admin,
        ip_address=client_ip
    )


@router.get("/{incident_id}/correlation-suggestions", response_model=CorrelationSuggestionsResponse, summary="Suggest Correlated Threat Events")
def get_correlation_suggestions(
    incident_id: str,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Inspect indicators from currently linked threats and suggest unlinked threat events
    sharing matching URLs, domains, or campaign signatures.
    """
    service = IncidentService(db)
    return service.get_correlation_suggestions(incident_id)


@router.get("/{incident_id}/timeline", response_model=List[IncidentEventResponse], summary="Get Incident Audit Timeline")
def get_incident_timeline(
    incident_id: str,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """
    Retrieve chronological audit trail of all state transitions, assignments,
    threat links, and administrative actions for this incident.
    """
    service = IncidentService(db)
    detail = service.get_incident_detail(incident_id)
    return detail.events


@router.delete("/{incident_id}", status_code=status.HTTP_200_OK, summary="Delete Security Incident")
def delete_incident(
    incident_id: str,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Delete an incident (SOC admin only)."""
    service = IncidentService(db)
    service.delete_incident(incident_id, admin)
    return {"success": True, "message": "Incident deleted successfully"}

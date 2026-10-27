"""
Incident Service for Phase 15.
Orchestrates business logic, legal state transitions, assignment authorization,
audit logging, and evidence representation for Security Incidents.
"""

from typing import Dict, Any, List, Optional, Tuple
from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models.incident import Incident, IncidentThreatLink, IncidentNote, IncidentEvent
from app.models.threat_event import ThreatEvent
from app.models.user import User
from app.repositories.incident_repo import IncidentRepository
from app.repositories.audit_repo import AuditRepository
from app.schemas.incident import (
    IncidentCreateRequest,
    IncidentStatusUpdateRequest,
    IncidentAssignRequest,
    IncidentSeverityUpdateRequest,
    IncidentNoteCreateRequest,
    IncidentThreatLinkRequest,
    IncidentResponse,
    IncidentDetailResponse,
    LinkedThreatSummary,
    IncidentNoteResponse,
    IncidentEventResponse,
    IncidentMetricsResponse,
    CorrelationSuggestionsResponse,
    LEGAL_STATUS_TRANSITIONS,
    VALID_SEVERITIES,
    VALID_STATUSES,
    VALID_CATEGORIES,
    VALID_RELATIONSHIPS
)


class IncidentService:
    def __init__(self, db: Session):
        self.db = db
        self.repo = IncidentRepository(db)
        self.audit_repo = AuditRepository(db)

    def create_incident(
        self,
        request: IncidentCreateRequest,
        creator_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        # Validate severity
        sev = request.severity.upper()
        if sev not in VALID_SEVERITIES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "INVALID_SEVERITY", "message": f"Severity must be one of: {', '.join(VALID_SEVERITIES)}"}
            )

        # Validate category
        cat = request.category.upper()
        if cat not in VALID_CATEGORIES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "INVALID_CATEGORY", "message": f"Category must be one of: {', '.join(VALID_CATEGORIES)}"}
            )

        # Validate assigned admin if provided
        assigned_admin_id = request.assigned_admin_id
        if assigned_admin_id:
            target_admin = self.db.query(User).filter(User.id == assigned_admin_id).first()
            if not target_admin or target_admin.role.upper() != "ADMIN":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail={"code": "INVALID_ASSIGNEE", "message": "Assignee must be an active administrator."}
                )

        incident = self.repo.create_incident(
            title=request.title,
            description=request.description,
            severity=sev,
            category=cat,
            assigned_admin_id=assigned_admin_id,
            created_by_admin_id=creator_admin.id,
            metadata_json=request.metadata_json
        )

        # Link seed threat event if provided
        if request.seed_threat_event_id:
            threat = self.db.query(ThreatEvent).filter(ThreatEvent.id == request.seed_threat_event_id).first()
            if threat:
                self.repo.link_threat(
                    incident_id=incident.id,
                    threat_event_id=threat.id,
                    relationship_type="CONFIRMED",
                    correlation_signal="INITIAL_SEED_DETECTION",
                    actor_admin_id=creator_admin.id
                )

        # System audit log
        self.audit_repo.log(
            action="INCIDENT_CREATED",
            resource_type="INCIDENT",
            actor_user_id=creator_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={
                "title": incident.title,
                "severity": incident.severity,
                "category": incident.category,
                "seed_threat": request.seed_threat_event_id
            }
        )

        return self.get_incident_detail(incident.id)

    def get_incident(self, incident_id: str) -> IncidentResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )
        return self._to_incident_response(incident)

    def get_incident_detail(self, incident_id: str) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )
        return self._to_incident_detail_response(incident)

    def delete_incident(self, incident_id: str, admin: User) -> bool:
        incident = self.repo.get_by_id(incident_id)
        if incident:
            self.db.delete(incident)
            self.db.commit()
            return True
        return False

    def list_incidents(
        self,
        skip: int = 0,
        limit: int = 20,
        status: Optional[str] = None,
        severity: Optional[str] = None,
        category: Optional[str] = None,
        assigned_admin_id: Optional[str] = None,
        search: Optional[str] = None,
        start_date: Optional[Any] = None,
        end_date: Optional[Any] = None
    ) -> Tuple[List[IncidentResponse], int]:
        items, total = self.repo.list_incidents(
            skip=skip,
            limit=limit,
            status=status,
            severity=severity,
            category=category,
            assigned_admin_id=assigned_admin_id,
            search=search,
            start_date=start_date,
            end_date=end_date
        )
        responses = [self._to_incident_response(inc) for inc in items]
        return responses, total

    def update_status(
        self,
        incident_id: str,
        request: IncidentStatusUpdateRequest,
        actor_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )

        target_status = request.status.upper()
        if target_status not in VALID_STATUSES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "INVALID_STATUS", "message": f"Status must be one of: {', '.join(VALID_STATUSES)}"}
            )

        # Enforce legal state transitions
        allowed_transitions = LEGAL_STATUS_TRANSITIONS.get(incident.status, set())
        if target_status not in allowed_transitions and target_status != incident.status:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={
                    "code": "INVALID_STATE_TRANSITION",
                    "message": f"Illegal status transition from '{incident.status}' to '{target_status}'. Allowed transitions: {list(allowed_transitions)}"
                }
            )

        # Default resolution summary when moving to RESOLVED if not provided
        res_summary = request.resolution_summary
        if target_status == "RESOLVED" and not res_summary:
            res_summary = "Incident resolved by administrator action."

        # Require reason when reopening from CLOSED or RESOLVED back to INVESTIGATING
        if incident.status in ("RESOLVED", "CLOSED") and target_status == "INVESTIGATING" and not request.reason:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "REOPEN_REASON_REQUIRED", "message": "A documented reason is required when reopening an incident."}
            )

        old_status = incident.status
        self.repo.update_status(
            incident=incident,
            new_status=target_status,
            actor_admin_id=actor_admin.id,
            resolution_summary=res_summary,
            reason=request.reason
        )

        # System audit log
        self.audit_repo.log(
            action="INCIDENT_STATUS_UPDATED",
            resource_type="INCIDENT",
            actor_user_id=actor_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={
                "previous_status": old_status,
                "new_status": target_status,
                "reason": request.reason,
                "resolution_summary": request.resolution_summary
            }
        )

        return self.get_incident_detail(incident.id)

    def assign_admin(
        self,
        incident_id: str,
        request: IncidentAssignRequest,
        actor_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )

        target_admin_id = request.assigned_admin_id
        if target_admin_id:
            target_admin = self.db.query(User).filter(User.id == target_admin_id).first()
            if not target_admin:
                raise HTTPException(
                    status_code=status.HTTP_404_NOT_FOUND,
                    detail={"code": "ADMIN_NOT_FOUND", "message": f"Administrator user {target_admin_id} does not exist."}
                )
            if target_admin.role.upper() != "ADMIN":
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail={"code": "INVALID_ROLE", "message": "Incidents can only be assigned to users with ADMIN role."}
                )

        old_assignee = incident.assigned_admin_id
        self.repo.assign_admin(
            incident=incident,
            target_admin_id=target_admin_id,
            actor_admin_id=actor_admin.id,
            note=request.note
        )

        # System audit log
        self.audit_repo.log(
            action="INCIDENT_ASSIGNED",
            resource_type="INCIDENT",
            actor_user_id=actor_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={
                "previous_assignee": old_assignee,
                "new_assignee": target_admin_id,
                "note": request.note
            }
        )

        return self.get_incident_detail(incident.id)

    def update_severity(
        self,
        incident_id: str,
        request: IncidentSeverityUpdateRequest,
        actor_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )

        new_sev = request.severity.upper()
        if new_sev not in VALID_SEVERITIES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "INVALID_SEVERITY", "message": f"Severity must be one of: {', '.join(VALID_SEVERITIES)}"}
            )

        old_sev = incident.severity
        self.repo.update_severity(
            incident=incident,
            new_severity=new_sev,
            actor_admin_id=actor_admin.id,
            reason=request.reason
        )

        # System audit log
        self.audit_repo.log(
            action="INCIDENT_SEVERITY_UPDATED",
            resource_type="INCIDENT",
            actor_user_id=actor_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={
                "previous_severity": old_sev,
                "new_severity": new_sev,
                "reason": request.reason
            }
        )

        return self.get_incident_detail(incident.id)

    def link_threat(
        self,
        incident_id: str,
        request: IncidentThreatLinkRequest,
        actor_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )

        threat = self.db.query(ThreatEvent).filter(ThreatEvent.id == request.threat_event_id).first()
        if not threat:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "THREAT_NOT_FOUND", "message": f"Threat event {request.threat_event_id} not found."}
            )

        rel = request.relationship_type.upper()
        if rel not in VALID_RELATIONSHIPS:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"code": "INVALID_RELATIONSHIP", "message": f"Relationship must be one of: {', '.join(VALID_RELATIONSHIPS)}"}
            )

        self.repo.link_threat(
            incident_id=incident_id,
            threat_event_id=request.threat_event_id,
            relationship_type=rel,
            correlation_signal=request.correlation_signal,
            actor_admin_id=actor_admin.id
        )

        self.audit_repo.log(
            action="INCIDENT_THREAT_LINKED",
            resource_type="INCIDENT",
            actor_user_id=actor_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={
                "threat_event_id": request.threat_event_id,
                "relationship_type": rel,
                "signal": request.correlation_signal
            }
        )

        return self.get_incident_detail(incident.id)

    def unlink_threat(
        self,
        incident_id: str,
        threat_event_id: str,
        actor_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )

        success = self.repo.unlink_threat(
            incident_id=incident_id,
            threat_event_id=threat_event_id,
            actor_admin_id=actor_admin.id
        )
        if not success:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "LINK_NOT_FOUND", "message": f"Threat event {threat_event_id} is not linked to this incident."}
            )

        self.audit_repo.log(
            action="INCIDENT_THREAT_UNLINKED",
            resource_type="INCIDENT",
            actor_user_id=actor_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={"threat_event_id": threat_event_id}
        )

        return self.get_incident_detail(incident.id)

    def add_note(
        self,
        incident_id: str,
        request: IncidentNoteCreateRequest,
        author_admin: User,
        ip_address: Optional[str] = None
    ) -> IncidentDetailResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )

        note = self.repo.add_note(
            incident_id=incident_id,
            author_user_id=author_admin.id,
            content=request.content,
            note_type=request.note_type
        )

        self.audit_repo.log(
            action="INCIDENT_NOTE_ADDED",
            resource_type="INCIDENT",
            actor_user_id=author_admin.id,
            resource_id=incident.id,
            ip_address=ip_address,
            metadata={"note_id": note.id, "note_type": request.note_type}
        )

        return self.get_incident_detail(incident.id)

    def get_correlation_suggestions(self, incident_id: str) -> CorrelationSuggestionsResponse:
        incident = self.repo.get_by_id(incident_id)
        if not incident:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "INCIDENT_NOT_FOUND", "message": f"Incident {incident_id} not found."}
            )
        suggestions = self.repo.find_correlation_candidates(incident_id)
        return CorrelationSuggestionsResponse(
            incident_id=incident_id,
            suggestions=suggestions,
            total_suggested=len(suggestions)
        )

    def get_metrics(self) -> IncidentMetricsResponse:
        raw_metrics = self.repo.calculate_metrics()
        return IncidentMetricsResponse(**raw_metrics)

    def _to_incident_response(self, inc: Incident) -> IncidentResponse:
        admin_email = inc.assigned_admin.email if inc.assigned_admin else None
        admin_name = inc.assigned_admin.display_name if inc.assigned_admin else None

        return IncidentResponse(
            id=inc.id,
            title=inc.title,
            description=inc.description,
            severity=inc.severity,
            status=inc.status,
            category=inc.category,
            assigned_admin_id=inc.assigned_admin_id,
            assigned_admin_email=admin_email,
            assigned_admin_name=admin_name,
            created_by_admin_id=inc.created_by_admin_id,
            created_at=inc.created_at,
            updated_at=inc.updated_at,
            triaged_at=inc.triaged_at,
            contained_at=inc.contained_at,
            resolved_at=inc.resolved_at,
            closed_at=inc.closed_at,
            resolution_summary=inc.resolution_summary,
            linked_threats_count=len(inc.threat_links),
            notes_count=len(inc.notes) if hasattr(inc, "notes") and inc.notes else 0,
            metadata_json=inc.metadata_json or {}
        )

    def _to_incident_detail_response(self, inc: Incident) -> IncidentDetailResponse:
        base = self._to_incident_response(inc)

        # Build threat links
        threat_summaries: List[LinkedThreatSummary] = []
        for link in inc.threat_links:
            t = link.threat_event
            if t:
                url = None
                domain = None
                if t.url_metadata:
                    url = t.url_metadata.get("url")
                    domain = t.url_metadata.get("domain")
                elif t.qr_metadata:
                    url = t.qr_metadata.get("raw_text")

                threat_summaries.append(LinkedThreatSummary(
                    id=t.id,
                    event_type=t.event_type,
                    risk_score=t.risk_score,
                    threat_category=t.threat_category,
                    recommended_action=t.recommended_action,
                    timestamp=t.timestamp,
                    url=url,
                    domain=domain,
                    reasons=t.reasons,
                    user_id=t.user_id,
                    relationship_type=link.relationship_type,
                    correlation_signal=link.correlation_signal,
                    linked_at=link.linked_at
                ))

        # Build notes
        notes_list: List[IncidentNoteResponse] = []
        for n in inc.notes:
            author_name = n.author.display_name or n.author.email if n.author else n.author_user_id
            notes_list.append(IncidentNoteResponse(
                id=n.id,
                incident_id=n.incident_id,
                author_user_id=n.author_user_id,
                author_name=author_name,
                note_type=n.note_type,
                content=n.content,
                created_at=n.created_at,
                updated_at=n.updated_at
            ))

        # Build events
        events_list: List[IncidentEventResponse] = []
        for e in inc.events:
            actor_name = e.actor.display_name or e.actor.email if e.actor else (e.actor_user_id or "System")
            events_list.append(IncidentEventResponse(
                id=e.id,
                incident_id=e.incident_id,
                actor_user_id=e.actor_user_id,
                actor_name=actor_name,
                event_type=e.event_type,
                previous_state=e.previous_state,
                new_state=e.new_state,
                summary=e.summary,
                details_json=e.details_json,
                timestamp=e.timestamp
            ))

        return IncidentDetailResponse(
            **base.model_dump(),
            threat_links=threat_summaries,
            notes=notes_list,
            events=events_list
        )

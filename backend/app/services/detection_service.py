from typing import Tuple, List, Optional
from sqlalchemy.orm import Session
from app.models.threat_event import ThreatEvent
from app.models.user import User
from app.schemas.detection import DetectionCreateRequest, DetectionFilterParams
from app.repositories.detection_repo import DetectionRepository
from app.repositories.audit_repo import AuditRepository


class DetectionService:
    def __init__(self, db: Session):
        self.db = db
        self.detection_repo = DetectionRepository(db)
        self.audit_repo = AuditRepository(db)

    def record_detection(
        self,
        request: DetectionCreateRequest,
        user: Optional[User] = None,
        ip_address: Optional[str] = None
    ) -> ThreatEvent:
        """
        Record a Phase 6 detection telemetry event.
        Strictly enforces data minimization: NO raw SMS messages are persisted.
        Enforces idempotency: Returns existing event if client_event_id is already recorded.
        """
        # Idempotency check: avoid duplicate event on network retries
        if request.client_event_id:
            existing = self.detection_repo.find_by_client_event_id(
                user_id=user.id if user else None,
                client_event_id=request.client_event_id
            )
            if existing:
                return existing

        event = ThreatEvent(
            user_id=user.id if user else None,
            device_id=request.device_id,
            client_event_id=request.client_event_id,
            event_type=request.event_type,
            risk_score=request.risk_score,
            threat_category=request.threat_category,
            recommended_action=request.recommended_action,
            timestamp=request.timestamp,
            model_versions=request.model_versions,
            model_results=request.model_results,
            rule_results=request.rule_results,
            context_results=request.context_results,
            reasons=request.reasons,
            url_metadata=request.url_metadata,
            qr_metadata=request.qr_metadata,
            related_threat_id=request.related_threat_id,
            campaign_id=request.campaign_id,
            status="REPORTED"
        )
        saved = self.detection_repo.create(event)

        # Audit log creation for high risk events
        if request.threat_category == "HIGH":
            self.audit_repo.log(
                action="HIGH_RISK_DETECTION_RECORDED",
                resource_type="THREAT_EVENT",
                actor_user_id=user.id if user else None,
                resource_id=saved.id,
                ip_address=ip_address,
                metadata={"risk_score": request.risk_score, "event_type": request.event_type}
            )

        return saved

    def get_user_detection(self, event_id: str, user_id: str) -> Optional[ThreatEvent]:
        return self.detection_repo.get_user_event(event_id, user_id)

    def get_any_detection(self, event_id: str) -> Optional[ThreatEvent]:
        return self.detection_repo.get_by_id(event_id)

    def list_user_detections(
        self,
        user_id: str,
        skip: int = 0,
        limit: int = 20,
        filters: Optional[DetectionFilterParams] = None
    ) -> Tuple[List[ThreatEvent], int]:
        return self.detection_repo.list_user_events(user_id, skip, limit, filters)

    def list_admin_detections(
        self,
        skip: int = 0,
        limit: int = 20,
        filters: Optional[DetectionFilterParams] = None
    ) -> Tuple[List[ThreatEvent], int]:
        return self.detection_repo.list_all_events(skip, limit, filters)

    def update_threat_status(
        self,
        event_id: str,
        new_status: str,
        admin_user_id: str,
        ip_address: Optional[str] = None
    ) -> Optional[ThreatEvent]:
        event = self.detection_repo.get_by_id(event_id)
        if not event:
            return None
        old_status = event.status
        updated = self.detection_repo.update_status(event_id, new_status)
        self.audit_repo.log(
            action="THREAT_STATUS_UPDATED",
            resource_type="THREAT_EVENT",
            actor_user_id=admin_user_id,
            resource_id=event_id,
            ip_address=ip_address,
            metadata={"old_status": old_status, "new_status": new_status}
        )
        return updated

    def record_user_action(
        self,
        event_id: str,
        user_id: str,
        action: str,
        notes: Optional[str] = None,
        ip_address: Optional[str] = None
    ) -> Optional[ThreatEvent]:
        """Record an authenticated user's decision/action on their own threat."""
        updated = self.detection_repo.record_user_action(event_id, user_id, action, notes)
        if updated:
            self.audit_repo.log(
                action="USER_THREAT_ACTION",
                resource_type="THREAT_EVENT",
                actor_user_id=user_id,
                resource_id=event_id,
                ip_address=ip_address,
                metadata={"action": action, "status": updated.status, "notes": notes}
            )
        return updated

    def delete_user_detection(
        self,
        event_id: str,
        user_id: str,
        ip_address: Optional[str] = None
    ) -> bool:
        """Delete an authenticated user's detection event."""
        deleted = self.detection_repo.delete_user_detection(event_id, user_id)
        if deleted:
            self.audit_repo.log(
                action="USER_THREAT_DELETED",
                resource_type="THREAT_EVENT",
                actor_user_id=user_id,
                resource_id=event_id,
                ip_address=ip_address,
                metadata={"deleted_event_id": event_id}
            )
        return deleted

    def get_user_stats(self, user_id: str) -> dict:
        """Fetch personal aggregated security statistics for the user."""
        return self.detection_repo.get_user_stats(user_id)

    def get_sync_reliability_metrics(self) -> dict:
        """Fetch system-wide operational synchronization metrics."""
        return self.detection_repo.get_operational_sync_metrics()




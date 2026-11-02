import uuid
from datetime import datetime, timezone
from typing import List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import select, and_, desc
from fastapi import HTTPException, status

from app.models.safety_alert import SafetyAlert
from app.models.threat_event import ThreatEvent
from app.models.audit_log import AuditLog
from app.schemas.safety_alert import SafetyAlertCreate, SafetyAlertResponse


class AlertService:
    def __init__(self, db: Session):
        self.db = db

    def get_alerts(self, user_id: str, status_filter: Optional[str] = None) -> List[SafetyAlert]:
        stmt = select(SafetyAlert).where(SafetyAlert.user_id == user_id)
        if status_filter and status_filter.upper() != "ALL":
            stmt = stmt.where(SafetyAlert.status == status_filter.upper())
        stmt = stmt.order_by(desc(SafetyAlert.created_at))
        alerts = list(self.db.scalars(stmt).all())

        # If user has no alerts yet, generate from high-risk threat events if present
        if not alerts and not status_filter:
            high_threats_stmt = select(ThreatEvent).where(
                and_(
                    ThreatEvent.user_id == user_id,
                    ThreatEvent.risk_score >= 70
                )
            ).limit(5)
            high_threats = list(self.db.scalars(high_threats_stmt).all())
            for t in high_threats:
                alert = SafetyAlert(
                    user_id=user_id,
                    threat_event_id=t.id,
                    title=f"High Risk Alert from {t.sender or 'Unknown Sender'}",
                    sender=t.sender,
                    message_preview=(t.raw_content[:100] if t.raw_content else "Suspicious smishing pattern detected"),
                    threat_category=t.threat_category,
                    severity="HIGH" if t.risk_score >= 80 else "MEDIUM",
                    risk_score=t.risk_score,
                    detection_reason=t.reasons[0] if (t.reasons and len(t.reasons) > 0) else "Detected malicious pattern",
                    status="ACTIVE",
                    created_at=t.timestamp or datetime.now(timezone.utc)
                )
                self.db.add(alert)
            if high_threats:
                self.db.commit()
                return list(self.db.scalars(stmt).all())

        return alerts

    def get_alert_by_id(self, user_id: str, alert_id: str) -> SafetyAlert:
        stmt = select(SafetyAlert).where(
            and_(
                SafetyAlert.id == alert_id,
                SafetyAlert.user_id == user_id
            )
        )
        alert = self.db.scalar(stmt)
        if not alert:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Safety alert not found")
        return alert

    def resolve_alert(self, user_id: str, alert_id: str, resolution_notes: Optional[str] = None) -> SafetyAlert:
        alert = self.get_alert_by_id(user_id, alert_id)
        now = datetime.now(timezone.utc)
        alert.status = "RESOLVED"
        alert.resolved_at = now
        alert.resolved_by = user_id
        alert.resolution_notes = resolution_notes or "Marked resolved by user"
        alert.updated_at = now

        # Also update linked threat_event if exists
        if alert.threat_event_id:
            threat = self.db.scalar(select(ThreatEvent).where(ThreatEvent.id == alert.threat_event_id))
            if threat:
                threat.status = "RESOLVED"
                threat.user_action = "RESOLVED"
                threat.updated_at = now

        audit = AuditLog(
            actor_user_id=user_id,
            action="RESOLVE_SAFETY_ALERT",
            resource_type="safety_alert",
            resource_id=alert.id,
            metadata_json={"status": "RESOLVED", "notes": resolution_notes}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(alert)
        return alert

    def acknowledge_alert(self, user_id: str, alert_id: str, notes: Optional[str] = None) -> SafetyAlert:
        alert = self.get_alert_by_id(user_id, alert_id)
        alert.status = "ACKNOWLEDGED"
        alert.resolution_notes = notes or "Acknowledged by user"
        alert.updated_at = datetime.now(timezone.utc)

        audit = AuditLog(
            actor_user_id=user_id,
            action="ACKNOWLEDGE_SAFETY_ALERT",
            resource_type="safety_alert",
            resource_id=alert.id,
            metadata_json={"status": "ACKNOWLEDGED"}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(alert)
        return alert

    def create_alert(self, user_id: str, alert_in: SafetyAlertCreate) -> SafetyAlert:
        alert = SafetyAlert(
            user_id=user_id,
            title=alert_in.title,
            sender=alert_in.sender,
            message_preview=alert_in.message_preview,
            threat_category=alert_in.threat_category,
            severity=alert_in.severity,
            risk_score=alert_in.risk_score,
            detection_reason=alert_in.detection_reason,
            threat_event_id=alert_in.threat_event_id,
            status="ACTIVE",
            created_at=datetime.now(timezone.utc),
            updated_at=datetime.now(timezone.utc)
        )
        self.db.add(alert)
        self.db.commit()
        self.db.refresh(alert)
        return alert

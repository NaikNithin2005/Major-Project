from typing import List, Optional, Tuple, Dict, Any
from sqlalchemy.orm import Session
from sqlalchemy import select, func, and_
from app.models.audit_log import AuditLog
from app.repositories.base import BaseRepository


class AuditRepository(BaseRepository[AuditLog]):
    def __init__(self, db: Session):
        super().__init__(AuditLog, db)

    def log(
        self,
        action: str,
        resource_type: str,
        actor_user_id: Optional[str] = None,
        resource_id: Optional[str] = None,
        ip_address: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> AuditLog:
        """Create structured audit log entry."""
        entry = AuditLog(
            actor_user_id=actor_user_id,
            action=action,
            resource_type=resource_type,
            resource_id=resource_id,
            ip_address=ip_address,
            metadata_json=metadata
        )
        self.db.add(entry)
        self.db.flush()
        return entry

    def list_audit_logs(
        self,
        skip: int = 0,
        limit: int = 50,
        action: Optional[str] = None,
        resource_type: Optional[str] = None,
        actor_user_id: Optional[str] = None
    ) -> Tuple[List[AuditLog], int]:
        conditions = []
        if action:
            conditions.append(AuditLog.action == action)
        if resource_type:
            conditions.append(AuditLog.resource_type == resource_type)
        if actor_user_id:
            conditions.append(AuditLog.actor_user_id == actor_user_id)

        where_clause = and_(*conditions) if conditions else True
        total_stmt = select(func.count()).select_from(AuditLog).where(where_clause)
        total = self.db.scalar(total_stmt) or 0

        query_stmt = (
            select(AuditLog)
            .where(where_clause)
            .order_by(AuditLog.timestamp.desc())
            .offset(skip)
            .limit(limit)
        )
        return list(self.db.scalars(query_stmt).all()), total

    def get_security_monitoring_summary(self) -> Dict[str, Any]:
        """Aggregate security-relevant event counts for operational security monitoring."""
        total_logs = self.db.scalar(select(func.count()).select_from(AuditLog)) or 0
        role_updates = self.db.scalar(
            select(func.count()).select_from(AuditLog).where(AuditLog.action == "USER_ROLE_UPDATED")
        ) or 0
        status_updates = self.db.scalar(
            select(func.count()).select_from(AuditLog).where(AuditLog.action == "USER_STATUS_UPDATED")
        ) or 0
        threat_triage = self.db.scalar(
            select(func.count()).select_from(AuditLog).where(AuditLog.action == "THREAT_STATUS_UPDATED")
        ) or 0
        high_risk_alerts = self.db.scalar(
            select(func.count()).select_from(AuditLog).where(AuditLog.action == "HIGH_RISK_DETECTION_RECORDED")
        ) or 0

        recent_entries = list(
            self.db.scalars(
                select(AuditLog).order_by(AuditLog.timestamp.desc()).limit(10)
            ).all()
        )

        return {
            "total_audit_events": total_logs,
            "role_modifications": role_updates,
            "account_status_changes": status_updates,
            "threat_triage_actions": threat_triage,
            "high_risk_alerts": high_risk_alerts,
            "recent_events": [
                {
                    "action": e.action,
                    "resource_type": e.resource_type,
                    "actor_user_id": e.actor_user_id,
                    "timestamp": e.timestamp.isoformat() if e.timestamp else None,
                }
                for e in recent_entries
            ],
        }


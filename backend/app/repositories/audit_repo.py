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

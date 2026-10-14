from typing import List, Tuple, Optional, Dict, Any
from sqlalchemy.orm import Session
from app.models.audit_log import AuditLog
from app.repositories.audit_repo import AuditRepository


class AuditService:
    def __init__(self, db: Session):
        self.db = db
        self.audit_repo = AuditRepository(db)

    def list_logs(
        self,
        skip: int = 0,
        limit: int = 50,
        action: Optional[str] = None,
        resource_type: Optional[str] = None,
        actor_user_id: Optional[str] = None
    ) -> Tuple[List[AuditLog], int]:
        return self.audit_repo.list_audit_logs(
            skip=skip,
            limit=limit,
            action=action,
            resource_type=resource_type,
            actor_user_id=actor_user_id
        )

    def get_security_summary(self) -> Dict[str, Any]:
        """Fetch operational security monitoring summary."""
        return self.audit_repo.get_security_monitoring_summary()

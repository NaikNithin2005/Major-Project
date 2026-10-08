from typing import List, Optional, Tuple, Dict, Any
from datetime import datetime, timezone
from sqlalchemy.orm import Session
from sqlalchemy import select, func, and_, or_
from app.models.threat_event import ThreatEvent
from app.repositories.base import BaseRepository
from app.schemas.detection import DetectionFilterParams


class DetectionRepository(BaseRepository[ThreatEvent]):
    def __init__(self, db: Session):
        super().__init__(ThreatEvent, db)

    def get_user_event(self, event_id: str, user_id: str) -> Optional[ThreatEvent]:
        """Fetch threat event with strict user isolation check."""
        stmt = select(ThreatEvent).where(
            ThreatEvent.id == event_id,
            ThreatEvent.user_id == user_id
        )
        return self.db.scalars(stmt).first()

    def find_by_client_event_id(self, user_id: Optional[str], client_event_id: str) -> Optional[ThreatEvent]:
        """Fetch existing event by client idempotency key to prevent duplicates."""
        stmt = select(ThreatEvent).where(ThreatEvent.client_event_id == client_event_id)
        if user_id:
            stmt = stmt.where(ThreatEvent.user_id == user_id)
        return self.db.scalars(stmt).first()

    def list_user_events(
        self,
        user_id: str,
        skip: int = 0,
        limit: int = 20,
        filters: Optional[DetectionFilterParams] = None
    ) -> Tuple[List[ThreatEvent], int]:
        """List user's own detections with pagination and filtering."""
        conditions = [ThreatEvent.user_id == user_id]
        if filters:
            if filters.event_type:
                conditions.append(ThreatEvent.event_type == filters.event_type.upper())
            if filters.threat_category:
                conditions.append(ThreatEvent.threat_category == filters.threat_category.upper())
            if filters.min_risk_score is not None:
                conditions.append(ThreatEvent.risk_score >= filters.min_risk_score)
            if filters.max_risk_score is not None:
                conditions.append(ThreatEvent.risk_score <= filters.max_risk_score)
            if filters.status:
                conditions.append(ThreatEvent.status == filters.status.upper())
            if filters.start_date:
                conditions.append(ThreatEvent.timestamp >= filters.start_date)
            if filters.end_date:
                conditions.append(ThreatEvent.timestamp <= filters.end_date)
            if filters.search:
                term = f"%{filters.search.strip()}%"
                conditions.append(or_(
                    ThreatEvent.id.ilike(term),
                    ThreatEvent.threat_category.ilike(term),
                    ThreatEvent.event_type.ilike(term),
                    ThreatEvent.recommended_action.ilike(term)
                ))

        where_clause = and_(*conditions)
        total_stmt = select(func.count()).select_from(ThreatEvent).where(where_clause)
        total = self.db.scalar(total_stmt) or 0

        query_stmt = (
            select(ThreatEvent)
            .where(where_clause)
            .order_by(ThreatEvent.timestamp.desc())
            .offset(skip)
            .limit(limit)
        )
        items = list(self.db.scalars(query_stmt).all())
        return items, total

    def list_all_events(
        self,
        skip: int = 0,
        limit: int = 20,
        filters: Optional[DetectionFilterParams] = None
    ) -> Tuple[List[ThreatEvent], int]:
        """Administrative list across all users."""
        conditions = []
        if filters:
            if filters.event_type:
                conditions.append(ThreatEvent.event_type == filters.event_type.upper())
            if filters.threat_category:
                conditions.append(ThreatEvent.threat_category == filters.threat_category.upper())
            if filters.min_risk_score is not None:
                conditions.append(ThreatEvent.risk_score >= filters.min_risk_score)
            if filters.max_risk_score is not None:
                conditions.append(ThreatEvent.risk_score <= filters.max_risk_score)
            if filters.status:
                conditions.append(ThreatEvent.status == filters.status.upper())
            if filters.start_date:
                conditions.append(ThreatEvent.timestamp >= filters.start_date)
            if filters.end_date:
                conditions.append(ThreatEvent.timestamp <= filters.end_date)
            if filters.search:
                term = f"%{filters.search.strip()}%"
                conditions.append(or_(
                    ThreatEvent.id.ilike(term),
                    ThreatEvent.threat_category.ilike(term),
                    ThreatEvent.event_type.ilike(term),
                    ThreatEvent.recommended_action.ilike(term)
                ))

        where_clause = and_(*conditions) if conditions else True
        total_stmt = select(func.count()).select_from(ThreatEvent).where(where_clause)
        total = self.db.scalar(total_stmt) or 0

        query_stmt = (
            select(ThreatEvent)
            .where(where_clause)
            .order_by(ThreatEvent.timestamp.desc())
            .offset(skip)
            .limit(limit)
        )
        items = list(self.db.scalars(query_stmt).all())
        return items, total

    def update_status(self, event_id: str, new_status: str) -> Optional[ThreatEvent]:
        """Update threat status with immediate database persistence."""
        event = self.get_by_id(event_id)
        if event:
            event.status = new_status
            self.db.commit()
            self.db.refresh(event)
        return event

    def record_user_action(
        self,
        event_id: str,
        user_id: str,
        action: str,
        notes: Optional[str] = None
    ) -> Optional[ThreatEvent]:
        """Record an authenticated user's decision/action on their own threat."""
        event = self.get_user_event(event_id, user_id)
        if not event:
            return None
        
        event.user_action = action.upper()
        if action.upper() == "BLOCK":
            event.status = "BLOCKED"
        elif action.upper() in ("RESOLVED", "MARK_SAFE"):
            event.status = "RESOLVED"
        elif action.upper() == "FALSE_POSITIVE":
            event.status = "FALSE_POSITIVE"
        elif action.upper() == "REPORT":
            event.status = "REVIEWED"
        elif action.upper() == "IGNORE":
            event.status = "RESOLVED"

        event.updated_at = datetime.now(timezone.utc)
        self.db.commit()
        self.db.refresh(event)
        return event

    def delete_user_detection(self, event_id: str, user_id: str) -> bool:
        """Delete an authenticated user's own threat detection."""
        event = self.get_user_event(event_id, user_id)
        if not event:
            return False
        self.db.delete(event)
        self.db.commit()
        return True

    def get_user_stats(self, user_id: str) -> dict:
        """Aggregate personal security metrics for an authenticated user."""
        user_filter = ThreatEvent.user_id == user_id
        
        total_scans = self.db.scalar(select(func.count()).select_from(ThreatEvent).where(user_filter)) or 0
        
        threats_detected = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.threat_category.in_(["LOW", "MEDIUM", "HIGH"])
            )
        ) or 0

        threats_blocked = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                or_(ThreatEvent.user_action == "BLOCK", ThreatEvent.status == "BLOCKED")
            )
        ) or 0

        safe_detections = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.threat_category == "SAFE"
            )
        ) or 0

        avg_risk = self.db.scalar(
            select(func.avg(ThreatEvent.risk_score)).where(user_filter)
        ) or 0.0

        high_risk = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.threat_category == "HIGH"
            )
        ) or 0

        med_risk = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.threat_category == "MEDIUM"
            )
        ) or 0

        low_risk = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.threat_category == "LOW"
            )
        ) or 0

        safe_count = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.threat_category == "SAFE"
            )
        ) or 0

        recent_qr = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.event_type == "QR"
            )
        ) or 0

        recent_sms = self.db.scalar(
            select(func.count()).select_from(ThreatEvent).where(
                user_filter,
                ThreatEvent.event_type == "SMS"
            )
        ) or 0

        return {
            "total_scans": total_scans,
            "threats_detected": threats_detected,
            "threats_blocked": threats_blocked,
            "safe_detections": safe_detections,
            "avg_risk_score": round(float(avg_risk), 1),
            "high_risk_count": high_risk,
            "medium_risk_count": med_risk,
            "low_risk_count": low_risk,
            "safe_count": safe_count,
            "recent_qr_count": recent_qr,
            "recent_sms_count": recent_sms,
        }


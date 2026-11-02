import uuid
from datetime import datetime, timezone
from typing import List, Optional, Dict, Any, Tuple
from sqlalchemy.orm import Session
from sqlalchemy import select, and_, or_, func, desc
from fastapi import HTTPException, status

from app.models.sender_security import SenderSecurity
from app.models.threat_event import ThreatEvent
from app.models.feedback import Feedback
from app.models.audit_log import AuditLog
from app.schemas.security import (
    SenderSecurityCreate,
    SenderSecurityUpdate,
    SenderSecurityResponse,
    QuarantinedSmsResponse,
    BlockedSmsResponse,
    SenderDetailsResponse,
    SmsDetailsResponse,
)


class SecurityService:
    def __init__(self, db: Session):
        self.db = db

    def get_trusted_senders(self, user_id: str) -> List[SenderSecurity]:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.trust_status == "TRUSTED",
                SenderSecurity.is_blocked == False
            )
        ).order_by(desc(SenderSecurity.updated_at))
        return list(self.db.scalars(stmt).all())

    def get_untrusted_senders(self, user_id: str) -> List[SenderSecurity]:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.trust_status.in_(["UNTRUSTED", "UNVERIFIED"]),
                SenderSecurity.is_blocked == False
            )
        ).order_by(desc(SenderSecurity.updated_at))
        return list(self.db.scalars(stmt).all())

    def get_blocked_senders(self, user_id: str) -> List[SenderSecurity]:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.is_blocked == True
            )
        ).order_by(desc(SenderSecurity.blocked_at))
        return list(self.db.scalars(stmt).all())

    def trust_sender(self, user_id: str, sender_id: str, display_name: Optional[str] = None, reason: Optional[str] = None) -> SenderSecurity:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.sender_id == sender_id
            )
        )
        record = self.db.scalar(stmt)
        now = datetime.now(timezone.utc)
        if not record:
            record = SenderSecurity(
                user_id=user_id,
                sender_id=sender_id,
                display_name=display_name or sender_id,
                trust_status="TRUSTED",
                verification_status="VERIFIED",
                reason=reason or "Marked as trusted by user",
                marked_by="USER",
                is_blocked=False,
                trusted_at=now,
                created_at=now,
                updated_at=now
            )
            self.db.add(record)
        else:
            record.trust_status = "TRUSTED"
            record.verification_status = "VERIFIED"
            record.reason = reason or "Marked as trusted by user"
            record.marked_by = "USER"
            record.is_blocked = False
            record.trusted_at = now
            record.blocked_at = None
            record.block_reason = None
            if display_name:
                record.display_name = display_name
            record.updated_at = now

        # Add audit log
        audit = AuditLog(
            actor_user_id=user_id,
            action="TRUST_SENDER",
            resource_type="sender_security",
            resource_id=record.id,
            metadata_json={"sender_id": sender_id, "reason": reason}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(record)
        return record

    def remove_trusted_sender(self, user_id: str, sender_id: str, reason: Optional[str] = None) -> SenderSecurity:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.sender_id == sender_id
            )
        )
        record = self.db.scalar(stmt)
        if not record:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sender not found in security records")
        
        record.trust_status = "UNVERIFIED"
        record.trusted_at = None
        record.reason = reason or "Removed from trusted list"
        record.updated_at = datetime.now(timezone.utc)
        
        audit = AuditLog(
            actor_user_id=user_id,
            action="UNTRUST_SENDER",
            resource_type="sender_security",
            resource_id=record.id,
            metadata_json={"sender_id": sender_id, "reason": reason}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(record)
        return record

    def block_sender(self, user_id: str, sender_id: str, reason: Optional[str] = None) -> SenderSecurity:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.sender_id == sender_id
            )
        )
        record = self.db.scalar(stmt)
        now = datetime.now(timezone.utc)
        if not record:
            record = SenderSecurity(
                user_id=user_id,
                sender_id=sender_id,
                display_name=sender_id,
                trust_status="BLOCKED",
                verification_status="SUSPICIOUS",
                is_blocked=True,
                blocked_at=now,
                blocked_by="USER",
                block_reason=reason or "Sender blocked by user",
                created_at=now,
                updated_at=now
            )
            self.db.add(record)
        else:
            record.is_blocked = True
            record.trust_status = "BLOCKED"
            record.blocked_at = now
            record.blocked_by = "USER"
            record.block_reason = reason or "Sender blocked by user"
            record.updated_at = now

        audit = AuditLog(
            actor_user_id=user_id,
            action="BLOCK_SENDER",
            resource_type="sender_security",
            resource_id=record.id,
            metadata_json={"sender_id": sender_id, "reason": reason}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(record)
        return record

    def unblock_sender(self, user_id: str, sender_id: str) -> SenderSecurity:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.sender_id == sender_id
            )
        )
        record = self.db.scalar(stmt)
        if not record:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sender not found in blocked list")
        
        record.is_blocked = False
        record.trust_status = "UNVERIFIED"
        record.blocked_at = None
        record.block_reason = None
        record.updated_at = datetime.now(timezone.utc)

        audit = AuditLog(
            actor_user_id=user_id,
            action="UNBLOCK_SENDER",
            resource_type="sender_security",
            resource_id=record.id,
            metadata_json={"sender_id": sender_id}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(record)
        return record

    def get_quarantined_sms(self, user_id: str) -> List[ThreatEvent]:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.user_id == user_id,
                ThreatEvent.is_quarantined == True
            )
        ).order_by(desc(ThreatEvent.timestamp))
        return list(self.db.scalars(stmt).all())

    def release_quarantined_sms(self, user_id: str, threat_id: str) -> ThreatEvent:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.id == threat_id,
                ThreatEvent.user_id == user_id
            )
        )
        event = self.db.scalar(stmt)
        if not event:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Quarantined SMS not found")

        # Invariant: Releasing from quarantine MUST NOT automatically whitelist sender
        event.is_quarantined = False
        event.user_action = "RELEASED"
        event.updated_at = datetime.now(timezone.utc)

        audit = AuditLog(
            actor_user_id=user_id,
            action="RELEASE_QUARANTINED_SMS",
            resource_type="threat_event",
            resource_id=event.id,
            metadata_json={"sender": event.sender, "released": True}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(event)
        return event

    def mark_quarantined_sms_safe(self, user_id: str, threat_id: str, reason: Optional[str] = None) -> ThreatEvent:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.id == threat_id,
                ThreatEvent.user_id == user_id
            )
        )
        event = self.db.scalar(stmt)
        if not event:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SMS event not found")

        event.is_quarantined = False
        event.user_action = "MARKED_SAFE"
        event.updated_at = datetime.now(timezone.utc)

        audit = AuditLog(
            actor_user_id=user_id,
            action="MARK_SMS_SAFE",
            resource_type="threat_event",
            resource_id=event.id,
            metadata_json={"sender": event.sender, "reason": reason}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(event)
        return event

    def get_blocked_sms(self, user_id: str) -> List[ThreatEvent]:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.user_id == user_id,
                ThreatEvent.is_blocked == True
            )
        ).order_by(desc(ThreatEvent.blocked_at or ThreatEvent.timestamp))
        return list(self.db.scalars(stmt).all())

    def block_individual_sms(self, user_id: str, threat_id: str, reason: Optional[str] = None) -> ThreatEvent:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.id == threat_id,
                ThreatEvent.user_id == user_id
            )
        )
        event = self.db.scalar(stmt)
        if not event:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SMS event not found")

        # Invariant: Blocking an SMS must not automatically block the sender
        now = datetime.now(timezone.utc)
        event.is_blocked = True
        event.blocked_at = now
        event.block_reason = reason or "SMS blocked by user"
        event.user_action = "BLOCKED"
        event.updated_at = now

        audit = AuditLog(
            actor_user_id=user_id,
            action="BLOCK_SMS",
            resource_type="threat_event",
            resource_id=event.id,
            metadata_json={"sender": event.sender, "reason": reason}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(event)
        return event

    def unblock_individual_sms(self, user_id: str, threat_id: str) -> ThreatEvent:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.id == threat_id,
                ThreatEvent.user_id == user_id
            )
        )
        event = self.db.scalar(stmt)
        if not event:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Blocked SMS not found")

        event.is_blocked = False
        event.blocked_at = None
        event.block_reason = None
        event.user_action = "RESTORED"
        event.updated_at = datetime.now(timezone.utc)

        audit = AuditLog(
            actor_user_id=user_id,
            action="UNBLOCK_SMS",
            resource_type="threat_event",
            resource_id=event.id,
            metadata_json={"sender": event.sender}
        )
        self.db.add(audit)
        self.db.commit()
        self.db.refresh(event)
        return event

    def get_sender_details(self, user_id: str, sender_id: str) -> Dict[str, Any]:
        stmt = select(SenderSecurity).where(
            and_(
                SenderSecurity.user_id == user_id,
                SenderSecurity.sender_id == sender_id
            )
        )
        sender_sec = self.db.scalar(stmt)
        now = datetime.now(timezone.utc)
        if not sender_sec:
            # Create transient / default representation
            sender_sec = SenderSecurity(
                id=str(uuid.uuid4()),
                user_id=user_id,
                sender_id=sender_id,
                display_name=sender_id,
                trust_status="UNVERIFIED",
                verification_status="UNVERIFIED",
                risk_score=20,
                marked_by="SYSTEM",
                is_blocked=False,
                created_at=now,
                updated_at=now
            )

        # Get messages from this sender for this user
        msg_stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.user_id == user_id,
                ThreatEvent.sender == sender_id
            )
        ).order_by(desc(ThreatEvent.timestamp))
        messages = list(self.db.scalars(msg_stmt).all())

        threat_history = [
            {
                "id": m.id,
                "timestamp": m.timestamp.isoformat() if m.timestamp else None,
                "risk_score": m.risk_score,
                "threat_category": m.threat_category,
                "content_preview": (m.raw_content[:80] + "...") if m.raw_content else "No content",
                "recommended_action": m.recommended_action,
                "is_quarantined": m.is_quarantined,
                "is_blocked": m.is_blocked
            }
            for m in messages
        ]

        # Audit trail
        audit_stmt = select(AuditLog).where(
            and_(
                AuditLog.actor_user_id == user_id,
                or_(
                    AuditLog.resource_id == sender_sec.id,
                    AuditLog.metadata_json.like(f"%{sender_id}%")
                )
            )
        ).order_by(desc(AuditLog.created_at))
        audits = [
            {
                "id": a.id,
                "action": a.action,
                "timestamp": a.created_at.isoformat() if a.created_at else None,
                "details": a.metadata_json
            }
            for a in self.db.scalars(audit_stmt).all()
        ]

        # Feedback history
        fb_stmt = select(Feedback).where(
            and_(
                Feedback.user_id == user_id,
                Feedback.threat_event_id.in_([m.id for m in messages]) if messages else False
            )
        ).order_by(desc(Feedback.created_at))
        feedbacks = [
            {
                "id": f.id,
                "original_prediction": f.predicted_label,
                "user_label": f.corrected_label,
                "review_status": f.review_status,
                "comments": f.comments,
                "created_at": f.created_at.isoformat() if f.created_at else None
            }
            for f in (self.db.scalars(fb_stmt).all() if messages else [])
        ]

        return {
            "sender": sender_sec,
            "message_history": threat_history,
            "threat_history": threat_history,
            "feedback_history": feedbacks,
            "audit_trail": audits
        }

    def get_sms_details(self, user_id: str, threat_id: str) -> Dict[str, Any]:
        stmt = select(ThreatEvent).where(
            and_(
                ThreatEvent.id == threat_id,
                ThreatEvent.user_id == user_id
            )
        )
        event = self.db.scalar(stmt)
        if not event:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SMS record not found")

        sender_info = None
        if event.sender:
            s_stmt = select(SenderSecurity).where(
                and_(
                    SenderSecurity.user_id == user_id,
                    SenderSecurity.sender_id == event.sender
                )
            )
            sender_info = self.db.scalar(s_stmt)

        # Audit logs for this SMS
        audit_stmt = select(AuditLog).where(
            and_(
                AuditLog.actor_user_id == user_id,
                AuditLog.resource_id == threat_id
            )
        ).order_by(desc(AuditLog.created_at))
        audits = [
            {
                "id": a.id,
                "action": a.action,
                "timestamp": a.created_at.isoformat() if a.created_at else None,
                "details": a.metadata_json
            }
            for a in self.db.scalars(audit_stmt).all()
        ]

        # Feedbacks
        fb_stmt = select(Feedback).where(
            and_(
                Feedback.threat_event_id == threat_id,
                Feedback.user_id == user_id
            )
        ).order_by(desc(Feedback.created_at))
        feedbacks = [
            {
                "id": f.id,
                "original_prediction": f.predicted_label, # Immutable original model prediction
                "user_classification": f.corrected_label,
                "review_status": f.review_status,
                "comments": f.comments,
                "created_at": f.created_at.isoformat() if f.created_at else None
            }
            for f in self.db.scalars(fb_stmt).all()
        ]

        threat_dict = {
            "id": event.id,
            "sender": event.sender,
            "raw_content": event.raw_content,
            "risk_score": event.risk_score,
            "threat_category": event.threat_category,
            "recommended_action": event.recommended_action,
            "timestamp": event.timestamp.isoformat() if event.timestamp else None,
            "is_quarantined": event.is_quarantined,
            "quarantined_at": event.quarantined_at.isoformat() if event.quarantined_at else None,
            "quarantine_reason": event.quarantine_reason,
            "is_blocked": event.is_blocked,
            "blocked_at": event.blocked_at.isoformat() if event.blocked_at else None,
            "block_reason": event.block_reason,
            "extracted_url": event.extracted_url,
            "model_results": event.model_results,
            "reasons": event.reasons,
            "user_action": event.user_action,
            "status": event.status
        }

        return {
            "threat_event": threat_dict,
            "sender_info": sender_info,
            "audit_trail": audits,
            "feedback_history": feedbacks
        }

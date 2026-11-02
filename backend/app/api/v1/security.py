from typing import List, Dict, Any, Optional
from fastapi import APIRouter, Depends, status, HTTPException
from sqlalchemy.orm import Session

from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.security import (
    SenderSecurityResponse,
    QuarantinedSmsResponse,
    BlockedSmsResponse,
    BlockSenderRequest,
    TrustSenderRequest,
    QuarantineActionRequest,
    SmsBlockActionRequest,
    SenderDetailsResponse,
    SmsDetailsResponse,
)
from app.services.security_service import SecurityService

router = APIRouter(prefix="/security", tags=["Security Management"])


@router.get("/trusted-senders", response_model=List[SenderSecurityResponse])
def get_trusted_senders(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    senders = service.get_trusted_senders(user_id=current_user.id)
    return [SenderSecurityResponse.model_validate(s) for s in senders]


@router.post("/trusted-senders", response_model=SenderSecurityResponse, status_code=status.HTTP_201_CREATED)
def add_trusted_sender(
    req: TrustSenderRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    record = service.trust_sender(
        user_id=current_user.id,
        sender_id=req.sender_id,
        display_name=req.display_name,
        reason=req.reason
    )
    return SenderSecurityResponse.model_validate(record)


@router.delete("/trusted-senders/{sender_id}", response_model=SenderSecurityResponse)
def remove_trusted_sender(
    sender_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    record = service.remove_trusted_sender(user_id=current_user.id, sender_id=sender_id)
    return SenderSecurityResponse.model_validate(record)


@router.get("/untrusted-senders", response_model=List[SenderSecurityResponse])
def get_untrusted_senders(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    senders = service.get_untrusted_senders(user_id=current_user.id)
    return [SenderSecurityResponse.model_validate(s) for s in senders]


@router.get("/blocked-senders", response_model=List[SenderSecurityResponse])
def get_blocked_senders(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    senders = service.get_blocked_senders(user_id=current_user.id)
    return [SenderSecurityResponse.model_validate(s) for s in senders]


@router.post("/blocked-senders", response_model=SenderSecurityResponse, status_code=status.HTTP_201_CREATED)
def block_sender(
    req: BlockSenderRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    record = service.block_sender(
        user_id=current_user.id,
        sender_id=req.sender_id,
        reason=req.reason
    )
    return SenderSecurityResponse.model_validate(record)


@router.delete("/blocked-senders/{sender_id}", response_model=SenderSecurityResponse)
def unblock_sender(
    sender_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    record = service.unblock_sender(user_id=current_user.id, sender_id=sender_id)
    return SenderSecurityResponse.model_validate(record)


@router.get("/quarantined-sms", response_model=List[QuarantinedSmsResponse])
def get_quarantined_sms(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    events = service.get_quarantined_sms(user_id=current_user.id)
    return [QuarantinedSmsResponse.model_validate(e) for e in events]


@router.post("/quarantined-sms/{threat_id}/action", response_model=QuarantinedSmsResponse)
def handle_quarantine_action(
    threat_id: str,
    req: QuarantineActionRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    action = req.action.upper()
    if action == "RELEASE":
        event = service.release_quarantined_sms(user_id=current_user.id, threat_id=threat_id)
    elif action == "MARK_SAFE":
        event = service.mark_quarantined_sms_safe(user_id=current_user.id, threat_id=threat_id, reason=req.reason)
    elif action == "CONFIRM_SMISHING":
        event = service.confirm_quarantined_smishing(user_id=current_user.id, threat_id=threat_id, reason=req.reason)
    elif action == "BLOCK_SENDER":
        evt = service.db.scalar(service.db.query_property()) # or retrieve event
        # First retrieve event to find sender
        from app.models.threat_event import ThreatEvent
        t_event = service.db.scalar(service.db.query(ThreatEvent).filter_by(id=threat_id, user_id=current_user.id))
        if t_event and t_event.sender:
            service.block_sender(user_id=current_user.id, sender_id=t_event.sender, reason=req.reason or "Quarantined SMS sender blocked")
        event = service.confirm_quarantined_smishing(user_id=current_user.id, threat_id=threat_id, reason=req.reason)
    else:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Unknown quarantine action: {req.action}")

    return QuarantinedSmsResponse.model_validate(event)


@router.get("/blocked-sms", response_model=List[BlockedSmsResponse])
def get_blocked_sms(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    events = service.get_blocked_sms(user_id=current_user.id)
    return [BlockedSmsResponse.model_validate(e) for e in events]


@router.post("/blocked-sms/{threat_id}/action", response_model=BlockedSmsResponse)
def handle_blocked_sms_action(
    threat_id: str,
    req: SmsBlockActionRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    action = req.action.upper()
    if action in ("UNBLOCK", "RESTORE", "MARK_SAFE"):
        event = service.unblock_individual_sms(user_id=current_user.id, threat_id=threat_id)
    elif action == "BLOCK":
        event = service.block_individual_sms(user_id=current_user.id, threat_id=threat_id, reason=req.reason)
    else:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Unknown SMS action: {req.action}")

    return BlockedSmsResponse.model_validate(event)


@router.get("/senders/{sender_id}", response_model=SenderDetailsResponse)
def get_sender_details(
    sender_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    details = service.get_sender_details(user_id=current_user.id, sender_id=sender_id)
    return SenderDetailsResponse(
        sender=SenderSecurityResponse.model_validate(details["sender"]),
        message_history=details["message_history"],
        threat_history=details["threat_history"],
        feedback_history=details["feedback_history"],
        audit_trail=details["audit_trail"]
    )


@router.get("/sms/{threat_id}", response_model=SmsDetailsResponse)
def get_sms_details(
    threat_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    service = SecurityService(db)
    details = service.get_sms_details(user_id=current_user.id, threat_id=threat_id)
    sender_info = (
        SenderSecurityResponse.model_validate(details["sender_info"])
        if details["sender_info"]
        else None
    )
    return SmsDetailsResponse(
        threat_event=details["threat_event"],
        sender_info=sender_info,
        audit_trail=details["audit_trail"],
        feedback_history=details["feedback_history"]
    )


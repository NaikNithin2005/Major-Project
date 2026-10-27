from typing import Tuple, List, Optional
from sqlalchemy.orm import Session
from fastapi import HTTPException, status
from app.models.feedback import Feedback
from app.models.user import User
from app.schemas.feedback import FeedbackCreateRequest
from app.repositories.feedback_repo import FeedbackRepository
from app.repositories.detection_repo import DetectionRepository
from app.repositories.audit_repo import AuditRepository


class FeedbackService:
    def __init__(self, db: Session):
        self.db = db
        self.feedback_repo = FeedbackRepository(db)
        self.detection_repo = DetectionRepository(db)
        self.audit_repo = AuditRepository(db)

    def submit_feedback(
        self,
        request: FeedbackCreateRequest,
        user: User,
        ip_address: Optional[str] = None
    ) -> Feedback:
        # Verify threat event exists
        event = self.detection_repo.get_by_id(request.threat_event_id)
        if not event:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "EVENT_NOT_FOUND", "message": f"Threat event {request.threat_event_id} not found."}
            )

        # Ensure user can only submit feedback for their own events or if admin
        if user.role != "ADMIN" and event.user_id and event.user_id != user.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail={"code": "FORBIDDEN", "message": "Cannot submit feedback for another user's event."}
            )

        feedback = Feedback(
            threat_event_id=request.threat_event_id,
            user_id=user.id,
            classification=request.classification,
            comment=request.comment
        )
        saved = self.feedback_repo.create(feedback)

        self.audit_repo.log(
            action="FEEDBACK_SUBMITTED",
            resource_type="FEEDBACK",
            actor_user_id=user.id,
            resource_id=saved.id,
            ip_address=ip_address,
            metadata={"classification": request.classification, "threat_event_id": request.threat_event_id}
        )

        return saved

    def list_user_feedback(self, user_id: str, skip: int = 0, limit: int = 20) -> Tuple[List[Feedback], int]:
        return self.feedback_repo.list_user_feedback(user_id, skip, limit)

    def list_all_feedback(self, skip: int = 0, limit: int = 50) -> Tuple[List[Feedback], int]:
        return self.feedback_repo.list_all_feedback(skip, limit)

    def delete_feedback(self, feedback_id: str, user_id: str, is_admin: bool = False) -> bool:
        fb = self.db.query(Feedback).filter(Feedback.id == feedback_id).first()
        if not fb:
            return False
        if not is_admin and fb.user_id != user_id:
            return False
        self.db.delete(fb)
        self.db.commit()
        return True

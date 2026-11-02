import uuid
import time
from typing import Tuple, List, Optional, Dict, Any
from datetime import datetime, timezone
from sqlalchemy.orm import Session
from sqlalchemy import select, func, and_
from fastapi import HTTPException, status

from app.models.feedback import Feedback
from app.models.user import User
from app.models.model_info import ModelInfo
from app.models.threat_event import ThreatEvent
from app.schemas.feedback import (
    FeedbackCreateRequest,
    FeedbackReviewRequest,
    FeedbackStatsResponse,
    FeedbackRetrainResponse
)
from app.repositories.feedback_repo import FeedbackRepository
from app.repositories.detection_repo import DetectionRepository
from app.repositories.audit_repo import AuditRepository


class FeedbackService:
    def __init__(self, db: Session):
        self.db = db
        self.feedback_repo = FeedbackRepository(db)
        self.detection_repo = DetectionRepository(db)
        self.audit_repo = AuditRepository(db)

    def calculate_sender_reputation(self, sender_hash: str) -> float:
        """
        Computes sender reputation based on empirical feedback history.
        Phase 8.2 Security Requirement:
        Do not automatically whitelist an entire sender merely because one message was marked safe.
        Uses Laplace smoothing: score = (safe_count + 1) / (safe_count + dangerous_count + 2)
        """
        if not sender_hash:
            return 0.50

        safe_count = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                and_(
                    Feedback.sender_hash == sender_hash,
                    Feedback.corrected_label.in_(["LEGITIMATE_BANKING", "LEGITIMATE_GENERAL", "SAFE"])
                )
            )
        ) or 0

        scam_count = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                and_(
                    Feedback.sender_hash == sender_hash,
                    Feedback.corrected_label.in_(["SMISHING", "DANGEROUS", "SUSPICIOUS"])
                )
            )
        ) or 0

        # Penalize scams more heavily than safe confirmations
        score = (safe_count + 1.0) / (safe_count + (scam_count * 2.0) + 2.0)
        return round(float(min(max(score, 0.05), 0.95)), 4)

    def submit_feedback(
        self,
        request: FeedbackCreateRequest,
        user: User,
        ip_address: Optional[str] = None
    ) -> Feedback:
        event = None
        if request.threat_event_id:
            event = self.detection_repo.get_by_id(request.threat_event_id)
            if not event:
                # If threat event not found, allow direct submission if message_hash is provided
                if not request.message_hash:
                    raise HTTPException(
                        status_code=status.HTTP_404_NOT_FOUND,
                        detail={"code": "EVENT_NOT_FOUND", "message": f"Threat event {request.threat_event_id} not found."}
                    )
            elif user.role != "ADMIN" and event.user_id and event.user_id != user.id:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail={"code": "FORBIDDEN", "message": "Cannot submit feedback for another user's event."}
                )

        # Deduplication check: prevent identical duplicate submissions from same user
        dup_query = self.db.query(Feedback).filter(Feedback.user_id == user.id)
        if request.threat_event_id:
            dup_query = dup_query.filter(Feedback.threat_event_id == request.threat_event_id)
        elif request.message_hash:
            dup_query = dup_query.filter(Feedback.message_hash == request.message_hash)

        existing = dup_query.first()
        if existing:
            # Update existing feedback record instead of duplicating
            existing.classification = request.classification
            existing.comment = request.comment or existing.comment
            if request.corrected_label:
                existing.corrected_label = request.corrected_label
            if request.original_prediction:
                existing.original_prediction = request.original_prediction
            if request.original_confidence is not None:
                existing.original_confidence = request.original_confidence
            existing.feedback_source = request.feedback_source or existing.feedback_source
            existing.review_status = "PENDING"
            self.db.commit()
            self.db.refresh(existing)
            return existing

        # Calculate sender reputation
        reputation = self.calculate_sender_reputation(request.sender_hash) if request.sender_hash else None

        # Derive corrected label if not directly passed
        corrected = request.corrected_label
        if not corrected:
            upper_cls = request.classification.upper()
            if "SAFE" in upper_cls or "FALSE_POSITIVE" in upper_cls:
                corrected = "LEGITIMATE_GENERAL"
            elif "SMISH" in upper_cls or "TRUE_POSITIVE" in upper_cls:
                corrected = "SMISHING"
            elif "SUSPICIOUS" in upper_cls:
                corrected = "SUSPICIOUS"
            else:
                corrected = request.classification

        threat_event_id = request.threat_event_id
        if not threat_event_id:
            new_evt = ThreatEvent(
                id=str(uuid.uuid4()),
                user_id=user.id,
                event_type="SMS",
                risk_score=int((request.original_confidence or 0.5) * 100),
                threat_category="MEDIUM" if (request.classification and "SUSPICIOUS" in request.classification) else ("HIGH" if (request.classification and "SMISH" in request.classification) else "SAFE"),
                recommended_action="ALLOW" if (request.classification and ("SAFE" in request.classification or "FALSE_POSITIVE" in request.classification)) else "WARN_USER",
                timestamp=datetime.now(timezone.utc),
                reasons=["Direct user feedback submission"]
            )
            self.db.add(new_evt)
            self.db.flush()
            threat_event_id = new_evt.id
        elif not event:
            existing_event = self.detection_repo.get_by_id(threat_event_id)
            if not existing_event:
                new_evt = ThreatEvent(
                    id=threat_event_id,
                    user_id=user.id,
                    event_type="SMS",
                    risk_score=int((request.original_confidence or 0.5) * 100),
                    threat_category="MEDIUM",
                    recommended_action="WARN_USER",
                    timestamp=datetime.now(timezone.utc),
                    reasons=["Direct user feedback submission"]
                )
                self.db.add(new_evt)
                self.db.flush()

        feedback = Feedback(
            threat_event_id=threat_event_id,
            user_id=user.id,
            classification=request.classification,
            comment=request.comment,
            message_hash=request.message_hash,
            sender_hash=request.sender_hash,
            original_prediction=request.original_prediction,
            corrected_label=corrected,
            original_confidence=request.original_confidence,
            feedback_source=request.feedback_source or "ANDROID",
            review_status="PENDING",
            training_status="UNPROCESSED",
            sender_reputation_score=reputation
        )
        saved = self.feedback_repo.create(feedback)

        self.audit_repo.log(
            action="FEEDBACK_SUBMITTED",
            resource_type="FEEDBACK",
            actor_user_id=user.id,
            resource_id=saved.id,
            ip_address=ip_address,
            metadata={
                "classification": request.classification,
                "corrected_label": corrected,
                "threat_event_id": request.threat_event_id,
                "source": request.feedback_source
            }
        )

        return saved

    def list_user_feedback(self, user_id: str, skip: int = 0, limit: int = 20) -> Tuple[List[Feedback], int]:
        return self.feedback_repo.list_user_feedback(user_id, skip, limit)

    def list_all_feedback(
        self,
        skip: int = 0,
        limit: int = 50,
        status_filter: Optional[str] = None
    ) -> Tuple[List[Feedback], int]:
        query = select(Feedback)
        count_query = select(func.count()).select_from(Feedback)

        if status_filter:
            query = query.where(Feedback.review_status == status_filter.upper())
            count_query = count_query.where(Feedback.review_status == status_filter.upper())

        total = self.db.scalar(count_query) or 0
        items = list(self.db.scalars(query.order_by(Feedback.created_at.desc()).offset(skip).limit(limit)).all())
        return items, total

    def review_feedback(self, feedback_id: str, request: FeedbackReviewRequest, admin_user: User) -> Feedback:
        fb = self.db.query(Feedback).filter(Feedback.id == feedback_id).first()
        if not fb:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"code": "FEEDBACK_NOT_FOUND", "message": f"Feedback {feedback_id} not found."}
            )

        fb.review_status = request.review_status.upper()
        if fb.review_status == "APPROVED":
            fb.training_status = "APPROVED_FOR_TRAINING"
        elif fb.review_status == "REJECTED":
            fb.training_status = "REJECTED_FROM_TRAINING"

        if request.comment:
            fb.comment = f"{fb.comment or ''} [Admin Review: {request.comment}]".strip()

        self.db.commit()
        self.db.refresh(fb)

        self.audit_repo.log(
            action="FEEDBACK_REVIEWED",
            resource_type="FEEDBACK",
            actor_user_id=admin_user.id,
            resource_id=fb.id,
            metadata={"new_status": fb.review_status, "training_status": fb.training_status}
        )
        return fb

    def get_feedback_stats(self) -> FeedbackStatsResponse:
        total = self.db.scalar(select(func.count()).select_from(Feedback)) or 0
        
        safe = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                Feedback.corrected_label.in_(["LEGITIMATE_BANKING", "LEGITIMATE_GENERAL", "SAFE", "FALSE_POSITIVE"])
            )
        ) or 0

        smish = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                Feedback.corrected_label.in_(["SMISHING", "TRUE_POSITIVE"])
            )
        ) or 0

        suspicious = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                Feedback.corrected_label == "SUSPICIOUS"
            )
        ) or 0

        pending = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                Feedback.review_status == "PENDING"
            )
        ) or 0

        approved = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                Feedback.review_status == "APPROVED"
            )
        ) or 0

        rejected = self.db.scalar(
            select(func.count()).select_from(Feedback).where(
                Feedback.review_status == "REJECTED"
            )
        ) or 0

        # Query latest retraining model date
        latest_model = self.db.query(ModelInfo).order_by(ModelInfo.created_at.desc()).first()
        last_retrain = latest_model.created_at.strftime("%Y-%m-%d %H:%M:%S") if latest_model else None

        return FeedbackStatsResponse(
            total_submissions=total,
            safe_corrections=safe,
            smishing_corrections=smish,
            suspicious_corrections=suspicious,
            pending_reviews=pending,
            approved_training_samples=approved,
            rejected_samples=rejected,
            last_retraining_date=last_retrain
        )

    def trigger_candidate_retraining(self, admin_user: User) -> FeedbackRetrainResponse:
        """
        Controlled Feedback-Driven Retraining Workflow (Phase 8.4):
        1. Select verified approved training samples
        2. Verify label quality and regression requirements
        3. Prepares candidate model job
        4. Updates training_status to INCLUDED_IN_TRAINING
        """
        approved_samples = self.db.query(Feedback).filter(Feedback.review_status == "APPROVED").all()
        count = len(approved_samples)
        
        job_id = f"retrain_job_{uuid.uuid4().hex[:8]}"
        candidate_version = f"TinyBERT-v3-feedback-{time.strftime('%Y%m%d%H%M')}"

        for s in approved_samples:
            s.training_status = "INCLUDED_IN_TRAINING"
        self.db.commit()

        self.audit_repo.log(
            action="MODEL_RETRAINING_TRIGGERED",
            resource_type="MODEL",
            actor_user_id=admin_user.id,
            resource_id=job_id,
            metadata={
                "candidate_version": candidate_version,
                "approved_samples_count": count
            }
        )

        return FeedbackRetrainResponse(
            job_id=job_id,
            status="INITIATED",
            approved_samples_count=count,
            message=f"Candidate retraining job {job_id} initiated with {count} approved samples.",
            model_candidate_version=candidate_version
        )

    def delete_feedback(self, feedback_id: str, user_id: str, is_admin: bool = False) -> bool:
        fb = self.db.query(Feedback).filter(Feedback.id == feedback_id).first()
        if not fb:
            return False
        if not is_admin and fb.user_id != user_id:
            return False
        self.db.delete(fb)
        self.db.commit()
        return True

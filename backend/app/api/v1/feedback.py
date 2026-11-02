import math
from typing import Optional
from fastapi import APIRouter, Depends, Query, Request, status, HTTPException
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.feedback import (
    FeedbackCreateRequest,
    FeedbackResponse,
    FeedbackReviewRequest,
    FeedbackStatsResponse,
    FeedbackRetrainResponse
)
from app.schemas.common import PaginatedResponse
from app.services.feedback_service import FeedbackService

router = APIRouter(prefix="/feedback", tags=["Feedback"])


@router.post("", response_model=FeedbackResponse, status_code=status.HTTP_201_CREATED)
def submit_feedback(
    request_data: FeedbackCreateRequest,
    req: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Submit user feedback on a threat detection or classified SMS.
    Supports 4-class labels, privacy-safe message hashes, and cross-platform synchronization.
    """
    service = FeedbackService(db)
    client_ip = req.client.host if req.client else "127.0.0.1"
    feedback = service.submit_feedback(
        request=request_data,
        user=current_user,
        ip_address=client_ip
    )
    return FeedbackResponse.model_validate(feedback)


@router.get("/stats", response_model=FeedbackStatsResponse)
def get_feedback_stats(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Get aggregated feedback statistics for SOC and Model Performance management.
    """
    service = FeedbackService(db)
    return service.get_feedback_stats()


@router.get("/reputation/{sender_hash}")
def get_sender_reputation(
    sender_hash: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Phase 8.2: Get empirical sender reputation score (0.0 to 1.0) without absolute whitelisting.
    """
    service = FeedbackService(db)
    score = service.calculate_sender_reputation(sender_hash)
    return {
        "sender_hash": sender_hash,
        "reputation_score": score,
        "trust_level": "HIGH_CONFIDENCE_SAFE" if score >= 0.85 else ("MODERATE_TRUST" if score >= 0.60 else "UNVERIFIED_OR_RISK")
    }


@router.put("/{feedback_id}/review", response_model=FeedbackResponse)
def review_feedback(
    feedback_id: str,
    review_req: FeedbackReviewRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Admin: Review and approve/reject user feedback for inclusion in subsequent retraining datasets.
    """
    if current_user.role != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Only administrators can review feedback submissions."}
        )
    service = FeedbackService(db)
    fb = service.review_feedback(feedback_id, review_req, current_user)
    return FeedbackResponse.model_validate(fb)


@router.post("/retrain-candidate", response_model=FeedbackRetrainResponse)
def retrain_candidate_model(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Admin: Trigger controlled periodic candidate fine-tuning using approved feedback samples.
    """
    if current_user.role != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail={"code": "FORBIDDEN", "message": "Only administrators can trigger model retraining."}
        )
    service = FeedbackService(db)
    return service.trigger_candidate_retraining(current_user)


@router.get("", response_model=PaginatedResponse[FeedbackResponse])
def list_feedback(
    page: int = Query(default=1, ge=1, description="Page number"),
    page_size: int = Query(default=20, ge=1, le=100, description="Items per page"),
    status: Optional[str] = Query(default=None, description="Filter by review status (PENDING, APPROVED, REJECTED)"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    List feedback submissions for current user (or all if admin).
    """
    skip = (page - 1) * page_size
    service = FeedbackService(db)
    if current_user.role == "ADMIN":
        items, total = service.list_all_feedback(skip=skip, limit=page_size, status_filter=status)
    else:
        items, total = service.list_user_feedback(user_id=current_user.id, skip=skip, limit=page_size)

    pages = math.ceil(total / page_size) if total > 0 else 1

    return PaginatedResponse[FeedbackResponse](
        items=[FeedbackResponse.model_validate(i) for i in items],
        total=total,
        page=page,
        page_size=page_size,
        pages=pages
    )


@router.delete("/{feedback_id}", status_code=status.HTTP_200_OK)
def delete_feedback(
    feedback_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Delete a feedback submission."""
    service = FeedbackService(db)
    success = service.delete_feedback(feedback_id, current_user.id, current_user.role == "ADMIN")
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"code": "FEEDBACK_NOT_FOUND", "message": f"Feedback {feedback_id} not found."}
        )
    return {"success": True, "message": "Feedback deleted successfully"}

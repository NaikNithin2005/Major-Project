import math
from fastapi import APIRouter, Depends, Query, Request, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.user import User
from app.auth.dependencies import get_current_user
from app.schemas.feedback import FeedbackCreateRequest, FeedbackResponse
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
    Submit user feedback on a threat detection (e.g. TRUE_POSITIVE, FALSE_POSITIVE).
    Verifies that the threat event exists and belongs to the authenticated user.
    """
    service = FeedbackService(db)
    client_ip = req.client.host if req.client else "127.0.0.1"
    feedback = service.submit_feedback(
        request=request_data,
        user=current_user,
        ip_address=client_ip
    )
    return FeedbackResponse.model_validate(feedback)


@router.get("", response_model=PaginatedResponse[FeedbackResponse])
def list_feedback(
    page: int = Query(default=1, ge=1, description="Page number"),
    page_size: int = Query(default=20, ge=1, le=100, description="Items per page"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    List feedback submissions for the current authenticated user (or all if admin).
    """
    skip = (page - 1) * page_size
    service = FeedbackService(db)
    if current_user.role == "ADMIN":
        items, total = service.list_all_feedback(skip=skip, limit=page_size)
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

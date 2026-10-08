from typing import List, Optional, Tuple
from sqlalchemy.orm import Session
from sqlalchemy import select, func
from app.models.feedback import Feedback
from app.repositories.base import BaseRepository


class FeedbackRepository(BaseRepository[Feedback]):
    def __init__(self, db: Session):
        super().__init__(Feedback, db)

    def list_user_feedback(self, user_id: str, skip: int = 0, limit: int = 20) -> Tuple[List[Feedback], int]:
        total_stmt = select(func.count()).select_from(Feedback).where(Feedback.user_id == user_id)
        total = self.db.scalar(total_stmt) or 0

        stmt = (
            select(Feedback)
            .where(Feedback.user_id == user_id)
            .order_by(Feedback.created_at.desc())
            .offset(skip)
            .limit(limit)
        )
        return list(self.db.scalars(stmt).all()), total

    def list_all_feedback(self, skip: int = 0, limit: int = 50) -> Tuple[List[Feedback], int]:
        total_stmt = select(func.count()).select_from(Feedback)
        total = self.db.scalar(total_stmt) or 0

        stmt = select(Feedback).order_by(Feedback.created_at.desc()).offset(skip).limit(limit)
        return list(self.db.scalars(stmt).all()), total

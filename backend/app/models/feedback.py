import uuid
from datetime import datetime, timezone
from sqlalchemy import String, Text, DateTime, ForeignKey, Float
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base


class Feedback(Base):
    __tablename__ = "feedback"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    threat_event_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("threat_events.id", ondelete="CASCADE"), index=True, nullable=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    
    classification: Mapped[str] = mapped_column(String(32), index=True, nullable=False)  # TRUE_POSITIVE, FALSE_POSITIVE, etc.
    comment: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

    # Extended Phase 8 Intelligent Feedback Fields (Privacy-conscious hashed records)
    message_hash: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)
    sender_hash: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)
    original_prediction: Mapped[str | None] = mapped_column(String(32), nullable=True)
    corrected_label: Mapped[str | None] = mapped_column(String(32), index=True, nullable=True)
    original_confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    feedback_source: Mapped[str] = mapped_column(String(16), default="ANDROID", index=True, nullable=False)
    review_status: Mapped[str] = mapped_column(String(16), default="PENDING", index=True, nullable=False)
    training_status: Mapped[str] = mapped_column(String(32), default="UNPROCESSED", index=True, nullable=False)
    sender_reputation_score: Mapped[float | None] = mapped_column(Float, nullable=True)

    # Relationships
    threat_event = relationship("ThreatEvent", back_populates="feedback")
    user = relationship("User", back_populates="feedback")

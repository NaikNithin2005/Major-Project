import uuid
from datetime import datetime, timezone
from sqlalchemy import String, Integer, DateTime, Text, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base


class SafetyAlert(Base):
    __tablename__ = "safety_alerts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    threat_event_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("threat_events.id", ondelete="SET NULL"), index=True, nullable=True)
    
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    sender: Mapped[str | None] = mapped_column(String(128), index=True, nullable=True)
    message_preview: Mapped[str | None] = mapped_column(Text, nullable=True)
    threat_category: Mapped[str] = mapped_column(String(32), default="HIGH", nullable=False)
    severity: Mapped[str] = mapped_column(String(32), default="HIGH", nullable=False)
    risk_score: Mapped[int] = mapped_column(Integer, default=85, nullable=False)
    detection_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    
    # Status: ACTIVE, ACKNOWLEDGED, RESOLVED, DISMISSED
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE", index=True, nullable=False)
    
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    resolved_by: Mapped[str | None] = mapped_column(String(36), nullable=True)
    resolution_notes: Mapped[str | None] = mapped_column(Text, nullable=True)
    
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)

    user = relationship("User", back_populates="safety_alerts")
    threat_event = relationship("ThreatEvent", backref="associated_alerts")

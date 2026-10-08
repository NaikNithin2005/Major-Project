import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List
from sqlalchemy import String, Integer, DateTime, ForeignKey, CheckConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base
from app.database.types import JSONBCompat


class ThreatEvent(Base):
    __tablename__ = "threat_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True)
    device_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("devices.id", ondelete="SET NULL"), index=True, nullable=True)
    
    event_type: Mapped[str] = mapped_column(String(16), index=True, nullable=False)  # "QR" or "SMS"
    risk_score: Mapped[int] = mapped_column(Integer, index=True, nullable=False)     # 0 to 100
    threat_category: Mapped[str] = mapped_column(String(16), index=True, nullable=False)  # SAFE, LOW, MEDIUM, HIGH
    recommended_action: Mapped[str] = mapped_column(String(64), nullable=False)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True, nullable=False)

    # Structured AI/Detection Breakdown (Portable JSONB)
    model_versions: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    model_results: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    rule_results: Mapped[List[Dict[str, Any]] | None] = mapped_column(JSONBCompat, nullable=True)
    context_results: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    reasons: Mapped[List[str] | None] = mapped_column(JSONBCompat, nullable=True)
    
    # Metadata (Data Minimized - NO raw SMS!)
    url_metadata: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    qr_metadata: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    
    client_event_id: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)
    related_threat_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    campaign_id: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="REPORTED", index=True, nullable=False)
    user_action: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)

    __table_args__ = (
        CheckConstraint("risk_score >= 0 AND risk_score <= 100", name="check_risk_score_range"),
    )

    # Relationships
    user = relationship("User", back_populates="threat_events")
    device = relationship("Device", back_populates="threat_events")
    feedback = relationship("Feedback", back_populates="threat_event", cascade="all, delete-orphan")

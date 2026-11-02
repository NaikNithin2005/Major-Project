import uuid
from datetime import datetime, timezone
from sqlalchemy import String, Integer, Boolean, DateTime, Text, ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base


class SenderSecurity(Base):
    __tablename__ = "sender_security"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    sender_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    display_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    
    # Trust status: TRUSTED, UNTRUSTED, BLOCKED, UNVERIFIED
    trust_status: Mapped[str] = mapped_column(String(32), default="UNVERIFIED", index=True, nullable=False)
    
    # Verification: VERIFIED, UNVERIFIED, SUSPICIOUS, CONFIRMED_MALICIOUS
    verification_status: Mapped[str] = mapped_column(String(32), default="UNVERIFIED", nullable=False)
    
    reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    marked_by: Mapped[str] = mapped_column(String(32), default="USER", nullable=False) # USER, SYSTEM, ADMIN
    
    # Risk and stats
    risk_score: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    total_messages_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    legitimate_messages_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    suspicious_messages_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    dangerous_messages_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    
    last_message_preview: Mapped[str | None] = mapped_column(Text, nullable=True)
    last_message_date: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    
    # Blocking info
    is_blocked: Mapped[bool] = mapped_column(Boolean, default=False, index=True, nullable=False)
    blocked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    blocked_by: Mapped[str | None] = mapped_column(String(32), nullable=True)
    block_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    
    trusted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)

    __table_args__ = (
        UniqueConstraint("user_id", "sender_id", name="uq_user_sender_security"),
    )

    user = relationship("User", back_populates="sender_security_records")

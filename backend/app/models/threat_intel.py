import uuid
from datetime import datetime, timezone
from typing import Any, Dict
from sqlalchemy import String, Float, DateTime
from sqlalchemy.orm import Mapped, mapped_column
from app.database.base import Base
from app.database.types import JSONBCompat


class ThreatIntel(Base):
    __tablename__ = "threat_intel"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    indicator_type: Mapped[str] = mapped_column(String(32), index=True, nullable=False)   # DOMAIN, URL, IP, SENDER
    indicator_value: Mapped[str] = mapped_column(String(512), unique=True, index=True, nullable=False)
    threat_type: Mapped[str] = mapped_column(String(32), index=True, nullable=False)       # QUISHING, SMISHING, PHISHING
    confidence: Mapped[float] = mapped_column(Float, default=1.0, nullable=False)
    source: Mapped[str] = mapped_column(String(64), default="INTERNAL", nullable=False)
    first_seen: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    last_seen: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE", index=True, nullable=False)
    metadata_json: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)

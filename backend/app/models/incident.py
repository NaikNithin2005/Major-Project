import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List
from sqlalchemy import String, Text, DateTime, ForeignKey, UniqueConstraint, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base
from app.database.types import JSONBCompat


class Incident(Base):
    __tablename__ = "incidents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title: Mapped[str] = mapped_column(String(255), nullable=False, index=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    severity: Mapped[str] = mapped_column(String(16), default="MEDIUM", index=True, nullable=False)  # LOW, MEDIUM, HIGH, CRITICAL
    status: Mapped[str] = mapped_column(String(32), default="NEW", index=True, nullable=False)      # NEW, TRIAGED, INVESTIGATING, CONTAINED, RESOLVED, CLOSED
    category: Mapped[str] = mapped_column(String(32), default="UNKNOWN", index=True, nullable=False)  # QUISHING_CAMPAIGN, SMISHING_SURGE, BRAND_IMPERSONATION, MALICIOUS_INFRASTRUCTURE, ACCOUNT_TAKEOVER_ATTEMPT, UNKNOWN

    assigned_admin_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True)
    created_by_admin_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)
    triaged_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    contained_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    closed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    resolution_summary: Mapped[str | None] = mapped_column(Text, nullable=True)
    metadata_json: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)

    # Relationships
    assigned_admin = relationship("User", foreign_keys=[assigned_admin_id])
    created_by_admin = relationship("User", foreign_keys=[created_by_admin_id])
    threat_links = relationship("IncidentThreatLink", back_populates="incident", cascade="all, delete-orphan")
    notes = relationship("IncidentNote", back_populates="incident", cascade="all, delete-orphan", order_by="IncidentNote.created_at.desc()")
    events = relationship("IncidentEvent", back_populates="incident", cascade="all, delete-orphan", order_by="IncidentEvent.timestamp.desc()")


class IncidentThreatLink(Base):
    __tablename__ = "incident_threat_links"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    incident_id: Mapped[str] = mapped_column(String(36), ForeignKey("incidents.id", ondelete="CASCADE"), index=True, nullable=False)
    threat_event_id: Mapped[str] = mapped_column(String(36), ForeignKey("threat_events.id", ondelete="CASCADE"), index=True, nullable=False)
    
    relationship_type: Mapped[str] = mapped_column(String(32), default="LIKELY", nullable=False)  # CONFIRMED, LIKELY, UNVERIFIED
    correlation_signal: Mapped[str | None] = mapped_column(String(64), nullable=True)  # MATCHING_DOMAIN, MATCHING_URL, SIMILAR_CAMPAIGN, MANUAL_CORRELATION, SAME_SENDER
    linked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    linked_by_admin_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

    __table_args__ = (
        UniqueConstraint("incident_id", "threat_event_id", name="uq_incident_threat_link"),
    )

    # Relationships
    incident = relationship("Incident", back_populates="threat_links")
    threat_event = relationship("ThreatEvent")
    linked_by = relationship("User", foreign_keys=[linked_by_admin_id])


class IncidentNote(Base):
    __tablename__ = "incident_notes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    incident_id: Mapped[str] = mapped_column(String(36), ForeignKey("incidents.id", ondelete="CASCADE"), index=True, nullable=False)
    author_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    
    note_type: Mapped[str] = mapped_column(String(32), default="OBSERVATION", nullable=False)  # OBSERVATION, ANALYSIS, REMEDIATION, EVIDENCE_NOTE
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)

    # Relationships
    incident = relationship("Incident", back_populates="notes")
    author = relationship("User")


class IncidentEvent(Base):
    __tablename__ = "incident_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    incident_id: Mapped[str] = mapped_column(String(36), ForeignKey("incidents.id", ondelete="CASCADE"), index=True, nullable=False)
    actor_user_id: Mapped[str | None] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True)
    
    event_type: Mapped[str] = mapped_column(String(64), index=True, nullable=False)  # STATUS_CHANGE, ASSIGNMENT_CHANGE, SEVERITY_CHANGE, THREAT_LINKED, THREAT_UNLINKED, NOTE_ADDED, RESOLUTION_LOGGED, REOPENED
    previous_state: Mapped[str | None] = mapped_column(String(64), nullable=True)
    new_state: Mapped[str | None] = mapped_column(String(64), nullable=True)
    summary: Mapped[str] = mapped_column(String(512), nullable=False)
    details_json: Mapped[Dict[str, Any] | None] = mapped_column(JSONBCompat, nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True, nullable=False)

    # Relationships
    incident = relationship("Incident", back_populates="events")
    actor = relationship("User")
